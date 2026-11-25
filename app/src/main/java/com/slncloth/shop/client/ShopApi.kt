package com.slncloth.shop.client

import com.slncloth.shop.core.api.AuditEventDto
import com.slncloth.shop.core.api.BillDto
import com.slncloth.shop.core.api.BillSummaryDto
import com.slncloth.shop.core.api.CreateBillRequest
import com.slncloth.shop.core.api.CreateCustomerRequest
import com.slncloth.shop.core.api.CreateUserRequest
import com.slncloth.shop.core.api.CustomerDetailDto
import com.slncloth.shop.core.api.CustomerDto
import com.slncloth.shop.core.api.DeviceDto
import com.slncloth.shop.core.api.HealthResponse
import com.slncloth.shop.core.api.LoginRequest
import com.slncloth.shop.core.api.LoginResponse
import com.slncloth.shop.core.api.MergeCustomerRequest
import com.slncloth.shop.core.api.OkResponse
import com.slncloth.shop.core.api.OverviewResponse
import com.slncloth.shop.core.api.PairRequest
import com.slncloth.shop.core.api.PairResponse
import com.slncloth.shop.core.api.PairingTokenResponse
import com.slncloth.shop.core.api.RequestStatusResponse
import com.slncloth.shop.core.api.ResolveItemRequest
import com.slncloth.shop.core.api.ResolveItemResponse
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
import com.slncloth.shop.security.SecureStore

/** Typed wrapper over [ApiClient] — one method per server route. */
class ShopApi(val client: ApiClient, private val secureStore: SecureStore) {

    init {
        client.sessionTokenProvider = { secureStore.get(SecureStore.KEY_SESSION) }
    }

    private fun <T> encode(value: T, serializer: kotlinx.serialization.KSerializer<T>): String =
        client.encode(value, serializer)

    suspend fun health(): HealthResponse =
        client.get("api/v1/health", HealthResponse.serializer())

    suspend fun pair(request: PairRequest): PairResponse =
        client.post("api/v1/pair", encode(request, PairRequest.serializer()), PairResponse.serializer())

    suspend fun login(pin: String): LoginResponse =
        client.post("api/v1/auth/login", encode(LoginRequest(pin), LoginRequest.serializer()), LoginResponse.serializer())

    suspend fun logout(): OkResponse =
        client.post("api/v1/auth/logout", "{}", OkResponse.serializer())

    suspend fun session(): SessionResponse =
        client.get("api/v1/session", SessionResponse.serializer())

    suspend fun shop(): ShopResponse =
        client.get("api/v1/shop", ShopResponse.serializer())

    // ---------------------------------------------------------------- customers

    suspend fun customers(query: String, limit: Int = 50): List<CustomerDto> =
        client.get(
            "api/v1/customers",
            customerListSerializer,
            mapOf("q" to query, "limit" to limit.toString())
        )

    suspend fun customer(id: String): CustomerDetailDto =
        client.get("api/v1/customers/$id", CustomerDetailDto.serializer())

    suspend fun createCustomer(request: CreateCustomerRequest): CustomerDto =
        client.post("api/v1/customers", encode(request, CreateCustomerRequest.serializer()), CustomerDto.serializer())

    suspend fun updateCustomer(id: String, request: UpdateCustomerRequest): CustomerDto =
        client.patch("api/v1/customers/$id", encode(request, UpdateCustomerRequest.serializer()), CustomerDto.serializer())

    suspend fun mergeCustomer(fromId: String, targetId: String): OkResponse =
        client.post(
            "api/v1/customers/$fromId/merge",
            encode(MergeCustomerRequest(targetId), MergeCustomerRequest.serializer()),
            OkResponse.serializer()
        )

    // ---------------------------------------------------------------- items + bills

    suspend fun resolveItem(request: ResolveItemRequest): ResolveItemResponse =
        client.post("api/v1/items/resolve", encode(request, ResolveItemRequest.serializer()), ResolveItemResponse.serializer())

    suspend fun createBill(request: CreateBillRequest): BillDto =
        client.post("api/v1/bills", encode(request, CreateBillRequest.serializer()), BillDto.serializer())

    suspend fun bills(
        query: String,
        from: Long,
        to: Long,
        status: String = "",
        limit: Int = 50,
    ): List<BillSummaryDto> =
        client.get(
            "api/v1/bills",
            billSummaryListSerializer,
            mapOf(
                "q" to query,
                "from" to from.toString(),
                "to" to to.toString(),
                "status" to status,
                "limit" to limit.toString(),
            )
        )

    suspend fun bill(id: Long): BillDto =
        client.get("api/v1/bills/$id", BillDto.serializer())

    suspend fun requestStatus(requestKey: String): RequestStatusResponse =
        client.get("api/v1/bills/request/$requestKey", RequestStatusResponse.serializer())

    suspend fun reverseBill(id: Long, request: ReverseBillRequest): BillDto =
        client.post(
            "api/v1/bills/$id/reverse",
            encode(request, ReverseBillRequest.serializer()),
            BillDto.serializer()
        )

    // ---------------------------------------------------------------- wallet

    suspend fun walletSummary(): WalletSummaryDto =
        client.get("api/v1/wallet/summary", WalletSummaryDto.serializer())

    suspend fun walletRecent(limit: Int = 100): List<WalletEntryDto> =
        client.get(
            "api/v1/wallet/recent",
            walletEntryListSerializer,
            mapOf("limit" to limit.toString())
        )

    suspend fun walletForCustomer(customerId: String, limit: Int = 100): List<WalletEntryDto> =
        client.get(
            "api/v1/wallet/customers/$customerId",
            walletEntryListSerializer,
            mapOf("limit" to limit.toString())
        )

    suspend fun walletAdjust(request: WalletAdjustRequest): WalletEntryDto =
        client.post("api/v1/wallet/adjust", encode(request, WalletAdjustRequest.serializer()), WalletEntryDto.serializer())

    suspend fun walletVerify(): WalletVerifyResponse =
        client.get("api/v1/wallet/verify", WalletVerifyResponse.serializer())

    // ---------------------------------------------------------------- overview

    suspend fun overview(from: Long, to: Long): OverviewResponse =
        client.get(
            "api/v1/overview",
            OverviewResponse.serializer(),
            mapOf("from" to from.toString(), "to" to to.toString())
        )

    // ---------------------------------------------------------------- admin

    suspend fun adminSettings(): SettingsDto =
        client.get("api/v1/admin/settings", SettingsDto.serializer())

    suspend fun updateSettings(request: UpdateSettingsRequest): SettingsDto =
        client.put("api/v1/admin/settings", encode(request, UpdateSettingsRequest.serializer()), SettingsDto.serializer())

    suspend fun users(): List<UserDto> =
        client.get("api/v1/admin/users", userListSerializer)

    suspend fun createUser(request: CreateUserRequest): UserDto =
        client.post("api/v1/admin/users", encode(request, CreateUserRequest.serializer()), UserDto.serializer())

    suspend fun updateUser(id: String, request: UpdateUserRequest): UserDto =
        client.patch("api/v1/admin/users/$id", encode(request, UpdateUserRequest.serializer()), UserDto.serializer())

    suspend fun devices(): List<DeviceDto> =
        client.get("api/v1/admin/devices", deviceListSerializer)

    suspend fun revokeDevice(id: String): DeviceDto =
        client.post("api/v1/admin/devices/$id/revoke", "{}", DeviceDto.serializer())

    suspend fun audit(limit: Int = 100): List<AuditEventDto> =
        client.get("api/v1/admin/audit", auditListSerializer, mapOf("limit" to limit.toString()))

    suspend fun createPairingToken(url: String): PairingTokenResponse =
        client.post(
            "api/v1/admin/pairing-token?url=${java.net.URLEncoder.encode(url, "UTF-8")}",
            "{}",
            PairingTokenResponse.serializer()
        )

    suspend fun clearPairingToken(): OkResponse =
        client.delete("api/v1/admin/pairing-token", OkResponse.serializer())

    companion object {
        val customerListSerializer = kotlinx.serialization.builtins.ListSerializer(CustomerDto.serializer())
        val billSummaryListSerializer = kotlinx.serialization.builtins.ListSerializer(BillSummaryDto.serializer())
        val walletEntryListSerializer = kotlinx.serialization.builtins.ListSerializer(WalletEntryDto.serializer())
        val userListSerializer = kotlinx.serialization.builtins.ListSerializer(UserDto.serializer())
        val deviceListSerializer = kotlinx.serialization.builtins.ListSerializer(DeviceDto.serializer())
        val auditListSerializer = kotlinx.serialization.builtins.ListSerializer(AuditEventDto.serializer())
    }
}
