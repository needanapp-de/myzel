package de.needanapp.myzel.ui.repositories

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import org.junit.Test

internal class RepoTimeTest {
  private val zone = ZoneId.of("Europe/Berlin")
  private val now = at(2026, 10, 31, 8, 0)

  private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
    ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

  private fun ZonedDateTime.millis() = toInstant().toEpochMilli()

  private fun pastTime(then: Long) = pastTime(then, now.millis(), zone)

  @Test
  fun `under a minute and in the future it is just now`() {
    assertEquals(PastTime.JustNow, pastTime(now.millis() - 59_999))
    assertEquals(PastTime.JustNow, pastTime(now.millis() + 5_000))
  }

  @Test
  fun `under an hour in minutes, under a day in hours`() {
    assertEquals(PastTime.MinutesAgo(1), pastTime(now.millis() - 60_000))
    assertEquals(PastTime.MinutesAgo(17), pastTime(now.minusMinutes(17).millis()))
    assertEquals(PastTime.MinutesAgo(59), pastTime(now.millis() - 3_599_999))
    assertEquals(PastTime.HoursAgo(1), pastTime(now.minusHours(1).millis()))
    assertEquals(PastTime.HoursAgo(23), pastTime(now.minusDays(1).millis() + 1))
  }

  @Test
  fun `from a day on the date and time, the year only when it differs`() {
    assertEquals(PastTime.At(now.minusDays(1), false), pastTime(now.minusDays(1).millis()))
    val older = at(2026, 10, 29, 12, 39)
    assertEquals(PastTime.At(older, false), pastTime(older.millis()))
    val lastYear = at(2025, 12, 24, 18, 5)
    assertEquals(PastTime.At(lastYear, true), pastTime(lastYear.millis()))
  }
}
