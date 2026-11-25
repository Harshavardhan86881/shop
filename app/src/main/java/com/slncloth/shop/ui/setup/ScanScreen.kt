package com.slncloth.shop.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.materialIconsFilled
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHostController
import com.slncloth.shop.ui.components.ShopBackground

/**
 * QR pairing entry point.
 *
 * Camera-based scanning is delegated to the system camera app; the scanned `SLNP1:` payload
 * is then pasted into manual entry. This avoids a hard dependency on the zxing activity-result
 * plumbing while keeping the documented manual-entry fallback fully functional.
 */
@Composable
fun ScanScreen(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.foundation.layout.Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(
            imageVector = materialIconsFilled.QrCodeScanner,
            contentDescription = "Scan",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("Scan pairing QR", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Point the camera at the QR code shown on the main phone, then paste the scanned code below.\n\nThe QR carries only the server address, certificate fingerprint and a one-time pairing token — never a PIN or secret.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.TextAlign.Center,
        )
        Button(onClick = { navController.navigate("pair") }) {
            Text("Paste scanned code / enter manually")
        }
    }
}
