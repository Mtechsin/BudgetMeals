package com.budgetmeals.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.state.RecommendationKind
import com.budgetmeals.app.state.activeMealStatus
import com.budgetmeals.app.state.helpMeChooseRecommendations
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.Motion
import com.budgetmeals.app.ui.theme.Spacing
import com.budgetmeals.app.ui.theme.extendedColors
import java.time.LocalDateTime
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HelpMeChooseSheet(
    snapshot: AppSnapshot,
    viewModel: BudgetViewModel,
    initialMealType: MealType? = null,
    onDismiss: () -> Unit,
) {
    val editorIdentity = "help_me_choose"
    val draft = rememberSheetDraft(editorIdentity, onDismiss)

    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            currentTime = LocalDateTime.now()
        }
    }
    val activeStatus = remember(snapshot.settings, currentTime) {
        activeMealStatus(snapshot.settings, currentTime)
    }

    var mealType by rememberSaveable(key = "${draft.draftKey}_type") {
        mutableStateOf(initialMealType)
    }
    // Track the meal window that is actually open instead of freezing the original choice.
    val trackActiveWindow = remember { initialMealType == null || initialMealType == activeStatus.activeMealType }
    LaunchedEffect(activeStatus.activeMealType) {
        if (trackActiveWindow) {
            mealType = activeStatus.activeMealType
        } else if (mealType == null) {
            mealType = initialMealType
        }
    }
    var showNiceMeals by rememberSaveable(key = "${draft.draftKey}_nice") { mutableStateOf(false) }
    var isSaving by rememberSaveable(key = "${draft.draftKey}_saving") { mutableStateOf(false) }

    val recommendations = remember(
        snapshot.templates,
        snapshot.stock,
        snapshot.foodCatalog,
        snapshot.mealLogs,
        snapshot.settings,
        mealType,
        showNiceMeals,
    ) {
        helpMeChooseRecommendations(snapshot, mealType = mealType, showNiceMeals = showNiceMeals)
    }

    var selectedIndex by rememberSaveable(key = "${draft.draftKey}_idx") { mutableIntStateOf(0) }
    var chosenMealName by rememberSaveable(key = "${draft.draftKey}_chosen") { mutableStateOf<String?>(null) }
    var hasAddedPlan by rememberSaveable(key = "${draft.draftKey}_hasAdded") { mutableStateOf(false) }

    LaunchedEffect(recommendations.size) {
        if (selectedIndex >= recommendations.size) {
            selectedIndex = 0
        }
    }

    SheetBody(
        title = if (mealType != null) "Choose ${mealType?.label?.lowercase(Locale.US)}" else "Help me choose",
        subtitle = "Ideas based on your kitchen and meal history",
        onClose = draft.dismiss,
    ) {
        // Quick toggle for "Show nice meals"
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (showNiceMeals) "Nice meals (special treats)" else "Everyday & essential meals",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilterChip(
                selected = showNiceMeals,
                onClick = {
                    showNiceMeals = !showNiceMeals
                    selectedIndex = 0
                },
                label = { Text("Show nice meals") },
                leadingIcon = if (showNiceMeals) ({
                    Icon(AppIcons.Check, null, Modifier.size(16.dp))
                }) else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
        if (recommendations.isEmpty()) {
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = Spacing.lg,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = if (mealType != null) "No ${mealType?.label?.lowercase(Locale.US)} ideas available" else "No meal ideas available",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (showNiceMeals) {
                            "No saved ${mealType?.label?.lowercase(Locale.US) ?: "meal"} recipes are marked as 'Nice'."
                        } else {
                            "Save ${mealType?.label?.lowercase(Locale.US) ?: "meal"} recipes in the Plan tab to get personalized recommendations based on pantry stock."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Close", draft.dismiss)
            return@SheetBody
        }

        val safeIndex = selectedIndex.coerceIn(0, recommendations.size - 1)
        val currentRec = recommendations[safeIndex]

        if (hasAddedPlan) {
            // Post-choose step: optional add missing ingredients to shopping
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SoftCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    contentPadding = Spacing.lg,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Icon(
                            AppIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Added to today's plan",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${chosenMealName ?: currentRec.template.name} is set for today.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (currentRec.missingIngredients.isNotEmpty()) {
                    Text(
                        text = "Missing ingredients:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        currentRec.missingIngredients.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${item.quantity.cleanNumber()} ${item.unit} ${item.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = if (item.priceKnown && item.estimatedCost > 0.0) {
                                            BudgetMath.money(item.estimatedCost)
                                        } else {
                                            "price unknown"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.priceKnown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    PrimaryButton(
                        text = "Add missing to shopping",
                        onClick = {
                            viewModel.addMissingIngredientsToShopping(currentRec.missingIngredients) {
                                draft.dismiss()
                            }
                        },
                        icon = AppIcons.ShoppingCart,
                    )
                }

                OutlinedButton(
                    onClick = draft.dismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                ) {
                    Text("Done")
                }
            }
            return@SheetBody
        }

        // Show single recommendation with smooth transition
        AnimatedContent(
            targetState = currentRec,
            transitionSpec = {
                Motion.fadeThrough().using(SizeTransform { _, _ -> Motion.CardExpandTween })
            },
            label = "recommendationTransition",
        ) { rec ->
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                borderColor = MaterialTheme.extendedColors.cardBorder,
                contentPadding = Spacing.lg,
            ) {
                // Header with kind pill and option counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val pillColor = when (rec.kind) {
                        RecommendationKind.PRACTICAL -> MaterialTheme.colorScheme.primary
                        RecommendationKind.DIFFERENT -> MaterialTheme.colorScheme.tertiary
                        RecommendationKind.NICER -> AccentPurple
                    }
                    StatusPill(
                        text = rec.kind.label,
                        color = pillColor,
                    )

                    Text(
                        text = "${safeIndex + 1} of ${recommendations.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Meal name
                Text(
                    text = rec.template.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "${rec.template.mealType.label} · Saved cost: ${BudgetMath.money(rec.template.cost)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.extendedColors.cardBorder,
                )

                // Available ingredients section
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        AppIcons.Kitchen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "In pantry:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (rec.availableIngredients.isNotEmpty()) {
                            Text(
                                text = rec.availableIngredients.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "None in stock yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Missing ingredients section
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        AppIcons.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Missing ingredients:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (rec.missingIngredients.isNotEmpty()) {
                            Text(
                                text = rec.missingIngredients.joinToString(", ") { item ->
                                    val priceNote = if (!item.priceKnown) " (price unknown)" else ""
                                    "${item.quantity.cleanNumber()} ${item.unit} ${item.name}$priceNote"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "All ingredients already available!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Estimated additional cost
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Additional ingredient cost:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (rec.hasUnknownPrice) {
                                "${BudgetMath.money(rec.estimatedAdditionalCost)} + unknown"
                            } else {
                                BudgetMath.money(rec.estimatedAdditionalCost)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (rec.estimatedAdditionalCost <= 0.01) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Actions: Choose & Another idea
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Another idea button
            OutlinedButton(
                onClick = {
                    selectedIndex = (selectedIndex + 1) % recommendations.size
                },
                enabled = recommendations.size > 1 && !isSaving,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Another idea", style = MaterialTheme.typography.labelLarge)
            }

            // Choose button
            PrimaryButton(
                text = if (isSaving) "Choosing..." else "Choose",
                onClick = {
                    if (isSaving) return@PrimaryButton
                    val meal = currentRec.template
                    chosenMealName = meal.name
                    isSaving = true
                    val targetDate = if (meal.mealType == activeStatus.activeMealType) activeStatus.mealDate else snapshot.today
                    viewModel.assignMealToDay(targetDate, meal.mealType, meal) { success ->
                        isSaving = false
                        if (success) {
                            if (currentRec.missingIngredients.isNotEmpty()) {
                                hasAddedPlan = true
                            } else {
                                draft.dismiss()
                            }
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
                icon = AppIcons.Check,
            )
        }
    }
}
