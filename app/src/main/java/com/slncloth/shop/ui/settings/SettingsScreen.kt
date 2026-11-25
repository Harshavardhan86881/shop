package com.slncloth.shop.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHostController
import com.slncloth.shop.client.ApiClient
import com.slncloth.shop.client.ShopApi
import com.slncloth.shop.ui.components.ErrorText
import com.slncloth.shop.ui.components.PrimaryButton
import com.slncloth.shop.ui.local.AppPrefsLocal
import com.slncloth.shop.ui.local.SecureStoreLocal
import com.slncloth.shop.ui.setup.PairingState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val prefs = AppPrefsLocal.current
    val secureStore = SecureStoreLocal.current
    val scope = rememberCoroutineScope()
    var markup by remember { mutableStateOf("20") }
    var cashback by remember { mutableStateOf("5") }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {

        Text("Settings", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Shop server", style = MaterialTheme.typography.titleMedium)
                Text("Mode: ${prefs.mode}", style = MaterialTheme.typography.bodyMedium)
                Text("Address: ${prefs.baseUrl.ifBlank("not connected")}", style = MaterialTheme.typography.bodyMedium)
                Text("Port: ${prefs.serverPort}", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { /* toggle server via LocalServerService */ }) {
                    Text("Start / stop server")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pricing defaults (owner)", style = MaterialTheme.typography.titleMedium)
                TextField(value = markup, onValueChange = { markup = it }, label = { Text("Default markup %") }, modifier = Modifier.fillMaxWidth(), keyboardType = android.text.InputType.TYPE_CLASS_NUMBER)
                TextField(value = cashback, onValueChange = { cashback = it }, label = { Text("Default cashback %") }, modifier = Modifier.fillMaxWidth(), keyboardType = android.text.InputType.TYPE_CLASS_NUMBER)
                Button(onClick = { /* save settings via API */ }) { Text("Save") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pairing & devices (owner)", style = MaterialTheme.typography.titleMedium)
                Text("Create a one-time pairing code and show a QR code for other phones to connect.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (prefs.baseUrl.isBlank()) {
                    Text("Start the server first so an address exists.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = {
                        if (prefs.baseUrl.isBlank()) { error = "Start the server first."; return@Button }
                        error = ""
                        info = ""
                        isWorking = true
                        scope.launch {
                            try {
                                val api = ShopApi(ApiClient(prefs, secureStore), secureStore)
                                val res = api.createPairingToken(prefs.baseUrl)
                                PairingState.shopName = res.shopName
                                PairingState.baseUrl = res.url
                                PairingState.fingerprint = res.fingerprint
                                PairingState.pairingToken = res.pairingToken
                                PairingState.createdAt = System.currentTimeMillis()
                                navController.navigate("qr")
                            } catch (e: Exception) {
                                error = e.message ?: "Could not create pairing code."
                            } finally {
                                isWorking = false
                            }
                        }
                    },
                    enabled = !isWorking && prefs.baseUrl.isNotEmpty(),
                ) {
                    androidx.compose.material3.Text(text = if (isWorking) "Creating…" else "Create pairing code")
                }
                if (error.isNotEmpty()) ErrorText(error, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (info.isNotEmpty()) {
                    androidx.compose.material3.Text(text = info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Backup & restore (owner)", style = MaterialTheme.typography.titleMedium)
                Text("Encrypted backup to a location you choose. The passphrase is not stored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { /* backup via SAF */ }) { Text("Back up now") }
                Button(onClick = { /* restore via SAF */ }) { Text("Restore from backup") }
            }
        }

    }
}
