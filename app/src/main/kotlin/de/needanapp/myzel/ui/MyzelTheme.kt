package de.needanapp.myzel.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.needanapp.design.theme.AccentPreset
import de.needanapp.design.theme.AccentSource
import de.needanapp.design.theme.DesignStyle
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.design.theme.NeedanappTheme
import de.needanapp.design.theme.ThemeSettings
import de.needanapp.design.theme.ThemeSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Signalfarbe von Myzel, solange in der Darstellung keine andere gewählt ist: Orange, in beiden
 * Stilen.
 */
val MyzelAccent = Color(0xFFFF5A1F)

/**
 * Die Darstellung von Myzel, solange nichts gespeichert ist: der Stil NeedAnApp in „Wolke“ und
 * „Nordnacht“ (die Standard-Farbwelten des Stils) mit Myzels Orange, auch als Vorlage und als erste
 * eigene Farbe. Bis 0.22.1 war es der Stil Myzel in Alu und Graphit; wer Myzel schon nutzt, behält
 * seinen Stil, denn [prepareMyzelAppearance] hat beim ersten Start die ganze Darstellung
 * gespeichert.
 */
val MyzelThemeDefaults =
  ThemeSettings(
    design = DesignStyle.NEEDANAPP,
    preset = AccentPreset.ORANGE,
    customAccent = MyzelAccent,
  )

/**
 * Bereitet die Darstellung beim Start vor: Myzels Standardwerte für den Speicher des Design-Pakets,
 * einmal die Übernahme aus älteren Versionen und das frühe Lesen, damit der erste Bildschirm nicht
 * auf die Einstellungen wartet.
 *
 * Bis 0.20.0 lag das Design-Paket für Myzel auf einem eigenen Zweig, dessen Stile Farbton,
 * Helligkeiten, Paletten-Stil, Farbverlauf, Ecken und Schrift übergingen. Auf dem Hauptzweig gelten
 * sie wieder. Was ältere Versionen dafür gespeichert haben, geht deshalb einmal auf die
 * Standardwerte zurück, damit Myzel nach dem Update aussieht wie vorher.
 *
 * Außerdem stellte dort jeder Tipp auf eine Signalfarbe die Akzentquelle auf „Vorlage“. Die
 * Farbwelten bringen ihre eigene Farbe aber nur bei „Standard“ mit; steht die Vorlage auf Orange,
 * Myzels Standard, wird sie deshalb einmal zu „Standard“ (seit 0.21.1). Gelb und Grün bleiben.
 */
fun prepareMyzelAppearance(context: Context) {
  ThemeSettingsStore.defaults = MyzelThemeDefaults
  val store = ThemeSettingsStore(context)
  val prefs = context.getSharedPreferences(APPEARANCE_PREFS, Context.MODE_PRIVATE)
  val stylesMerged = prefs.getBoolean(KEY_STYLES_MERGED, false)
  val accentMerged = prefs.getBoolean(KEY_ORANGE_TO_STANDARD, false)
  if (stylesMerged && accentMerged) {
    store.preload()
    return
  }
  CoroutineScope(Dispatchers.IO).launch {
    val defaults = MyzelThemeDefaults
    store.update { stored ->
      val merged =
        if (stylesMerged) stored
        else
          stored.copy(
            darkBrightness = defaults.darkBrightness,
            surfaceTint = defaults.surfaceTint,
            cardBrightness = defaults.cardBrightness,
            backgroundGlow = defaults.backgroundGlow,
            paletteStyle = defaults.paletteStyle,
            cornerRadius = defaults.cornerRadius,
            customFontPath = null,
          )
      val orangePreset =
        merged.accentSource == AccentSource.PRESET && merged.preset == AccentPreset.ORANGE
      if (orangePreset) merged.copy(accentSource = AccentSource.DEFAULT) else merged
    }
    prefs.edit {
      putBoolean(KEY_STYLES_MERGED, true)
      putBoolean(KEY_ORANGE_TO_STANDARD, true)
    }
    store.preload()
  }
}

private const val APPEARANCE_PREFS = "myzel_appearance"
private const val KEY_STYLES_MERGED = "styles_merged"
private const val KEY_ORANGE_TO_STANDARD = "orange_to_standard"

/**
 * Das Theme von Myzel. Farben, Hell/Dunkel, Ecken, Schrift und Effekte kommen aus den
 * Darstellungs-Einstellungen des Design-Pakets von needanapp.de; die von F-Droid sind ersetzt.
 * Hinter allen Bildschirmen liegt der Verlauf des Pakets.
 *
 * Beginnt mit den Einstellungen, die App.onCreate vorab gelesen hat; sind sie noch nicht da, wartet
 * es auf sie, statt kurz die Standardwerte aufblitzen zu lassen. In Vorschauen gelten die
 * Standardwerte.
 */
@Composable
fun MyzelTheme(content: @Composable () -> Unit) {
  if (LocalInspectionMode.current) {
    ThemedSurface(MyzelThemeDefaults, content)
  } else {
    val context = LocalContext.current
    val store = remember { ThemeSettingsStore(context) }
    val settings by store.settings.collectAsStateWithLifecycle(store.cachedSettings)
    settings?.let { ThemedSurface(it, content) }
  }
}

/**
 * Zählt die Bildschirme, die den Verlauf gerade selbst über das ganze Fenster zeichnen; solange es
 * einer tut, lässt [MyzelTheme] ihn aus, damit er nicht doppelt gezeichnet wird.
 */
@Stable
class BackgroundCover {
  var count by mutableIntStateOf(0)
    private set

  /** Meldet, dass der Aufrufer den Verlauf zeichnet, solange [isCovering] gilt. */
  @Composable
  fun Covering(isCovering: Boolean) {
    DisposableEffect(isCovering) {
      if (isCovering) count++
      onDispose { if (isCovering) count-- }
    }
  }
}

val LocalBackgroundCover = staticCompositionLocalOf { BackgroundCover() }

@Composable
private fun ThemedSurface(settings: ThemeSettings, content: @Composable () -> Unit) {
  NeedanappTheme(settings, defaultAccent = MyzelAccent) {
    val cover = remember { BackgroundCover() }
    val brush = LocalDesignColors.current.backgroundBrush()
    // gibt die volle Größe weiter wie vorher das Fenster an die Surface
    Box(
      Modifier.fillMaxSize().drawBehind { if (cover.count == 0) drawRect(brush) },
      propagateMinConstraints = true,
    ) {
      CompositionLocalProvider(LocalBackgroundCover provides cover) {
        Surface(
          color = Color.Transparent,
          contentColor = MaterialTheme.colorScheme.onSurface,
          content = content,
        )
      }
    }
  }
}
