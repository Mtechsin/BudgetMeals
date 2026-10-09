package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import java.time.LocalDate
import kotlin.math.abs

private enum class BudgetCorrectionMode {
    MATCH_MONTH,
    MISSED_SPENDING,
}

@Composable
internal fun BudgetCorrectionSheet(
    snapshot: AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(BudgetCorrectionMode.MATCH_MONTH) }
    var actual by remember { mutableStateOf("") }
    var missedAmount by remember { mutableStateOf("") }
    var daysAgo by remember { mutableIntStateOf(0) }
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
                "${BudgetMath.formatDate(expense.date)} Â· ${expense.description}",
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
