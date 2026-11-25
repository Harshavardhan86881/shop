package com.slncloth.shop.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.slncloth.shop.client.ApiClient
import com.slncloth.shop.client.ShopApi
import com.slncloth.shop.ui.components.ErrorText
import com.slncloth.shop.ui.components.PrimaryButton
import com.slncloth.shop.ui.components.ShopBackground
import com.slncloth.shop.ui.local.AppPrefsLocal
import com.slncloth.shop.ui.local.SecureStoreLocal
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavHostController) {
    val prefs = AppPrefsLocal.current
    val secureStore = SecureStoreLocal.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }

    ShopBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = androidx.compose.foundation.layout.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = materialIconsFilled.Storefront,
                contentDescription = "Shop",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Owner sign-in", style = MaterialTheme.typography.titleLarge)
            Text("Enter your owner PIN.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

            TextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("Owner PIN") },
                modifier = Modifier.fillMaxWidth().widthIn(min = 200.dp),
                singleLine = true,
                keyboardType = android.text.InputType.TYPE_CLASS_NUMBER,
                visualTransformation = androidx.compose.foundation.text.KeyboardVisualTransformation.Password,
            )
            if (error.isNotEmpty()) ErrorText(error, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            PrimaryButton(
                onClick = {
                    error = ""
                    isLoggingIn = true
                    scope.launch {
                        try {
                            val api = ShopApi(ApiClient(prefs, secureStore), secureStore)
                            val res = api.login(pin)
                            secureStore.put(com.slncloth.shop.security.SecureStore.KEY_SESSION, res.sessionToken)
                            navController.navigate("settings") {
                                androidx.navigation.NavOptions.Builder().setLaunchSingleTop().build()
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Sign-in failed."
                        } finally {
                            isLoggingIn = false
                        }
                    }
                },
                enabled = !isLoggingIn && pin.length >= 4,
            ) {
                androidx.compose.material3.Text(text = if (isLoggingIn) "Signing in…" else "Sign in")
            }
        }
    }
}
