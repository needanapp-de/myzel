package de.needanapp.myzel.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Größte erlaubte Nutzlast eines Frames; Rohdaten laufen nicht über Frames. */
const val MAX_FRAME_SIZE = 64 * 1024

/** Verstoß gegen das Protokoll: unbekannter Typ, zu großer Frame, falsche Nutzdaten. */
class LinkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Typ-Bytes auf der Leitung; die Werte dürfen sich nicht ändern. */
internal object FrameType {
  const val HELLO = 1
  const val PING = 2
  const val PONG = 3
  const val OFFER = 4
  const val RESULT = 5
  const val INSTALL = 6
  const val BYE = 7
  const val AUTH = 8
  const val AUTH_RESULT = 9
  const val APP_QUERY = 10
  const val APP_STATES = 11
  const val UNINSTALL = 12
}

/** Schreibt und liest Frames: [Typ: 1 Byte][Länge: 4 Byte][Nutzdaten]. */
internal object Frames {

  fun write(out: DataOutputStream, message: Message) {
    val payload = ByteArrayOutputStream()
    val data = DataOutputStream(payload)
    val type =
      when (message) {
        is Hello -> {
          data.writeInt(message.protocol)
          data.writeUTF(message.device)
          data.writeUTF(message.nonce)
          data.writeUTF(message.keyId)
          data.writeUTF(message.proof)
          data.writeInt(message.sdk)
          data.writeLong(message.appVersionCode)
          // seit Protokoll 5; ältere Leser übergehen es
          data.writeUTF(message.appSha256)
          FrameType.HELLO
        }
        is Auth -> {
          data.writeUTF(message.proof)
          FrameType.AUTH
        }
        is AuthResult -> {
          data.writeBoolean(message.ok)
          data.writeUTF(message.message)
          FrameType.AUTH_RESULT
        }
        is Ping -> {
          data.writeLong(message.stamp)
          FrameType.PING
        }
        is Pong -> {
          data.writeLong(message.stamp)
          FrameType.PONG
        }
        is Offer -> {
          data.writeByte(message.kind.ordinal)
          data.writeUTF(message.name)
          data.writeLong(message.versionCode)
          data.writeLong(message.size)
          data.writeUTF(message.sha256)
          FrameType.OFFER
        }
        is Result -> {
          data.writeBoolean(message.ok)
          data.writeUTF(message.sha256)
          data.writeLong(message.millis)
          data.writeUTF(message.message)
          FrameType.RESULT
        }
        is Install -> {
          data.writeByte(message.status.ordinal)
          data.writeUTF(message.message)
          FrameType.INSTALL
        }
        is AppQuery -> {
          data.writeCount(message.packageNames.size)
          message.packageNames.forEach { data.writeUTF(it) }
          FrameType.APP_QUERY
        }
        is AppStates -> {
          data.writeCount(message.apps.size)
          message.apps.forEach { app ->
            data.writeUTF(app.packageName)
            data.writeBoolean(app.installed)
            data.writeLong(app.versionCode)
            data.writeUTF(app.versionName)
            data.writeCount(app.signers.size)
            app.signers.forEach { data.writeUTF(it) }
            data.writeUTF(app.installer)
          }
          FrameType.APP_STATES
        }
        is Uninstall -> {
          data.writeUTF(message.packageName)
          FrameType.UNINSTALL
        }
        Bye -> FrameType.BYE
      }
    data.flush()
    if (payload.size() > MAX_FRAME_SIZE)
      throw LinkException("Frame zu groß: ${payload.size()} Bytes")
    out.writeByte(type)
    out.writeInt(payload.size())
    payload.writeTo(out)
  }

  fun read(input: DataInputStream): Message {
    val type = input.readUnsignedByte()
    val length = input.readInt()
    if (length !in 0..MAX_FRAME_SIZE) throw LinkException("Ungültige Framelänge $length")
    val payload = ByteArray(length)
    input.readFully(payload)
    val data = DataInputStream(ByteArrayInputStream(payload))
    return try {
      when (type) {
        FrameType.HELLO -> {
          val protocol = data.readInt()
          val device = data.readUTF()
          // Protokoll 1 kannte keine Anmeldung, 2 weder API-Stufe noch versionCode; die
          // Gegenseite meldet dann die Version. Felder hinten fehlen bei älteren Versionen und
          // werden von älteren Lesern übergangen.
          when {
            protocol < 2 -> Hello(protocol, device, "")
            protocol < 3 -> Hello(protocol, device, data.readUTF(), data.readUTF(), data.readUTF())
            else ->
              Hello(
                protocol,
                device,
                data.readUTF(),
                data.readUTF(),
                data.readUTF(),
                data.readInt(),
                data.readLong(),
                appSha256 = if (data.available() > 0) data.readUTF() else "",
              )
          }
        }
        FrameType.AUTH -> Auth(data.readUTF())
        FrameType.AUTH_RESULT -> AuthResult(data.readBoolean(), data.readUTF())
        FrameType.PING -> Ping(data.readLong())
        FrameType.PONG -> Pong(data.readLong())
        FrameType.OFFER ->
          Offer(
            kind = enumAt(OfferKind.entries, data.readUnsignedByte()),
            name = data.readUTF(),
            versionCode = data.readLong(),
            size = data.readLong(),
            sha256 = data.readUTF(),
          )
        FrameType.RESULT ->
          Result(data.readBoolean(), data.readUTF(), data.readLong(), data.readUTF())
        FrameType.INSTALL ->
          Install(enumAt(InstallStatus.entries, data.readUnsignedByte()), data.readUTF())
        FrameType.APP_QUERY -> AppQuery(List(data.readCount()) { data.readUTF() })
        FrameType.APP_STATES ->
          AppStates(
            List(data.readCount()) {
              AppState(
                packageName = data.readUTF(),
                installed = data.readBoolean(),
                versionCode = data.readLong(),
                versionName = data.readUTF(),
                signers = List(data.readCount()) { data.readUTF() },
                installer = data.readUTF(),
              )
            }
          )
        FrameType.UNINSTALL -> Uninstall(data.readUTF())
        FrameType.BYE -> Bye
        else -> throw LinkException("Unbekannter Frame-Typ $type")
      }
    } catch (e: IOException) {
      if (e is LinkException) throw e
      throw LinkException("Frame vom Typ $type unvollständig", e)
    }
  }

  private fun DataOutputStream.writeCount(count: Int) {
    if (count > MAX_APPS) throw LinkException("Zu viele Einträge: $count")
    writeShort(count)
  }

  private fun DataInputStream.readCount(): Int {
    val count = readUnsignedShort()
    if (count > MAX_APPS) throw LinkException("Zu viele Einträge: $count")
    return count
  }

  private fun <T> enumAt(entries: List<T>, index: Int): T =
    entries.getOrNull(index) ?: throw LinkException("Unbekannter Wert $index")
}
