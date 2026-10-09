package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.ExpenseCategory
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun ExpenseFormSheet(
    snapshot: AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
    existing: Expense? = null,
) {
    var selectedCategoryId by rememberSaveable {
        mutableStateOf(existing?.categoryId ?: snapshot.categories.firstOrNull { !it.isFood }?.id ?: snapshot.categories.firstOrNull()?.id.orEmpty())
    }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.cleanNumber() ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }
    var recurring by rememberSaveable { mutableStateOf(existing?.isRecurring ?: false) }
    val category = snapshot.categories.firstOrNull { it.id == selectedCategoryId }
    val valid = selectedCategoryId.isNotBlank() && amount.asDouble() > 0.0
    SheetBody(
        title = if (existing == null) "Log an expense" else "Edit expense",
        subtitle = if (existing == null) "Two taps is enough. You can edit the details later." else "Change the amount or category without starting over.",
        onClose = onDismiss,
    ) {
        FormSectionTitle("Category")
        DropdownField(
            label = "Category",
            value = category ?: snapshot.categories.first(),
            values = snapshot.categories,
            labelOf = { it.name },
            onSelected = { selectedCategoryId = it.id },
        )
        Spacer(Modifier.height(8.dp))
        FormSectionTitle("Details")
        NumberField(amount, { amount = it }, "Amount", prefix = "EGP ")
        BudgetTextField(description, { description = it }, label = "What was it for? (optional)", modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Checkbox(checked = recurring, onCheckedChange = { recurring = it }, colors = sheetCheckboxColors())
            Column {
                Text("This repeats every month", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text("For internet or phone credit, set it once.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        if (category?.isFood == true) {
            Text(
                "Food expenses are counted toward the ${BudgetMath.money(category.monthlyBudget)} ceiling.",
                style = MaterialTheme.typography.bodySmall,
                color = AccentMintLight,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(if (existing == null) "Save expense" else "Save changes", {
            val expense = Expense(
                id = existing?.id ?: UUID.randomUUID().toString(),
                categoryId = selectedCategoryId,
                amount = amount.asDouble(),
                date = existing?.date ?: LocalDate.now(),
                description = description.trim(),
                isRecurring = recurring,
                recurringFrequency = if (recurring) "MONTHLY" else null,
                isCorrection = existing?.isCorrection ?: false,
            )
            viewModel.recordExpense(expense)
            onDismiss()
        }, enabled = valid, icon = AppIcons.Check)
        if (existing != null) {
            OutlinedButton(
                onClick = { viewModel.deleteExpense(existing); onDismiss() },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ErrorBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 50.dp),
            ) {
                Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete expense", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun CategoryFormSheet(viewModel: BudgetViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var isFood by remember { mutableStateOf(false) }
    val icons = listOf("receipt", "transport", "health", "personal", "other")
    var icon by remember { mutableStateOf(icons.first()) }
    val valid = name.isNotBlank() && budget.asDouble() >= 0.0
    SheetBody("Add a category", null, onDismiss) {
        FormSectionTitle("Category details")
        BudgetTextField(name, { name = it }, label = "Category name")
        NumberField(budget, { budget = it }, "Monthly budget", Modifier.padding(top = 12.dp), prefix = "EGP ")
        FormSectionTitle("Icon")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            icons.forEach { option ->
                TagChip(
                    text = option.replaceFirstChar { it.uppercase() },
                    selected = (icon == option),
                    onClick = { icon = option },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
            Checkbox(checked = isFood, onCheckedChange = { isFood = it }, colors = sheetCheckboxColors())
            Text("Count this category toward the food ceiling", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Save category", {
            viewModel.saveCategory(
                ExpenseCategory(
                    id = "custom_${UUID.randomUUID()}",
                    name = name.trim(),
                    icon = icon,
                    monthlyBudget = budget.asDouble(),
                    isFood = isFood,
                    isCustom = true,
                    sortOrder = 20,
                ),
            )
            onDismiss()
        }, enabled = valid, icon = AppIcons.Check)
    }
}
