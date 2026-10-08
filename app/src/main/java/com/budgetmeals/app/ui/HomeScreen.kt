package com.budgetmeals.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import com.budgetmeals.app.ui.theme.Motion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.MealDataCodec
import com.budgetmeals.app.data.MealLog
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.DayMealSummary
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.Spacing
import com.budgetmeals.app.ui.theme.extendedColors
import java.time.LocalTime
import java.util.Locale

/**
 * Home answers three questions, in order:
 *  1. How much can I still spend today?      -> budget hero
 *  2. What am I eating, and did I eat it?    -> meal rows (one tap to check off)
 *  3. Is anything needing my attention?      -> wrap-up, low stock, insight
 * Adding things (purchases, expenses, meals) lives in the floating button, not here.
 */
@Composable
fun HomeScreen(
    snapshot: AppSnapshot,
    onOpenShopping: () -> Unit,
    onOpenStock: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenSpares: () -> Unit,
    onOpenDayReview: () -> Unit,
    onOpenBudgetCorrection: () -> Unit,
    onEditItem: (StockItem) -> Unit,
    onMarkMeal: (MealTemplate) -> Unit,
    onRememberStock: ((StockItem) -> Unit)? = null,
    onAssignMeal: ((MealType, MealTemplate?) -> Unit)? = null,
) {
    val today = snapshot.today

    // If today has planned meals use the non-cleared ones; otherwise fall back to generated recurring meals.
    val todayPlan = remember(snapshot.dayPlans, snapshot.templates, snapshot.settings, snapshot.sparesBalance, today) {
        val plannedList = snapshot.mealPlan(today)
        if (plannedList.isNotEmpty()) {
            plannedList.filter { !it.isCleared }.associate { plan ->
                plan.mealType to MealTemplate(
                    id = plan.templateId ?: "plan-${plan.date}-${plan.mealType.name}",
                    name = plan.name,
                    mealType = plan.mealType,
                    cost = plan.cost,
                    isRecurring = false,
                    notes = plan.foods,
                    isCustom = true,
                    components = plan.components.ifEmpty { MealDataCodec.legacyComponents(plan.foods) },
                )
            }
        } else {
            val planned = BudgetMath.plannedMealsForDate(snapshot, today).toMutableMap()
            val generated = BudgetMath.generatedMealsForDate(snapshot, today)
            MealType.entries.forEach { type ->
                if (!planned.containsKey(type) && generated.containsKey(type)) {
                    planned[type] = generated.getValue(type)
                }
            }
            planned
        }
    }

    val todayLogs = snapshot.todayLogs

    val daySummary = remember(snapshot.mealLogs, snapshot.dayPlans, snapshot.dayClosures, snapshot.templates, snapshot.settings, today) {
        BudgetMath.dayMealSummary(snapshot, today)
    }
    val suggestion = remember(
        snapshot.templates,
        snapshot.todayFoodRemaining,
        snapshot.sparesBalance,
        snapshot.stock,
        snapshot.foodCatalog,
    ) {
        BudgetMath.suggestedMeals(snapshot).firstOrNull()
    }
    val monthlyProgress = remember(snapshot.foodSpentThisMonth, snapshot.settings.monthlyFoodBudget) {
        BudgetMath.percent(snapshot.foodSpentThisMonth, snapshot.settings.monthlyFoodBudget)
    }
    val completedMealsCount = remember(todayLogs) {
        MealType.entries.count { type -> todayLogs[type]?.isConsumed == true }
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
        item(key = "greeting", contentType = "greeting") {
            Column(Modifier.padding(top = Spacing.sm)) {
                Text(
                    text = BudgetMath.formatDate(today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = greetingText(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        item(key = "hero", contentType = "hero") {
            BudgetHero(
                snapshot = snapshot,
                onOpenSpares = onOpenSpares,
                onOpenBudgetCorrection = onOpenBudgetCorrection,
            )
        }

        item(key = "meals_header", contentType = "section_header") {
            SectionTitle(
                title = "Today's meals",
                trailing = {
                    if (todayPlan.isNotEmpty()) {
                        val allDone = completedMealsCount >= todayPlan.size
                        StatusPill(
                            text = if (allDone) "All done" else "$completedMealsCount of ${todayPlan.size}",
                            color = if (allDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    TextButton(onClick = onOpenPlan) { Text("Week plan") }
                },
            )
        }

        items(MealType.entries, key = { it.name }, contentType = { "meal_row" }) { mealType ->
            val planned = todayPlan[mealType]
            val log = todayLogs[mealType]
            MealRow(
                mealType = mealType,
                snapshot = snapshot,
                template = planned,
                log = log,
                onEaten = { planned?.let(onMarkMeal) },
                onEdit = {
                    if (log == null) {
                        onAssignMeal?.invoke(mealType, planned) ?: onOpenPlan()
                    } else {
                        onOpenDayReview()
                    }
                },
                onQuickShop = { item -> onRememberStock?.invoke(item) ?: onOpenShopping() },
            )
        }

        if (todayPlan.isNotEmpty()) {
            item(key = "day_wrap_up", contentType = "day_wrap_up") {
                DayWrapUpCard(summary = daySummary, onReview = onOpenDayReview)
            }
        }

        if (snapshot.lowStock.isNotEmpty()) {
            item(key = "stock_alerts", contentType = "stock_alerts") {
                RunningLowSection(
                    lowStock = snapshot.lowStock,
                    onOpenStock = onOpenStock,
                    onEditItem = onEditItem,
                    onRememberStock = onRememberStock ?: { onOpenStock() },
                )
            }
        }

        item(key = "insight", contentType = "insight") {
            InsightCard(
                suggestion = suggestion,
                projectionMessage = projectionMessage(snapshot, monthlyProgress),
                onOpenPlan = onOpenPlan,
            )
        }
    }
}

// ============================================================================
// Budget hero: the one number that matters, big
// ============================================================================

@Composable
private fun BudgetHero(
    snapshot: AppSnapshot,
    onOpenSpares: () -> Unit,
    onOpenBudgetCorrection: () -> Unit,
) {
    val today = snapshot.today
    val allowance = snapshot.settings.dailyFoodBudget
    val spent = snapshot.todayFoodSpent
    val remaining = snapshot.todayFoodRemaining
    val isOver = spent > allowance
    val progress = BudgetMath.percent(spent, allowance).toFloat()

    val container = if (isOver) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (isOver) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val accent = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    SoftCard(
        containerColor = container,
        borderColor = Color.Transparent,
        contentPadding = Spacing.xl - Spacing.xs,
    ) {
        Text(
            text = if (isOver) "Over today's budget" else "Left to spend today",
            style = MaterialTheme.typography.labelLarge,
            color = onContainer.copy(alpha = 0.8f),
        )
        Spacer(Modifier.size(Spacing.xs))
        Text(
            text = if (isOver) BudgetMath.money(spent - allowance) else BudgetMath.money(remaining),
            style = MaterialTheme.typography.displaySmall,
            color = onContainer,
            maxLines = 1,
        )
        Spacer(Modifier.size(Spacing.md))
        ProgressBar(
            progress = progress,
            color = accent,
            trackColor = onContainer.copy(alpha = 0.16f),
            height = 10.dp,
        )
        Spacer(Modifier.size(Spacing.sm))
        Text(
            text = "${BudgetMath.money(spent)} of ${BudgetMath.money(allowance)} spent today",
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
                    "This month",
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.75f),
                )
                Text(
                    "${BudgetMath.money(snapshot.foodSpentThisMonth)} / ${BudgetMath.money(snapshot.settings.monthlyFoodBudget)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${BudgetMath.daysLeftInMonth(today)} days left",
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.75f),
                )
            }
            Surface(
                onClick = onOpenSpares,
                shape = RoundedCornerShape(14.dp),
                color = onContainer.copy(alpha = 0.10f),
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(AppIcons.Savings, contentDescription = null, tint = onContainer, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Spares", style = MaterialTheme.typography.labelSmall, color = onContainer.copy(alpha = 0.75f))
                        Text(
                            BudgetMath.money(snapshot.sparesBalance),
                            style = MaterialTheme.typography.titleSmall,
                            color = onContainer,
                        )
                    }
                }
            }
        }
        TextButton(
            onClick = onOpenBudgetCorrection,
            modifier = Modifier.padding(top = Spacing.xs),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = onContainer),
        ) {
            Text("Numbers look off? Match real spending", style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ============================================================================
// Meal row: scannable when collapsed, detailed when expanded
// ============================================================================

private fun formatComponentQuantity(quantity: Double, unit: String): String {
    val qtyStr = BudgetMath.quantity(quantity)
    val cleanUnit = when (unit.lowercase().trim()) {
        "piece" -> if (quantity == 1.0) "pc" else "pcs"
        "spoon" -> if (quantity == 1.0) "spoon" else "spoons"
        "cup" -> if (quantity == 1.0) "cup" else "cups"
        "pack" -> if (quantity == 1.0) "pack" else "packs"
        "serving" -> if (quantity == 1.0) "serving" else "servings"
        else -> unit.trim()
    }
    return "$qtyStr $cleanUnit"
}

@Composable
private fun mealAccent(type: MealType): Color = when (type) {
    MealType.BREAKFAST -> AccentAmber
    MealType.LUNCH -> AccentMint
    MealType.DINNER -> AccentIndigo
    MealType.SNACK -> AccentPurple
}

@Composable
private fun MealRow(
    mealType: MealType,
    snapshot: AppSnapshot,
    template: MealTemplate?,
    log: MealLog?,
    onEaten: () -> Unit,
    onEdit: () -> Unit,
    onQuickShop: (StockItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = mealAccent(mealType)
    var expanded by rememberSaveable(mealType.name) { mutableStateOf(false) }

    val fullyEaten = log?.isConsumed == true && log.leftoverCost <= 0.001
    val hasLeftovers = log?.isConsumed == true && log.leftoverCost > 0.001
    val skipped = log?.isSkipped == true
    val isPlanned = template != null
    val mealName = template?.name ?: log?.name
    val cost = log?.cost ?: template?.cost ?: 0.0

    val components = remember(template, log) {
        val direct = log?.components?.ifEmpty { template?.components.orEmpty() } ?: template?.components.orEmpty()
        if (direct.isNotEmpty()) {
            direct
        } else {
            val raw = log?.foodSummary?.ifBlank { template?.foodSummary.orEmpty() }
                ?: template?.foodSummary.orEmpty().ifBlank { template?.notes.orEmpty() }
            if (raw.isNotBlank()) MealDataCodec.legacyComponents(raw) else emptyList()
        }
    }
    val foodsText = log?.foodSummary?.ifBlank { template?.foodSummary.orEmpty() } ?: template?.foodSummary.orEmpty()

    val shortages = remember(components, snapshot.foodCatalogById, snapshot.foodCatalogByName, snapshot.catalogAvailablePortions, snapshot.stockByCatalogId) {
        components.mapNotNull { component ->
            val catalog = if (component.catalogId != null) {
                snapshot.foodCatalogById[component.catalogId]
            } else {
                snapshot.foodCatalogByName[component.name.lowercase(Locale.US)]
            }
            if (component.useStock && catalog != null) {
                val available = snapshot.catalogAvailablePortions[catalog.id] ?: 0.0
                if (available < component.quantity) {
                    Triple(component.name, available, snapshot.stockByCatalogId[catalog.id]?.firstOrNull())
                } else null
            } else null
        }
    }
    val shortagesByName = remember(shortages) { shortages.associateBy { it.first.lowercase(Locale.US) } }
    val estimateIncomplete = template != null && BudgetMath.isMealCostIncomplete(snapshot, template)

    val base = cardSurfaceColor()
    val primary = MaterialTheme.colorScheme.primary
    val containerColor = when {
        fullyEaten -> primary.copy(alpha = 0.07f).compositeOver(base)
        hasLeftovers -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.07f).compositeOver(base)
        else -> base
    }
    val borderColor = when {
        fullyEaten -> primary.copy(alpha = 0.30f)
        hasLeftovers -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f)
        else -> MaterialTheme.extendedColors.cardBorder
    }

    val summaryLine = remember(components, foodsText) {
        if (components.isNotEmpty()) {
            val names = components.take(3).joinToString(", ") { it.name }
            if (components.size > 3) "$names +${components.size - 3}" else names
        } else foodsText
    }

    SoftCard(
        modifier = modifier,
        onClick = { expanded = !expanded },
        containerColor = containerColor,
        borderColor = borderColor,
        contentPadding = Spacing.md,
    ) {
        Column(Modifier.animateContentSize(Motion.CardExpandTween)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = mealIconVector(mealType),
                    tint = accent,
                    size = 48.dp,
                    shape = CircleShape,
                )
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = mealType.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                    Text(
                        text = mealName ?: "Nothing planned",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (mealName != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (summaryLine.isNotBlank()) {
                        Text(
                            text = summaryLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val showStatus = fullyEaten || hasLeftovers || skipped || shortages.isNotEmpty() || estimateIncomplete
                    if (showStatus) {
                        Row(
                            modifier = Modifier.padding(top = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            when {
                                fullyEaten -> StatusPill("Eaten", primary)
                                hasLeftovers -> StatusPill("${BudgetMath.money(log?.leftoverCost ?: 0.0)} saved", MaterialTheme.colorScheme.secondary)
                                skipped -> StatusPill("Skipped", MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (shortages.isNotEmpty() && !fullyEaten && !hasLeftovers) {
                                StatusPill("Low stock", MaterialTheme.colorScheme.error)
                            }
                            if (estimateIncomplete) {
                                StatusPill("Cost incomplete", MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    if (cost > 0.0 && !estimateIncomplete) {
                        Text(
                            text = BudgetMath.money(if (fullyEaten || hasLeftovers) log?.consumedCost ?: cost else cost),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.size(Spacing.xs))
                    }
                    MealAction(
                        eaten = fullyEaten || hasLeftovers,
                        skipped = skipped,
                        planned = isPlanned,
                        mealLabel = mealType.label,
                        onEaten = onEaten,
                        onChoose = onEdit,
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.md),
                    color = MaterialTheme.extendedColors.cardBorder,
                )
                if (components.isEmpty() && foodsText.isBlank()) {
                    Text(
                        text = "No foods added yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    components.forEach { comp ->
                        val shortage = shortagesByName[comp.name.lowercase(Locale.US)]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .padding(0.dp)
                                    .semantics { contentDescription = if (shortage != null) "Low stock" else "In stock" }
                            ) {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = CircleShape,
                                    color = if (shortage != null) MaterialTheme.colorScheme.error else accent,
                                ) {}
                            }
                            Spacer(Modifier.width(Spacing.md))
                            Text(
                                text = comp.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatComponentQuantity(comp.quantity, comp.unit),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (shortage != null) {
                                val stockItem = shortage.third
                                if (stockItem != null) {
                                    TextButton(
                                        onClick = { onQuickShop(stockItem) },
                                        contentPadding = PaddingValues(horizontal = Spacing.sm),
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) { Text("Add to list") }
                                }
                            } else if (comp.estimatedCost > 0.0) {
                                Text(
                                    text = BudgetMath.money(comp.estimatedCost),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = Spacing.md),
                                )
                            }
                        }
                    }
                    if (components.isEmpty() && foodsText.isNotBlank()) {
                        Text(
                            text = foodsText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.padding(top = Spacing.xs),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Icon(AppIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        when {
                            log != null -> "Edit result"
                            isPlanned -> "Change meal"
                            else -> "Choose a meal"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun mealIconVector(type: MealType) = when (type) {
    MealType.BREAKFAST -> AppIcons.BreakfastDining
    MealType.LUNCH -> AppIcons.Restaurant
    MealType.DINNER -> AppIcons.DinnerDining
    MealType.SNACK -> AppIcons.Cookie
}

@Composable
private fun MealAction(
    eaten: Boolean,
    skipped: Boolean,
    planned: Boolean,
    mealLabel: String,
    onEaten: () -> Unit,
    onChoose: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    AnimatedContent(
        targetState = Triple(eaten, skipped, planned),
        transitionSpec = {
            (fadeIn(tween(Motion.FastMs, easing = Motion.EmphasizedDecelerate)) +
                scaleIn(Motion.MicroSpring, initialScale = 0.8f))
                .togetherWith(
                    fadeOut(tween(Motion.FastMs, easing = Motion.EmphasizedAccelerate)) +
                        scaleOut(tween(Motion.FastMs), targetScale = 0.8f)
                )
        },
        label = "mealActionState",
    ) { (isEaten, isSkipped, isPlanned) ->
        when {
            isEaten -> Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.Check,
                        contentDescription = "$mealLabel eaten",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            isSkipped -> Unit
            isPlanned -> Surface(
                onClick = onEaten,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(2.dp, primary),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.Check,
                        contentDescription = "Mark $mealLabel eaten",
                        tint = primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            else -> FilledTonalButton(
                onClick = onChoose,
                modifier = Modifier.heightIn(min = 40.dp),
                contentPadding = PaddingValues(horizontal = Spacing.md),
            ) { Text("Choose") }
        }
    }
}

// ============================================================================
// Day wrap-up
// ============================================================================

@Composable
private fun DayWrapUpCard(
    summary: DayMealSummary,
    onReview: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    SoftCard(borderColor = if (summary.isClosed) primary.copy(alpha = 0.3f) else MaterialTheme.extendedColors.cardBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = if (summary.isClosed) AppIcons.Check else AppIcons.FactCheck,
                tint = primary,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    if (summary.isClosed) "Today is settled" else "Wrap up today",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        summary.isClosed -> "Stock updated and numbers locked in."
                        summary.pendingMeals > 0 -> "${summary.pendingMeals} open ${if (summary.pendingMeals == 1) "meal" else "meals"} will count as skipped."
                        else -> "Every meal is recorded. Ready to close."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (summary.consumedCost > 0 || summary.leftoverCost > 0 || summary.skippedCost > 0) {
            Spacer(Modifier.size(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                DayFigure("Eaten", summary.consumedCost, primary, Modifier.weight(1f))
                DayFigure("Saved", summary.leftoverCost, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                DayFigure("Skipped", summary.skippedCost, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.size(Spacing.md))
        if (summary.isClosed) {
            FilledTonalButton(
                onClick = onReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text("See details") }
        } else {
            androidx.compose.material3.Button(
                onClick = onReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text("Review and close day") }
        }
    }
}

@Composable
private fun DayFigure(label: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                BudgetMath.money(value),
                style = MaterialTheme.typography.titleSmall,
                color = color,
                maxLines = 1,
            )
        }
    }
}

// ============================================================================
// Running low: only shown when something needs attention
// ============================================================================

@Composable
private fun RunningLowSection(
    lowStock: List<StockItem>,
    onOpenStock: () -> Unit,
    onEditItem: (StockItem) -> Unit,
    onRememberStock: (StockItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionTitle(
            title = "Running low",
            trailing = {
                StatusPill("${lowStock.size}", MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(Spacing.xs))
                TextButton(onClick = onOpenStock) { Text("Stock") }
            },
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            lowStock.take(6).forEach { item ->
                LowStockCard(
                    item = item,
                    onClick = { onEditItem(item) },
                    onRemember = { onRememberStock(item) },
                )
            }
        }
    }
}

@Composable
private fun LowStockCard(
    item: StockItem,
    onClick: () -> Unit,
    onRemember: () -> Unit,
) {
    val error = MaterialTheme.colorScheme.error
    SoftCard(
        modifier = Modifier.width(168.dp),
        onClick = onClick,
        borderColor = error.copy(alpha = 0.30f),
        contentPadding = Spacing.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CategoryIcon(item.category.icon, modifier = Modifier.size(22.dp), tint = error)
            StatusPill(item.daysLabel, error)
        }
        Spacer(Modifier.size(Spacing.sm))
        Text(
            item.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(Spacing.sm))
        ProgressBar(item.remainingPercent.toFloat(), color = error, height = 6.dp)
        Spacer(Modifier.size(Spacing.md))
        FilledTonalButton(
            onClick = onRemember,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            contentPadding = PaddingValues(horizontal = Spacing.sm),
        ) {
            Icon(AppIcons.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("Add to list")
        }
    }
}

// ============================================================================
// Insight
// ============================================================================

@Composable
private fun InsightCard(
    suggestion: MealTemplate?,
    projectionMessage: String,
    onOpenPlan: () -> Unit,
) {
    SoftCard(
        onClick = onOpenPlan,
        containerColor = MaterialTheme.extendedColors.tipContainer,
        borderColor = Color.Transparent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.Lightbulb, MaterialTheme.colorScheme.primary, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    projectionMessage,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.extendedColors.onTipContainer,
                )
                Text(
                    suggestion?.let { "Idea: ${it.name} (${BudgetMath.money(it.cost)}) fits today's budget." }
                        ?: "Save a few favourite meals to get daily ideas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.onTipContainer.copy(alpha = 0.85f),
                )
            }
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.extendedColors.onTipContainer)
        }
    }
}

private fun greetingText(): String {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

private fun projectionMessage(snapshot: AppSnapshot, monthlyProgress: Double): String {
    val projected = BudgetMath.projectedMonthEnd(snapshot)
    return when {
        projected < 0 -> "At this pace you may run over budget before month-end."
        monthlyProgress > 0.8 -> "You've used over 80% of this month's food budget."
        else -> "You're on track this month."
    }
}
