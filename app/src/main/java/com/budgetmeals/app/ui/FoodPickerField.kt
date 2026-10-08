package com.budgetmeals.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.state.BudgetMath

@Composable
internal fun FoodPickerField(
    component: MealComponent,
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    catalogItems: List<FoodCatalogItem>,
    query: String,
    onQueryChanged: (String) -> Unit,
    onSelected: (FoodCatalogItem) -> Unit,
    onCreate: (String) -> Unit,
) {
    var expanded by remember(component.id) { mutableStateOf(false) }
    val normalizedQuery = query.trim().lowercase()
    val matches = catalogItems
        .filter { normalizedQuery.isBlank() || it.name.lowercase().contains(normalizedQuery) }
        .sortedBy { it.name.lowercase() }
    val exactMatch = catalogItems.any { it.name.equals(query.trim(), ignoreCase = true) }
    val selectedCatalog = catalogItems.firstOrNull { it.id == component.catalogId }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BudgetTextField(
            value = query.ifBlank { component.name },
            onValueChange = {
                onQueryChanged(it)
                expanded = true
            },
            label = "Food / Ingredient",
            modifier = Modifier.fillMaxWidth(),
        )

        if (catalogItems.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                catalogItems.take(12).forEach { food ->
                    val isSelected = food.id == component.catalogId
                    TagChip(
                        text = food.name,
                        selected = isSelected,
                        onClick = {
                            onQueryChanged(food.name)
                            onSelected(food)
                            expanded = false
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    if (!expanded) onQueryChanged("")
                    expanded = !expanded
                },
            ) {
                Text(if (expanded) "Close catalog list" else "Browse all (${catalogItems.size}) items")
            }

            if (selectedCatalog != null) {
                Text(
                    "${selectedCatalog.portionUnit} Â· ${BudgetMath.money(BudgetMath.catalogCostPerPortion(snapshot, selectedCatalog), 2)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentMintLight,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DarkSurface),
        ) {
            matches.forEach { food ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(food.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text(
                                "${food.category.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} Â· ${food.conversionLabel} Â· ${BudgetMath.money(BudgetMath.catalogCostPerPortion(snapshot, food), 2)} / ${food.portionUnit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    },
                    onClick = {
                        onQueryChanged(food.name)
                        onSelected(food)
                        expanded = false
                    },
                )
            }
            if (query.isNotBlank() && !exactMatch) {
                DropdownMenuItem(
                    text = { Text("Add \"$query\" to food items", color = AccentMintLight) },
                    onClick = {
                        onCreate(query)
                        expanded = false
                    },
                )
            }
        }
    }
}
