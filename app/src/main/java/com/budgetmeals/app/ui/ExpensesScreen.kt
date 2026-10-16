package com.budgetmeals.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.BudgetRepository
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.ExpenseCategory
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.extendedColors
import com.budgetmeals.app.ui.theme.Motion

@Composable
fun ExpensesScreen(
    snapshot: AppSnapshot,
    onBack: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onAddCategory: () -> Unit,
    onEditPurchase: (StockItem) -> Unit,
    onMatchSpending: () -> Unit,
    onOpenSpares: () -> Unit,
) {
    val month = snapshot.today
    val categories = snapshot.categories
    val foodCategory = remember(categories) { categories.firstOrNull { it.isFood } }
    val totalBudget = remember(categories) { categories.sumOf { it.monthlyBudget } }
    val totalSpent = remember(snapshot.currentMonthExpenses) { snapshot.currentMonthExpenses.sumOf { it.amount } }
    val monthTransactions = remember(snapshot.expenses, month) {
        snapshot.expenses.filter { it.date.year == month.year && it.date.month == month.month }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = "Expenses",
                onBack = onBack,
            )
        }

        item {
            SoftCard(contentPadding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tracked this month", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(BudgetMath.money(totalSpent), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Allocated", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(BudgetMath.money(totalBudget), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    ProgressBar(
                        (if (totalBudget > 0) totalSpent / totalBudget else 0.0).toFloat(),
                        color = if (totalBudget > 0 && totalSpent > totalBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        if (foodCategory != null) "Food ceiling: ${BudgetMath.money(foodCategory.monthlyBudget - snapshot.foodSpentThisMonth)} left" else "Add a food category to start the ceiling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "Category budgets",
                actionLabel = "Add category",
                onAction = onAddCategory,
            )
        }

        items(categories, key = { it.id }, contentType = { "category_card" }) { category ->
            CategoryBudgetCard(
                category, snapshot,
                modifier = Modifier.animateItem(
                    fadeInSpec = Motion.FadeTween,
                    placementSpec = Motion.PlacementSpring,
                    fadeOutSpec = Motion.FadeTween,
                ),
            )
        }

        item {
            SectionHeader("Recent transactions")
        }

        if (monthTransactions.isEmpty()) {
            item {
                EmptyState(
                    title = "No expenses yet",
                    message = "Tap Add expense to record internet, calling, or other spending.",
                    icon = AppIcons.ReceiptLong,
                )
            }
        } else {
            items(monthTransactions, key = { it.id }, contentType = { "expense_row" }) { expense ->
                val purchase = findPurchaseForExpense(snapshot.stock, expense)
                ExpenseRow(
                    modifier = Modifier.animateItem(
                        fadeInSpec = Motion.FadeTween,
                        placementSpec = Motion.PlacementSpring,
                        fadeOutSpec = Motion.FadeTween,
                    ),
                    expense = expense,
                    category = categories.firstOrNull { it.id == expense.categoryId },
                    onEdit = { onEditExpense(expense) },
                    onDelete = { onDeleteExpense(expense) },
                    purchase = purchase,
                    onEditPurchase = { purchase?.let(onEditPurchase) },
                    onMatchSpending = onMatchSpending,
                    onOpenSpares = onOpenSpares,
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "You can edit any category later. Start with the numbers you know today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBudgetCard(category: ExpenseCategory, snapshot: AppSnapshot, modifier: Modifier = Modifier) {
    val spent = BudgetMath.categorySpent(snapshot, category.id)
    val progress = BudgetMath.percent(spent, category.monthlyBudget).toFloat()
    SoftCard(modifier = modifier, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = when (category.icon.lowercase()) {
                    "rice", "staples" -> AppIcons.RiceBowl
                    "restaurant", "food" -> AppIcons.Restaurant
                    "wifi", "internet" -> AppIcons.Wifi
                    "call", "calling" -> AppIcons.Call
                    "home", "household" -> AppIcons.Home
                    "receipt" -> AppIcons.ReceiptLong
                    else -> AppIcons.Category
                },
                tint = if (category.isFood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                size = 42.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row {
                    Text(category.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${BudgetMath.money(spent)} / ${BudgetMath.money(category.monthlyBudget)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProgressBar(progress, color = if (progress >= 0.9f) MaterialTheme.colorScheme.error else if (category.isFood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    category: ExpenseCategory?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    purchase: StockItem?,
    onEditPurchase: () -> Unit,
    onMatchSpending: () -> Unit,
    onOpenSpares: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSparesWithdrawal = expense.id.startsWith(BudgetRepository.SPARES_EXPENSE_PREFIX)
    SoftCard(modifier = modifier, contentPadding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = when (category?.icon?.lowercase() ?: "receipt") {
                    "rice", "staples" -> AppIcons.RiceBowl
                    "restaurant", "food" -> AppIcons.Restaurant
                    "wifi", "internet" -> AppIcons.Wifi
                    "call", "calling" -> AppIcons.Call
                    "home", "household" -> AppIcons.Home
                    "receipt" -> AppIcons.ReceiptLong
                    else -> AppIcons.Category
                },
                tint = MaterialTheme.colorScheme.tertiary,
                size = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(expense.description.ifBlank { category?.name ?: "Expense" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (expense.isMealExpense) "${BudgetMath.formatDate(expense.date)} · Change through meal review"
                    else if (purchase != null) "${BudgetMath.formatDate(expense.date)} · Stock purchase"
                    else if (isSparesWithdrawal) "${BudgetMath.formatDate(expense.date)} · Spares withdrawal"
                    else "${category?.name ?: "Other"} · ${BudgetMath.formatDate(expense.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(BudgetMath.money(expense.amount), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!expense.isCorrection && !expense.isMealExpense && purchase == null && !isSparesWithdrawal) {
                IconButton(onClick = onEdit) { Icon(AppIcons.Edit, contentDescription = "Edit expense", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (!expense.isMealExpense && purchase == null && !isSparesWithdrawal) {
                IconButton(onClick = onDelete) {
                    Icon(
                        AppIcons.Delete,
                        contentDescription = if (expense.isRecurring || !expense.recurringScheduleId.isNullOrBlank()) {
                            "Delete expense and stop repeats"
                        } else {
                            "Delete expense"
                        },
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (purchase != null && !expense.isMealExpense) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEditPurchase, modifier = Modifier.weight(1f)) {
                    Text("Change through stock")
                }
                if (category?.isFood == true) {
                    TextButton(onClick = onMatchSpending, modifier = Modifier.weight(1f)) {
                        Text("Match real spending")
                    }
                }
            }
        } else if (isSparesWithdrawal) {
            TextButton(onClick = onOpenSpares) { Text("View spares") }
        }
    }
}
