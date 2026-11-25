package com.slncloth.shop.ui.setup

/** Holds the most recently created pairing token so the QR screen can display it. */
object PairingState {
    var shopName: String = ""
    var baseUrl: String = ""
    var fingerprint: String = ""
    var pairingToken: String = ""
    var createdAt: Long = 0

    fun isFresh(maxAgeMillis: Long = 10 * 60 * 1000L): Boolean =
        System.currentTimeMillis() - createdAt < maxAgeMillis
}
