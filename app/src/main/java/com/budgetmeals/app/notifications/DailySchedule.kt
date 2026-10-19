package com.budgetmeals.app.notifications

import com.budgetmeals.app.data.BudgetSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Resolve each occurrence in the local calendar so daylight saving does not shift its hour. */
internal fun nextDailyOccurrence(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val today = now.toLocalDate().atTime(time).atZone(now.zone)
    return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
}

internal data class DayCloseSchedule(val date: LocalDate, val trigger: ZonedDateTime)

internal fun nextDayClose(now: ZonedDateTime, settings: BudgetSettings? = null): DayCloseSchedule {
    val today = now.toLocalDate()
    val localTime = now.toLocalTime()
    val dinnerStart = settings?.dinnerStart ?: LocalTime.of(18, 0)
    val dinnerEnd = settings?.dinnerEnd ?: LocalTime.of(23, 0)
    val isOvernight = dinnerEnd <= dinnerStart

    if (isOvernight) {
        if (localTime < dinnerEnd) {
            val date = today.minusDays(1)
            val trigger = today.atTime(dinnerEnd).atZone(now.zone)
            return DayCloseSchedule(date = date, trigger = trigger)
        }
        val date = today
        val trigger = today.plusDays(1).atTime(dinnerEnd).atZone(now.zone)
        return DayCloseSchedule(date = date, trigger = trigger)
    } else {
        val date = today
        val trigger = today.plusDays(1).atStartOfDay(now.zone)
        return DayCloseSchedule(date = date, trigger = trigger)
    }
}
