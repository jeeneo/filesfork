package texteditor.server.service

// SPDX-License-Identifier: GPL-3.0-or-later

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import texteditor.R
import texteditor.server.ServerGraphProvider
import texteditor.server.http.serverModule

class ServerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var server: EmbeddedServer<*, *>? = null
    private var settingsObserverJob: Job? = null
    private var boundAddress: String = "0.0.0.0"
    private var boundPort: Int = 8066
    private var baseUrlOverride: String = ""

    private val graph get() = ServerGraphProvider.get(applicationContext)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createServerChannel()
        startSettingsObserver()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
            }

            else -> {
                startServer()
            }
        }
        return START_NOT_STICKY
    }

    private val startLock = Any()
    private var booting = false

    private fun startServer() {
        synchronized(startLock) {
            if (booting || server != null) {
                return
            }
            booting = true
        }
        startForeground(
            NOTIFICATION_ID,
            serverNotification(getString(R.string.editor_server_starting)),
        )
        graph.registry.serverStarting()

        serviceScope.launch {
            try {
                val settings = graph.settingsRepository.settings.first()
                baseUrlOverride = settings.baseUrlOverride
                val url = withContext(Dispatchers.IO) {
                    bindAndStart(serverIp = settings.serverIp, preferredPort = settings.serverPort)
                }
                startForeground(
                    NOTIFICATION_ID,
                    serverNotification(url),
                )
                graph.registry.serverStarted(url)
            } catch (e: Exception) {
                e.printStackTrace()
                server = null
                cancelServerNotification()
                graph.registry.serverStopped()
                Toast.makeText(
                    this@ServerService,
                    getString(R.string.editor_server_failed_start, e.localizedMessage),
                    Toast.LENGTH_LONG,
                ).show()
                stopSelf()
            } finally {
                synchronized(startLock) { booting = false }
            }
        }
    }

    private fun bindAndStart(
        serverIp: String,
        preferredPort: Int,
    ): String {
        val address = bindAddress(serverIp)
        boundAddress = address
        boundPort = preferredPort
        val engine = embeddedServer(CIO, port = preferredPort, host = address) {
            serverModule(graph.buildRouteContext(this@ServerService))
        }
        engine.start(wait = false)
        server = engine
        return currentServerUrl()
    }

    private fun ipv6(host: String): String = if (host.contains(':')) "[$host]" else host

    private fun currentServerUrl(): String {
        val override = baseUrlOverride.trim()
        if (override.isNotBlank()) {
            return override.removeSuffix("/")
        }
        return "http://${ipv6(boundAddress)}:$boundPort"
    }

    private fun refreshServerNotification() {
        if (server == null) return
        val url = currentServerUrl()
        graph.registry.setServerUrl(url)
        startForeground(
            NOTIFICATION_ID,
            serverNotification(url),
        )
    }

    private fun bindAddress(serverIp: String): String {
        if (serverIp != "0.0.0.0") return serverIp
        val addresses = graph.networkInfoProvider.scanAddresses()
        return graph.networkInfoProvider.pickDefault(addresses)?.address ?: "0.0.0.0"
    }

    private fun startSettingsObserver() {
        settingsObserverJob?.cancel()
        settingsObserverJob = serviceScope.launch {
            var lastPort = -1
            var lastBaseUrlOverride = ""
            graph.settingsRepository.settings.drop(1).collect { settings ->
                val port = settings.serverPort
                val override = settings.baseUrlOverride
                val overrideChanged = override != lastBaseUrlOverride
                val portChanged = lastPort != -1 && port != lastPort
                lastPort = port
                lastBaseUrlOverride = override
                if (server == null) return@collect
                if (portChanged) {
                    restartServer()
                    return@collect
                }
                if (overrideChanged) {
                    baseUrlOverride = override
                    refreshServerNotification()
                }
            }
        }
    }

    private fun restartServer() {
        val runningServer = server
        server = null
        graph.registry.serverStopping()
        serviceScope.launch {
            try {
                runningServer?.let {
                    withContext(Dispatchers.IO) {
                        it.stop(gracePeriodMillis = 0, timeoutMillis = 500)
                    }
                }
            } finally {
                graph.registry.serverStopped()
                startServer()
            }
        }
    }

    override fun onDestroy() {
        settingsObserverJob?.cancel()
        settingsObserverJob = null
        graph.registry.serverStopping()
        val runningServer = server
        server = null
        val stopScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        stopScope.launch {
            try {
                runningServer?.let {
                    withContext(Dispatchers.IO) {
                        it.stop(gracePeriodMillis = 0, timeoutMillis = 500)
                    }
                }
            } finally {
                graph.registry.serverStopped()
                stopScope.cancel()
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createServerChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.editor_server_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.editor_server_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun serverNotification(url: String): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.editor_server_notification_title))
            .setContentText(getString(R.string.editor_server_notification_text, url))
            .setSmallIcon(R.drawable.ic_localhost).setOngoing(true).addAction(
                0,
                getString(R.string.editor_server_notification_stop),
                stopPending,
            )
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return builder.build()
    }

    private fun cancelServerNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "texteditor_server"
        const val NOTIFICATION_ID = 100
        const val ACTION_STOP = "texteditor.STOP_SERVICE"

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, ServerService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ServerService::class.java))
        }
    }

    private val stopPending: PendingIntent by lazy {
        val stopIntent = Intent(this, ServerService::class.java).apply {
            action = ACTION_STOP
        }
        PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
