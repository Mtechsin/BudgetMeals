package com.budgetmeals.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.monthFoodSpendingComparison
import com.budgetmeals.app.state.weekFoodSpendingComparison
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.Motion
import com.budgetmeals.app.ui.theme.Spacing
import com.budgetmeals.app.ui.theme.extendedColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FoodSpendingScreen(
    snapshot: AppSnapshot,
    onBack: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onEditPurchase: (StockItem) -> Unit,
) {
    var isMonthMode by rememberSaveable { mutableStateOf(false) }
    var currentWeekStart by rememberSaveable(stateSaver = localDateSaver) {
        mutableStateOf(BudgetMath.currentWeekStart(snapshot.today))
    }
    var currentMonthStart by rememberSaveable(stateSaver = localDateSaver) {
        mutableStateOf(snapshot.today.withDayOfMonth(1))
    }

    val foodCategoryIds = remember(snapshot.categories) {
        snapshot.categories.filter { it.isFood }.map { it.id }.toSet()
    }

    val weekComparison = remember(snapshot.expenses, currentWeekStart, snapshot.today) {
        weekFoodSpendingComparison(snapshot, currentWeekStart)
    }

    val monthComparison = remember(snapshot.expenses, currentMonthStart, snapshot.today) {
        monthFoodSpendingComparison(snapshot, currentMonthStart)
    }

    // Filter transactions for current view period
    val activeTransactions = remember(
        snapshot.expenses,
        isMonthMode,
        currentWeekStart,
        currentMonthStart,
        foodCategoryIds,
    ) {
        if (!isMonthMode) {
            val weekEnd = currentWeekStart.plusDays(6)
            snapshot.expenses.filter {
                it.categoryId in foodCategoryIds &&
                    !it.isBudgetTransfer &&
                    it.date in currentWeekStart..weekEnd
            }.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.amount })
        } else {
            val monthEnd = currentMonthStart.plusMonths(1).minusDays(1)
            snapshot.expenses.filter {
                it.categoryId in foodCategoryIds &&
                    !it.isBudgetTransfer &&
                    it.date in currentMonthStart..monthEnd
            }.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.amount })
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = Spacing.md,
            bottom = Spacing.bottomClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Food spending",
                subtitle = "Track what you spend on food and groceries",
                onBack = onBack,
            )
        }

        // Mode switch: Week vs Month
        item(key = "mode_toggle") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !isMonthMode,
                    onClick = { isMonthMode = false },
                    label = { Text("Week", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = isMonthMode,
                    onClick = { isMonthMode = true },
                    label = { Text("Month", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        // Period Navigator (Previous / Label / Next)
        item(key = "navigator") {
            val periodLabel = if (!isMonthMode) {
                val weekEnd = currentWeekStart.plusDays(6)
                val sameMonth = currentWeekStart.month == weekEnd.month
                if (sameMonth) {
                    "${currentWeekStart.dayOfMonth} – ${weekEnd.dayOfMonth} ${weekEnd.month.name.take(3).lowercase(Locale.ROOT).replaceFirstChar { c -> c.uppercase() }} ${weekEnd.year}"
                } else {
                    "${currentWeekStart.dayOfMonth} ${currentWeekStart.month.name.take(3)} – ${weekEnd.dayOfMonth} ${weekEnd.month.name.take(3)} ${weekEnd.year}"
                }
            } else {
                "${currentMonthStart.month.name.lowercase(Locale.ROOT).replaceFirstChar { c -> c.uppercase() }} ${currentMonthStart.year}"
            }

            val isCurrentPeriod = if (!isMonthMode) {
                currentWeekStart == BudgetMath.currentWeekStart(snapshot.today)
            } else {
                currentMonthStart.year == snapshot.today.year && currentMonthStart.month == snapshot.today.month
            }

            SoftCard(
                contentPadding = Spacing.md,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (!isMonthMode) {
                                currentWeekStart = currentWeekStart.minusWeeks(1)
                            } else {
                                currentMonthStart = currentMonthStart.minusMonths(1)
                            }
                        },
                    ) {
                        Icon(AppIcons.ArrowBack, contentDescription = "Previous period")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = periodLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (isCurrentPeriod) {
                            Text(
                                text = "Current period",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (!isMonthMode) {
                                currentWeekStart = currentWeekStart.plusWeeks(1)
                            } else {
                                currentMonthStart = currentMonthStart.plusMonths(1)
                            }
                        },
                    ) {
                        Icon(AppIcons.ArrowForward, contentDescription = "Next period")
                    }
                }
            }
        }

        // Totals Card with comparison
        item(key = "totals_card") {
            val totalSpent = if (!isMonthMode) weekComparison.currentSpent else monthComparison.currentSpent
            val comparisonText = if (!isMonthMode) weekComparison.comparisonLabel else monthComparison.comparisonLabel

            SoftCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                borderColor = MaterialTheme.extendedColors.cardBorder,
                contentPadding = Spacing.lg,
            ) {
                Text(
                    text = if (!isMonthMode) "This week's food spending" else "This month's food spending",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = BudgetMath.money(totalSpent),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                ) {
                    Text(
                        text = comparisonText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Daily Bar Chart
        item(key = "daily_chart") {
            if (!isMonthMode) {
                WeekBarChart(
                    weekStart = currentWeekStart,
                    snapshot = snapshot,
                )
            } else {
                MonthBarChart(
                    monthStart = currentMonthStart,
                    snapshot = snapshot,
                )
            }
        }

        // Transactions header
        item(key = "transactions_header") {
            SectionTitle(
                title = "Transactions",
                trailing = {
                    Text(
                        text = "${activeTransactions.size} ${if (activeTransactions.size == 1) "entry" else "entries"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        // Transaction list
        if (activeTransactions.isEmpty()) {
            item(key = "empty_transactions") {
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = Spacing.lg,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "No food spending recorded for this period.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(activeTransactions, key = { it.id }) { expense ->
                TransactionRow(
                    expense = expense,
                    snapshot = snapshot,
                    onClick = {
                        val stockItem = if (expense.id.startsWith("stock-")) {
                            val stockId = expense.id.removePrefix("stock-")
                            snapshot.stock.firstOrNull { it.id == stockId || it.linkedExpenseId == expense.id }
                        } else null

                        if (stockItem != null) {
                            onEditPurchase(stockItem)
                        } else {
                            onEditExpense(expense)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekBarChart(
    weekStart: LocalDate,
    snapshot: AppSnapshot,
) {
    val days = remember(weekStart) {
        (0..6).map { weekStart.plusDays(it.toLong()) }
    }
    val dailySpent = remember(days, snapshot.expenses) {
        days.map { day -> day to BudgetMath.foodSpentOn(day, snapshot) }
    }
    val maxSpent = remember(dailySpent) {
        (dailySpent.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(10.0)
    }

    SoftCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.extendedColors.cardBorder,
        contentPadding = Spacing.md,
    ) {
        Text(
            text = "Daily food expenses",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            dailySpent.forEach { (day, spent) ->
                val isToday = day == snapshot.today
                val heightFraction = (spent / maxSpent).toFloat().coerceIn(0.04f, 1f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    if (spent > 0.0) {
                        Text(
                            text = spent.toInt().toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    } else {
                        Spacer(Modifier.height(14.dp))
                    }

                    Spacer(Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .fillMaxHeight(0.70f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFraction)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                ),
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = day.dayOfWeek.name.take(3).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthBarChart(
    monthStart: LocalDate,
    snapshot: AppSnapshot,
) {
    val monthLength = monthStart.lengthOfMonth()
    val days = remember(monthStart) {
        (1..monthLength).map { monthStart.withDayOfMonth(it) }
    }
    val dailySpent = remember(days, snapshot.expenses) {
        days.map { day -> day to BudgetMath.foodSpentOn(day, snapshot) }
    }
    val maxSpent = remember(dailySpent) {
        (dailySpent.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(10.0)
    }

    SoftCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.extendedColors.cardBorder,
        contentPadding = Spacing.md,
    ) {
        Text(
            text = "Daily food expenses across month",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .height(130.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            dailySpent.forEach { (day, spent) ->
                val isToday = day == snapshot.today
                val heightFraction = (spent / maxSpent).toFloat().coerceIn(0.04f, 1f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(22.dp),
                ) {
                    if (spent > 0.0) {
                        Text(
                            text = spent.toInt().toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    } else {
                        Spacer(Modifier.height(14.dp))
                    }

                    Spacer(Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .fillMaxHeight(0.70f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFraction)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                ),
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    expense: Expense,
    snapshot: AppSnapshot,
    onClick: () -> Unit,
) {
    val classificationLabel = when (expense.foodClassification) {
        "groceries" -> "Groceries"
        "ready_to_eat", "ready_made" -> "Ready-to-eat"
        "street_food" -> "Street food"
        "snack" -> "Snack"
        "meal" -> "Meal"
        "restaurant" -> "Restaurant"
        else -> null
    }

    SoftCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Spacing.md,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = expense.description.ifBlank { "Food" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (classificationLabel != null) {
                        StatusPill(
                            text = classificationLabel,
                            color = when (expense.foodClassification) {
                                "groceries" -> MaterialTheme.colorScheme.secondary
                                "snack" -> AccentAmber
                                "meal" -> AccentPurple
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = BudgetMath.formatDate(expense.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = BudgetMath.money(expense.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    AppIcons.Edit,
                    contentDescription = "Edit transaction",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
