package com.budgetmeals.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.budgetmeals.app.data.BudgetSettings
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {
    private const val REQUEST_CODE = 7301
    private const val CLOSE_REQUEST_CODE = 7302
    private const val HOUR = 22
    private const val MINUTE = 0
    private const val CLOSE_HOUR = 23
    private const val CLOSE_MINUTE = 59

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
        scheduleRepeating(
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
        val now = LocalDateTime.now()
        var targetDate = now.toLocalDate()
        var trigger = now.withHour(CLOSE_HOUR).withMinute(CLOSE_MINUTE).withSecond(0).withNano(0)
        if (!trigger.isAfter(now)) {
            targetDate = targetDate.plusDays(1)
            trigger = trigger.plusDays(1)
        }
        val intent = Intent(context, DayCloseReceiver::class.java).apply {
            putExtra(DayCloseReceiver.EXTRA_CLOSE_DATE, targetDate.toEpochDay())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            CLOSE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            pendingIntent,
        )
    }

    private fun scheduleRepeating(
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
        val now = LocalDateTime.now()
        var trigger = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!trigger.isAfter(now)) trigger = trigger.plusDays(1)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
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
