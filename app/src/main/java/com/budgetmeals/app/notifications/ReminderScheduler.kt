package com.budgetmeals.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.budgetmeals.app.data.BudgetSettings
import java.time.LocalTime
import java.time.ZonedDateTime

object ReminderScheduler {
    private const val REQUEST_CODE = 7301
    private const val CLOSE_REQUEST_CODE = 7302
    private const val HOUR = 22
    private const val MINUTE = 0

    fun sync(context: Context, settings: BudgetSettings) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        scheduleClose(alarmManager, context)
        if (settings.remindersEnabled) {
            scheduleReminder(alarmManager, context)
        } else {
            cancelReminder(alarmManager, context)
        }
    }

    fun scheduleDaily(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        scheduleReminder(alarmManager, context)
        scheduleClose(alarmManager, context)
    }

    private fun scheduleReminder(alarmManager: AlarmManager, context: Context) {
        NotificationHelper.createChannel(context)
        scheduleReminderOccurrence(
            alarmManager = alarmManager,
            context = context,
            requestCode = REQUEST_CODE,
            receiver = ReminderReceiver::class.java,
            hour = HOUR,
            minute = MINUTE,
        )
    }

    fun scheduleClose(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        scheduleClose(alarmManager, context)
    }

    private fun scheduleClose(alarmManager: AlarmManager, context: Context) {
        val schedule = nextDayClose(ZonedDateTime.now())
        val intent = Intent(context, DayCloseReceiver::class.java).apply {
            putExtra(DayCloseReceiver.EXTRA_CLOSE_DATE, schedule.date.toEpochDay())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            CLOSE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            schedule.trigger.toInstant().toEpochMilli(),
            pendingIntent,
        )
    }

    private fun scheduleReminderOccurrence(
        alarmManager: AlarmManager,
        context: Context,
        requestCode: Int,
        receiver: Class<*>,
        hour: Int,
        minute: Int,
    ) {
        val intent = Intent(context, receiver)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val trigger = nextDailyOccurrence(ZonedDateTime.now(), LocalTime.of(hour, minute))
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            trigger.toInstant().toEpochMilli(),
            pendingIntent,
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        cancelReminder(alarmManager, context)
        val intent = Intent(context, DayCloseReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            CLOSE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun cancelReminder(alarmManager: AlarmManager, context: Context) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
    }
}
