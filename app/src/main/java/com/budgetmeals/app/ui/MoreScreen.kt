package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.AppThemeMode
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.extendedColors

@Composable
fun MoreScreen(
    snapshot: AppSnapshot,
    onOpenShopping: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenSpares: () -> Unit,
    onOpenFoodCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBudgetCorrection: () -> Unit,
    onExport: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onResetPantry: () -> Unit = {},
    onLoadSamplePantry: () -> Unit = {},
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "More",
                subtitle = "Settings, appearance, and export",
            )
        }

        item {
            SoftCard(contentPadding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = AppIcons.BarChart,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 46.dp,
                        iconSize = 24.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "Your numbers, in plain language",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Food ${BudgetMath.money(snapshot.foodSpentThisMonth)} · spares ${BudgetMath.money(snapshot.sparesBalance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { SectionHeader("Appearance") }
        item {
            ThemeSelectorCard(
                currentMode = snapshot.settings.themeMode,
                onModeSelected = onThemeModeChange,
            )
        }

        item { SectionHeader("Budget tools") }
        item {
            ActionTile("Food items", "${snapshot.foodCatalog.size} ingredients for building meals", AppIcons.Restaurant, MaterialTheme.colorScheme.primary, onOpenFoodCatalog, Modifier.fillMaxWidth())
        }
        item {
            ActionTile("Match real spending", "Fix forgotten or duplicate food costs", AppIcons.FactCheck, MaterialTheme.colorScheme.primary, onOpenBudgetCorrection, Modifier.fillMaxWidth())
        }
        item {
            ActionTile("Expenses", "Internet, calling, household, and custom", AppIcons.ReceiptLong, MaterialTheme.colorScheme.tertiary, onOpenExpenses, Modifier.fillMaxWidth())
        }
        item {
            ActionTile("Spares", "Rewards and treat history", AppIcons.Savings, MaterialTheme.extendedColors.sparesGold, onOpenSpares, Modifier.fillMaxWidth())
        }
        item {
            ActionTile("Shopping list", "${snapshot.openShoppingCount} items waiting", AppIcons.ShoppingCart, MaterialTheme.colorScheme.primary, onOpenShopping, Modifier.fillMaxWidth())
        }

        item { SectionHeader("Pantry management") }
        if (snapshot.stock.isNotEmpty()) {
            item {
                ActionTile(
                    "Reset pantry to empty",
                    "Clear ${snapshot.stock.size} items to start with your actual groceries",
                    AppIcons.Delete,
                    MaterialTheme.colorScheme.error,
                    onResetPantry,
                    Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            ActionTile(
                "Load sample pantry",
                "Populate starter pantry with standard staples (oil, eggs, cheese...)",
                AppIcons.Restaurant,
                MaterialTheme.colorScheme.primary,
                onLoadSamplePantry,
                Modifier.fillMaxWidth(),
            )
        }

        item { SectionHeader("App") }
        item {
            ActionTile("Settings", "Budgets, koshary day, reminders", AppIcons.Settings, MaterialTheme.colorScheme.secondary, onOpenSettings, Modifier.fillMaxWidth())
        }
        item {
            ActionTile("Share summary", "Export a readable CSV through Android", AppIcons.Share, MaterialTheme.colorScheme.primary, onExport, Modifier.fillMaxWidth())
        }

        item {
            Text(
                "BudgetMeals is a private, local ledger. Nothing leaves your phone unless you choose to share an export.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ThemeSelectorCard(
    currentMode: AppThemeMode,
    onModeSelected: (AppThemeMode) -> Unit,
) {
    SoftCard(contentPadding = 16.dp) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (currentMode) {
                    AppThemeMode.SYSTEM -> AppIcons.BrightnessAuto
                    AppThemeMode.LIGHT -> AppIcons.LightMode
                    AppThemeMode.DARK -> AppIcons.DarkMode
                }
                IconBadge(
                    icon = icon,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 42.dp,
                    iconSize = 22.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        when (currentMode) {
                            AppThemeMode.SYSTEM -> "System default"
                            AppThemeMode.LIGHT -> "Light theme active"
                            AppThemeMode.DARK -> "Dark theme active"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppThemeMode.entries.forEach { mode ->
                    val selected = currentMode == mode
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selected,
                        onClick = { onModeSelected(mode) },
                        label = {
                            Text(
                                mode.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        leadingIcon = {
                            val icon = when (mode) {
                                AppThemeMode.SYSTEM -> AppIcons.BrightnessAuto
                                AppThemeMode.LIGHT -> AppIcons.LightMode
                                AppThemeMode.DARK -> AppIcons.DarkMode
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }
        }
    }
}
