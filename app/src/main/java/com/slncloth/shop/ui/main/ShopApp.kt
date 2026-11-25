package com.slncloth.shop.ui.main

import android.app.Application
import com.slncloth.shop.data.db.DatabaseProvider
import com.slncloth.shop.security.SecureStore
import com.slncloth.shop.server.ServerServices
import com.slncloth.shop.server.tls.ServerTls
import com.slncloth.shop.util.AppPrefs

/**
 * Application subclass. Holds the long-lived singletons so activities and the foreground
 * service share one database, one server controller and one secure store.
 */
class ShopApp : Application() {

    lateinit var prefs: AppPrefs
    lateinit var secureStore: SecureStore
    lateinit var tls: ServerTls
    lateinit var dbProvider: DatabaseProvider
    lateinit var services: ServerServices

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        secureStore = SecureStore(this)
        tls = ServerTls(this)
        dbProvider = DatabaseProvider(this)
        services = ServerServices(dbProvider)
    }
}

fun app(context: android.content.Context): ShopApp =
    context.applicationContext as ShopApp
