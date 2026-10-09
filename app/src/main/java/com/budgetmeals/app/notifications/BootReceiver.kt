package com.budgetmeals.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.budgetmeals.app.data.BudgetRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val settings = BudgetRepository(context.applicationContext).use { it.readSettings() }
            ReminderScheduler.sync(context, settings)
        }
    }
}
