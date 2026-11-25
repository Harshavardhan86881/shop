package com.slncloth.shop.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.slncloth.shop.ui.theme.SlnTheme
import com.slncloth.shop.ui.nav.AppNavigator

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = app(this)
        setContent {
            CompositionLocalProvider(
                AppPrefsLocal provides application.prefs,
                SecureStoreLocal provides application.secureStore,
                TlsLocal provides application.tls,
                DbProviderLocal provides application.dbProvider,
                ServicesLocal provides application.services,
            ) {
                SlnTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppNavigator()
                    }
                }
            }
        }
    }
}
