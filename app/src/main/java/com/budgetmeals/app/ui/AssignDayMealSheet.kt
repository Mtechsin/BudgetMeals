package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import java.time.LocalDate

@Composable
internal fun AssignDayMealSheet(
    date: LocalDate,
    mealType: MealType,
    currentTemplate: MealTemplate?,
    snapshot: AppSnapshot,
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
