package com.slncloth.shop.server

import android.util.Log
import com.slncloth.shop.core.api.ApiException
import com.slncloth.shop.core.api.AppJson
import com.slncloth.shop.core.api.AuditEventDto
import com.slncloth.shop.core.api.BillDto
import com.slncloth.shop.core.api.CreateBillRequest
import com.slncloth.shop.core.api.CreateCustomerRequest
import com.slncloth.shop.core.api.CreateUserRequest
import com.slncloth.shop.core.api.CustomerDetailDto
import com.slncloth.shop.core.api.CustomerDto
import com.slncloth.shop.core.api.DeviceDto
import com.slncloth.shop.core.api.ErrorResponse
import com.slncloth.shop.core.api.HealthResponse
import com.slncloth.shop.core.api.LoginRequest
import com.slncloth.shop.core.api.LoginResponse
import com.slncloth.shop.core.api.MergeCustomerRequest
import com.slncloth.shop.core.api.OverviewResponse
import com.slncloth.shop.core.api.PairRequest
import com.slncloth.shop.core.api.PairResponse
import com.slncloth.shop.core.api.PairingTokenResponse
import com.slncloth.shop.core.api.ResolveItemRequest
import com.slncloth.shop.core.api.ResolveItemResponse
import com.slncloth.shop.core.api.RequestStatusResponse
import com.slncloth.shop.core.api.ReverseBillRequest
import com.slncloth.shop.core.api.SessionResponse
import com.slncloth.shop.core.api.SettingsDto
import com.slncloth.shop.core.api.ShopResponse
import com.slncloth.shop.core.api.UpdateCustomerRequest
import com.slncloth.shop.core.api.UpdateSettingsRequest
import com.slncloth.shop.core.api.UpdateUserRequest
import com.slncloth.shop.core.api.UserDto
import com.slncloth.shop.core.api.WalletAdjustRequest
import com.slncloth.shop.core.api.WalletEntryDto
import com.slncloth.shop.core.api.WalletSummaryDto
import com.slncloth.shop.core.api.WalletVerifyResponse
import com.slncloth.shop.data.db.DatabaseProvider
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.serialization.kotlinx.json.json

/** Constructs all server-side services once. */
class ServerServices(val dbProvider: DatabaseProvider) {
    val audit = AuditLogger { dbProvider.get() }
    val auth = AuthRepository(dbProvider, audit)
    val customers = CustomerRepository(dbProvider, audit)
    val billing = BillingService(dbProvider, audit)
    val wallet = WalletService(dbProvider, audit)
    val overview = OverviewService(dbProvider)
    val admin = AdminService(dbProvider, audit, auth)
}

object ShopServer {

    const val API = "/api/v1"
    const val SERVER_VERSION = "1.0.0"
    const val DB_VERSION = 2

    fun Application.shopModule(
        services: ServerServices,
        serverFingerprint: () -> String,
    ) {
        install(ContentNegotiation) {
            json(AppJson.json)
        }

        install(StatusPages) {
            exception<ApiException> { call, cause ->
                call.respond(HttpStatusCode.fromValue(cause.status), ErrorResponse(cause.code, cause.message))
            }
            exception<ContentConvertException> { call, _ ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_JSON", "Request body is not valid"))
            }
            exception<Throwable> { call, cause ->
                // Never log request bodies: they contain PINs and secret item codes.
                Log.e(TAG, "Unhandled API failure: ${cause::class.java.simpleName}")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("SERVER_ERROR", "The server could not complete the request")
                )
            }
        }

        routing {
            // ---------------------------------------------------------------- health (public, minimal)
            get("$API/health") {
                val db = services.dbProvider.get()
                val settings = db.settingsDao().get()
                val pairingActive = db.pairingDao().get()?.let { it.expiresAt > System.currentTimeMillis() } ?: false
                call.respond(
                    HealthResponse(
                        ok = true,
                        shopName = settings?.shopName ?: "Shop not set up",
                        fingerprint = serverFingerprint(),
                        pairingEnabled = pairingActive,
                        serverVersion = SERVER_VERSION,
                        dbVersion = DB_VERSION,
                    )
                )
            }

            // ---------------------------------------------------------------- pairing + auth
            post("$API/pair") {
                val req = call.receive<PairRequest>()
                val result: PairResponse = services.auth.pair(req.pairingToken, req.deviceName)
                call.respond(result)
            }

            post("$API/auth/login") {
                val deviceToken = call.request.header("X-Device-Token")
                    ?: throw ApiException.unauthorized("Device token missing")
                val req = call.receive<LoginRequest>()
                val result: LoginResponse = services.auth.login(deviceToken, req.pin)
                call.respond(result)
            }

            post("$API/auth/logout") {
                val ctx = call.authContext(services)
                services.auth.logout(rawBearer(call))
                call.respond(mapOf("ok" to true))
            }

            get("$API/session") {
                val ctx = call.authContext(services)
                val device = services.dbProvider.get().deviceDao().all().firstOrNull { it.id == ctx.deviceId }
                call.respond(
                    SessionResponse(
                        role = ctx.role.name,
                        userName = ctx.userName,
                        expiresAt = services.dbProvider.get().sessionDao().byTokenHash(ctx.sessionTokenHash)?.expiresAt
                            ?: 0L,
                        deviceId = ctx.deviceId,
                        deviceName = device?.name ?: "",
                    )
                )
            }

            get("$API/shop") {
                call.authContext(services)
                val settings = services.dbProvider.get().settingsDao().get()
                    ?: throw ApiException.server("Shop settings are missing")
                call.respond(
                    ShopResponse(
                        shopName = settings.shopName,
                        address = settings.address,
                        cashbackBp = settings.cashbackBp,
                        allowStaffPriceOverride = settings.allowStaffPriceOverride,
                        receiptFooter = settings.receiptFooter,
                    )
                )
            }

            // ---------------------------------------------------------------- customers
            get("$API/customers") {
                call.authContext(services)
                val q = call.request.queryParameters["q"].orEmpty()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val result: List<CustomerDto> = services.customers.search(q, limit)
                call.respond(result)
            }

            post("$API/customers") {
                val ctx = call.authContext(services)
                val req = call.receive<CreateCustomerRequest>()
                call.respond(services.customers.create(ctx, req))
            }

            get("$API/customers/{id}") {
                call.authContext(services)
                val id = call.parameter("id")
                val result: CustomerDetailDto = services.customers.detail(id)
                call.respond(result)
            }

            patch("$API/customers/{id}") {
                val ctx = call.authContext(services)
                val id = call.parameter("id")
                val req = call.receive<UpdateCustomerRequest>()
                call.respond(services.customers.update(ctx, id, req))
            }

            post("$API/customers/{id}/merge") {
                val ctx = call.authContext(services, ownerOnly = true)
                val id = call.parameter("id")
                val req = call.receive<MergeCustomerRequest>()
                services.customers.merge(ctx, id, req.targetId)
                call.respond(mapOf("ok" to true))
            }

            // ---------------------------------------------------------------- items + bills
            post("$API/items/resolve") {
                val ctx = call.authContext(services)
                val req = call.receive<ResolveItemRequest>()
                val result: ResolveItemResponse = services.billing.resolveItem(ctx, req)
                call.respond(result)
            }

            post("$API/bills") {
                val ctx = call.authContext(services)
                val req = call.receive<CreateBillRequest>()
                val result: BillDto = services.billing.createBill(ctx, req)
                call.respond(result)
            }

            get("$API/bills") {
                call.authContext(services)
                val q = call.request.queryParameters["q"].orEmpty()
                val from = call.request.queryParameters["from"]?.toLongOrNull() ?: 0L
                val to = call.request.queryParameters["to"]?.toLongOrNull() ?: Long.MAX_VALUE
                val status = call.request.queryParameters["status"].orEmpty()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                call.respond(services.billing.listBills(q, from, to, status, limit))
            }

            get("$API/bills/request/{key}") {
                call.authContext(services)
                val key = call.parameter("key")
                val result: RequestStatusResponse = services.billing.requestStatus(key)
                call.respond(result)
            }

            get("$API/bills/{id}") {
                call.authContext(services)
                val id = call.longParameter("id")
                val result: BillDto = services.billing.getBill(id)
                call.respond(result)
            }

            post("$API/bills/{id}/reverse") {
                val ctx = call.authContext(services, ownerOnly = true)
                val id = call.longParameter("id")
                val req = call.receive<ReverseBillRequest>()
                val result: BillDto = services.billing.reverseBill(ctx, id, req)
                call.respond(result)
            }

            // ---------------------------------------------------------------- wallet
            get("$API/wallet/summary") {
                call.authContext(services)
                val result: WalletSummaryDto = services.wallet.summary()
                call.respond(result)
            }

            get("$API/wallet/recent") {
                call.authContext(services)
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val result: List<WalletEntryDto> = services.wallet.recent(limit)
                call.respond(result)
            }

            get("$API/wallet/customers/{id}") {
                call.authContext(services)
                val id = call.parameter("id")
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                call.respond(services.wallet.entriesForCustomer(id, limit))
            }

            post("$API/wallet/adjust") {
                val ctx = call.authContext(services, ownerOnly = true)
                val req = call.receive<WalletAdjustRequest>()
                call.respond(services.wallet.adjust(ctx, req))
            }

            get("$API/wallet/verify") {
                call.authContext(services, ownerOnly = true)
                val result: WalletVerifyResponse = services.wallet.verify()
                call.respond(result)
            }

            // ---------------------------------------------------------------- overview (owner)
            get("$API/overview") {
                call.authContext(services, ownerOnly = true)
                val now = System.currentTimeMillis()
                val from = call.request.queryParameters["from"]?.toLongOrNull() ?: (now - 30L * 86_400_000L)
                val to = call.request.queryParameters["to"]?.toLongOrNull() ?: now
                val result: OverviewResponse = services.overview.overview(from, to)
                call.respond(result)
            }

            // ---------------------------------------------------------------- admin (owner)
            get("$API/admin/settings") {
                call.authContext(services, ownerOnly = true)
                val result: SettingsDto = services.admin.getSettings()
                call.respond(result)
            }

            put("$API/admin/settings") {
                val ctx = call.authContext(services, ownerOnly = true)
                val req = call.receive<UpdateSettingsRequest>()
                call.respond(services.admin.updateSettings(ctx, req))
            }

            get("$API/admin/users") {
                call.authContext(services, ownerOnly = true)
                val result: List<UserDto> = services.admin.users()
                call.respond(result)
            }

            post("$API/admin/users") {
                val ctx = call.authContext(services, ownerOnly = true)
                val req = call.receive<CreateUserRequest>()
                call.respond(services.admin.createUser(ctx, req))
            }

            patch("$API/admin/users/{id}") {
                val ctx = call.authContext(services, ownerOnly = true)
                val id = call.parameter("id")
                val req = call.receive<UpdateUserRequest>()
                call.respond(services.admin.updateUser(ctx, id, req))
            }

            get("$API/admin/devices") {
                call.authContext(services, ownerOnly = true)
                val result: List<DeviceDto> = services.admin.devices()
                call.respond(result)
            }

            post("$API/admin/devices/{id}/revoke") {
                val ctx = call.authContext(services, ownerOnly = true)
                val id = call.parameter("id")
                call.respond(services.admin.revokeDevice(ctx, id))
            }

            get("$API/admin/audit") {
                call.authContext(services, ownerOnly = true)
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val result: List<AuditEventDto> = services.admin.auditEvents(limit)
                call.respond(result)
            }

            post("$API/admin/pairing-token") {
                val ctx = call.authContext(services, ownerOnly = true)
                val url = call.request.queryParameters["url"].orEmpty()
                if (url.isBlank()) throw ApiException.badRequest("Missing server URL")
                val result: PairingTokenResponse = services.admin.createPairingToken(ctx, url, serverFingerprint())
                call.respond(result)
            }

            delete("$API/admin/pairing-token") {
                val ctx = call.authContext(services, ownerOnly = true)
                services.dbProvider.get().pairingDao().clear()
                services.audit.log(ctx, "PAIRING_TOKEN_CLEARED", "pairing", "1", "Pairing token cleared")
                call.respond(mapOf("ok" to true))
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun ApplicationCall.authContext(
        services: ServerServices,
        ownerOnly: Boolean = false,
    ): AuthContext {
        val header = request.header(HttpHeaders.Authorization) ?: throw ApiException.unauthorized()
        if (!header.startsWith("Bearer ", ignoreCase = true)) {
            throw ApiException.unauthorized("Invalid authorization header")
        }
        val raw = header.substring(7).trim()
        val ctx = services.auth.session(raw)
        if (ownerOnly) ctx.requireOwner()
        return ctx
    }

    private fun ApplicationCall.rawBearer(): String =
        request.header(HttpHeaders.Authorization)?.substringAfter("Bearer ", "")?.trim().orEmpty()

    private fun ApplicationCall.parameter(name: String): String {
        val value = parameters[name]
        if (value.isNullOrBlank()) throw ApiException.badRequest("Missing $name")
        return value
    }

    private fun ApplicationCall.longParameter(name: String): Long =
        parameters[name]?.toLongOrNull() ?: throw ApiException.badRequest("Invalid $name")

    private const val TAG = "ShopServer"
}
