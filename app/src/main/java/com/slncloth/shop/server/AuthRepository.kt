package com.slncloth.shop.server

import com.slncloth.shop.core.api.ApiException
import com.slncloth.shop.core.api.LoginResponse
import com.slncloth.shop.core.api.PairResponse
import com.slncloth.shop.core.api.PairingTokenResponse
import com.slncloth.shop.core.util.Ids
import com.slncloth.shop.core.util.Validation
import com.slncloth.shop.data.db.DatabaseProvider
import com.slncloth.shop.data.db.entity.DeviceEntity
import com.slncloth.shop.data.db.entity.LoginAttemptEntity
import com.slncloth.shop.data.db.entity.PairingTokenEntity
import com.slncloth.shop.data.db.entity.SessionEntity
import com.slncloth.shop.data.db.entity.UserEntity
import com.slncloth.shop.security.PinHasher
import kotlinx.coroutines.delay

class AuthRepository(
    private val dbProvider: DatabaseProvider,
    private val audit: AuditLogger,
) {

    companion object {
        const val SESSION_TTL_MILLIS = 8 * 60 * 60 * 1000L
        const val MAX_FAILED_ATTEMPTS = 5
        const val FAILURE_WINDOW_MILLIS = 15 * 60 * 1000L
        const val PAIRING_TOKEN_TTL_MILLIS = 10 * 60 * 1000L
        private const val MIN_PIN = 4
        private const val MAX_PIN = 10
    }

    // ------------------------------------------------------------------ bootstrap / pairing

    /**
     * Trusted local bootstrap on the main phone during first-run setup.
     * Creates a device record for the main phone itself so its own UI uses the same
     * authenticated API as every other phone.
     */
    suspend fun bootstrapMainDevice(deviceName: String): String {
        val db = dbProvider.get()
        val raw = Ids.newToken(32)
        val now = System.currentTimeMillis()
        db.deviceDao().insert(
            DeviceEntity(
                id = Ids.uuid(),
                name = deviceName.take(60),
                tokenHash = Ids.sha256Hex(raw),
                pairedAt = now,
                lastSeenAt = now,
                revokedAt = null,
                createdByUserId = "setup",
            )
        )
        return raw
    }

    suspend fun createPairingToken(ctx: AuthContext, baseUrl: String, fingerprint: String, shopName: String): PairingTokenResponse {
        ctx.requireOwner()
        val raw = Ids.newToken(24)
        val now = System.currentTimeMillis()
        val expiresAt = now + PAIRING_TOKEN_TTL_MILLIS
        dbProvider.get().pairingDao().upsert(
            PairingTokenEntity(
                id = 1,
                tokenHash = Ids.sha256Hex(raw),
                createdAt = now,
                expiresAt = expiresAt,
            )
        )
        audit.log(ctx, "PAIRING_TOKEN_CREATED", "pairing", "1", "One-time pairing token issued")
        return PairingTokenResponse(
            pairingToken = raw,
            url = baseUrl,
            fingerprint = fingerprint,
            expiresAt = expiresAt,
            shopName = shopName,
        )
    }

    suspend fun pair(pairingToken: String, deviceName: String): PairResponse {
        val name = deviceName.trim()
        if (name.length !in 1..60) throw ApiException.badRequest("A device name is required")
        if (pairingToken.isBlank()) throw ApiException.unauthorized("Pairing code is required")

        val db = dbProvider.get()
        val stored = db.pairingDao().get()
        val now = System.currentTimeMillis()
        if (stored == null || now > stored.expiresAt || !Ids.constantTimeEquals(stored.tokenHash, Ids.sha256Hex(pairingToken))) {
            throw ApiException.unauthorized("Pairing code is invalid or expired")
        }

        val raw = Ids.newToken(32)
        val settings = db.settingsDao().get()
        db.deviceDao().insert(
            DeviceEntity(
                id = Ids.uuid(),
                name = name,
                tokenHash = Ids.sha256Hex(raw),
                pairedAt = now,
                lastSeenAt = now,
                revokedAt = null,
                createdByUserId = "pairing",
            )
        )
        // Single use.
        db.pairingDao().clear()
        audit.logSystem("DEVICE_PAIRED", "device", name, "Device paired with a one-time token")
        return PairResponse(
            deviceToken = raw,
            shopName = settings?.shopName ?: "",
            serverVersion = BuildConfigLike.VERSION,
        )
    }

    // ------------------------------------------------------------------ PIN login

    suspend fun login(deviceToken: String, pin: String): LoginResponse {
        if (!Validation.validPin(pin)) throw ApiException.unauthorized("Incorrect PIN")
        val db = dbProvider.get()
        val deviceHash = Ids.sha256Hex(deviceToken)
        val device = db.deviceDao().byTokenHash(deviceHash)
            ?: throw ApiException.unauthorized("This phone is not paired with the shop")
        if (device.revokedAt != null) throw ApiException.unauthorized("This phone has been revoked")

        val now = System.currentTimeMillis()
        val failures = db.loginAttemptDao().failedSince(now - FAILURE_WINDOW_MILLIS)
        if (failures >= MAX_FAILED_ATTEMPTS) {
            val last = db.loginAttemptDao().lastFailureSince(now - FAILURE_WINDOW_MILLIS) ?: now
            val waitSeconds = ((last + FAILURE_WINDOW_MILLIS - now) / 1000L).coerceAtLeast(1)
            throw ApiException.tooManyRequests("Too many incorrect attempts. Try again in $waitSeconds seconds.")
        }

        val users = db.userDao().all().filter { it.active }
        val pinChars = pin.toCharArray()
        val matched = users.firstOrNull { it.pinMatches(pinChars) }

        if (matched == null) {
            db.loginAttemptDao().insert(LoginAttemptEntity(createdAt = now, success = false, deviceId = device.id))
            // Slow down online guessing; the delay grows with each recent failure.
            delay((failures + 1) * 250L)
            throw ApiException.unauthorized("Incorrect PIN")
        }

        val rawSession = Ids.newToken(32)
        val expiresAt = now + SESSION_TTL_MILLIS
        db.sessionDao().deleteExpired(now)
        db.sessionDao().insert(
            SessionEntity(
                tokenHash = Ids.sha256Hex(rawSession),
                userId = matched.id,
                deviceId = device.id,
                role = matched.role,
                createdAt = now,
                expiresAt = expiresAt,
            )
        )
        db.userDao().touchLogin(matched.id, now)
        db.deviceDao().touch(device.id, now)
        db.loginAttemptDao().insert(LoginAttemptEntity(createdAt = now, success = true, deviceId = device.id))
        audit.logSystem("LOGIN", "user", matched.id, "Successful PIN sign-in from ${device.name}")

        return LoginResponse(
            sessionToken = rawSession,
            role = matched.role,
            userName = matched.name,
            expiresAt = expiresAt,
            deviceId = device.id,
            deviceName = device.name,
        )
    }

    suspend fun session(rawToken: String): AuthContext {
        if (rawToken.isBlank()) throw ApiException.unauthorized()
        val db = dbProvider.get()
        val entity = db.sessionDao().byTokenHash(Ids.sha256Hex(rawToken))
            ?: throw ApiException.unauthorized("Session expired. Sign in again.")
        val now = System.currentTimeMillis()
        if (entity.expiresAt < now) {
            db.sessionDao().delete(entity.tokenHash)
            throw ApiException.unauthorized("Session expired. Sign in again.")
        }
        val user = db.userDao().getById(entity.userId)
        if (user == null || !user.active) {
            db.sessionDao().delete(entity.tokenHash)
            throw ApiException.unauthorized("This account is no longer active")
        }
        val deviceEntity = db.deviceDao().all().firstOrNull { it.id == entity.deviceId }
        if (deviceEntity == null || deviceEntity.revokedAt != null) {
            db.sessionDao().delete(entity.tokenHash)
            throw ApiException.unauthorized("This phone has been revoked")
        }
        db.deviceDao().touch(deviceEntity.id, now)
        return AuthContext(
            userId = user.id,
            userName = user.name,
            role = Roles.parse(entity.role),
            deviceId = deviceEntity.id,
            sessionTokenHash = entity.tokenHash,
        )
    }

    suspend fun logout(rawToken: String) {
        dbProvider.get().sessionDao().delete(Ids.sha256Hex(rawToken))
    }

    // ------------------------------------------------------------------ owner bootstrap

    suspend fun createOwner(name: String, pin: String): UserEntity {
        val cleanName = name.trim()
        if (!Validation.validName(cleanName)) throw ApiException.badRequest("Owner name must be 2 to 80 characters")
        if (!Validation.validPin(pin)) throw ApiException.badRequest("PIN must be 4 to 10 digits")
        val db = dbProvider.get()
        if (db.userDao().countOwners() > 0) throw ApiException.conflict("An owner already exists")
        val hash = PinHasher.hash(pin.toCharArray())
        val user = UserEntity(
            id = Ids.uuid(),
            name = cleanName,
            role = "OWNER",
            pinSalt = hash.salt,
            pinHash = hash.hash,
            pinIterations = hash.iterations,
            active = true,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null,
        )
        db.userDao().insert(user)
        return user
    }

    @Suppress("unused")
    private fun unusedPinGuard(pin: String): Boolean = pin.length in MIN_PIN..MAX_PIN
}

/** Build config values exposed to pairing clients without leaking anything sensitive. */
object BuildConfigLike {
    const val VERSION = "1.0.0"
}
