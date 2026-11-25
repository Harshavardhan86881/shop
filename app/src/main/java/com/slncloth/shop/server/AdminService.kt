package com.slncloth.shop.server

import com.slncloth.shop.core.api.ApiException
import com.slncloth.shop.core.api.AuditEventDto
import com.slncloth.shop.core.api.CreateUserRequest
import com.slncloth.shop.core.api.DeviceDto
import com.slncloth.shop.core.api.PairingTokenResponse
import com.slncloth.shop.core.api.SettingsDto
import com.slncloth.shop.core.api.UpdateSettingsRequest
import com.slncloth.shop.core.api.UpdateUserRequest
import com.slncloth.shop.core.api.UserDto
import com.slncloth.shop.core.util.Ids
import com.slncloth.shop.core.util.Validation
import com.slncloth.shop.data.db.DatabaseProvider
import com.slncloth.shop.data.db.entity.UserEntity
import com.slncloth.shop.security.PinHasher

class AdminService(
    private val dbProvider: DatabaseProvider,
    private val audit: AuditLogger,
    private val authRepository: AuthRepository,
) {

    private data class ValidatedSettings(
        val shopName: String,
        val address: String,
        val ownerName: String,
        val markupBp: Int,
        val cashbackBp: Int,
        val allowStaffPriceOverride: Boolean,
        val receiptFooter: String,
        val serverPort: Int,
        val bindAddress: String,
    )

    suspend fun getSettings(): SettingsDto {
        val s = dbProvider.get().settingsDao().get() ?: throw ApiException.server("Shop settings are missing")
        return s.toDto()
    }

    suspend fun updateSettings(ctx: AuthContext, req: UpdateSettingsRequest): SettingsDto {
        ctx.requireOwner()
        val validated = validateSettings(req)
        val db = dbProvider.get()
        val existing = db.settingsDao().get() ?: throw ApiException.server("Shop settings are missing")
        val updated = existing.copy(
            shopName = validated.shopName,
            address = validated.address,
            ownerName = validated.ownerName,
            markupBp = validated.markupBp,
            cashbackBp = validated.cashbackBp,
            allowStaffPriceOverride = validated.allowStaffPriceOverride,
            receiptFooter = validated.receiptFooter,
            serverPort = validated.serverPort,
            bindAddress = validated.bindAddress,
            updatedAt = System.currentTimeMillis(),
        )
        db.settingsDao().upsert(updated)
        audit.log(ctx, "SETTINGS_UPDATED", "settings", "1", "Shop settings updated")
        return updated.toDto()
    }

    suspend fun users(): List<UserDto> = dbProvider.get().userDao().all().map { it.toDto() }

    suspend fun createUser(ctx: AuthContext, req: CreateUserRequest): UserDto {
        ctx.requireOwner()
        val name = Validation.cleanText(req.name, 80)
        if (!Validation.validName(name)) throw ApiException.unprocessable("Name must be 2 to 80 characters", "INVALID_NAME")
        if (!Validation.validPin(req.pin)) throw ApiException.unprocessable("PIN must be 4 to 10 digits", "INVALID_PIN")
        if (req.role != "STAFF") throw ApiException.unprocessable("Only staff accounts can be created here", "INVALID_ROLE")

        val db = dbProvider.get()
        val hash = PinHasher.hash(req.pin.toCharArray())
        val user = UserEntity(
            id = Ids.uuid(),
            name = name,
            role = "STAFF",
            pinSalt = hash.salt,
            pinHash = hash.hash,
            pinIterations = hash.iterations,
            active = true,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null,
        )
        db.userDao().insert(user)
        audit.log(ctx, "STAFF_CREATED", "user", user.id, "Staff account created")
        return user.toDto()
    }

    suspend fun updateUser(ctx: AuthContext, userId: String, req: UpdateUserRequest): UserDto {
        ctx.requireOwner()
        val db = dbProvider.get()
        val existing = db.userDao().getById(userId) ?: throw ApiException.notFound("User not found")
        if (ctx.userId == userId && req.active == false) {
            throw ApiException.conflict("You cannot deactivate your own account while signed in")
        }
        val newName = req.name?.let { Validation.cleanText(it, 80) }
        if (newName != null && !Validation.validName(newName)) {
            throw ApiException.unprocessable("Name must be 2 to 80 characters", "INVALID_NAME")
        }
        if (req.pin != null && !Validation.validPin(req.pin)) {
            throw ApiException.unprocessable("PIN must be 4 to 10 digits", "INVALID_PIN")
        }
        val hash = req.pin?.let { PinHasher.hash(it.toCharArray()) }
        val updated = existing.copy(
            name = newName ?: existing.name,
            active = req.active ?: existing.active,
            pinSalt = hash?.salt ?: existing.pinSalt,
            pinHash = hash?.hash ?: existing.pinHash,
            pinIterations = hash?.iterations ?: existing.pinIterations,
        )
        db.userDao().update(updated)
        if (req.active == false) db.sessionDao().deleteForUser(userId)
        audit.log(ctx, "STAFF_UPDATED", "user", userId, "Staff account updated (role unchanged)")
        return updated.toDto()
    }

    suspend fun devices(): List<DeviceDto> = dbProvider.get().deviceDao().all().map { it.toDto() }

    suspend fun revokeDevice(ctx: AuthContext, deviceId: String): DeviceDto {
        ctx.requireOwner()
        val db = dbProvider.get()
        val device = db.deviceDao().all().firstOrNull { it.id == deviceId }
            ?: throw ApiException.notFound("Device not found")
        if (device.id == ctx.deviceId) throw ApiException.conflict("This is the phone you are using. Use another owner phone to revoke it.")
        val now = System.currentTimeMillis()
        db.deviceDao().revoke(deviceId, now)
        db.sessionDao().deleteForDevice(deviceId)
        audit.log(ctx, "DEVICE_REVOKED", "device", deviceId, "Paired device revoked: ${device.name}")
        return device.copy(revokedAt = now).toDto()
    }

    suspend fun auditEvents(limit: Int = 100): List<AuditEventDto> =
        dbProvider.get().auditDao().recent(limit.coerceIn(1, 500)).map {
            AuditEventDto(
                id = it.id,
                createdAt = it.createdAt,
                actorName = it.actorName,
                action = it.action,
                entityType = it.entityType,
                entityId = it.entityId,
                details = it.details,
            )
        }

    suspend fun createPairingToken(ctx: AuthContext, url: String, fingerprint: String): PairingTokenResponse {
        val shopName = dbProvider.get().settingsDao().get()?.shopName ?: ""
        return authRepository.createPairingToken(ctx, url, fingerprint, shopName)
    }

    private fun validateSettings(req: UpdateSettingsRequest): ValidatedSettings {
        val shopName = Validation.cleanText(req.shopName, 120)
        val address = Validation.cleanText(req.address, 300)
        val ownerName = Validation.cleanText(req.ownerName, 80)
        val footer = Validation.cleanText(req.receiptFooter, 300)
        if (!Validation.validName(shopName)) throw ApiException.unprocessable("Shop name must be 2 to 120 characters", "INVALID_SHOP_NAME")
        if (!Validation.validName(ownerName)) throw ApiException.unprocessable("Owner name must be 2 to 80 characters", "INVALID_OWNER_NAME")
        if (req.markupBp !in 0..10_000) throw ApiException.unprocessable("Markup must be between 0% and 100%", "INVALID_MARKUP")
        if (req.cashbackBp !in 0..2_000) throw ApiException.unprocessable("Cashback must be between 0% and 20%", "INVALID_CASHBACK")
        if (req.serverPort !in 1024..65_535) throw ApiException.unprocessable("Port must be between 1024 and 65535", "INVALID_PORT")
        if (req.bindAddress.isNotEmpty() && !IPV4.matches(req.bindAddress.trim())) {
            throw ApiException.unprocessable("Bind address must be a valid IPv4 address or blank", "INVALID_ADDRESS")
        }
        return ValidatedSettings(
            shopName = shopName,
            address = address,
            ownerName = ownerName,
            markupBp = req.markupBp,
            cashbackBp = req.cashbackBp,
            allowStaffPriceOverride = req.allowStaffPriceOverride,
            receiptFooter = footer,
            serverPort = req.serverPort,
            bindAddress = req.bindAddress.trim(),
        )
    }

    private fun UserEntity.toDto() = UserDto(
        id = id,
        name = name,
        role = role,
        active = active,
        createdAt = createdAt,
        lastLoginAt = lastLoginAt,
    )

    private fun com.slncloth.shop.data.db.entity.SettingsEntity.toDto() = SettingsDto(
        shopName = shopName,
        address = address,
        ownerName = ownerName,
        markupBp = markupBp,
        cashbackBp = cashbackBp,
        allowStaffPriceOverride = allowStaffPriceOverride,
        receiptFooter = receiptFooter,
        serverPort = serverPort,
        bindAddress = bindAddress,
    )

    private fun com.slncloth.shop.data.db.entity.DeviceEntity.toDto() = DeviceDto(
        id = id,
        name = name,
        pairedAt = pairedAt,
        lastSeenAt = lastSeenAt,
        revokedAt = revokedAt,
    )

    companion object {
        private val IPV4 = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
    }
}
