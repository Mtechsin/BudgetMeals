package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.MealMarker
import com.budgetmeals.app.data.MealStatus
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.activeMealStatus
import com.budgetmeals.app.state.habitObservationForHome
import com.budgetmeals.app.state.weekFoodSpendingComparison
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.Spacing
import com.budgetmeals.app.ui.theme.extendedColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * One-hand home dashboard:
 *  - Spending summaries near top:
 *      Today: EGP 85 spent
 *      This week: EGP 420 · View spending
 *  - Current meal and status card:
 *      Lunch · Choose or change
 *      Breakfast skipped · Change
 *  - One optional habit observation below main content.
 *  - Anchored frequent actions within thumb reach:
 *      [ Help me choose {mealType} ]
 *      [       Log food            ]
 */
@Composable
fun HomeScreen(
    snapshot: AppSnapshot,
    onLogFood: () -> Unit = {},
    onHelpMeChoose: (MealType?) -> Unit = {},
    onOpenMealAction: (MealType, LocalDate) -> Unit = { _, _ -> },
    onOpenSpending: () -> Unit = {},
    onOpenStock: () -> Unit = {},
    onOpenShopping: () -> Unit = {},
    onOpenPlan: () -> Unit = {},
    onOpenSpares: () -> Unit = {},
    onOpenDayReview: () -> Unit = {},
    onOpenBudgetCorrection: () -> Unit = {},
    onEditItem: (StockItem) -> Unit = {},
    onMarkMeal: (MealTemplate) -> Unit = {},
    onRememberStock: ((StockItem) -> Unit)? = null,
    onAssignMeal: ((MealType, MealTemplate?) -> Unit)? = null,
) {
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            currentTime = LocalDateTime.now()
        }
    }

    val activeStatus = remember(snapshot.settings, currentTime) {
        activeMealStatus(snapshot.settings, currentTime)
    }

    val mealDate = activeStatus.mealDate
    val today = snapshot.today
    val weekStart = remember(today) { BudgetMath.currentWeekStart(today) }
    val weekComparison = remember(snapshot.expenses, weekStart, today) {
        weekFoodSpendingComparison(snapshot, weekStart)
    }
    val tip = remember(snapshot.expenses, snapshot.stock, snapshot.settings, today) {
        habitObservationForHome(snapshot)
    }

    // Today's meal logs & plans
    val dateLogs = remember(snapshot.mealLogs, mealDate) {
        snapshot.mealLogs
            .filter { it.date == mealDate }
            .groupBy { it.mealType }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.actualTime } }
    }
    val datePlans = remember(snapshot.dayPlans, mealDate) {
        snapshot.dayPlansByDate[mealDate]?.associateBy { it.mealType }.orEmpty()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = Spacing.md,
                bottom = 148.dp, // clearance for thumb actions
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // 1. Spending summaries near top
            item(key = "spending_summary") {
                SoftCard(
                    containerColor = cardSurfaceColor(),
                    contentPadding = Spacing.lg,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        // Today: EGP 85 spent
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = BudgetMath.money(snapshot.todayFoodSpent),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "spent",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.extendedColors.cardBorder,
                        )

                        // This week: EGP 420 · View spending
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenSpending)
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "This week: ${BudgetMath.money(weekComparison.currentSpent)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (weekComparison.label.isNotBlank()) {
                                    Text(
                                        text = weekComparison.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = "View spending",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // 2. Current meal and status
            item(key = "current_meal_and_status") {
                SoftCard(
                    containerColor = cardSurfaceColor(),
                    contentPadding = Spacing.lg,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Current meal and status",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (mealDate != today) {
                                Text(
                                    text = "Overnight (${BudgetMath.formatWeekday(mealDate)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }

                        // Active meal or outside hours indicator
                        val activeType = activeStatus.activeMealType
                        if (activeType != null) {
                            // Active meal row
                            val activeLog = dateLogs[activeType]
                            val activePlan = datePlans[activeType]
                            MealStatusRow(
                                mealType = activeType,
                                isActive = true,
                                log = activeLog,
                                plan = activePlan,
                                onAction = { onOpenMealAction(activeType, mealDate) },
                            )
                        } else {
                            // Outside hours: show next meal start time without suggestions
                            val nextType = activeStatus.nextMealType
                            val nextStart = activeStatus.nextMealStartTime
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        AppIcons.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    val startStr = if (nextStart != null) "%02d:%02d".format(Locale.US, nextStart.hour, nextStart.minute) else "--:--"
                                    Text(
                                        text = "Next meal: ${nextType?.label ?: "Meal"} at $startStr",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        // Other meals of the day that have status (e.g. Breakfast skipped · Change)
                        val otherMealTypes = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
                            .filter { it != activeType }

                        val mealsWithStatus = otherMealTypes.filter { type ->
                            dateLogs.containsKey(type) || datePlans.containsKey(type)
                        }

                        if (mealsWithStatus.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.extendedColors.cardBorder,
                            )
                            mealsWithStatus.forEach { type ->
                                MealStatusRow(
                                    mealType = type,
                                    isActive = false,
                                    log = dateLogs[type],
                                    plan = datePlans[type],
                                    onAction = { onOpenMealAction(type, mealDate) },
                                )
                            }
                        }
                    }
                }
            }

            // 3. One optional habit observation below the main content
            if (tip != null) {
                item(key = "habit_tip") {
                    SoftCard(
                        onClick = {
                            when (tip.destination) {
                                "spending" -> onOpenSpending()
                                "stock" -> onOpenStock()
                                else -> onOpenSpending()
                            }
                        },
                        containerColor = cardSurfaceColor(),
                        contentPadding = Spacing.md,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            IconBadge(
                                icon = AppIcons.Lightbulb,
                                tint = MaterialTheme.colorScheme.secondary,
                                size = 36.dp,
                                iconSize = 18.dp,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = tip.title,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = tip.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Icon(
                                AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp).align(Alignment.CenterVertically),
                            )
                        }
                    }
                }
            }
        }

        // Anchored thumb zone above bottom navigation
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val activeType = activeStatus.activeMealType
                if (activeType != null) {
                    PrimaryButton(
                        text = "Help me choose ${activeType.label.lowercase()}",
                        onClick = { onHelpMeChoose(activeType) },
                        icon = AppIcons.AutoFixHigh,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )
                    OutlinedButton(
                        onClick = onLogFood,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                    ) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Log food", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    PrimaryButton(
                        text = "Log food",
                        onClick = onLogFood,
                        icon = AppIcons.Add,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MealStatusRow(
    mealType: MealType,
    isActive: Boolean,
    log: com.budgetmeals.app.data.MealLog?,
    plan: com.budgetmeals.app.data.DailyMealPlan?,
    onAction: () -> Unit,
) {
    val titleText = when {
        log?.status == MealStatus.SKIPPED -> "${mealType.label} skipped"
        log?.status == MealStatus.EATEN -> "${mealType.label}: ${log.name.ifBlank { "eaten" }}"
        plan != null && plan.name.isNotBlank() -> "${mealType.label}: ${plan.name}"
        else -> mealType.label
    }

    val actionText = when {
        log != null -> "Change"
        plan != null -> "Change"
        isActive -> "Choose or change"
        else -> "Change"
    }

    Surface(
        onClick = onAction,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.extendedColors.cardBorder,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isActive) {
                    Text(
                        text = "Active meal window",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
