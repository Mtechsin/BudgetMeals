package com.budgetmeals.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.buildMealPlanWindow
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.Spacing
import com.budgetmeals.app.ui.theme.extendedColors
import java.time.LocalDate

@Composable
fun PlanScreen(
    snapshot: AppSnapshot,
    onAddMeal: () -> Unit,
    onAssignMeal: (LocalDate, MealType, MealTemplate?) -> Unit,
    onRemoveMeal: (LocalDate, MealType) -> Unit,
    onEditTemplate: (MealTemplate) -> Unit,
    onDeleteTemplate: (MealTemplate) -> Unit,
    onOpenSettings: () -> Unit,
    onRebuildPlan: (List<LocalDate>) -> Unit,
) {
    val today = snapshot.today
    var planStartEpoch by rememberSaveable(saver = mutableLongStateSaver) {
        mutableLongStateOf(today.toEpochDay())
    }
    var selectedDateEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastObservedToday by remember { mutableStateOf(today) }
    val planStart = LocalDate.ofEpochDay(planStartEpoch)

    LaunchedEffect(today) {
        if (lastObservedToday != today && planStart == lastObservedToday) {
            planStartEpoch = today.toEpochDay()
        }
        lastObservedToday = today
    }

    val plan = remember(
        snapshot.dayPlans,
        snapshot.templates,
        snapshot.settings,
        snapshot.sparesBalance,
        snapshot.stock,
        snapshot.foodCatalog,
        planStart,
        today,
    ) {
        buildMealPlanWindow(snapshot, planStart)
    }
    val weeklyTotal = remember(plan) { plan.sumOf { day -> day.meals.values.sumOf { it.cost } } }

    val selectedDate = selectedDateEpoch?.let { LocalDate.ofEpochDay(it) }
    val selectedDay = remember(plan, selectedDate) {
        selectedDate?.let { date -> plan.firstOrNull { it.date == date } }
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
        // 1. Header (consistent with HomeScreen typography and alignment)
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (planStart == today) "Next 7 days" else "From ${BudgetMath.formatDate(planStart)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Meal plan",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    IconButton(
                        onClick = { onRebuildPlan(plan.map { it.date }) },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            AppIcons.AutoFixHigh,
                            contentDescription = "Auto-refill plan",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            AppIcons.Settings,
                            contentDescription = "Plan settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 2. Weekly Budget Hero (matching HomeScreen.BudgetHero styling)
        item(key = "weekly_hero") {
            WeeklyBudgetHero(
                planStart = planStart,
                today = today,
                weeklyTotal = weeklyTotal,
                weeklyLimit = snapshot.settings.weeklyFoodLimit,
                dailyAllowance = snapshot.settings.dailyFoodBudget,
                onRebuild = { onRebuildPlan(plan.map { it.date }) },
            )
        }

        // 3. Week Navigator (Previous / This week / Next)
        item(key = "week_navigator") {
            WeekNavigator(
                isCurrentWeek = planStart == today,
                onPrevious = {
                    planStartEpoch = planStart.minusWeeks(1).toEpochDay()
                    selectedDateEpoch = null
                },
                onCurrent = {
                    planStartEpoch = today.toEpochDay()
                    selectedDateEpoch = null
                },
                onNext = {
                    planStartEpoch = planStart.plusWeeks(1).toEpochDay()
                    selectedDateEpoch = null
                },
            )
        }

        // 4. 7-Day Carousel Section
        item(key = "days_header") {
            SectionTitle(
                title = "Days",
                trailing = {
                    val kosharyDayName = snapshot.settings.kosharyDay.name.lowercase().replaceFirstChar { it.uppercase() }
                    TextButton(onClick = onOpenSettings) {
                        Text("Koshary on $kosharyDayName")
                    }
                },
            )
        }

        item(key = "days_carousel") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                plan.forEach { day ->
                    val isSelected = selectedDateEpoch == day.date.toEpochDay()
                    val isToday = day.date == today
                    val dayTotal = day.meals.values.sumOf { it.cost }

                    DayCard(
                        date = day.date,
                        mealCount = day.meals.size,
                        totalCost = dayTotal,
                        dailyLimit = snapshot.settings.dailyFoodBudget,
                        isToday = isToday,
                        isSelected = isSelected,
                        onClick = {
                            selectedDateEpoch = if (isSelected) null else day.date.toEpochDay()
                        },
                    )
                }
            }
        }

        // 5. Selected Day Breakdown or Guide prompt
        item(key = "selected_day_content") {
            if (selectedDay == null || selectedDate == null) {
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = Spacing.xl,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        IconBadge(
                            icon = AppIcons.CalendarMonth,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 48.dp,
                            iconSize = 24.dp,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "Select a day to view or edit meals",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Tap any day card above to assign meals, check ingredients, or change recipes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                DayDetailCard(
                    snapshot = snapshot,
                    date = selectedDate,
                    dayPlan = selectedDay,
                    isToday = selectedDate == today,
                    onClose = { selectedDateEpoch = null },
                    onAssignMeal = { mealType, template -> onAssignMeal(selectedDate, mealType, template) },
                    onRemoveMeal = { mealType -> onRemoveMeal(selectedDate, mealType) },
                )
            }
        }

        // 6. Meal Shortcuts / Templates Section
        item(key = "templates_header") {
            SectionTitle(
                title = "Meal shortcuts",
                trailing = {
                    TextButton(onClick = onAddMeal) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Add")
                    }
                },
            )
        }

        if (snapshot.templates.isEmpty()) {
            item(key = "no_templates") {
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = Spacing.lg,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = "No saved meal shortcuts yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Save meals you eat often to quickly assign them to any day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            items(snapshot.templates, key = { it.id }, contentType = { "template_row" }) { template ->
                MealTemplateRow(
                    snapshot = snapshot,
                    template = template,
                    onEdit = { onEditTemplate(template) },
                    onDelete = { onDeleteTemplate(template) },
                )
            }
        }
    }
}

// ============================================================================
// Weekly Budget Hero Card (matching HomeScreen.BudgetHero)
// ============================================================================

@Composable
private fun WeeklyBudgetHero(
    planStart: LocalDate,
    today: LocalDate,
    weeklyTotal: Double,
    weeklyLimit: Double,
    dailyAllowance: Double,
    onRebuild: () -> Unit,
) {
    val isOver = weeklyTotal > weeklyLimit && weeklyLimit > 0.0
    val progressRatio = if (weeklyLimit > 0.0) {
        (weeklyTotal / weeklyLimit).toFloat()
    } else 0f

    val container = if (isOver) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (isOver) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val accent = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    SoftCard(
        containerColor = container,
        borderColor = Color.Transparent,
        contentPadding = Spacing.xl - Spacing.xs,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (planStart == today) "Current week's plan" else "Planned 7 days",
                style = MaterialTheme.typography.labelLarge,
                color = onContainer.copy(alpha = 0.8f),
            )
            StatusPill(
                text = if (isOver) "Over budget" else "On track",
                color = accent,
                filled = true,
            )
        }

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = BudgetMath.money(weeklyTotal),
            style = MaterialTheme.typography.displaySmall,
            color = onContainer,
            maxLines = 1,
        )

        Spacer(Modifier.height(Spacing.md))

        ProgressBar(
            progress = progressRatio,
            color = accent,
            trackColor = onContainer.copy(alpha = 0.16f),
            height = 10.dp,
        )

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = "${BudgetMath.money(weeklyTotal)} of ${BudgetMath.money(weeklyLimit)} planned",
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer.copy(alpha = 0.85f),
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.md),
            color = onContainer.copy(alpha = 0.14f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isOver) "Over limit" else "Room left",
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.75f),
                )
                Text(
                    text = if (isOver) {
                        "${BudgetMath.money(weeklyTotal - weeklyLimit)} over"
                    } else {
                        BudgetMath.money(weeklyLimit - weeklyTotal)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Daily target ~${BudgetMath.money(dailyAllowance)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.75f),
                )
            }

            Surface(
                onClick = onRebuild,
                shape = RoundedCornerShape(14.dp),
                color = onContainer.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        AppIcons.AutoFixHigh,
                        contentDescription = null,
                        tint = onContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Auto-fill",
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ============================================================================
// Week Navigator
// ============================================================================

@Composable
private fun WeekNavigator(
    isCurrentWeek: Boolean,
    onPrevious: () -> Unit,
    onCurrent: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onPrevious,
            modifier = Modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            contentPadding = PaddingValues(horizontal = Spacing.md),
        ) {
            Icon(AppIcons.ArrowBack, contentDescription = "Previous week", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("Previous", style = MaterialTheme.typography.labelMedium)
        }

        FilledTonalButton(
            onClick = onCurrent,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isCurrentWeek) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    cardSurfaceColor()
                },
                contentColor = if (isCurrentWeek) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ),
        ) {
            Icon(
                AppIcons.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = "This week",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isCurrentWeek) FontWeight.Bold else FontWeight.Medium,
            )
        }

        OutlinedButton(
            onClick = onNext,
            modifier = Modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            contentPadding = PaddingValues(horizontal = Spacing.md),
        ) {
            Text("Next", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(Spacing.xs))
            Icon(AppIcons.ArrowForward, contentDescription = "Next week", modifier = Modifier.size(18.dp))
        }
    }
}

// ============================================================================
// Day Carousel Card
// ============================================================================

@Composable
private fun DayCard(
    date: LocalDate,
    mealCount: Int,
    totalCost: Double,
    dailyLimit: Double,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isOver = totalCost > dailyLimit && dailyLimit > 0.0
    val cardColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> cardSurfaceColor()
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.extendedColors.cardBorder
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    SoftCard(
        modifier = Modifier.width(116.dp),
        onClick = onClick,
        containerColor = cardColor,
        borderColor = borderColor,
        contentPadding = Spacing.md,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Box(
                modifier = Modifier.height(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isToday) {
                    StatusPill("TODAY", MaterialTheme.colorScheme.primary, filled = isSelected)
                } else if (isOver) {
                    StatusPill("OVER", MaterialTheme.colorScheme.error)
                }
            }

            Text(
                text = BudgetMath.formatWeekday(date).take(3).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "${date.dayOfMonth} ${date.month.name.take(3)}",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = BudgetMath.money(totalCost),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) contentColor else if (isOver) MaterialTheme.colorScheme.error else contentColor,
                maxLines = 1,
            )

            Text(
                text = "$mealCount meals",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) contentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

// ============================================================================
// Selected Day Detail Card
// ============================================================================

@Composable
private fun DayDetailCard(
    snapshot: AppSnapshot,
    date: LocalDate,
    dayPlan: com.budgetmeals.app.state.WeeklyPlanDay,
    isToday: Boolean,
    onClose: () -> Unit,
    onAssignMeal: (MealType, MealTemplate?) -> Unit,
    onRemoveMeal: (MealType) -> Unit,
) {
    val dayTotal = dayPlan.meals.values.sumOf { it.cost }
    val dailyLimit = snapshot.settings.dailyFoodBudget
    val isOver = dayTotal > dailyLimit && dailyLimit > 0.0

    SoftCard(
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        contentPadding = Spacing.lg,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = BudgetMath.formatWeekday(date),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (isToday) {
                            Spacer(Modifier.width(Spacing.sm))
                            StatusPill("TODAY", MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        text = "${BudgetMath.formatDate(date)} · ${BudgetMath.money(dayTotal)} planned",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        AppIcons.Close,
                        contentDescription = "Close details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Meal Slots
            MealType.entries.forEach { mealType ->
                val template = dayPlan.meals[mealType]
                val typeAccent = when (mealType) {
                    MealType.BREAKFAST -> AccentAmber
                    MealType.LUNCH -> AccentMint
                    MealType.DINNER -> AccentIndigo
                    MealType.SNACK -> AccentPurple
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder.copy(alpha = 0.6f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            icon = when (mealType) {
                                MealType.BREAKFAST -> AppIcons.BreakfastDining
                                MealType.LUNCH -> AppIcons.Restaurant
                                MealType.DINNER -> AppIcons.DinnerDining
                                MealType.SNACK -> AppIcons.Cookie
                            },
                            tint = typeAccent,
                            size = 44.dp,
                            iconSize = 22.dp,
                        )

                        Spacer(Modifier.width(Spacing.md))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = mealType.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = typeAccent,
                            )
                            if (template != null) {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val isCostIncomplete = BudgetMath.isMealCostIncomplete(snapshot, template)
                                Text(
                                    text = if (isCostIncomplete) "Estimate incomplete" else BudgetMath.money(template.cost),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCostIncomplete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (template.foodSummary.isNotBlank()) {
                                    Text(
                                        text = template.foodSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                Text(
                                    text = "No meal planned",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }

                        Spacer(Modifier.width(Spacing.sm))

                        if (template != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onAssignMeal(mealType, template) },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        AppIcons.Edit,
                                        contentDescription = "Change meal",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveMeal(mealType) },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        AppIcons.Delete,
                                        contentDescription = "Remove meal",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onAssignMeal(mealType, null) },
                                modifier = Modifier.heightIn(min = 40.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = Spacing.md),
                            ) {
                                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(Spacing.xs))
                                Text("Assign", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Meal Template Shortcut Row
// ============================================================================

@Composable
private fun MealTemplateRow(
    snapshot: AppSnapshot,
    template: MealTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val mealColor = when (template.mealType) {
        MealType.BREAKFAST -> AccentAmber
        MealType.LUNCH -> AccentMint
        MealType.DINNER -> AccentIndigo
        MealType.SNACK -> AccentPurple
    }

    SoftCard(contentPadding = Spacing.md) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = when (template.mealType) {
                    MealType.BREAKFAST -> AppIcons.BreakfastDining
                    MealType.LUNCH -> AppIcons.Restaurant
                    MealType.DINNER -> AppIcons.DinnerDining
                    MealType.SNACK -> AppIcons.Cookie
                },
                tint = mealColor,
                size = 44.dp,
                iconSize = 22.dp,
            )

            Spacer(Modifier.width(Spacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = template.mealType.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = mealColor,
                    )
                    Text(" · ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    val isCostIncomplete = template.cost <= 0.0 || (template.components.isNotEmpty() && template.components.any { it.costPerUnit <= 0.0 })
                    Text(
                        text = if (isCostIncomplete) "estimate incomplete" else BudgetMath.money(template.cost),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCostIncomplete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (template.foodSummary.isNotBlank()) {
                    Text(
                        text = template.foodSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    AppIcons.Edit,
                    contentDescription = "Edit meal shortcut",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = "Delete meal shortcut",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

