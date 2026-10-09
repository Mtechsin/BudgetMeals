package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons

@Composable
internal fun SparesFormSheet(snapshot: AppSnapshot, viewModel: BudgetViewModel, onDismiss: () -> Unit) {
    var spend by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    val value = amount.asDouble()
    val valid = value > 0.0 && (!spend || value <= snapshot.sparesBalance)
    SheetBody(if (spend) "Spend from spares" else "Add to spares", null, onDismiss) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            OutlinedButton(
                onClick = { spend = false },
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (!spend) AccentMint.copy(alpha = 0.15f) else DarkSurfaceLow,
                    contentColor = if (!spend) AccentMintLight else TextSecondary,
                ),
                border = BorderStroke(1.dp, if (!spend) AccentMint.copy(alpha = 0.4f) else DarkBorder),
            ) {
                Text("Add money", fontWeight = if (!spend) FontWeight.Bold else FontWeight.Normal)
            }
            OutlinedButton(
                onClick = { spend = true },
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (spend) AccentAmber.copy(alpha = 0.15f) else DarkSurfaceLow,
                    contentColor = if (spend) AccentAmber else TextSecondary,
                ),
                border = BorderStroke(1.dp, if (spend) AccentAmber.copy(alpha = 0.4f) else DarkBorder),
            ) {
                Text("Spend treat", fontWeight = if (spend) FontWeight.Bold else FontWeight.Normal)
            }
        }
        FormSectionTitle("Amount")
        NumberField(amount, { amount = it }, "Amount", prefix = "EGP ")
        BudgetTextField(reason, { reason = it }, label = "Reason", modifier = Modifier.padding(top = 12.dp))
        if (spend) {
            DropdownField("Treat type", category, listOf("snack", "meal_treat", "dessert", "other"), { it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }, { category = it }, Modifier.padding(top = 12.dp))
        }
        if (spend && value > snapshot.sparesBalance) {
            Text(
                "Your balance is ${BudgetMath.money(snapshot.sparesBalance)}. The app will not let spares go negative.",
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else if (spend) {
            Text(
                "You earned this. Spend it without guilt.",
                style = MaterialTheme.typography.bodySmall,
                color = AccentMintLight,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(if (spend) "Spend treat" else "Add to spares", {
            val description = reason.trim().ifBlank { if (spend) "Treat" else "Added money" }
            if (spend) {
                viewModel.spendFromSpares(value, description, category)
            } else {
                viewModel.addSpares(value, description, category)
            }
            onDismiss()
        }, enabled = valid, icon = if (spend) AppIcons.ShoppingCart else AppIcons.Savings)
    }
}
