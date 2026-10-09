package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.data.ShoppingItem
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun ShoppingFormSheet(existing: ShoppingItem?, viewModel: BudgetViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(existing?.quantity?.cleanNumber() ?: "1") }
    var unit by rememberSaveable { mutableStateOf(existing?.unit ?: "piece") }
    var price by rememberSaveable { mutableStateOf(existing?.let { if (it.priceKnown) it.estimatedPrice.cleanNumber() else "" } ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: ItemCategory.FOOD_STAPLE) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    val valid = name.isNotBlank() && quantity.asDouble() > 0.0
    SheetBody(if (existing == null) "Add to shopping" else "Edit shopping item", "The list is shared with your stock habits.", onDismiss) {
        FormSectionTitle("Item")
        BudgetTextField(name, { name = it }, label = "Item")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            NumberField(quantity, { quantity = it }, "Quantity", Modifier.weight(1f))
            BudgetTextField(unit, { unit = it }, label = "Unit", modifier = Modifier.weight(1f))
        }
        NumberField(price, { price = it }, "Expected price", Modifier.padding(top = 12.dp), prefix = "EGP ")
        FormSectionTitle("Category")
        DropdownField("Category", category, ItemCategory.entries, { it.label }, { category = it })
        BudgetTextField(note, { note = it }, label = "Note (optional)", modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Save item", {
            val p = price.asDouble()
            val isKnown = price.isNotBlank() && p >= 0.0
            viewModel.saveShopping(
                ShoppingItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    quantity = quantity.asDouble(),
                    unit = unit.trim().ifBlank { "piece" },
                    estimatedPrice = if (isKnown) p else 0.0,
                    category = category,
                    isChecked = existing?.isChecked ?: false,
                    createdDate = existing?.createdDate ?: LocalDate.now(),
                    sourceItemId = existing?.sourceItemId,
                    note = note.trim(),
                    purchaseExpenseId = existing?.purchaseExpenseId,
                    purchaseStockId = existing?.purchaseStockId,
                    priceKnown = isKnown,
                ),
            )
            onDismiss()
        }, enabled = valid, icon = AppIcons.Check)
        if (existing != null) {
            OutlinedButton(
                onClick = { viewModel.deleteShopping(existing); onDismiss() },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ErrorBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 50.dp),
            ) {
                Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remove from list", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun BuyShoppingSheet(item: ShoppingItem, viewModel: BudgetViewModel, onDismiss: () -> Unit) {
    var price by remember(item.id) { mutableStateOf(if (item.priceKnown && item.estimatedPrice > 0.0) item.estimatedPrice.cleanNumber() else "") }
    var remember by remember(item.id) { mutableStateOf(false) }
    val valid = price.isNotBlank() && price.asDouble() >= 0.0
    SheetBody("Buy ${item.name}", "One tap saves it to stock, spending, and the next list if you want.", onDismiss) {
        FormSectionTitle("Purchase price")
        NumberField(price, { price = it }, "Actual price", prefix = "EGP ")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Checkbox(checked = remember, onCheckedChange = { remember = it }, colors = sheetCheckboxColors())
            Text("Keep it on the list for next time", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        Spacer(Modifier.height(14.dp))
        AnimatedSaveHint("This will be counted as food spending today and added to stock automatically.")
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Confirm purchase", { viewModel.buyShoppingItem(item, price.asDouble(), remember); onDismiss() }, enabled = valid, icon = AppIcons.Check)
    }
}
