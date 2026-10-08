package com.budgetmeals.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.AppThemeMode
import com.budgetmeals.app.data.BudgetRepository
import com.budgetmeals.app.data.BudgetSettings
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.ExpenseCategory
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.MealLog
import com.budgetmeals.app.data.MealReconciliation
import com.budgetmeals.app.data.MealStatus
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.ShoppingItem
import com.budgetmeals.app.data.StockItem
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BudgetRepository(application)
    private val mutationMutex = Mutex()
    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private val _builderDraft = MutableStateFlow(MealBuilderDraft())
    val builderDraft: StateFlow<MealBuilderDraft> = _builderDraft.asStateFlow()

    fun updateBuilderDraft(draft: MealBuilderDraft) {
        _builderDraft.value = draft.copy(hasStarted = true)
    }

    fun clearBuilderDraft() {
        _builderDraft.value = MealBuilderDraft()
    }

    val themeMode: StateFlow<AppThemeMode> = _uiState
        .map { it.snapshot.settings.themeMode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.SYSTEM)

    init {
        viewModelScope.coroutineContext[Job]?.invokeOnCompletion { repository.close() }
        refresh()
    }

    fun refresh() {
        launchMutation("Could not load your budget data.") {
            val today = LocalDate.now()
            val initial = repository.loadSnapshot()
            val inserted = repository.ensureDayPlan(today, BudgetMath.generatedMealsForDate(initial, today).values.toList())
            val snapshot = if (inserted > 0) repository.loadSnapshot() else initial
            MutationUpdate(snapshot = snapshot)
        }
    }

    fun recordPurchase(
        item: StockItem,
        rememberForNextShop: Boolean = false,
        catalogItem: FoodCatalogItem? = null,
    ) {
        mutate("Purchase saved. Added to spending${if (rememberForNextShop) " and next shop" else ""}.") {
            repository.recordPurchase(item, rememberForNextShop, catalogItem)
        }
    }

    fun saveStock(
        item: StockItem,
        rememberForNextShop: Boolean = false,
        catalogItem: FoodCatalogItem? = null,
    ) {
        mutate("Stock item updated.") { repository.saveStock(item, rememberForNextShop, catalogItem) }
    }

    fun deleteStock(item: StockItem) {
        mutate("${item.name} removed from stock.") { repository.deleteStock(item.id) }
    }

    fun rememberStock(item: StockItem) {
        mutate("${item.name} added to the next shop.") { repository.rememberStockForNextShop(item) }
    }

    fun logUsage(item: StockItem, amount: Double, date: LocalDate = LocalDate.now()) {
        mutate("Usage logged.") { repository.logUsage(item.id, amount, date) }
    }

    fun clearSampleStock() {
        mutate("Sample pantry cleared.") { repository.clearSampleStock() }
    }

    fun clearAllStock() {
        mutate("Pantry cleared.") { repository.clearAllStock() }
    }

    fun recordExpense(expense: Expense) {
        mutate("Expense added.") { repository.saveExpense(expense) }
    }

    fun deleteExpense(expense: Expense) {
        mutate("Expense deleted.") { repository.deleteExpense(expense.id) }
    }

    fun saveCategory(category: ExpenseCategory) {
        mutate("Category saved.") { repository.saveCategory(category) }
    }

    fun deleteCategory(category: ExpenseCategory) {
        mutate("Category deleted.") { repository.deleteCategory(category.id) }
    }

    fun saveFoodCatalogItem(item: FoodCatalogItem) {
        mutate("Food item saved.") { repository.saveFoodCatalogItem(item) }
    }

    fun importFoodCatalogJson(raw: String) {
        launchMutation("The food catalog JSON could not be imported.") {
            val count = repository.importFoodCatalogJson(raw)
            MutationUpdate(message = "Imported $count food ${if (count == 1) "item" else "items"}.")
        }
    }

    fun exportFoodCatalogJson(onReady: (String) -> Unit) {
        launchIo("Could not export the food catalog.") {
            val json = repository.exportFoodCatalogJson(_uiState.value.snapshot)
            withContext(Dispatchers.Main) { onReady(json) }
        }
    }

    fun deleteFoodCatalogItem(item: FoodCatalogItem) {
        mutate("Food item deleted.") { repository.deleteFoodCatalogItem(item.id) }
    }

    fun saveTemplate(template: MealTemplate) {
        mutate("Meal saved.") {
            repository.saveTemplate(template)
            rebuildOpenTodayPlan()
        }
    }

    fun deleteTemplate(template: MealTemplate) {
        mutate("Meal deleted.") {
            repository.deleteTemplate(template.id)
            rebuildOpenTodayPlan()
        }
    }

    fun recordMeal(template: MealTemplate, date: LocalDate = LocalDate.now(), costOverride: Double? = null) {
        val current = _uiState.value.snapshot
        if (date != current.today || date != LocalDate.now()) {
            showMessage("Only today's open meal can be marked from the plan.")
            return
        }
        if (current.dayClosure(date) != null) {
            showMessage("This day is already closed. Open the day review to change it.")
            return
        }
        val mealCost = costOverride ?: template.cost
        val log = MealLog(
            date = date,
            mealType = template.mealType,
            templateId = template.id,
            name = template.name,
            cost = mealCost,
            consumedCost = mealCost,
            foods = template.foodSummary,
            status = MealStatus.EATEN,
            components = template.components.ifEmpty { com.budgetmeals.app.data.MealDataCodec.legacyComponents(template.notes) },
        )
        mutate("${template.name} marked as eaten.") { repository.recordMeal(log) }
    }

    fun saveMealLog(log: MealLog) {
        mutate("Meal log updated.") { repository.recordMeal(log) }
    }

    fun reconcileMeals(date: LocalDate, entries: List<MealReconciliation>) {
        if (entries.isEmpty()) {
            showMessage("There are no planned meals to close yet.")
            return
        }
        val consumed = entries.sumOf { it.consumedCost }
        val unused = entries.sumOf { (it.template.cost - it.consumedCost).coerceAtLeast(0.0) }
        mutate("Day closed. ${money(consumed)} consumed, ${money(unused)} left or skipped.") {
            repository.reconcileMeals(date, entries)
        }
    }

    fun deleteMealLog(log: MealLog) {
        mutate("Meal log removed.") { repository.deleteMealLog(log.id) }
    }

    fun saveShopping(item: ShoppingItem) {
        mutate("Shopping list updated.") { repository.saveShopping(item) }
    }

    fun undoShoppingPurchase(item: ShoppingItem) {
        mutateShoppingAction(
            successMessage = "Purchase undone for ${item.name}.",
            failureMessage = "That shopping purchase has already been undone or removed.",
        ) {
            repository.undoShoppingPurchase(item)
        }
    }

    fun buyAgainShoppingItem(item: ShoppingItem) {
        mutate("${item.name} added back to shopping list.") {
            repository.buyAgainShoppingItem(item)
        }
    }

    fun toggleShopping(item: ShoppingItem) {
        toggleShopping(item, null)
    }

    fun toggleShopping(item: ShoppingItem, onPromptBuy: ((ShoppingItem) -> Unit)?) {
        if (item.isChecked) {
            undoShoppingPurchase(item)
        } else if (item.estimatedPrice <= 0.0 && !item.priceKnown && onPromptBuy != null) {
            onPromptBuy(item)
        } else {
            mutateShoppingAction(
                successMessage = "Bought ${item.name}. Price saved to stock and spending.",
                failureMessage = "${item.name} was already bought or removed.",
            ) {
                repository.buyShoppingItem(item, item.estimatedPrice, false)
            }
        }
    }

    fun deleteShopping(item: ShoppingItem) {
        mutate("Shopping item removed.") { repository.deleteShopping(item.id) }
    }

    fun buyShoppingItem(item: ShoppingItem, price: Double, rememberForNextShop: Boolean = false) {
        mutateShoppingAction(
            successMessage = "Bought ${item.name}. Price saved to stock and spending.",
            failureMessage = "${item.name} was already bought or removed.",
        ) {
            repository.buyShoppingItem(item, price, rememberForNextShop)
        }
    }

    fun spendFromSpares(
        amount: Double,
        reason: String,
        category: String? = null,
        expenseCategoryId: String = "spares",
    ) {
        val safeAmount = kotlin.math.abs(amount)
        if (safeAmount == 0.0) {
            showMessage("Enter an amount first.")
            return
        }
        mutate("Reward spent. You earned this.") {
            repository.spendFromSpares(safeAmount, reason, category, expenseCategoryId)
        }
    }

    fun addSpares(amount: Double, reason: String, category: String? = null) {
        val safeAmount = amount
        if (safeAmount == 0.0) {
            showMessage("Enter an amount first.")
            return
        }
        if (safeAmount < 0.0) {
            spendFromSpares(-safeAmount, reason, category)
            return
        }
        mutate("Added to spares.") {
            repository.addSpares(safeAmount, reason, category)
        }
    }

    fun saveTodayToSpares() {
        launchMutation("Could not move today's spare to savings.") {
            val today = LocalDate.now()
            val amount = repository.saveTodayToSpares()
            val refreshed = repository.loadSnapshot()
            val message = when {
                amount > 0.0 -> "${money(amount)} moved to spares."
                refreshed.settings.lastSparesAutoSaveDate == today -> "Today's spare is already in spares."
                else -> "No spare to move today. Check your spending first."
            }
            MutationUpdate(message = message, snapshot = refreshed)
        }
    }

    fun saveBudgetCorrection(amount: Double, reason: String, date: LocalDate = LocalDate.now()) {
        if (kotlin.math.abs(amount) < 0.01) {
            showMessage("The app already matches that amount.")
            return
        }
        val direction = if (amount > 0) "added to" else "removed from"
        mutate("${money(kotlin.math.abs(amount))} $direction food spending.") {
            repository.saveBudgetCorrection(
                Expense(
                    categoryId = "food",
                    amount = amount,
                    date = date,
                    description = reason.trim().ifBlank { "Matched to actual food spending" },
                    isCorrection = true,
                ),
            )
        }
    }

    fun deleteBudgetCorrection(expense: Expense) {
        mutate("Budget correction removed.") { repository.deleteBudgetCorrection(expense) }
    }

    fun assignMealToDay(date: LocalDate, mealType: MealType, template: MealTemplate?) {
        val message = if (template != null) {
            "${template.name} assigned to ${mealType.label.lowercase()} for ${BudgetMath.formatWeekday(date)}."
        } else {
            "${mealType.label} removed for ${BudgetMath.formatWeekday(date)}."
        }
        launchMutation("Could not update the meal plan.") {
            val current = repository.loadSnapshot()
            val resultMessage = if (current.dayClosure(date) != null) {
                "This day is already closed. Open the day review to change it."
            } else if (repository.assignMealToDay(date, mealType, template, current)) {
                message
            } else {
                "This day is already closed. Open the day review to change it."
            }
            MutationUpdate(message = resultMessage)
        }
    }

    fun rebuildPlanWindow(dates: List<LocalDate>) {
        launchMutation("Could not rebuild the meal plan.") {
            val snapshot = repository.loadSnapshot()
            val count = repository.rebuildPlanDates(dates, snapshot)
            val message = if (count > 0) {
                "Plan rebuilt for $count day${if (count == 1) "" else "s"}."
            } else {
                "Days in this window are already closed or have logged meals."
            }
            MutationUpdate(message = message, preserveExistingMessage = false)
        }
    }

    fun regenerateTodayPlan() {
        launchMutation("Could not rebuild today's meal plan.") {
            val today = LocalDate.now()
            val snapshot = repository.loadSnapshot()
            val replaced = repository.replaceOpenDayPlan(
                today,
                BudgetMath.generatedMealsForDate(snapshot, today).values.toList(),
            )
            val message = if (replaced) {
                "Today's plan was rebuilt from your meal shortcuts."
            } else {
                "Today's plan is already locked by a meal result or day closure."
            }
            MutationUpdate(message = message, preserveExistingMessage = false)
        }
    }

    fun saveSettings(settings: BudgetSettings) {
        mutate("Settings saved.") {
            repository.saveSettings(settings)
            rebuildOpenTodayPlan()
        }
    }

    fun setThemeMode(themeMode: AppThemeMode) {
        mutate(null) {
            val currentSettings = repository.readSettings()
            if (currentSettings.themeMode != themeMode) {
                repository.saveSettings(currentSettings.copy(themeMode = themeMode))
            }
        }
    }

    fun resetPantryToEmpty() {
        mutate("Pantry reset to empty.") {
            repository.resetPantryToEmpty()
        }
    }

    fun loadSamplePantry() {
        mutate("Sample pantry loaded.") {
            repository.loadSamplePantry()
        }
    }

    fun processRecurringExpenses(today: LocalDate = LocalDate.now()) {
        mutate(null) {
            repository.processRecurringExpenses(today)
        }
    }

    fun exportCsv(onReady: (String) -> Unit) {
        launchIo("Could not export your data.") {
            val csv = repository.exportCsv(_uiState.value.snapshot)
            withContext(Dispatchers.Main) { onReady(csv) }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun rebuildOpenTodayPlan() {
        val today = LocalDate.now()
        val snapshot = repository.loadSnapshot()
        if (snapshot.mealPlan(today).isNotEmpty()) return
        repository.replaceOpenDayPlan(
            today,
            BudgetMath.generatedMealsForDate(snapshot, today).values.toList(),
        )
    }

    private data class MutationUpdate(
        val message: String? = null,
        val preserveExistingMessage: Boolean = true,
        val snapshot: AppSnapshot? = null,
    )

    private fun mutate(message: String?, operation: suspend () -> Unit) {
        launchMutation("Could not save that change. Please try again.") {
            operation()
            MutationUpdate(message = message)
        }
    }

    private fun mutateShoppingAction(
        successMessage: String,
        failureMessage: String,
        operation: suspend () -> Boolean,
    ) {
        launchMutation("Could not update the shopping item.") {
            val message = if (operation()) successMessage else failureMessage
            MutationUpdate(message = message)
        }
    }

    private fun launchMutation(
        fallbackMessage: String,
        operation: suspend () -> MutationUpdate,
    ) {
        launchIo(fallbackMessage) {
            val update = operation()
            val snapshot = update.snapshot ?: repository.loadSnapshot()
            publishSnapshot(snapshot, update.message, update.preserveExistingMessage)
        }
    }

    private fun launchIo(fallbackMessage: String, operation: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            mutationMutex.withLock {
                runWithFailureHandling(fallbackMessage, operation)
            }
        }
    }

    private fun publishSnapshot(
        snapshot: AppSnapshot,
        message: String?,
        preserveExistingMessage: Boolean,
    ) {
        _uiState.update { current ->
            val nextMessage = if (preserveExistingMessage) current.message ?: message else message
            current.copy(snapshot = snapshot, isLoading = false, message = nextMessage)
        }
    }

    private suspend fun runWithFailureHandling(fallbackMessage: String, operation: suspend () -> Unit) {
        try {
            operation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = error.message?.takeIf { it.isNotBlank() } ?: fallbackMessage
            val refreshed = try {
                repository.loadSnapshot()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            publishSnapshot(refreshed ?: _uiState.value.snapshot, message, preserveExistingMessage = false)
        }
    }

    fun notify(message: String) = showMessage(message)

    private fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private fun money(value: Double): String = String.format(java.util.Locale.US, "%.0f", value)
}
