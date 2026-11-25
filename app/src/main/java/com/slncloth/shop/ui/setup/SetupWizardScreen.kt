package com.slncloth.shop.ui.setup

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExposedDropdownMenuBox
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
import com.slncloth.shop.ui.components.ShopBackground
import com.slncloth.shop.ui.components.PrimaryButton
import com.slncloth.shop.ui.components.ErrorText
import com.slncloth.shop.ui.local.AppPrefsLocal
import com.slncloth.shop.ui.local.SecureStoreLocal
import com.slncloth.shop.security.PinHasher
import com.slncloth.shop.server.AuthRepository
import com.slncloth.shop.server.ServerServices
import com.slncloth.shop.ui.local.DbProviderLocal
import kotlinx.coroutines.launch

@Composable
fun SetupWizardScreen(navController: NavHostController) {
    val prefs = AppPrefsLocal.current
    val secureStore = SecureStoreLocal.current
    val dbProvider = DbProviderLocal.current
    val scope = rememberCoroutineScope()
    val auth = AuthRepository(dbProvider, com.slncloth.shop.server.AuditLogger { dbProvider })

    var shopName by remember { mutableStateOf("Sri Lakshmi Narasimha Cloth and Readymade") }
    var address by remember { mutableStateOf("Bustand Road, Penagalur, 516127") }
    var ownerName by remember { mutableStateOf("Suresh Babu") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Set up this shop", style = MaterialTheme.typography.titleLarge)
                TextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("Shop name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                TextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                TextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Owner name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Owner credentials", style = MaterialTheme.typography.titleMedium)
                .let { androidx.compose.material3.Text(it, style = MaterialTheme.typography.titleMedium) }
                .also {}
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                TextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Owner PIN (4+ digits)") },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    singleLine = true,
                    keyboardType = android.text.InputType.TYPE_CLASS_NUMBER,
                    visualTransformation = androidx.compose.foundation.text.KeyboardVisualTransformation.Password,
                )
                TextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it },
                    label = { Text("Confirm PIN") },
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    singleLine = true,
                    keyboardType = android.text.InputType.TYPE_CLASS_NUMBER,
                    visualTransformation = androidx.compose.foundation.text.KeyboardVisualTransformation.Password,
                )
            }
        }

        if (error.isNotEmpty()) ErrorText(error, modifier = Modifier.fillMaxWidth())

        PrimaryButton(
            onClick = {
                error = ""
                if (shopName.isBlank() || address.isBlank() || ownerName.isBlank()) {
                    error = "Fill in shop name, address and owner name."
                    return@PrimaryButton
                }
                if (pin.length < 4 || !pin.all { it.isDigit() }) {
                    error = "PIN must be at least 4 digits."
                    return@PrimaryButton
                }
                if (pin != confirmPin) {
                    error = "PINs do not match."
                    return@PrimaryButton
                }
                isSubmitting = true
                scope.launch {
                    try {
                        auth.createOwner(ownerName, pin)
                        prefs.mode = com.slncloth.shop.util.AppMode.MAIN
                        prefs.configured = true
                        prefs.onboardingDone = true
                        Handler(Looper.getMainLooper()).post {
                            navController.navigate("server")
                        }
                    } catch (e: Exception) {
                        error = "Setup failed: ${e.message ?: e::class.simpleName}"
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            enabled = !isSubmitting,
        ) {
            Text(if (isSubmitting) "Creating…" else "Create shop and owner")
        }
    }
}

@Composable
fun ServerStartScreen(navController: NavHostController) {
    val prefs = AppPrefsLocal.current
    val services = com.slncloth.shop.ui.local.ServicesLocal.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Ready to start the local server.") }
    var isStarting by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            horizontalAlignment = androidx.compose.foundation.layout.Alignment.CenterHorizontally,
        ),
        content = {
            androidx.compose.material3.Icon(
                imageVector = materialIconsFilled.DeviceHub,
                contentDescription = "Server",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Local shop server", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "This phone runs the HTTPS server. Client phones connect over the hotspot.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            PrimaryButton(
                onClick = {
                    isStarting = true
                    status = "Starting server…"
                    scope.launch {
                        try {
                            com.slncloth.shop.server.ServerController(
                                prefs,
                                com.slncloth.shop.server.tls.ServerTls(android.app.Application.getSystemService ? return@launch),
                                services,
                            )
                        } catch (e: Exception) {
                            status = "Could not start: ${e.message}"
                        } finally {
                            isStarting = false
                        }
                    }
                },
                enabled = !isStarting,
            ) {
                Text("Start server")
            }
        },
    )
}
