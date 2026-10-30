package de.needanapp.myzel.wear

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import de.needanapp.myzel.link.LINK_KEY_SIZE
import de.needanapp.myzel.link.android.LinkKeyStore
import de.needanapp.myzel.link.hexToBytes

/**
 * Nimmt bei der Einrichtung den Schlüssel vom Handy an, über die ADB-Shell: `content call --uri
 * content://<Paket>.key --method setKey --arg <Kennung>:<Geheimnis>`.
 *
 * Nur die Shell darf das, also wer per ADB auf der Uhr ist; jede andere App bekommt eine
 * SecurityException. Ein neuer Schlüssel ersetzt den alten: Die Uhr gehört zu genau einem Handy.
 */
class KeyProvider : ContentProvider() {

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
    val uid = Binder.getCallingUid()
    if (uid != Process.SHELL_UID && uid != Process.ROOT_UID) throw SecurityException("ADB only")
    require(method == "setKey") { "Unknown method $method" }
    val id = arg?.substringBefore(':').orEmpty()
    val secret = arg?.substringAfter(':', "").orEmpty()
    require(KEY_ID.matches(id) && secret.length == LINK_KEY_SIZE * 2) { "Expected <id>:<secret>" }
    val bytes = secret.hexToBytes()
    try {
      LinkKeyStore.import(id, bytes)
    } finally {
      bytes.fill(0)
    }
    LinkKeyStore.ids().filter { it != id }.forEach { LinkKeyStore.delete(it) }
    WatchLink.keyChanged()
    WatchLink.event(R.string.event_key)
    return Bundle().apply {
      putBoolean("ok", true)
      putString("keyId", id)
    }
  }

  override fun onCreate() = true

  override fun query(
    uri: Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String?,
  ) = null

  override fun getType(uri: Uri) = null

  override fun insert(uri: Uri, values: ContentValues?) = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<String>?,
  ) = 0

  private companion object {
    val KEY_ID = Regex("[0-9a-f]{16}")
  }
}
