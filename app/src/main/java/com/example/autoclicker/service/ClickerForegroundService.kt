package com.example.autoclicker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.autoclicker.R
import com.example.autoclicker.core.AppContainer
import com.example.autoclicker.model.ClickConfig
import com.example.autoclicker.ui.MainActivity
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** 前台服务：保活 + 承载悬浮窗 + 接收启动/停止指令 */
class ClickerForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.autoclicker.START"
        const val ACTION_STOP = "com.example.autoclicker.STOP"
        const val EXTRA_CONFIG = "config_json"
        private const val CHANNEL_ID = "clicker_channel"
        private const val TAG = "ClickerFGS"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("待机"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val json = intent.getStringExtra(EXTRA_CONFIG)
                val config = runCatching {
                    Gson().fromJson(json, ClickConfig::class.java)
                }.getOrDefault(ClickConfig())
                AppContainer.scheduler.start(config)
                updateNotification("正在连点…")
            }
            ACTION_STOP -> {
                AppContainer.scheduler.stop()
                updateNotification("已停止")
            }
        }
        return START_STICKY
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "连点服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppContainer.scheduler.stop()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
