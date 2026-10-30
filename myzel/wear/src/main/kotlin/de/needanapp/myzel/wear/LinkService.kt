package de.needanapp.myzel.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val CHANNEL = "link"
private const val NOTIFICATION_ID = 1

/** So lange lauscht Myzel Wear ohne Handy, dann pausiert es und gibt die Uhr wieder frei. */
private const val IDLE_MINUTES = 10

/**
 * Hält Myzel Wear am Leben, solange es lauscht. Ohne den Dienst friert Wear OS die App ein, sobald
 * das Display ausgeht, und eine laufende Übertragung bleibt stehen (gemessen auf der Pixel Watch
 * 2). Kommt [IDLE_MINUTES] lang kein Handy, endet er von selbst.
 */
class LinkService : Service() {
  private var idleJob: Job? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      notification(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
    )
    WatchLink.start()
    if (idleJob?.isActive != true) {
      idleJob =
        CoroutineScope(Dispatchers.Main).launch {
          while (isActive) {
            delay(30_000)
            val idle = SystemClock.elapsedRealtime() - WatchLink.lastActivity
            if (!WatchLink.isBusy && idle > IDLE_MINUTES * 60_000L) {
              WatchLink.event(R.string.event_idle, IDLE_MINUTES)
              stopSelf()
              break
            }
          }
        }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    idleJob?.cancel()
    WatchLink.stop()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun notification(): Notification {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL,
        getString(R.string.channel_link),
        NotificationManager.IMPORTANCE_LOW,
      )
    )
    val open =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
      )
    return Notification.Builder(this, CHANNEL)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(getString(R.string.app_name))
      .setContentText(getString(R.string.notification_link))
      .setContentIntent(open)
      .setOngoing(true)
      .build()
  }

  companion object {
    fun start(context: Context) {
      context.startForegroundService(Intent(context, LinkService::class.java))
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, LinkService::class.java))
    }
  }
}
