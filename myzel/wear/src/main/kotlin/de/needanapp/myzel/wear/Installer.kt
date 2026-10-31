package de.needanapp.myzel.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import androidx.core.content.IntentCompat
import de.needanapp.myzel.link.CONFIRM_BY_NOTIFICATION
import de.needanapp.myzel.link.Install
import de.needanapp.myzel.link.InstallStatus
import de.needanapp.myzel.link.LinkConnection
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val CONFIRM_CHANNEL = "install"

/** Kennung eines Auftrags im Intent an den [InstallReceiver]. */
private const val EXTRA_REQUEST = "de.needanapp.myzel.wear.REQUEST"

/**
 * Installiert empfangene APKs über eine Sitzung des PackageInstallers, entfernt Apps auf Wunsch des
 * Handys und meldet den Stand an das Handy. Die Sitzung bittet darum, ohne Nachfrage zu
 * installieren; bei Updates von Apps, die Myzel Wear selbst installiert hat, erlaubt Wear OS das,
 * sonst fragt es den Nutzer. Beim Entfernen fragt es immer.
 */
object Installer {
  private class Pending(
    val name: String,
    val connection: LinkConnection,
    val removal: Boolean,
    var askedUser: Boolean = false,
  )

  private val requests = ConcurrentHashMap<Int, Pending>()
  private val nextRequest = AtomicInteger()

  /** Installiert [apk]; [name] erscheint im Verlauf und in der Bitte um Bestätigung. */
  fun install(context: Context, apk: File, name: String, connection: LinkConnection) {
    val installer = context.packageManager.packageInstaller
    val params =
      SessionParams(SessionParams.MODE_FULL_INSTALL).apply {
        setSize(apk.length())
        if (SDK_INT >= 31) setRequireUserAction(SessionParams.USER_ACTION_NOT_REQUIRED)
      }
    val sessionId = installer.createSession(params)
    val id = nextRequest.incrementAndGet()
    requests[id] = Pending(name, connection, removal = false)
    installer.openSession(sessionId).use { session ->
      apk.inputStream().use { input ->
        session.openWrite("base.apk", 0, apk.length()).use { output ->
          input.copyTo(output)
          session.fsync(output)
        }
      }
      session.commit(statusReceiver(context, id))
    }
  }

  /** Entfernt [packageName]; Wear OS fragt dabei den Nutzer. */
  fun uninstall(context: Context, packageName: String, name: String, connection: LinkConnection) {
    val id = nextRequest.incrementAndGet()
    requests[id] = Pending(name, connection, removal = true)
    context.packageManager.packageInstaller.uninstall(packageName, statusReceiver(context, id))
  }

  /** Wohin der PackageInstaller den Stand von Auftrag [id] meldet. */
  private fun statusReceiver(context: Context, id: Int): IntentSender {
    val intent = Intent(context, InstallReceiver::class.java).putExtra(EXTRA_REQUEST, id)
    // Der PackageInstaller ergänzt den Stand als Extras, deshalb veränderbar
    return PendingIntent.getBroadcast(
        context,
        id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
      )
      .intentSender
  }

  fun onStatus(context: Context, intent: Intent) {
    val id = intent.getIntExtra(EXTRA_REQUEST, -1)
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
    val pending = requests[id]
    val name = pending?.name ?: "#$id"
    val removal = pending?.removal == true
    // Bestätigen zeigt der eigene Knopf; danach ist der Auftrag Sache von Wear OS
    WatchLink.setTask(null)
    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        pending?.askedUser = true
        val confirm =
          IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        val how =
          if (confirm == null) {
            "no dialog"
          } else if (WatchLink.inForeground) {
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirm)
            "dialog"
          } else {
            // Aus dem Hintergrund darf die App keine Activity starten
            WatchLink.setConfirm(PendingConfirm(id, name, removal, confirm))
            notifyConfirm(context, id, name, removal, confirm)
            CONFIRM_BY_NOTIFICATION
          }
        WatchLink.event(R.string.event_confirm, name)
        pending?.let { WatchLink.send(it.connection, Install(InstallStatus.CONFIRM, how)) }
      }
      PackageInstaller.STATUS_SUCCESS -> {
        requests.remove(id)
        done(context, id)
        WatchLink.event(if (removal) R.string.event_uninstalled else R.string.event_installed, name)
        val asked = if (pending?.askedUser == true) "confirmed" else "without confirmation"
        pending?.let { WatchLink.send(it.connection, Install(InstallStatus.SUCCESS, asked)) }
      }
      else -> {
        requests.remove(id)
        done(context, id)
        Log.w(
          "MyzelWear",
          "${if (removal) "Entfernen" else "Installation"} von $name: $status $message",
        )
        WatchLink.event(
          if (removal) R.string.event_uninstall_failed else R.string.event_install_failed,
          name,
        )
        pending?.let {
          WatchLink.send(it.connection, Install(InstallStatus.FAILURE, "$status: $message"))
        }
      }
    }
  }

  /** Öffnet den Bestätigungsdialog, solange die Activity sichtbar ist. */
  fun showConfirm(context: Context, confirm: PendingConfirm) {
    context
      .getSystemService(NotificationManager::class.java)
      .cancel(notificationId(confirm.requestId))
    context.startActivity(Intent(confirm.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  private fun done(context: Context, id: Int) {
    WatchLink.takeConfirm(id)
    context.getSystemService(NotificationManager::class.java).cancel(notificationId(id))
  }

  private fun notifyConfirm(
    context: Context,
    id: Int,
    name: String,
    removal: Boolean,
    confirm: Intent,
  ) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(
        CONFIRM_CHANNEL,
        context.getString(R.string.channel_install),
        NotificationManager.IMPORTANCE_HIGH,
      )
    )
    val open =
      PendingIntent.getActivity(
        context,
        id,
        Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val notification =
      Notification.Builder(context, CONFIRM_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(
          context.getString(
            if (removal) R.string.notification_uninstall_title
            else R.string.notification_install_title
          )
        )
        .setContentText(
          context.getString(
            if (removal) R.string.notification_uninstall_text
            else R.string.notification_install_text,
            name,
          )
        )
        .setContentIntent(open)
        .setAutoCancel(true)
        .setCategory(Notification.CATEGORY_RECOMMENDATION)
        .build()
    manager.notify(notificationId(id), notification)
  }

  private fun notificationId(id: Int) = 1_000 + id
}

class InstallReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) = Installer.onStatus(context, intent)
}
