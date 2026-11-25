package com.slncloth.shop.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExposedDropdownMenuField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.materialIconsFilled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHostController
import com.slncloth.shop.R
import com.slncloth.shop.ui.components.ShopBackground
import com.slncloth.shop.ui.components.PrimaryButton
import com.slncloth.shop.ui.components.ErrorText
import com.slncloth.shop.ui.local.AppPrefsLocal
import com.slncloth.shop.ui.local.SecureStoreLocal
import com.slncloth.shop.client.ApiClient
import com.slncloth.shop.client.PairingManager
import com.slncloth.shop.client.ShopApi
import kotlinx.coroutines.launch

@Composable
fun PairingScreen(navController: NavHostController) {
    val prefs = AppPrefsLocal.current
    val secureStore = SecureStoreLocal.current
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8787") }
    var pairingCode by remember { mutableStateOf("") }
    var fingerprintPrefix by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        horizontalAlignment = androidx.compose.foundation.layout.Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(
            imageVector = materialIconsFilled.PhoneAndroid,
            contentDescription = "Pair",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("Pair with main phone", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Connect to the main phone’s hotspot in Android settings, then enter its address and pairing code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Button(
            onClick = { navController.navigate("scan") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.material3.Icon(
                imageVector = materialIconsFilled.QrCodeScanner,
                contentDescription = "Scan QR",
            )
            androidx.compose.material3.Text(text = "Scan pairing QR code")
        }
        Text(
            text = "Or pair manually below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Manual entry", style = MaterialTheme.typography.titleMedium)
                TextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Main phone IP (e.g. 192.168.43.1)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    TextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        singleLine = true,
                        keyboardType = android.text.InputType.TYPE_CLASS_NUMBER,
                    )
                    TextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it },
                        label = { Text("Pairing code") },
                        modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp),
                        singleLine = true,
                    )
                    TextField(
                        value = fingerprintPrefix,
                        onValueChange = { fingerprintPrefix = it },
                        label = { Text("Security code (8 chars)") },
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        singleLine = true,
                        maxLength = 8,
                    )
                }
                if (error.isNotEmpty()) ErrorText(error, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }

        PrimaryButton(
            onClick = {
                error = ""
                isPairing = true
                scope.launch {
                    try {
                        val api = ShopApi(ApiClient(prefs, secureStore), secureStore)
                        val manager = PairingManager(prefs, secureStore, api)
                        val shopName = manager.pairManually(
                            PairingManager.ManualRequest(
                                host = host,
                                port = port.toInt(),
                                pairingCode = pairingCode,
                                fingerprintPrefix = fingerprintPrefix,
                            )
                        )
                        prefs.mode = com.slncloth.shop.util.AppMode.CLIENT
                        prefs.configured = true
                        prefs.onboardingDone = true
                        navController.navigate("bill") {
                            androidx.navigation.NavOptions.Builder().setLaunchSingleTop().build()
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Pairing failed."
                    } finally {
                        isPairing = false
                    }
                }
            },
            enabled = !isPairing,
        ) {
            androidx.compose.material3.Text(text = if (isPairing) "Pairing…" else "Pair")
        }
    }
}
