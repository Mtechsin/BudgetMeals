package com.budgetmeals.app.data

import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.rules.ExternalResource

/** Owns each test's storage and closes every connection before deleting the test data. */
internal class BudgetStorageRule : ExternalResource() {
    val today: LocalDate = LocalDate.of(2026, 10, 8)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    val context = IsolatedBudgetTestContext(
        base = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
        prefix = "test-${UUID.randomUUID()}-",
    )
    private val closeActions = mutableListOf<() -> Unit>()

    fun openDatabase(): BudgetDatabase = BudgetDatabase(context).also {
        closeActions.add(it::close)
    }

    fun openRepository(): BudgetRepository = BudgetRepository(context, clock).also {
        closeActions.add(it::close)
    }

    override fun after() {
        try {
            closeActions.asReversed().forEach { close -> close() }
        } finally {
            closeActions.clear()
            context.clearPreferences("budget_meals")
            context.deleteSharedPreferences("budget_meals")
            context.deleteDatabase(BudgetDao.DATABASE_NAME)
        }
    }
}
