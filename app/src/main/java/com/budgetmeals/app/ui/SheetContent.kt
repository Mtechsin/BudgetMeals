package com.budgetmeals.app.ui

import androidx.compose.runtime.Composable
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.state.BudgetViewModel

@Composable
fun SheetContent(
    sheet: AppSheet,
    viewModel: BudgetViewModel,
    snapshot: AppSnapshot,
    onDismiss: () -> Unit,
    onOpenSheet: ((AppSheet) -> Unit)? = null,
    onDismissGuardChanged: ((() -> Unit)?) -> Unit = {},
) {
    when (sheet) {
        AppSheet.QuickAdd -> Unit // Rendered by BudgetMealsApp (needs to swap to another sheet)
        AppSheet.LogFood -> LogFoodSheet(snapshot, viewModel, onDismiss)
        is AppSheet.HelpMeChoose -> HelpMeChooseSheet(snapshot, viewModel, initialMealType = sheet.mealType, onDismiss = onDismiss)
        is AppSheet.MealAction -> MealActionSheet(
            mealType = sheet.mealType,
            date = sheet.date,
            snapshot = snapshot,
            viewModel = viewModel,
            onDismiss = onDismiss,
            onOpenHelpMeChoose = { type ->
                onOpenSheet?.invoke(AppSheet.HelpMeChoose(type)) ?: onDismiss()
            },
        )
        AppSheet.AddPurchase -> StockFormSheet(null, snapshot, viewModel, onDismiss)
        is AppSheet.EditStock -> StockFormSheet(sheet.item, snapshot, viewModel, onDismiss)
        is AppSheet.LogUsage -> UsageFormSheet(sheet.item, snapshot, viewModel, onDismiss)
        AppSheet.AddExpense -> ExpenseFormSheet(snapshot, viewModel, onDismiss)
        is AppSheet.EditExpense -> ExpenseFormSheet(snapshot, viewModel, onDismiss, sheet.expense)
        AppSheet.AddCategory -> CategoryFormSheet(viewModel, onDismiss)
        AppSheet.AddFoodItem -> FoodItemFormSheet(null, snapshot, viewModel, onDismiss)
        is AppSheet.EditFoodItem -> FoodItemFormSheet(sheet.item, snapshot, viewModel, onDismiss)
        AppSheet.AddTemplate -> TemplateFormSheet(null, snapshot, viewModel, onDismiss, onDismissGuardChanged)
        is AppSheet.EditTemplate -> TemplateFormSheet(sheet.template, snapshot, viewModel, onDismiss, onDismissGuardChanged)
        is AppSheet.AssignDayMeal -> AssignDayMealSheet(sheet.date, sheet.mealType, sheet.currentTemplate, snapshot, viewModel, onDismiss)
        AppSheet.AddShopping -> ShoppingFormSheet(null, snapshot, viewModel, onDismiss)
        is AppSheet.EditShopping -> ShoppingFormSheet(sheet.item, snapshot, viewModel, onDismiss)
        is AppSheet.BuyShopping -> BuyShoppingSheet(sheet.item, snapshot, viewModel, onDismiss)
        AppSheet.AddSpares -> SparesFormSheet(snapshot, viewModel, onDismiss)
        AppSheet.DayReview -> DayReviewSheet(snapshot, viewModel, onDismiss)
        AppSheet.BudgetCorrection -> BudgetCorrectionSheet(snapshot, viewModel, onDismiss)
        AppSheet.Settings -> SettingsSheet(snapshot.settings, viewModel, onDismiss)
    }
}
