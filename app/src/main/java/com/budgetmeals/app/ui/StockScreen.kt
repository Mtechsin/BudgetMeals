package com.budgetmeals.app.ui

import com.budgetmeals.app.ui.icons.AppIcons

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.BatchType
import com.budgetmeals.app.data.DatabaseMigrations
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.ui.theme.extendedColors
import java.time.LocalDate

@Composable
fun StockScreen(
    snapshot: AppSnapshot,
    onAddItem: () -> Unit,
    onEditItem: (StockItem) -> Unit,
    onLogUsage: (StockItem) -> Unit,
    onLogUsageWithDate: ((StockItem, Double, LocalDate) -> Unit)? = null,
    onClearSampleStock: (() -> Unit)? = null,
) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var itemForDateUsage by remember { mutableStateOf<StockItem?>(null) }

    val activeStock = remember(snapshot.stock) { snapshot.stock.filter { !it.isFinished } }
    val lowStockCount = remember(snapshot.lowStock) { snapshot.lowStock.size }
    val standingCost = remember(snapshot.stock) { BudgetMath.standingDailyCost(snapshot) }

    val visible = remember(filter, snapshot.stock) {
        when (filter) {
            "Low" -> snapshot.stock.filter { it.isLow && !it.isFinished }
            "Weekly" -> snapshot.stock.filter { it.batchType == BatchType.WEEKLY && !it.isFinished }
            "Monthly" -> snapshot.stock.filter { it.batchType == BatchType.MONTHLY && !it.isFinished }
            else -> snapshot.stock.filter { !it.isFinished }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Header
        item(key = "header") {
            ScreenHeader(
                title = "Stock",
                subtitle = "${activeStock.size} pantry items",
            )
        }

        // 2. Clean, Compact Overview Card
        item(key = "overview_card") {
            SoftCard(contentPadding = 16.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        icon = AppIcons.Savings,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 46.dp,
                        iconSize = 24.dp,
                    )

                    Spacer(Modifier.width(14.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = BudgetMath.money(standingCost, 1),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = " / day",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 1.dp, start = 2.dp),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Standing pantry cost",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (lowStockCount > 0) {
                        Surface(
                            onClick = { filter = "Low" },
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    AppIcons.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "$lowStockCount low",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Sample Starter Pantry Bar (Compact, only when sample items present)
        val hasSampleStock = snapshot.stock.any { it.id in DatabaseMigrations.STARTER_STOCK_IDS }
        if (hasSampleStock && onClearSampleStock != null) {
            item(key = "sample_pantry_notice") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Sample starter pantry loaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onClearSampleStock,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "Clear",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        // 4. Clean Filter Chips
        item(key = "filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val filters = listOf("All", "Low", "Weekly", "Monthly")
                filters.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option) },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
        }

        // 5. Stock Items or Empty State
        if (visible.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = if (filter == "Low") "All stocked up" else "Your stock list is empty",
                    message = if (filter == "Low") "None of your pantry items are running low right now." else "Add bulk staples like eggs, rice or coffee to track their daily cost.",
                    icon = if (filter == "Low") AppIcons.Check else AppIcons.Inventory2,
                    actionLabel = if (filter == "Low") "View all items" else "Add a batch",
                    onAction = if (filter == "Low") { { filter = "All" } } else onAddItem,
                )
            }
        } else {
            items(visible, key = { it.id }, contentType = { "stock_progress_row" }) { item ->
                StockProgressRow(
                    item = item,
                    catalogLabel = snapshot.foodCatalogById[item.catalogId]?.conversionLabel,
                    onClick = { onEditItem(item) },
                    onLogUsage = {
                        if (onLogUsageWithDate != null) {
                            itemForDateUsage = item
                        } else {
                            onLogUsage(item)
                        }
                    },
                )
            }
        }

    }

    // 7. Date Usage Dialog (when logging use with specific date)
    val dateUsageTarget = itemForDateUsage
    if (dateUsageTarget != null && onLogUsageWithDate != null) {
        var usageAmount by remember(dateUsageTarget.id) { mutableStateOf("") }
        var usageDate by remember(dateUsageTarget.id) { mutableStateOf(LocalDate.now()) }
        AlertDialog(
            onDismissRequest = { itemForDateUsage = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = "Log use Â· ${dateUsageTarget.name}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${dateUsageTarget.remainingQuantity.cleanNumber()} ${dateUsageTarget.unit} left Â· ${dateUsageTarget.daysLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Date", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = usageDate == LocalDate.now(),
                            onClick = { usageDate = LocalDate.now() },
                            label = { Text("Today") },
                            shape = RoundedCornerShape(8.dp),
                        )
                        FilterChip(
                            selected = usageDate == LocalDate.now().minusDays(1),
                            onClick = { usageDate = LocalDate.now().minusDays(1) },
                            label = { Text("Yesterday") },
                            shape = RoundedCornerShape(8.dp),
                        )
                        FilterChip(
                            selected = usageDate == LocalDate.now().minusDays(2),
                            onClick = { usageDate = LocalDate.now().minusDays(2) },
                            label = { Text("2 days ago") },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                    OutlinedTextField(
                        value = usageAmount,
                        onValueChange = { usageAmount = it },
                        label = { Text("Amount used (${dateUsageTarget.unit})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
            confirmButton = {
                val parsed = usageAmount.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = {
                        if (parsed > 0.0) {
                            onLogUsageWithDate(dateUsageTarget, parsed, usageDate)
                            itemForDateUsage = null
                        }
                    },
                    enabled = (usageAmount.toDoubleOrNull() ?: 0.0) > 0.0,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemForDateUsage = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
