package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.AppThemeMode
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.Spacing
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
    onThemeModeChange: (AppThemeMode) -> Unit,
    onResetPantry: () -> Unit = {},
    onOpenFoodSpending: () -> Unit = {},
    // Backup & Export
    backupFolderName: String? = null,
    lastBackupTimeText: String? = null,
    isBackupPermissionLost: Boolean = false,
    onChooseBackupFolder: () -> Unit = {},
    onBackupNow: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    onSaveExcel: () -> Unit = {},
    onShareExcel: () -> Unit = {},
    onSaveCsvZip: () -> Unit = {},
    onShareCsvZip: () -> Unit = {},
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(title = "More")
        }

        // 1. Appearance (Theme on top)
        item { SectionHeader("Appearance") }
        item {
            ThemeSelectorCard(
                currentMode = snapshot.settings.themeMode,
                onModeSelected = onThemeModeChange,
            )
        }

        // 2. Budget & Meal tools
        item { SectionHeader("Budget & meal tools") }
        item {
            ActionTile("Settings", "Budgets, meal windows, reminders", AppIcons.Settings, MaterialTheme.colorScheme.primary, onOpenSettings, Modifier.fillMaxWidth())
        }
        item {
            ActionTile("Food spending", "Weekly and monthly breakdown with charts", AppIcons.BarChart, MaterialTheme.colorScheme.primary, onOpenFoodSpending, Modifier.fillMaxWidth())
        }
        item {
            ActionTile("Match real spending", "Fix forgotten or duplicate food costs", AppIcons.FactCheck, MaterialTheme.colorScheme.primary, onOpenBudgetCorrection, Modifier.fillMaxWidth())
        }
        item {
            ActionTile("Expenses", "Internet, calling, household, and custom", AppIcons.ReceiptLong, MaterialTheme.colorScheme.tertiary, onOpenExpenses, Modifier.fillMaxWidth())
        }
        item {
            ActionTile("Spares", "Rewards and treat history (${BudgetMath.money(snapshot.sparesBalance)})", AppIcons.Savings, MaterialTheme.extendedColors.sparesGold, onOpenSpares, Modifier.fillMaxWidth())
        }
        item {
            ActionTile("Food items", "${snapshot.foodCatalog.size} ingredients for building meals", AppIcons.Restaurant, MaterialTheme.colorScheme.secondary, onOpenFoodCatalog, Modifier.fillMaxWidth())
        }

        // 3. Backup & export (Moved to bottom)
        item { SectionHeader("Backup & export") }

        item {
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = cardSurfaceColor(),
                contentPadding = Spacing.lg,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        IconBadge(
                            icon = AppIcons.Inventory2,
                            tint = if (isBackupPermissionLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            size = 42.dp,
                            iconSize = 22.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Automatic backup",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val statusDesc = when {
                                isBackupPermissionLost -> "Folder access lost. Choose folder again to restore automatic backups."
                                backupFolderName != null -> "Folder: $backupFolderName · Daily & debounced changes"
                                else -> "Choose a folder to enable daily automatic backups."
                            }
                            Text(
                                text = statusDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isBackupPermissionLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (lastBackupTimeText != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Last successful backup: $lastBackupTimeText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }

                    // Backup Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (backupFolderName == null || isBackupPermissionLost) {
                            PrimaryButton(
                                text = if (isBackupPermissionLost) "Choose backup folder again" else "Choose backup folder",
                                onClick = onChooseBackupFolder,
                                icon = AppIcons.Inventory2,
                                modifier = Modifier.weight(1f).height(48.dp),
                            )
                        } else {
                            OutlinedButton(
                                onClick = onChooseBackupFolder,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                            ) {
                                Text("Change folder", style = MaterialTheme.typography.labelMedium)
                            }
                            PrimaryButton(
                                text = "Back up now",
                                onClick = onBackupNow,
                                icon = AppIcons.Check,
                                modifier = Modifier.weight(1f).height(48.dp),
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onRestoreBackup,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                    ) {
                        Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restore from backup archive", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Export Workbooks (Excel & CSV ZIP)
        item {
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = cardSurfaceColor(),
                contentPadding = Spacing.lg,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // Excel .xlsx
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Excel workbook (.xlsx)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "13 sheets with full history, filters, and numeric cells",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onSaveExcel,
                                modifier = Modifier.height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                            ) {
                                Text("Save", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = onShareExcel,
                                modifier = Modifier.height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Icon(AppIcons.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.extendedColors.cardBorder,
                    )

                    // CSV Package (.zip)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "CSV archive (.zip)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Separate CSV files for all tables in a single ZIP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onSaveCsvZip,
                                modifier = Modifier.height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                            ) {
                                Text("Save", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = onShareCsvZip,
                                modifier = Modifier.height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                            ) {
                                Icon(AppIcons.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // 4. Pantry management (Destructive action at the very bottom)
        if (snapshot.stock.isNotEmpty()) {
            item { SectionHeader("Pantry management") }
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
            Text(
                "BudgetMeals is a private, local ledger. Nothing leaves your phone unless you choose to share an export or save a backup.",
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
