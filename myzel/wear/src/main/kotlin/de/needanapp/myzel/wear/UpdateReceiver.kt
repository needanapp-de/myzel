package de.needanapp.myzel.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.needanapp.myzel.link.android.hasLinkPermissions

/**
 * Nach einem Update von Myzel Wear (vom Handy über Bluetooth) wieder lauschen: Das Update beendet
 * den Prozess samt Verbindung, und das Handy wartet darauf, dass die neue Version antwortet. Den
 * Vordergrunddienst darf die App aus diesem Broadcast heraus starten.
 */
class UpdateReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
    WatchLink.event(R.string.event_updated)
    if (context.hasLinkPermissions()) LinkService.start(context)
  }
}
