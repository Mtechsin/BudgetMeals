package com.budgetmeals.app.notifications

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Resolve each occurrence in the local calendar so daylight saving does not shift its hour. */
internal fun nextDailyOccurrence(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val today = now.toLocalDate().atTime(time).atZone(now.zone)
    return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
}

internal data class DayCloseSchedule(val date: LocalDate, val trigger: ZonedDateTime)

internal fun nextDayClose(now: ZonedDateTime): DayCloseSchedule = DayCloseSchedule(
    date = now.toLocalDate(),
    trigger = now.toLocalDate().plusDays(1).atStartOfDay(now.zone),
)
