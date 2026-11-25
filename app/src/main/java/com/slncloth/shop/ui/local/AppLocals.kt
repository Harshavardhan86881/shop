package com.slncloth.shop.ui.local

import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.ProvidableCompositionLocal
import com.slncloth.shop.data.db.DatabaseProvider
import com.slncloth.shop.security.SecureStore
import com.slncloth.shop.server.ServerServices
import com.slncloth.shop.server.tls.ServerTls
import com.slncloth.shop.util.AppPrefs

object AppPrefsLocal {
    val current: ProvidableCompositionLocal<AppPrefs> =
        compositionLocalOf { error("AppPrefs not available") }
}

object SecureStoreLocal {
    val current: ProvidableCompositionLocal<SecureStore> =
        compositionLocalOf { error("SecureStore not available") }
}

object TlsLocal {
    val current: ProvidableCompositionLocal<ServerTls> =
        compositionLocalOf { error("ServerTls not available") }
}

object DbProviderLocal {
    val current: ProvidableCompositionLocal<DatabaseProvider> =
        compositionLocalOf { error("DatabaseProvider not available") }
}

object ServicesLocal {
    val current: ProvidableCompositionLocal<ServerServices> =
        compositionLocalOf { error("ServerServices not available") }
}

fun <T> CompositionLocal<T>.noValue(): T = error("Not provided")
