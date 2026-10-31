package de.needanapp.myzel.ui.repositories

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import org.fdroid.R

/**
 * Welche Form [asPastTimeString] wählt; ohne Android-Ressourcen, damit der Test sie prüfen kann.
 */
internal sealed interface PastTime {
  data object JustNow : PastTime

  data class MinutesAgo(val minutes: Long) : PastTime

  data class HoursAgo(val hours: Long) : PastTime

  /** @param withYear [time] liegt in einem anderen Jahr als jetzt */
  data class At(val time: ZonedDateTime, val withYear: Boolean) : PastTime
}

/** Bis 24 Stunden relativ, danach Datum und Uhrzeit; was in der Zukunft liegt, gilt als eben. */
internal fun pastTime(then: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): PastTime {
  val ago = now - then
  return when {
    ago < DateUtils.MINUTE_IN_MILLIS -> PastTime.JustNow
    ago < DateUtils.HOUR_IN_MILLIS -> PastTime.MinutesAgo(ago / DateUtils.MINUTE_IN_MILLIS)
    ago < DateUtils.DAY_IN_MILLIS -> PastTime.HoursAgo(ago / DateUtils.HOUR_IN_MILLIS)
    else -> {
      val time = Instant.ofEpochMilli(then).atZone(zone)
      PastTime.At(time, withYear = time.year != Instant.ofEpochMilli(now).atZone(zone).year)
    }
  }
}

/**
 * Wann etwas bei einer Paketquelle war (veröffentlicht, heruntergeladen, zuletzt nach Updates
 * gesucht): bis 24 Stunden relativ („vor 11 Std.“), danach mit Datum und Uhrzeit („am 29.10. um
 * 12:39“, aus einem anderen Jahr mit Jahr). Ersetzt dort die Angaben von [DateUtils] wie
 * „Vorgestern“, die nicht sagen, wann genau. Die Uhrzeit folgt der 12/24-Stunden-Einstellung.
 */
fun Long.asPastTimeString(context: Context, now: Long = System.currentTimeMillis()): String =
  when (val past = pastTime(this, now)) {
    PastTime.JustNow -> context.getString(R.string.myzel_time_just_now)
    is PastTime.MinutesAgo -> context.getString(R.string.myzel_time_minutes_ago, past.minutes)
    is PastTime.HoursAgo -> context.getString(R.string.myzel_time_hours_ago, past.hours)
    is PastTime.At -> {
      val pattern =
        context.getString(
          if (past.withYear) R.string.myzel_time_date_year_pattern
          else R.string.myzel_time_date_pattern
        )
      val locale = context.resources.configuration.locales[0]
      val date = DateTimeFormatter.ofPattern(pattern, locale).format(past.time)
      val clock = DateFormat.getTimeFormat(context).format(Date(this))
      context.getString(R.string.myzel_time_at, date, clock)
    }
  }

/** Siehe [asPastTimeString]. */
@Composable fun Long.asPastTimeString(): String = asPastTimeString(LocalContext.current)
