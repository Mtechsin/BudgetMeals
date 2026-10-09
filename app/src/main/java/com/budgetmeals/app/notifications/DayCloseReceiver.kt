package com.budgetmeals.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.budgetmeals.app.data.BudgetRepository
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DayCloseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val requestedEpochDay = intent?.getLongExtra(EXTRA_CLOSE_DATE, Long.MIN_VALUE) ?: Long.MIN_VALUE
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val date = if (requestedEpochDay != Long.MIN_VALUE) {
                    LocalDate.ofEpochDay(requestedEpochDay)
                } else {
                    val now = LocalTime.now()
                    if (now.hour >= 23) LocalDate.now() else LocalDate.now().minusDays(1)
                }
                BudgetRepository(appContext).use { it.closeDay(date) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Could not close the meal plan", error)
            } finally {
                try {
                    ReminderScheduler.scheduleClose(appContext)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "Could not schedule the next meal plan close", error)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_CLOSE_DATE = "close_date"
        private const val TAG = "DayCloseReceiver"
    }
}
