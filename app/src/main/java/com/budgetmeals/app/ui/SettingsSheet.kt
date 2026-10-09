package com.budgetmeals.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppThemeMode
import com.budgetmeals.app.data.BudgetSettings
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import java.time.DayOfWeek

@Composable
internal fun SettingsSheet(settings: BudgetSettings, viewModel: BudgetViewModel, onDismiss: () -> Unit) {
    var monthly by remember { mutableStateOf(settings.monthlyFoodBudget.cleanNumber()) }
    var daily by remember { mutableStateOf(settings.dailyFoodBudget.cleanNumber()) }
    var weekly by remember { mutableStateOf(settings.weeklyFoodLimit.cleanNumber()) }
    var kosharyPrice by remember { mutableStateOf(settings.kosharyPrice.cleanNumber()) }
    var kosharyDay by remember { mutableStateOf(settings.kosharyDay) }
    var shoppingDay by remember { mutableStateOf(settings.shoppingDay) }
    var autoSpares by remember { mutableStateOf(settings.autoSaveSpares) }
    var reminders by remember { mutableStateOf(settings.remindersEnabled) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    val valid = monthly.asDouble() > 0 && daily.asDouble() > 0 && weekly.asDouble() > 0
    SheetBody("Settings", "Change the numbers when prices change.", onDismiss) {
        FormSectionTitle("Appearance")
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppThemeMode.entries.forEach { mode ->
                val isSelected = themeMode == mode
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = isSelected,
                    onClick = { themeMode = mode },
                    label = { Text(mode.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        val icon = when (mode) {
                            AppThemeMode.SYSTEM -> AppIcons.BrightnessAuto
                            AppThemeMode.LIGHT -> AppIcons.LightMode
                            AppThemeMode.DARK -> AppIcons.DarkMode
                        }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentMint.copy(alpha = 0.15f),
                        selectedLabelColor = AccentMintLight,
                        selectedLeadingIconColor = AccentMintLight,
                        containerColor = DarkSurfaceLow,
                        labelColor = TextSecondary,
                        iconColor = TextSecondary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = DarkBorder,
                        selectedBorderColor = AccentMint.copy(alpha = 0.4f),
                    ),
                )
            }
        }
        FormSectionTitle("Food limits")
        NumberField(monthly, { monthly = it }, "Monthly food ceiling", prefix = "EGP ")
        NumberField(daily, { daily = it }, "Daily average", prefix = "EGP ", modifier = Modifier.padding(top = 12.dp))
        NumberField(weekly, { weekly = it }, "Weekly target", prefix = "EGP ", modifier = Modifier.padding(top = 12.dp))
        FormSectionTitle("Koshary day")
        NumberField(kosharyPrice, { kosharyPrice = it }, "Koshary price", prefix = "EGP ")
        DropdownField("Day", kosharyDay, DayOfWeek.entries, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, { kosharyDay = it }, Modifier.padding(top = 12.dp))
        FormSectionTitle("Shopping reminders")
        DropdownField("Shopping day", shoppingDay, DayOfWeek.entries, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, { shoppingDay = it })
        SettingSwitch("Daily budget reminder", reminders, { reminders = it })
        SettingSwitch("Show a spare-saving prompt", autoSpares, { autoSpares = it })
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Save settings", {
            viewModel.saveSettings(
                settings.copy(
                    monthlyFoodBudget = monthly.asDouble(),
                    dailyFoodBudget = daily.asDouble(),
                    weeklyFoodLimit = weekly.asDouble(),
                    kosharyPrice = kosharyPrice.asDouble(),
                    kosharyDay = kosharyDay,
                    shoppingDay = shoppingDay,
                    remindersEnabled = reminders,
                    autoSaveSpares = autoSpares,
                    themeMode = themeMode,
                ),
            )
            onDismiss()
        }, enabled = valid, icon = AppIcons.Check)
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text("You can turn this off at any time.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = AccentMint,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DarkSurfaceHigh,
                uncheckedBorderColor = DarkBorder,
            ),
        )
    }
}
