package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.Spacing
import com.budgetmeals.app.ui.theme.extendedColors
import java.time.LocalDate
import java.util.Locale

@Composable
fun MealActionSheet(
    mealType: MealType,
    date: LocalDate,
    snapshot: AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
    onOpenHelpMeChoose: (MealType) -> Unit,
) {
    val editorIdentity = "meal_action_${mealType.name}_$date"
    val draft = rememberSheetDraft(editorIdentity, onDismiss)

    val log = remember(snapshot.mealLogs, date, mealType) {
        snapshot.mealLogs
            .filter { it.date == date && it.mealType == mealType }
            .maxByOrNull { it.actualTime }
    }

    val plan = remember(snapshot.dayPlans, date, mealType) {
        snapshot.dayPlansByDate[date]?.firstOrNull { it.mealType == mealType }
    }

    val template = remember(plan, snapshot.templates) {
        val tmplId = plan?.templateId
        val tmplName = plan?.name
        when {
            tmplId != null -> snapshot.templates.firstOrNull { it.id == tmplId }
            tmplName != null -> snapshot.templates.firstOrNull { it.name.equals(tmplName, ignoreCase = true) }
            else -> null
        }
    }

    var cashPaid by rememberSaveable(key = "${draft.draftKey}_cash") { mutableStateOf("") }
    var isSubmitting by rememberSaveable(key = "${draft.draftKey}_submitting") { mutableStateOf(false) }

    val dateSubtitle = if (date == snapshot.today) "Today" else BudgetMath.formatDate(date)

    SheetBody(
        title = mealType.label,
        subtitle = dateSubtitle,
        onClose = draft.dismiss,
    ) {
        when {
            // Case 1: Skipped
            log?.status == MealStatus.SKIPPED -> {
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentPadding = Spacing.lg,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        IconBadge(
                            icon = AppIcons.Schedule,
                            tint = MaterialTheme.colorScheme.outline,
                            size = 40.dp,
                            iconSize = 22.dp,
                        )
                        Column {
                            Text(
                                text = "Skipped today",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "No pantry ingredients were consumed and no spending was recorded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xl))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    PrimaryButton(
                        text = if (isSubmitting) "Updating..." else "Undo skip",
                        onClick = {
                            if (isSubmitting) return@PrimaryButton
                            isSubmitting = true
                            viewModel.undoSkipMeal(date, mealType) {
                                isSubmitting = false
                                draft.dismiss()
                            }
                        },
                        enabled = !isSubmitting,
                        icon = AppIcons.Refresh,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )

                    OutlinedButton(
                        onClick = {
                            viewModel.undoSkipMeal(date, mealType) {
                                draft.dismiss()
                                onOpenHelpMeChoose(mealType)
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                    ) {
                        Text("Choose another meal", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Case 2: Eaten
            log?.status == MealStatus.EATEN || log?.status == MealStatus.PARTIAL -> {
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    contentPadding = Spacing.lg,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        IconBadge(
                            icon = AppIcons.Check,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 40.dp,
                            iconSize = 22.dp,
                        )
                        Column {
                            Text(
                                text = log.name.ifBlank { mealType.label },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            val timeStr = "%02d:%02d".format(Locale.US, log.actualTime.hour, log.actualTime.minute)
                            val cash = log.cashPaid ?: 0.0
                            val costNote = if (cash > 0.0) " · Cash paid: ${BudgetMath.money(cash)}" else ""
                            Text(
                                text = "Recorded as eaten at $timeStr$costNote",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xl))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (isSubmitting) return@OutlinedButton
                            isSubmitting = true
                            viewModel.deleteMealLog(log)
                            isSubmitting = false
                            draft.dismiss()
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("Undo / Change eaten record", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            draft.dismiss()
                            onOpenHelpMeChoose(mealType)
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                    ) {
                        Text("Choose another meal", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Case 3: Planned and uneaten
            template != null -> {
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentPadding = Spacing.lg,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = template.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${mealType.label} · Recipe cost: ${BudgetMath.money(template.cost)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (template.marker != MealMarker.UNMARKED) {
                            StatusPill(
                                text = template.marker.label,
                                color = if (template.marker == MealMarker.NICE) AccentPurple else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (template.components.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = "Ingredients: " + template.components.joinToString(", ") { "${it.quantity.cleanNumber()} ${it.unit} ${it.name}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                // Optional cash spent override
                NumberField(
                    value = cashPaid,
                    onValueChange = { cashPaid = it },
                    label = "Cash paid (optional override)",
                    prefix = "EGP ",
                    placeholder = "0 (leave empty if cooking from stock)",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(Spacing.xl))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    // Mark as eaten
                    PrimaryButton(
                        text = if (isSubmitting) "Marking eaten..." else "Mark as eaten",
                        onClick = {
                            if (isSubmitting) return@PrimaryButton
                            val overrideAmount = cashPaid.asDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            isSubmitting = true
                            viewModel.recordMeal(
                                template = template,
                                date = date,
                                cashPaidOverride = overrideAmount,
                            ) { success ->
                                isSubmitting = false
                                if (success) draft.dismiss()
                            }
                        },
                        enabled = !isSubmitting,
                        icon = AppIcons.Check,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )

                    // Skip today
                    OutlinedButton(
                        onClick = {
                            if (isSubmitting) return@OutlinedButton
                            isSubmitting = true
                            viewModel.skipMeal(date, mealType) {
                                isSubmitting = false
                                draft.dismiss()
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                    ) {
                        Text("Skip today", style = MaterialTheme.typography.labelLarge)
                    }

                    // Change meal
                    OutlinedButton(
                        onClick = {
                            draft.dismiss()
                            onOpenHelpMeChoose(mealType)
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                    ) {
                        Text("Choose another meal", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Case 4: Unplanned and uneaten
            else -> {
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentPadding = Spacing.lg,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = "No meal chosen yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Pick a saved meal or skip this meal today.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.xl))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    PrimaryButton(
                        text = "Help me choose",
                        onClick = {
                            draft.dismiss()
                            onOpenHelpMeChoose(mealType)
                        },
                        icon = AppIcons.AutoFixHigh,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )

                    OutlinedButton(
                        onClick = {
                            if (isSubmitting) return@OutlinedButton
                            isSubmitting = true
                            viewModel.skipMeal(date, mealType) {
                                isSubmitting = false
                                draft.dismiss()
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                    ) {
                        Text("Skip today", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
