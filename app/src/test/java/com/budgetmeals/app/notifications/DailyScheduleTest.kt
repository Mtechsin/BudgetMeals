package com.budgetmeals.app.notifications

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyScheduleTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun reminderKeepsLocalHourAcrossSpringClockChange() {
        val now = ZonedDateTime.of(2026, 3, 7, 22, 0, 0, 0, zone)
        val next = nextDailyOccurrence(now, LocalTime.of(22, 0))

        assertEquals(LocalDate.of(2026, 3, 8), next.toLocalDate())
        assertEquals(LocalTime.of(22, 0), next.toLocalTime())
        assertEquals(23L, Duration.between(now, next).toHours())
    }

    @Test
    fun reminderKeepsLocalHourAcrossAutumnClockChange() {
        val now = ZonedDateTime.of(2026, 10, 31, 22, 0, 0, 0, zone)
        val next = nextDailyOccurrence(now, LocalTime.of(22, 0))

        assertEquals(LocalTime.of(22, 0), next.toLocalTime())
        assertEquals(25L, Duration.between(now, next).toHours())
    }

    @Test
    fun reminderBeforeItsHourRunsToday() {
        val now = ZonedDateTime.of(2026, 10, 15, 21, 59, 0, 0, zone)
        val next = nextDailyOccurrence(now, LocalTime.of(22, 0))

        assertEquals(now.toLocalDate(), next.toLocalDate())
        assertTrue(next.isAfter(now))
    }

    @Test
    fun dayStaysOpenUntilNextMidnightIncludingItsFinalMinute() {
        val now = ZonedDateTime.of(2026, 10, 15, 23, 59, 30, 0, zone)
        val next = nextDayClose(now)

        assertEquals(now.toLocalDate(), next.date)
        assertEquals(LocalDate.of(2026, 10, 16), next.trigger.toLocalDate())
        assertEquals(LocalTime.MIDNIGHT, next.trigger.toLocalTime())
        assertTrue(next.trigger.isAfter(now))
    }

    @Test
    fun nextCloseUsesLocalCalendarOnShortAndLongDays() {
        val spring = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, zone)
        val autumn = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)

        assertEquals(23L, Duration.between(spring, nextDayClose(spring).trigger).toHours())
        assertEquals(25L, Duration.between(autumn, nextDayClose(autumn).trigger).toHours())
    }
}
