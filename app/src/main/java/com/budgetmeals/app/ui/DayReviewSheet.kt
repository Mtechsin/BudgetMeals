package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.MealReconciliation
import com.budgetmeals.app.data.MealStatus
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import kotlin.math.roundToInt

private enum class MealReviewChoice {
    FULL,
    LEFTOVERS,
    SKIPPED,
    UNRECORDED,
}

@Composable
internal fun DayReviewSheet(
    snapshot: AppSnapshot,
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
                                " Â· ${availability.portions.cleanNumber()} available (${availability.baseQuantity.cleanNumber()} ${availability.baseUnit})"
                            availability.note != null -> " Â· ${availability.note}"
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
                        MealReviewChoice.LEFTOVERS -> "Consumed ${BudgetMath.money(consumed)} Â· left over ${BudgetMath.money((template.cost - consumed).coerceAtLeast(0.0))}"
                        MealReviewChoice.SKIPPED -> "Skipped Â· ${BudgetMath.money(template.cost)} not consumed"
                        MealReviewChoice.UNRECORDED -> "Unrecorded Â· no meal record"
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
                    "Food from stock reduces inventory. The rest is added to this day's spending. Leftovers do not refund money spent.",
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

private fun Int?.orZeroPercent(): Int = this ?: 0
