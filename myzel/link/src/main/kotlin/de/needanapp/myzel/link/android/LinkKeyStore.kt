package de.needanapp.myzel.link.android

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import de.needanapp.myzel.link.LinkKey
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Schlüssel von Myzel Link im Android-Keystore. Nach dem Import verlässt das Geheimnis den Keystore
 * nicht mehr; andere Apps kommen nicht heran.
 */
object LinkKeyStore {
  private const val PREFIX = "myzel-link-"

  private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

  fun import(id: String, secret: ByteArray) {
    keyStore()
      .setEntry(
        PREFIX + id,
        KeyStore.SecretKeyEntry(SecretKeySpec(secret, "HmacSHA256")),
        KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build(),
      )
  }

  fun get(id: String): LinkKey? =
    (keyStore().getKey(PREFIX + id, null) as? SecretKey)?.let { LinkKey(id, it) }

  /** Kennungen aller gespeicherten Schlüssel. */
  fun ids(): List<String> =
    keyStore().aliases().toList().filter { it.startsWith(PREFIX) }.map { it.removePrefix(PREFIX) }

  fun delete(id: String) = keyStore().deleteEntry(PREFIX + id)
}
