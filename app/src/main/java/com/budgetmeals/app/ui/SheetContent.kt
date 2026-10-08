package com.budgetmeals.app.ui

import com.budgetmeals.app.ui.icons.AppIcons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmeals.app.data.AppThemeMode
import com.budgetmeals.app.data.BudgetSettings
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.ExpenseCategory
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.data.MealReconciliation
import com.budgetmeals.app.data.MealStatus
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.ShoppingItem
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt


private enum class MealReviewChoice {
    FULL,
    LEFTOVERS,
    SKIPPED,
    UNRECORDED,
}

private enum class BudgetCorrectionMode {
    MATCH_MONTH,
    MISSED_SPENDING,
}


@Composable
fun SheetContent(
    sheet: AppSheet,
    viewModel: BudgetViewModel,
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    onDismiss: () -> Unit,
) {
    when (sheet) {
        AppSheet.QuickAdd -> Unit // Rendered by BudgetMealsApp (needs to swap to another sheet)
        AppSheet.AddPurchase -> StockFormSheet(null, snapshot, viewModel, onDismiss)
        is AppSheet.EditStock -> StockFormSheet(sheet.item, snapshot, viewModel, onDismiss)
        is AppSheet.LogUsage -> UsageFormSheet(sheet.item, snapshot, viewModel, onDismiss)
        AppSheet.AddExpense -> ExpenseFormSheet(snapshot, viewModel, onDismiss)
        is AppSheet.EditExpense -> ExpenseFormSheet(snapshot, viewModel, onDismiss, sheet.expense)
        AppSheet.AddCategory -> CategoryFormSheet(viewModel, onDismiss)
        AppSheet.AddFoodItem -> FoodItemFormSheet(null, snapshot, viewModel, onDismiss)
        is AppSheet.EditFoodItem -> FoodItemFormSheet(sheet.item, snapshot, viewModel, onDismiss)
        AppSheet.AddTemplate -> TemplateFormSheet(null, snapshot, viewModel, onDismiss)
        is AppSheet.EditTemplate -> TemplateFormSheet(sheet.template, snapshot, viewModel, onDismiss)
        is AppSheet.AssignDayMeal -> AssignDayMealSheet(sheet.date, sheet.mealType, sheet.currentTemplate, snapshot, viewModel, onDismiss)
        AppSheet.AddShopping -> ShoppingFormSheet(null, viewModel, onDismiss)
        is AppSheet.EditShopping -> ShoppingFormSheet(sheet.item, viewModel, onDismiss)
        is AppSheet.BuyShopping -> BuyShoppingSheet(sheet.item, viewModel, onDismiss)
        AppSheet.AddSpares -> SparesFormSheet(snapshot, viewModel, onDismiss)
        AppSheet.DayReview -> DayReviewSheet(snapshot, viewModel, onDismiss)
        AppSheet.BudgetCorrection -> BudgetCorrectionSheet(snapshot, viewModel, onDismiss)
        AppSheet.Settings -> SettingsSheet(snapshot.settings, viewModel, onDismiss)
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String? = null, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        IconButton(onClick = onClose) {
            Icon(AppIcons.Close, contentDescription = "Close", tint = TextSecondary)
        }
    }
}



@Composable
private fun ExpenseFormSheet(
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
    existing: Expense? = null,
) {
    var selectedCategoryId by rememberSaveable {
        mutableStateOf(existing?.categoryId ?: snapshot.categories.firstOrNull { !it.isFood }?.id ?: snapshot.categories.firstOrNull()?.id.orEmpty())
    }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.cleanNumber() ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }
    var recurring by rememberSaveable { mutableStateOf(existing?.isRecurring ?: false) }
    val category = snapshot.categories.firstOrNull { it.id == selectedCategoryId }
    val valid = selectedCategoryId.isNotBlank() && amount.asDouble() > 0.0
    SheetBody(
        title = if (existing == null) "Log an expense" else "Edit expense",
        subtitle = if (existing == null) "Two taps is enough. You can edit the details later." else "Change the amount or category without starting over.",
        onClose = onDismiss,
    ) {
        FormSectionTitle("Category")
        DropdownField(
            label = "Category",
            value = category ?: snapshot.categories.first(),
            values = snapshot.categories,
            labelOf = { it.name },
            onSelected = { selectedCategoryId = it.id },
        )
        Spacer(Modifier.height(8.dp))
        FormSectionTitle("Details")
        NumberField(amount, { amount = it }, "Amount", prefix = "EGP ")
        BudgetTextField(description, { description = it }, label = "What was it for? (optional)", modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Checkbox(checked = recurring, onCheckedChange = { recurring = it }, colors = sheetCheckboxColors())
            Column {
                Text("This repeats every month", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text("For internet or phone credit, set it once.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        if (category?.isFood == true) {
            Text(
                "Food expenses are counted toward the ${BudgetMath.money(category.monthlyBudget)} ceiling.",
                style = MaterialTheme.typography.bodySmall,
                color = AccentMintLight,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(if (existing == null) "Save expense" else "Save changes", {
            val expense = Expense(
                id = existing?.id ?: UUID.randomUUID().toString(),
                categoryId = selectedCategoryId,
                amount = amount.asDouble(),
                date = existing?.date ?: LocalDate.now(),
                description = description.trim(),
                isRecurring = recurring,
                recurringFrequency = if (recurring) "MONTHLY" else null,
                isCorrection = existing?.isCorrection ?: false,
            )
            viewModel.recordExpense(expense)
            onDismiss()
        }, enabled = valid, icon = AppIcons.Check)
        if (existing != null) {
            OutlinedButton(
                onClick = { viewModel.deleteExpense(existing); onDismiss() },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ErrorBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 50.dp),
            ) {
                Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete expense", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CategoryFormSheet(viewModel: BudgetViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var isFood by remember { mutableStateOf(false) }
    val icons = listOf("receipt", "transport", "health", "personal", "other")
    var icon by remember { mutableStateOf(icons.first()) }
    val valid = name.isNotBlank() && budget.asDouble() >= 0.0
    SheetBody("Add a category", "Use this for anything outside food.", onDismiss) {
        FormSectionTitle("Category details")
        BudgetTextField(name, { name = it }, label = "Category name")
        NumberField(budget, { budget = it }, "Monthly budget", Modifier.padding(top = 12.dp), prefix = "EGP ")
        FormSectionTitle("Icon")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            icons.forEach { option ->
                TagChip(
                    text = option.replaceFirstChar { it.uppercase() },
                    selected = (icon == option),
                    onClick = { icon = option },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
            Checkbox(checked = isFood, onCheckedChange = { isFood = it }, colors = sheetCheckboxColors())
            Text("Count this category toward the food ceiling", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Save category", {
            viewModel.saveCategory(
                ExpenseCategory(
                    id = "custom_${UUID.randomUUID()}",
                    name = name.trim(),
                    icon = icon,
                    monthlyBudget = budget.asDouble(),
                    isFood = isFood,
                    isCustom = true,
                    sortOrder = 20,
                ),
            )
            onDismiss()
        }, enabled = valid, icon = AppIcons.Check)
    }
}




@Composable
private fun ShoppingFormSheet(existing: ShoppingItem?, viewModel: BudgetViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(existing?.quantity?.cleanNumber() ?: "1") }
    var unit by rememberSaveable { mutableStateOf(existing?.unit ?: "piece") }
    var price by rememberSaveable { mutableStateOf(existing?.let { if (it.priceKnown) it.estimatedPrice.cleanNumber() else "" } ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: ItemCategory.FOOD_STAPLE) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    val valid = name.isNotBlank() && quantity.asDouble() > 0.0
    SheetBody(if (existing == null) "Add to shopping" else "Edit shopping item", "The list is shared with your stock habits.", onDismiss) {
        FormSectionTitle("Item")
        BudgetTextField(name, { name = it }, label = "Item")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            NumberField(quantity, { quantity = it }, "Quantity", Modifier.weight(1f))
            BudgetTextField(unit, { unit = it }, label = "Unit", modifier = Modifier.weight(1f))
        }
        NumberField(price, { price = it }, "Expected price", Modifier.padding(top = 12.dp), prefix = "EGP ")
        FormSectionTitle("Category")
        DropdownField("Category", category, ItemCategory.entries, { it.label }, { category = it })
        BudgetTextField(note, { note = it }, label = "Note (optional)", modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Save item", {
            val p = price.asDouble()
            val isKnown = price.isNotBlank() && p >= 0.0
            viewModel.saveShopping(
                ShoppingItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    quantity = quantity.asDouble(),
                    unit = unit.trim().ifBlank { "piece" },
                    estimatedPrice = if (isKnown) p else 0.0,
                    category = category,
                    isChecked = existing?.isChecked ?: false,
                    createdDate = existing?.createdDate ?: LocalDate.now(),
                    sourceItemId = existing?.sourceItemId,
                    note = note.trim(),
                    purchaseExpenseId = existing?.purchaseExpenseId,
                    purchaseStockId = existing?.purchaseStockId,
                    priceKnown = isKnown,
                ),
            )
            onDismiss()
        }, enabled = valid, icon = AppIcons.Check)
        if (existing != null) {
            OutlinedButton(
                onClick = { viewModel.deleteShopping(existing); onDismiss() },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ErrorBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 50.dp),
            ) {
                Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remove from list", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BuyShoppingSheet(item: ShoppingItem, viewModel: BudgetViewModel, onDismiss: () -> Unit) {
    var price by remember(item.id) { mutableStateOf(if (item.priceKnown && item.estimatedPrice > 0.0) item.estimatedPrice.cleanNumber() else "") }
    var remember by remember(item.id) { mutableStateOf(false) }
    val valid = price.isNotBlank() && price.asDouble() >= 0.0
    SheetBody("Buy ${item.name}", "One tap saves it to stock, spending, and the next list if you want.", onDismiss) {
        FormSectionTitle("Purchase price")
        NumberField(price, { price = it }, "Actual price", prefix = "EGP ")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Checkbox(checked = remember, onCheckedChange = { remember = it }, colors = sheetCheckboxColors())
            Text("Keep it on the list for next time", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        Spacer(Modifier.height(14.dp))
        AnimatedSaveHint("This will be counted as food spending today and added to stock automatically.")
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Confirm purchase", { viewModel.buyShoppingItem(item, price.asDouble(), remember); onDismiss() }, enabled = valid, icon = AppIcons.Check)
    }
}

@Composable
private fun SparesFormSheet(snapshot: com.budgetmeals.app.data.AppSnapshot, viewModel: BudgetViewModel, onDismiss: () -> Unit) {
    var spend by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    val value = amount.asDouble()
    val valid = value > 0.0 && (!spend || value <= snapshot.sparesBalance)
    SheetBody(if (spend) "Spend from spares" else "Add to spares", "A reward, not a punishment.", onDismiss) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            OutlinedButton(
                onClick = { spend = false },
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (!spend) AccentMint.copy(alpha = 0.15f) else DarkSurfaceLow,
                    contentColor = if (!spend) AccentMintLight else TextSecondary,
                ),
                border = BorderStroke(1.dp, if (!spend) AccentMint.copy(alpha = 0.4f) else DarkBorder),
            ) {
                Text("Add money", fontWeight = if (!spend) FontWeight.Bold else FontWeight.Normal)
            }
            OutlinedButton(
                onClick = { spend = true },
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (spend) AccentAmber.copy(alpha = 0.15f) else DarkSurfaceLow,
                    contentColor = if (spend) AccentAmber else TextSecondary,
                ),
                border = BorderStroke(1.dp, if (spend) AccentAmber.copy(alpha = 0.4f) else DarkBorder),
            ) {
                Text("Spend treat", fontWeight = if (spend) FontWeight.Bold else FontWeight.Normal)
            }
        }
        FormSectionTitle("Amount")
        NumberField(amount, { amount = it }, "Amount", prefix = "EGP ")
        BudgetTextField(reason, { reason = it }, label = "Reason", modifier = Modifier.padding(top = 12.dp))
        if (spend) {
            DropdownField("Treat type", category, listOf("snack", "meal_treat", "dessert", "other"), { it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }, { category = it }, Modifier.padding(top = 12.dp))
        }
        if (spend && value > snapshot.sparesBalance) {
            Text(
                "Your balance is ${BudgetMath.money(snapshot.sparesBalance)}. The app will not let spares go negative.",
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else if (spend) {
            Text(
                "You earned this. Spend it without guilt.",
                style = MaterialTheme.typography.bodySmall,
                color = AccentMintLight,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(if (spend) "Spend treat" else "Add to spares", {
            val description = reason.trim().ifBlank { if (spend) "Treat" else "Added money" }
            if (spend) {
                viewModel.spendFromSpares(value, description, category)
            } else {
                viewModel.addSpares(value, description, category)
            }
            onDismiss()
        }, enabled = valid, icon = if (spend) AppIcons.ShoppingCart else AppIcons.Savings)
    }
}

@Composable
private fun DayReviewSheet(
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
) {
    var selectedDate by remember { mutableStateOf(snapshot.today) }
    val selectableDates = remember(snapshot.today) {
        (0..6).map { snapshot.today.minusDays(it.toLong()) }
    }

    val plan = remember(
        snapshot.dayPlans,
        snapshot.templates,
        snapshot.settings,
        snapshot.sparesBalance,
        snapshot.stock,
        snapshot.foodCatalog,
        selectedDate,
    ) {
        BudgetMath.plannedMealsForDate(snapshot, selectedDate).toSortedMap()
    }
    val logs = remember(snapshot.mealLogs, selectedDate) {
        snapshot.mealLogs
            .filter { it.date == selectedDate }
            .groupBy { it.mealType }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.actualTime } }
    }
    val initialChoices = remember(plan, logs, selectedDate) {
        plan.mapValues { (mealType, _) ->
            val log = logs[mealType]
            when {
                log == null -> MealReviewChoice.UNRECORDED
                log.status == MealStatus.UNRECORDED -> MealReviewChoice.UNRECORDED
                log.status == MealStatus.SKIPPED -> MealReviewChoice.SKIPPED
                log.status == MealStatus.PARTIAL -> MealReviewChoice.LEFTOVERS
                else -> MealReviewChoice.FULL
            }
        }
    }
    val initialLeftoverPercent = remember(plan, logs, selectedDate) {
        plan.mapValues { (mealType, template) ->
            val log = logs[mealType]
            if (log == null || template.cost <= 0.001) {
                25
            } else {
                ((log.leftoverCost / template.cost) * 100.0).roundToInt().coerceIn(1, 99)
            }
        }
    }
    var choices by remember(selectedDate) { mutableStateOf(initialChoices) }
    var leftoverPercent by remember(selectedDate) { mutableStateOf(initialLeftoverPercent) }

    LaunchedEffect(selectedDate, plan, logs) {
        choices = initialChoices
        leftoverPercent = initialLeftoverPercent
    }

    val wasClosed = snapshot.dayClosure(selectedDate) != null

    val consumedTotal = plan.entries.sumOf { (mealType, template) ->
        when (choices[mealType]) {
            MealReviewChoice.FULL -> template.cost
            MealReviewChoice.LEFTOVERS -> template.cost * (1.0 - leftoverPercent[mealType].orZeroPercent() / 100.0)
            MealReviewChoice.SKIPPED, MealReviewChoice.UNRECORDED, null -> 0.0
        }
    }
    val leftoverTotal = plan.entries.sumOf { (mealType, template) ->
        if (choices[mealType] == MealReviewChoice.LEFTOVERS) {
            template.cost * (leftoverPercent[mealType].orZeroPercent() / 100.0)
        } else {
            0.0
        }
    }
    val skippedTotal = plan.entries.sumOf { (mealType, template) ->
        if (choices[mealType] == MealReviewChoice.SKIPPED) template.cost else 0.0
    }
    val unrecordedTotal = plan.entries.sumOf { (mealType, template) ->
        if (choices[mealType] == MealReviewChoice.UNRECORDED) template.cost else 0.0
    }

    val isDateToday = selectedDate == snapshot.today
    val dateLabel = if (isDateToday) "today's" else "${BudgetMath.formatDate(selectedDate)}'s"

    SheetBody(
        title = if (wasClosed) "Review $dateLabel meals" else "Close $dateLabel meals",
        subtitle = "Review and correct meals eaten, leftovers, or missed days.",
        onClose = onDismiss,
    ) {
        Text(
            "Select date to review:",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selectableDates.forEach { d ->
                val isSelected = d == selectedDate
                val isClosed = snapshot.dayClosure(d) != null
                val label = when (d) {
                    snapshot.today -> "Today"
                    snapshot.today.minusDays(1) -> "Yesterday"
                    else -> "${BudgetMath.formatWeekday(d)} ${d.dayOfMonth}"
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedDate = d },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label)
                            if (isClosed) {
                                Icon(AppIcons.Check, contentDescription = "Day reviewed", tint = AccentMintLight, modifier = Modifier.size(16.dp))
                            } else if (d < snapshot.today) {
                                Icon(AppIcons.Schedule, contentDescription = "Review pending", tint = AccentAmber, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentMint.copy(alpha = 0.16f),
                        selectedLabelColor = AccentMintLight,
                    ),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        if (plan.isEmpty()) {
            AnimatedSaveHint("There are no planned meals for ${BudgetMath.formatDate(selectedDate)}.")
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Back", onDismiss, icon = AppIcons.ArrowBack)
            return@SheetBody
        }

        Text(
            "Select what happened for each meal. Past unrecorded meals can be logged or corrected here.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))

        plan.forEach { (mealType, template) ->
            val choice = choices[mealType] ?: MealReviewChoice.UNRECORDED
            val percent = leftoverPercent[mealType] ?: 25
            val consumed = when (choice) {
                MealReviewChoice.FULL -> template.cost
                MealReviewChoice.LEFTOVERS -> template.cost * (1.0 - percent / 100.0)
                MealReviewChoice.SKIPPED, MealReviewChoice.UNRECORDED -> 0.0
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
                border = BorderStroke(1.dp, DarkBorder),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = AccentMint.copy(alpha = 0.12f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MealIcon(mealType, modifier = Modifier.size(19.dp), tint = AccentMintLight)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mealType.label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                template.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(BudgetMath.money(template.cost), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    }
                    Text(
                        template.foodSummary.ifBlank { "No foods listed for this meal." },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (template.foodSummary.isBlank()) TextMuted else TextSecondary,
                    )
                    template.components.filter { it.useStock }.forEach { component ->
                        val catalog = snapshot.foodCatalog.firstOrNull { it.id == component.catalogId }
                        val availability = catalog?.let { snapshot.availabilityFor(it) }
                        val availabilityText = when {
                            availability == null -> ""
                            availability.hasUsableStock ->
                                " · ${availability.portions.cleanNumber()} available (${availability.baseQuantity.cleanNumber()} ${availability.baseUnit})"
                            availability.note != null -> " · ${availability.note}"
                            else -> ""
                        }
                        Text(
                            buildString {
                                append(component.quantity.cleanNumber())
                                append(" ${component.unit} ${component.name}")
                                append(availabilityText)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (availability?.hasUsableStock == true && availability.portions < component.quantity) ErrorRed else TextMuted,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ReviewChoiceButton(
                            label = "Ate all",
                            selected = choice == MealReviewChoice.FULL,
                            selectedColor = AccentMint,
                            onClick = { choices = choices + (mealType to MealReviewChoice.FULL) },
                            modifier = Modifier.weight(1f),
                        )
                        ReviewChoiceButton(
                            label = "Left some",
                            selected = choice == MealReviewChoice.LEFTOVERS,
                            selectedColor = AccentAmber,
                            onClick = { choices = choices + (mealType to MealReviewChoice.LEFTOVERS) },
                            modifier = Modifier.weight(1f),
                        )
                        ReviewChoiceButton(
                            label = "Skipped",
                            selected = choice == MealReviewChoice.SKIPPED,
                            selectedColor = ErrorRed,
                            onClick = { choices = choices + (mealType to MealReviewChoice.SKIPPED) },
                            modifier = Modifier.weight(1f),
                        )
                        ReviewChoiceButton(
                            label = "Missed",
                            selected = choice == MealReviewChoice.UNRECORDED,
                            selectedColor = TextMuted,
                            onClick = { choices = choices + (mealType to MealReviewChoice.UNRECORDED) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (choice == MealReviewChoice.LEFTOVERS) {
                        NumberField(
                            value = percent.toString(),
                            onValueChange = { raw ->
                                val parsed = raw.toIntOrNull() ?: return@NumberField
                                leftoverPercent = leftoverPercent + (mealType to parsed.coerceIn(1, 99))
                            },
                            label = "Percent left over",
                            prefix = "% ",
                            decimal = false,
                        )
                        Text(
                            "All raw ingredients are deducted from stock (since the meal was cooked). Leftover portions are tracked as prepared food.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentAmber,
                        )
                    }
                    val subtitle = when (choice) {
                        MealReviewChoice.FULL -> "Consumed ${BudgetMath.money(consumed)}"
                        MealReviewChoice.LEFTOVERS -> "Consumed ${BudgetMath.money(consumed)} · saved ${BudgetMath.money((template.cost - consumed).coerceAtLeast(0.0))}"
                        MealReviewChoice.SKIPPED -> "Skipped · ${BudgetMath.money(template.cost)} not consumed"
                        MealReviewChoice.UNRECORDED -> "Unrecorded · no meal record"
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = when (choice) {
                            MealReviewChoice.FULL -> AccentMintLight
                            MealReviewChoice.LEFTOVERS -> AccentAmber
                            MealReviewChoice.SKIPPED -> ErrorRed
                            MealReviewChoice.UNRECORDED -> TextMuted
                        },
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AccentMint.copy(alpha = 0.09f)),
            border = BorderStroke(1.dp, AccentMint.copy(alpha = 0.25f)),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (isDateToday) "Today's result" else "${BudgetMath.formatDate(selectedDate)} result",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Text("Consumed ${BudgetMath.money(consumedTotal)}", style = MaterialTheme.typography.bodySmall, color = AccentMintLight)
                Text("Left over ${BudgetMath.money(leftoverTotal)}", style = MaterialTheme.typography.bodySmall, color = AccentAmber)
                if (skippedTotal > 0.0) {
                    Text("Skipped ${BudgetMath.money(skippedTotal)}", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                }
                if (unrecordedTotal > 0.0) {
                    Text("Unrecorded / missed ${BudgetMath.money(unrecordedTotal)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Text(
                    "This records meal consumption and updates stock accordingly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = if (wasClosed) "Save meal review" else "Save and close day",
            onClick = {
                val entries = plan.map { (mealType, template) ->
                    val choice = choices[mealType] ?: MealReviewChoice.UNRECORDED
                    val percent = leftoverPercent[mealType] ?: 25
                    val consumed = when (choice) {
                        MealReviewChoice.FULL -> template.cost
                        MealReviewChoice.LEFTOVERS -> template.cost * (1.0 - percent / 100.0)
                        MealReviewChoice.SKIPPED, MealReviewChoice.UNRECORDED -> 0.0
                    }
                    val status = when (choice) {
                        MealReviewChoice.FULL -> MealStatus.EATEN
                        MealReviewChoice.LEFTOVERS -> MealStatus.PARTIAL
                        MealReviewChoice.SKIPPED -> MealStatus.SKIPPED
                        MealReviewChoice.UNRECORDED -> MealStatus.UNRECORDED
                    }
                    MealReconciliation(
                        template = template,
                        consumedCost = consumed.coerceIn(0.0, template.cost.coerceAtLeast(0.0)),
                        status = status,
                    )
                }
                viewModel.reconcileMeals(selectedDate, entries)
                onDismiss()
            },
            icon = AppIcons.FactCheck,
        )
    }
}

@Composable
private fun ReviewChoiceButton(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) selectedColor.copy(alpha = 0.55f) else DarkBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) selectedColor.copy(alpha = 0.14f) else DarkSurfaceLow,
            contentColor = if (selected) selectedColor else TextSecondary,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun BudgetCorrectionSheet(
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(BudgetCorrectionMode.MATCH_MONTH) }
    var actual by remember { mutableStateOf("") }
    var missedAmount by remember { mutableStateOf("") }
    var daysAgo by remember { mutableStateOf(0) }
    var reason by remember { mutableStateOf("") }
    val recorded = snapshot.foodSpentThisMonth
    val amountValue = actual.asDouble()
    val missedValue = missedAmount.asDouble()
    val difference = if (mode == BudgetCorrectionMode.MATCH_MONTH) {
        if (actual.isNotBlank()) amountValue - recorded else 0.0
    } else {
        missedValue
    }
    val valid = if (mode == BudgetCorrectionMode.MATCH_MONTH) {
        actual.isNotBlank() && amountValue >= 0.0 && abs(difference) >= 0.01
    } else {
        missedValue > 0.0
    }
    val correctionDate = if (mode == BudgetCorrectionMode.MATCH_MONTH) {
        LocalDate.now()
    } else {
        LocalDate.now().minusDays(daysAgo.toLong())
    }
    val recentCorrections = snapshot.expenses
        .filter { it.isCorrection }
        .sortedByDescending { it.date }

    SheetBody(
        title = "Match real spending",
        subtitle = "Fix the budget when something was missed or entered twice.",
        onClose = onDismiss,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
            border = BorderStroke(1.dp, DarkBorder),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recorded food spending this month", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(BudgetMath.money(recorded), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text(
                    "Purchases are counted once. Checking off a planned meal does not add its cost again. This correction fixes the budget; use Bought something when you also need stock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CorrectionModeButton(
                label = "Match month",
                selected = mode == BudgetCorrectionMode.MATCH_MONTH,
                onClick = { mode = BudgetCorrectionMode.MATCH_MONTH },
                modifier = Modifier.weight(1f),
            )
            CorrectionModeButton(
                label = "Add one missed",
                selected = mode == BudgetCorrectionMode.MISSED_SPENDING,
                onClick = { mode = BudgetCorrectionMode.MISSED_SPENDING },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))

        if (mode == BudgetCorrectionMode.MATCH_MONTH) {
            FormSectionTitle("Actual monthly total")
            NumberField(
                value = actual,
                onValueChange = { actual = it },
                label = "What did you really spend on food this month?",
                prefix = "EGP ",
            )
        } else {
            FormSectionTitle("Forgotten spending")
            NumberField(
                value = missedAmount,
                onValueChange = { missedAmount = it },
                label = "Amount you forgot to enter",
                prefix = "EGP ",
            )
            DropdownField(
                label = "When did you spend it?",
                value = daysAgo,
                values = (0..30).toList(),
                labelOf = { days ->
                    when (days) {
                        0 -> "Today"
                        1 -> "Yesterday"
                        else -> "$days days ago"
                    }
                },
                onSelected = { daysAgo = it },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        BudgetTextField(
            value = reason,
            onValueChange = { reason = it },
            label = "What are you correcting? (optional)",
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.height(12.dp))
        AnimatedSaveHint(
            when {
                mode == BudgetCorrectionMode.MISSED_SPENDING && missedValue <= 0.0 -> "Enter the missing amount and the day it belongs to."
                mode == BudgetCorrectionMode.MATCH_MONTH && actual.isBlank() -> "Enter the amount from your real spending. The app will calculate the correction."
                difference > 0.0 && mode == BudgetCorrectionMode.MISSED_SPENDING -> "Adds ${BudgetMath.money(difference)} to ${BudgetMath.formatDate(correctionDate)} food spending."
                difference > 0.0 -> "Adds ${BudgetMath.money(difference)} to food spending."
                difference < 0.0 -> "Removes ${BudgetMath.money(abs(difference))} from food spending."
                else -> "The recorded total already matches."
            },
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = when {
                !valid && mode == BudgetCorrectionMode.MATCH_MONTH -> "Enter actual spending"
                !valid -> "Enter missing amount"
                difference > 0.0 -> "Add ${BudgetMath.money(difference)}"
                difference < 0.0 -> "Remove ${BudgetMath.money(abs(difference))}"
                else -> "Already matched"
            },
            onClick = {
                val defaultReason = if (mode == BudgetCorrectionMode.MATCH_MONTH) {
                    "Matched this month's actual food spending"
                } else {
                    "Added forgotten food spending"
                }
                viewModel.saveBudgetCorrection(difference, reason.ifBlank { defaultReason }, correctionDate)
                onDismiss()
            },
            enabled = valid,
            icon = AppIcons.Check,
        )

        if (recentCorrections.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            FormSectionTitle("Recent corrections")
            recentCorrections.take(6).forEach { expense ->
                BudgetCorrectionRow(
                    expense = expense,
                    onDelete = { viewModel.deleteBudgetCorrection(expense) },
                )
            }
        }
    }
}

@Composable
private fun CorrectionModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) AccentMint.copy(alpha = 0.5f) else DarkBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) AccentMint.copy(alpha = 0.14f) else DarkSurfaceLow,
            contentColor = if (selected) AccentMintLight else TextSecondary,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun BudgetCorrectionRow(expense: Expense, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (expense.amount >= 0.0) "+" else "-") + BudgetMath.money(abs(expense.amount)),
                style = MaterialTheme.typography.titleMedium,
                color = if (expense.amount >= 0.0) AccentAmber else AccentMintLight,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${BudgetMath.formatDate(expense.date)} · ${expense.description}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(AppIcons.Delete, contentDescription = "Remove correction", tint = ErrorRed)
        }
    }
}

private fun Int?.orZeroPercent(): Int = this ?: 0

@Composable
private fun SettingsSheet(settings: BudgetSettings, viewModel: BudgetViewModel, onDismiss: () -> Unit) {
    var monthly by remember { mutableStateOf(settings.monthlyFoodBudget.cleanNumber()) }
    var daily by remember { mutableStateOf(settings.dailyFoodBudget.cleanNumber()) }
    var weekly by remember { mutableStateOf(settings.weeklyFoodLimit.cleanNumber()) }
    var kosharyPrice by remember { mutableStateOf(settings.kosharyPrice.cleanNumber()) }
    var kosharyDay by remember { mutableStateOf(settings.kosharyDay) }
    var shoppingDay by remember { mutableStateOf(settings.shoppingDay) }
    var autoSpares by remember { mutableStateOf(settings.autoSaveSpares) }
    var reminders by remember { mutableStateOf(settings.remindersEnabled) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    val valid = monthly.asDouble() > 0 && daily.asDouble() > 0 && weekly.asDouble() > 0
    SheetBody("Settings", "Change the numbers when prices change.", onDismiss) {
        FormSectionTitle("Appearance")
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppThemeMode.entries.forEach { mode ->
                val isSelected = themeMode == mode
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = isSelected,
                    onClick = { themeMode = mode },
                    label = { Text(mode.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        val icon = when (mode) {
                            AppThemeMode.SYSTEM -> AppIcons.BrightnessAuto
                            AppThemeMode.LIGHT -> AppIcons.LightMode
                            AppThemeMode.DARK -> AppIcons.DarkMode
                        }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentMint.copy(alpha = 0.15f),
                        selectedLabelColor = AccentMintLight,
                        selectedLeadingIconColor = AccentMintLight,
                        containerColor = DarkSurfaceLow,
                        labelColor = TextSecondary,
                        iconColor = TextSecondary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = DarkBorder,
                        selectedBorderColor = AccentMint.copy(alpha = 0.4f),
                    ),
                )
            }
        }
        FormSectionTitle("Food limits")
        NumberField(monthly, { monthly = it }, "Monthly food ceiling", prefix = "EGP ")
        NumberField(daily, { daily = it }, "Daily average", prefix = "EGP ", modifier = Modifier.padding(top = 12.dp))
        NumberField(weekly, { weekly = it }, "Weekly target", prefix = "EGP ", modifier = Modifier.padding(top = 12.dp))
        FormSectionTitle("Koshary day")
        NumberField(kosharyPrice, { kosharyPrice = it }, "Koshary price", prefix = "EGP ")
        DropdownField("Day", kosharyDay, DayOfWeek.entries, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, { kosharyDay = it }, Modifier.padding(top = 12.dp))
        FormSectionTitle("Shopping reminders")
        DropdownField("Shopping day", shoppingDay, DayOfWeek.entries, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, { shoppingDay = it })
        SettingSwitch("Daily budget reminder", reminders, { reminders = it })
        SettingSwitch("Show a spare-saving prompt", autoSpares, { autoSpares = it })
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Save settings", {
            viewModel.saveSettings(
                settings.copy(
                    monthlyFoodBudget = monthly.asDouble(),
                    dailyFoodBudget = daily.asDouble(),
                    weeklyFoodLimit = weekly.asDouble(),
                    kosharyPrice = kosharyPrice.asDouble(),
                    kosharyDay = kosharyDay,
                    shoppingDay = shoppingDay,
                    remindersEnabled = reminders,
                    autoSaveSpares = autoSpares,
                    themeMode = themeMode,
                ),
            )
            onDismiss()
        }, enabled = valid, icon = AppIcons.Check)
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text("You can turn this off at any time.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = AccentMint,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DarkSurfaceHigh,
                uncheckedBorderColor = DarkBorder,
            ),
        )
    }
}

@Composable
fun SheetBody(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        SheetHeader(title, subtitle, onClose)
        content()
        Spacer(Modifier.height(24.dp))
    }
}


@Composable
private fun AssignDayMealSheet(
    date: LocalDate,
    mealType: MealType,
    currentTemplate: MealTemplate?,
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
) {
    var mode by remember {
        mutableStateOf(
            if (snapshot.templates.any { it.mealType == mealType }) "shortcuts" else "custom"
        )
    }
    var selectedFilter by remember { mutableStateOf<MealType?>(mealType) }
    val initialCustomName = currentTemplate?.name.orEmpty()
    val initialCustomCost = currentTemplate?.cost?.let { if (it == 0.0) "" else it.cleanNumber() } ?: ""
    val initialCustomNotes = currentTemplate?.notes.orEmpty()
    var customName by remember { mutableStateOf(initialCustomName) }
    var customCost by remember { mutableStateOf(initialCustomCost) }
    var customNotes by remember { mutableStateOf(initialCustomNotes) }

    val templates = remember(snapshot.templates, selectedFilter) {
        if (selectedFilter == null) {
            snapshot.templates
        } else {
            snapshot.templates.filter { it.mealType == selectedFilter }
        }
    }

    SheetBody(
        title = "Assign ${mealType.label}",
        subtitle = "${BudgetMath.formatWeekday(date)}, ${BudgetMath.formatDate(date)}",
        onClose = onDismiss,
    ) {
        if (currentTemplate != null) {
            SoftCard(
                modifier = Modifier.padding(bottom = 12.dp),
                contentPadding = 14.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Current meal", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(currentTemplate.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(BudgetMath.money(currentTemplate.cost), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.assignMealToDay(date, mealType, null)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        border = BorderStroke(1.dp, ErrorBorder),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode == "shortcuts",
                onClick = { mode = "shortcuts" },
                label = { Text("Saved shortcuts (${snapshot.templates.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentMint.copy(alpha = 0.16f),
                    selectedLabelColor = AccentMintLight,
                ),
            )
            FilterChip(
                selected = mode == "custom",
                onClick = { mode = "custom" },
                label = { Text("Custom meal") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentMint.copy(alpha = 0.16f),
                    selectedLabelColor = AccentMintLight,
                ),
            )
        }

        if (mode == "shortcuts") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("All") },
                )
                MealType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedFilter == type,
                        onClick = { selectedFilter = type },
                        label = { Text(type.label) },
                    )
                }
            }

            if (templates.isEmpty()) {
                SoftCard(contentPadding = 16.dp) {
                    Text(
                        "No saved shortcuts found. Switch to 'Custom meal' to enter a meal for this day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { template ->
                        SoftCard(
                            onClick = {
                                viewModel.assignMealToDay(date, mealType, template)
                                onDismiss()
                            },
                            contentPadding = 14.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    if (template.notes.isNotBlank()) {
                                        Text(template.notes, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                                Text(
                                    BudgetMath.money(template.cost),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BudgetTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = "Meal name",
                )
                NumberField(
                    value = customCost,
                    onValueChange = { customCost = it },
                    label = "Estimated cost",
                    prefix = "EGP ",
                )
                BudgetTextField(
                    value = customNotes,
                    onValueChange = { customNotes = it },
                    label = "Note (optional)",
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "Assign meal",
                    onClick = {
                        val customTemplate = MealTemplate(
                            name = customName.trim(),
                            mealType = mealType,
                            cost = customCost.asDouble(),
                            notes = customNotes.trim(),
                            isCustom = true,
                        )
                        viewModel.assignMealToDay(date, mealType, customTemplate)
                        onDismiss()
                    },
                    enabled = customName.isNotBlank() && customCost.asDouble() >= 0.0,
                    icon = AppIcons.Check,
                )
            }
        }
    }
}
