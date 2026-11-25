package com.slncloth.shop.ui.setup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawRect
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.slncloth.shop.client.PairQr
import com.slncloth.shop.ui.local.AppPrefsLocal

/**
 * Shows the pairing QR code on the main phone.
 *
 * The QR encodes only: server URL, certificate fingerprint (full), one-time pairing token
 * and shop name. Never a PIN, session or secret.
 */
@Composable
fun QrDisplayScreen() {
    val prefs = AppPrefsLocal.current
    val shopName = PairingState.shopName
    val baseUrl = PairingState.baseUrl
    val fingerprint = PairingState.fingerprint
    val pairingToken = PairingState.pairingToken
    val qrText = PairQr.encode(baseUrl, fingerprint, pairingToken, shopName)
    val matrix = generateQr(qrText)

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = androidx.compose.foundation.layout.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        Text("Pairing QR code", style = MaterialTheme.typography.titleLarge)
        Text("Scan this from the other phone. It expires after one use.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(modifier = Modifier.size(280.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.width / matrix.width
                for (y in 0 until matrix.height) {
                    for (x in 0 until matrix.width) {
                        if (matrix.get(x, y)) {
                            drawRect(
                                color = Color.Black,
                                size = androidx.compose.ui.geometry.Size(cell, cell),
                                topLeft = androidx.compose.ui.geometry.Offset(x * cell, y * cell),
                            )
                        }
                    }
                }
            }
        }
        Text("Address: $baseUrl", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Security code: ${fingerprint.take(8).uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Pairing code: $pairingToken", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun generateQr(text: String): BitMatrix {
    val writer = QRCodeWriter()
    return writer.encode(text, BarcodeFormat.QR_CODE, 400, 400)
}
