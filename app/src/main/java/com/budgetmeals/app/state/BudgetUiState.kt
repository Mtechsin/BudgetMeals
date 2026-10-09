package com.budgetmeals.app.state

import androidx.compose.runtime.Immutable
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.MealType
import java.time.DayOfWeek

@Immutable
data class MealBuilderDraft(
    val name: String = "",
    val mealType: MealType = MealType.LUNCH,
    val components: List<MealComponent> = emptyList(),
    val cost: String = "",
    val isRecurring: Boolean = true,
    val dayOfWeek: DayOfWeek? = null,
    val notes: String = "",
    val hasStarted: Boolean = false,
)

@Immutable
data class BudgetUiState(
    val snapshot: AppSnapshot = AppSnapshot(),
    val isLoading: Boolean = true,
    val message: String? = null,
)
