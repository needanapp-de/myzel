package de.needanapp.myzel.link

/**
 * Version des Protokolls; beide Seiten nennen sie im [Hello]. 2: Anmeldung mit gemeinsamem
 * Schlüssel. 3: [Hello] nennt API-Stufe und versionCode der App. 4: Stand von Apps auf der Uhr
 * ([AppQuery]) und Entfernen ([Uninstall]). 5: [AppState.installer].
 *
 * Die Uhr nimmt nur ihre eigene Version an. Damit ein neues Myzel jede ältere Myzel Wear über
 * Bluetooth aktualisieren kann, bleiben die Kernnachrichten seit Protokoll 3 auf der Leitung
 * gleich: [Hello] (neue Felder nur hinten anhängen), [Auth], [AuthResult], [Offer], [Result],
 * [Install] und [Bye]. Mit ihnen spricht das Handy die Version der Uhr ([MIN_UPDATE_PROTOCOL] bis
 * jetzt) und schickt ihr die neue Myzel Wear.
 */
const val PROTOCOL_VERSION = 5

/** Die älteste Version, deren Uhr das Handy noch über Bluetooth aktualisieren kann. */
const val MIN_UPDATE_PROTOCOL = 3

/** So viele Apps fragt ein [AppQuery] höchstens ab. */
const val MAX_APPS = 256

/**
 * Name eines Geräts für [Hello.device], etwa „Google Pixel Watch 2“. Bei Google steht der
 * Hersteller schon im Modell; dann nicht doppelt.
 */
fun deviceName(manufacturer: String, model: String): String =
  if (model.startsWith(manufacturer, ignoreCase = true)) model.trim()
  else "$manufacturer $model".trim()

/**
 * Nachrichten von Myzel Link. Auf der Leitung ist jede ein Frame aus Typ (1 Byte), Länge (4 Byte)
 * und Nutzdaten ([Frames]). Nach einem [Offer] folgen ohne Frame genau [Offer.size] Bytes Rohdaten.
 */
sealed interface Message

/**
 * Begrüßung in beide Richtungen, zugleich der Beginn der Anmeldung:
 * 1. Handy → Uhr: Hello mit einer Zufallszahl [nonce].
 * 2. Uhr → Handy: Hello mit eigener [nonce], der Kennung ihres Schlüssels [keyId] und dem Nachweis
 *    [proof], dass sie den Schlüssel kennt (HMAC über beide Zufallszahlen). Ohne Schlüssel bleiben
 *    beide leer: Die Uhr ist nicht eingerichtet.
 * 3. Handy → Uhr: [Auth] mit seinem Nachweis; die Uhr antwortet mit [AuthResult].
 *
 * Erst danach nimmt die Uhr andere Nachrichten an. Die frischen Zufallszahlen beider Seiten
 * verhindern, dass ein mitgeschnittener Nachweis ein zweites Mal taugt.
 *
 * @param device Name des Geräts zur Anzeige, etwa „Google Pixel Watch 2“
 * @param nonce Zufallszahl, [NONCE_SIZE] Bytes hexadezimal
 * @param sdk API-Stufe des Geräts; das Handy wählt danach passende Uhr-APKs
 * @param appVersionCode versionCode von Myzel oder Myzel Wear auf dem Gerät
 * @param appSha256 SHA-256 der installierten APK von Myzel Wear; daran erkennt das Handy, ob die
 *   Uhr die Myzel Wear aus seiner Beilage hat. Leer vom Handy und von Uhren vor Protokoll 5.
 */
data class Hello(
  val protocol: Int,
  val device: String,
  val nonce: String,
  val keyId: String = "",
  val proof: String = "",
  val sdk: Int = 0,
  val appVersionCode: Long = 0,
  val appSha256: String = "",
) : Message

/** Handy → Uhr: Nachweis des Handys, dass es den Schlüssel der Uhr kennt. */
data class Auth(val proof: String) : Message

/** Uhr → Handy: ob die Anmeldung gilt. Bei [ok] = false schließt die Uhr danach die Verbindung. */
data class AuthResult(val ok: Boolean, val message: String) : Message

/** Handy → Uhr; die Uhr antwortet mit [Pong] und demselben Zeitstempel. */
data class Ping(val stamp: Long) : Message

data class Pong(val stamp: Long) : Message

/** Was mit den Rohdaten nach einem [Offer] geschehen soll. */
enum class OfferKind {
  /** Nur messen: Die Uhr prüft den SHA-256 und verwirft die Daten. */
  TEST,

  /** Eine APK, die die Uhr nach der Prüfung installiert. */
  APK,
}

/**
 * Handy → Uhr: Ankündigung der Rohdaten, die direkt danach folgen.
 *
 * @param name Paketname der APK oder Bezeichnung des Tests
 * @param sha256 SHA-256 der Rohdaten, kleingeschrieben hexadezimal
 */
data class Offer(
  val kind: OfferKind,
  val name: String,
  val versionCode: Long,
  val size: Long,
  val sha256: String,
) : Message

/**
 * Uhr → Handy, nach den Rohdaten eines [Offer]: ob alles ankam und der SHA-256 stimmt.
 *
 * @param sha256 der auf der Uhr gemessene SHA-256
 * @param millis Dauer des Empfangs auf der Uhr
 */
data class Result(val ok: Boolean, val sha256: String, val millis: Long, val message: String) :
  Message

/** Stand einer Installation oder Deinstallation auf der Uhr. */
enum class InstallStatus {
  /** Die Uhr fragt den Nutzer; das Ergebnis folgt mit [SUCCESS] oder [FAILURE]. */
  CONFIRM,
  SUCCESS,
  FAILURE,
}

/**
 * Uhr → Handy, nach einem [Result] für eine APK oder nach [Uninstall]; kann mehrmals kommen
 * ([InstallStatus.CONFIRM]).
 */
data class Install(val status: InstallStatus, val message: String) : Message

/** Handy → Uhr: Welche dieser Apps sind installiert? Die Uhr antwortet mit [AppStates]. */
data class AppQuery(val packageNames: List<String>) : Message

/**
 * Stand einer App auf der Uhr.
 *
 * @param versionCode 0, wenn sie nicht installiert ist
 * @param signers SHA-256 der Signaturzertifikate, kleingeschrieben hexadezimal
 * @param installer Paketname der App, die sie installiert hat (etwa der Play Store); leer, wenn
 *   unbekannt oder per ADB installiert
 */
data class AppState(
  val packageName: String,
  val installed: Boolean,
  val versionCode: Long = 0,
  val versionName: String = "",
  val signers: List<String> = emptyList(),
  val installer: String = "",
)

/** Uhr → Handy: die Antwort auf ein [AppQuery], in derselben Reihenfolge. */
data class AppStates(val apps: List<AppState>) : Message

/**
 * Handy → Uhr: [packageName] entfernen. Die Uhr fragt den Nutzer und meldet den Stand wie bei einer
 * Installation mit [Install].
 */
data class Uninstall(val packageName: String) : Message

/** Verabschiedung; danach schließt die Gegenseite die Verbindung. */
data object Bye : Message
