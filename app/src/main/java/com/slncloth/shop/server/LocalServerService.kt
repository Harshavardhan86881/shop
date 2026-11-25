package com.slncloth.shop.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.slncloth.shop.R
import com.slncloth.shop.ui.main.MainActivity
import com.slncloth.shop.security.SecureStore
import com.slncloth.shop.security.PinnedTls
import com.slncloth.shop.server.tls.ServerTls
import com.slncloth.shop.util.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.collect

/**
 * Foreground service that keeps the embedded HTTPS server alive while the shop is open.
 *
 * Foreground service type: connectedDevice (declared in the manifest). The service never
 * creates a hotspot and never touches mobile data — it binds only to the local interface
 * chosen by the owner.
 *
 * The server is stoppable from Settings at any time. Android may still stop the service
 * under memory pressure or after a force stop; this is not 24/7 uptime and is documented
 * as such.
 */
class LocalServerService : Service() {

    private lateinit var prefs: AppPrefs
    private lateinit var secureStore: SecureStore
    private lateinit var tls: ServerTls
    private lateinit var dbProvider: DatabaseProvider
    private lateinit var services: ServerServices
    private lateinit var controller: ServerController

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    private const val CHANNEL_ID = "sln_server"
    private const val CHANNEL_NAME = "Shop server"
    private const val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        secureStore = SecureStore(this)
        tls = ServerTls(this)
        dbProvider = DatabaseProvider(this)
        services = ServerServices(dbProvider)
        controller = ServerController(prefs, tls, services)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_TOGGLE
        when (action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
            ACTION_TOGGLE -> if (controller.isRunning) stopServer() else startServer()
        }
        // Return START_NOT_STICKY: if the system kills us we do not resurrect silently.
        return Service.START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun startServer() {
        stopForeground(true)
        val notification = buildNotification("Starting…", running = false)
        startForeground(NOTIFICATION_ID, notification)

        scope = CoroutineScope(Dispatchers.Default)
        job = scope!!.launch {
            try {
                val state = controller.start()
                if (state is ServerController.State.Running) {
                    updateNotification(state, running = true)
                } else if (state is ServerController.State.Failed) {
                    updateNotification(state.message, running = false)
                    stopSelf()
                } else {
                    stopSelf()
                }
            } catch (e: Exception) {
                updateNotification("The server could not start.", running = false)
                stopSelf()
            }
        }
    }

    private fun stopServer() {
        scope = CoroutineScope(Dispatchers.Default)
        job = scope!!.launch {
            controller.stopAsync()
            stopForeground(true)
            stopSelf()
        }
    }

    private fun updateNotification(state: ServerController.State.Running, running: Boolean) {
        val summary = if (running) "Running on ${state.address}:${state.port}" else "Stopped"
        val content = if (running) {
            if (state.isLoopback) {
                "Local only (127.0.0.1). Turn on the hotspot and restart the server so client phones can connect."
            } else {
                "Client phones connect to ${state.baseUrl}"
            }
        } else {
            "Server stopped"
        }
        val notification = buildNotification(content, running = running, state = state)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(message: String, running: Boolean) {
        val notification = buildNotification(message, running = running)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        content: String,
        running: Boolean,
        state: ServerController.State.Running? = null,
    ): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = Intent(this, LocalServerService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setContentIntent(pending)
            .setOngoing(running)
            .setPriority(Notification.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_stop, "Stop server", stopPending)

        state?.let { running ->
            builder.setSubText("${it.address}:${it.port}")
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Runs the shop's local HTTPS server while billing."
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.slncloth.shop.action.START_SERVER"
        const val ACTION_STOP = "com.slncloth.shop.action.STOP_SERVER"
        const val ACTION_TOGGLE = "com.slncloth.shop.action.TOGGLE_SERVER"
    }
}
