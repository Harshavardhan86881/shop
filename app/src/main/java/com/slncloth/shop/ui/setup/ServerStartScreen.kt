package com.slncloth.shop.ui.setup

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.materialIconsFilled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHostController
import com.slncloth.shop.ui.components.PrimaryButton
import com.slncloth.shop.ui.local.AppPrefsLocal
import com.slncloth.shop.ui.local.TlsLocal
import com.slncloth.shop.ui.local.ServicesLocal
import com.slncloth.shop.server.ServerController
import com.slncloth.shop.server.tls.ServerTls
import kotlinx.coroutines.launch

@Composable
fun ServerStartScreen(navController: NavHostController) {
    val prefs = AppPrefsLocal.current
    val services = ServicesLocal.current
    val tls = TlsLocal.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Tap Start to run the local shop server.") }
    var isStarting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
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
                        val controller = ServerController(prefs, tls, services)
                        val state = controller.start()
                        if (state is ServerController.State.Running) {
                            status = "Running on ${state.address}:${state.port}"
                            navController.navigate("bill") {
                                androidx.navigation.NavOptions.Builder().setLaunchSingleTop().build()
                            }
                        } else if (state is ServerController.State.Failed) {
                            status = state.message
                        } else {
                            status = "Server did not start."
                        }
                    } catch (e: Exception) {
                        status = "Could not start: ${e.message ?: e::class.simpleName}"
                    } finally {
                        isStarting = false
                    }
                }
            },
            enabled = !isStarting,
        ) {
            Text("Start server")
        }
    }
}
