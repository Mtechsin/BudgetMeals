package com.budgetmeals.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.budgetmeals.app.data.BudgetRepository
import com.budgetmeals.app.state.BudgetMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = BudgetRepository(appContext).use { it.loadSnapshot() }
                val summary = BudgetMath.dayMealSummary(snapshot, snapshot.today)
                val lowStock = snapshot.lowStock.firstOrNull()
                val mealMessage = if (summary.isClosed) {
                    "Today's food plan is closed. You consumed ${BudgetMath.money(summary.consumedCost)}, left ${BudgetMath.money(summary.leftoverCost)}, and skipped ${BudgetMath.money(summary.skippedCost)}."
                } else {
                    "Review today's food plan. ${summary.pendingMeals} ${if (summary.pendingMeals == 1) "meal is" else "meals are"} still open. Unmarked meals will be counted as skipped."
                }
                val message = if (lowStock != null) {
                    "$mealMessage ${lowStock.name} is also getting low."
                } else {
                    mealMessage
                }
                NotificationHelper.show(
                    appContext,
                    title = if (summary.isClosed) "Today's meals are settled" else "Close today's food plan",
                    message = message,
                    openDayReview = !summary.isClosed,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Could not prepare the meal reminder", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ReminderReceiver"
    }
}
