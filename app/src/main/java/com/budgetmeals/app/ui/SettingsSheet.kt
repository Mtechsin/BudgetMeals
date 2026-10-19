@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.budgetmeals.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.platform.LocalContext
import com.budgetmeals.app.data.AppThemeMode
import com.budgetmeals.app.data.BudgetSettings
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale

private val localTimeSaver = Saver<LocalTime, String>(
    save = { "%02d:%02d".format(Locale.US, it.hour, it.minute) },
    restore = { s -> runCatching { LocalTime.parse(s) }.getOrDefault(LocalTime.of(12, 0)) },
)

@Composable
internal fun SettingsSheet(settings: BudgetSettings, viewModel: BudgetViewModel, onDismiss: () -> Unit) {
    val editorIdentity = "settings"
    val draft = rememberSheetDraft(editorIdentity, onDismiss)
    val draftKey = draft.draftKey
    var monthly by rememberSaveable(key = "${draftKey}_monthly") { mutableStateOf(settings.monthlyFoodBudget.cleanNumber()) }
    var daily by rememberSaveable(key = "${draftKey}_daily") { mutableStateOf(settings.dailyFoodBudget.cleanNumber()) }
    var weekly by rememberSaveable(key = "${draftKey}_weekly") { mutableStateOf(settings.weeklyFoodLimit.cleanNumber()) }
    var kosharyPrice by rememberSaveable(key = "${draftKey}_koshPrice") { mutableStateOf(settings.kosharyPrice.cleanNumber()) }
    var kosharyDay by rememberSaveable(key = "${draftKey}_koshDay", stateSaver = enumSaver<DayOfWeek>()) { mutableStateOf(settings.kosharyDay) }
    var shoppingDay by rememberSaveable(key = "${draftKey}_shopDay", stateSaver = enumSaver<DayOfWeek>()) { mutableStateOf(settings.shoppingDay) }
    var autoSpares by rememberSaveable(key = "${draftKey}_autoSpares") { mutableStateOf(settings.autoSaveSpares) }
    var reminders by rememberSaveable(key = "${draftKey}_reminders") { mutableStateOf(settings.remindersEnabled) }
    var themeMode by rememberSaveable(key = "${draftKey}_themeMode", stateSaver = enumSaver<AppThemeMode>()) { mutableStateOf(settings.themeMode) }
    var foodPreferences by rememberSaveable(key = "${draftKey}_foodPrefs") { mutableStateOf(settings.foodPreferences) }
    var foodExclusions by rememberSaveable(key = "${draftKey}_foodExcls") { mutableStateOf(settings.foodExclusions) }
    var habitMessages by rememberSaveable(key = "${draftKey}_habitMsgs") { mutableStateOf(settings.habitMessagesEnabled) }

    var bStart by rememberSaveable(key = "${draftKey}_bStart", stateSaver = localTimeSaver) { mutableStateOf(settings.breakfastStart) }
    var bEnd by rememberSaveable(key = "${draftKey}_bEnd", stateSaver = localTimeSaver) { mutableStateOf(settings.breakfastEnd) }
    var lStart by rememberSaveable(key = "${draftKey}_lStart", stateSaver = localTimeSaver) { mutableStateOf(settings.lunchStart) }
    var lEnd by rememberSaveable(key = "${draftKey}_lEnd", stateSaver = localTimeSaver) { mutableStateOf(settings.lunchEnd) }
    var dStart by rememberSaveable(key = "${draftKey}_dStart", stateSaver = localTimeSaver) { mutableStateOf(settings.dinnerStart) }
    var dEnd by rememberSaveable(key = "${draftKey}_dEnd", stateSaver = localTimeSaver) { mutableStateOf(settings.dinnerEnd) }

    val monthlyValue = monthly.asDouble()
    val monthlyValid = monthly.isNotBlank() && monthlyValue > 0.0
    val dailyValue = daily.asDouble()
    val dailyValid = daily.isNotBlank() && dailyValue > 0.0
    val weeklyValue = weekly.asDouble()
    val weeklyValid = weekly.isNotBlank() && weeklyValue > 0.0
    val kosharyPriceValid = kosharyPrice.isNonNegativeNumber()
    val windowError = BudgetSettings.validateWindows(bStart, bEnd, lStart, lEnd, dStart, dEnd)

    val valid = monthlyValid && dailyValid && weeklyValid && kosharyPriceValid && windowError == null

    SheetBody("Settings", null, onClose = draft.dismiss) {
        FormSectionTitle("Appearance")
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppThemeMode.entries.forEach { mode ->
                val isSelected = themeMode == mode
                FilterChip(
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
        NumberField(
            value = monthly,
            onValueChange = { monthly = it },
            label = "Monthly food ceiling",
            prefix = "EGP ",
            isError = !monthlyValid && monthly.isNotBlank(),
            supportingText = if (!monthlyValid && monthly.isNotBlank()) ({
                Text("Monthly food ceiling must be greater than 0 EGP.")
            }) else null,
        )
        NumberField(
            value = daily,
            onValueChange = { daily = it },
            label = "Daily average",
            prefix = "EGP ",
            modifier = Modifier.padding(top = 12.dp),
            isError = !dailyValid && daily.isNotBlank(),
            supportingText = if (!dailyValid && daily.isNotBlank()) ({
                Text("Daily average must be greater than 0 EGP.")
            }) else null,
        )
        NumberField(
            value = weekly,
            onValueChange = { weekly = it },
            label = "Weekly target",
            prefix = "EGP ",
            modifier = Modifier.padding(top = 12.dp),
            isError = !weeklyValid && weekly.isNotBlank(),
            supportingText = if (!weeklyValid && weekly.isNotBlank()) ({
                Text("Weekly target must be greater than 0 EGP.")
            }) else null,
        )
        FormSectionTitle("Koshary day")
        NumberField(
            value = kosharyPrice,
            onValueChange = { kosharyPrice = it },
            label = "Koshary price",
            prefix = "EGP ",
            isError = !kosharyPriceValid && kosharyPrice.isNotBlank(),
            supportingText = if (!kosharyPriceValid && kosharyPrice.isNotBlank()) ({
                Text("Koshary price must be 0 EGP or more.")
            }) else null,
        )
        DropdownField("Day", kosharyDay, DayOfWeek.entries, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, { kosharyDay = it }, Modifier.padding(top = 12.dp))
        FormSectionTitle("Shopping reminders")
        DropdownField("Shopping day", shoppingDay, DayOfWeek.entries, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, { shoppingDay = it })
        SettingSwitch("Daily budget reminder", reminders, { reminders = it })
        SettingSwitch("Show a spare-saving prompt", autoSpares, { autoSpares = it })

        FormSectionTitle("Food ideas & habits")
        BudgetTextField(
            value = foodPreferences,
            onValueChange = { foodPreferences = it },
            label = "Preferred foods or ingredients (optional)",
            placeholder = "e.g. eggs, chicken, lentils, rice",
            modifier = Modifier.padding(top = 4.dp),
        )
        BudgetTextField(
            value = foodExclusions,
            onValueChange = { foodExclusions = it },
            label = "Excluded foods or allergies (optional)",
            placeholder = "e.g. fish, peanuts, dairy",
            modifier = Modifier.padding(top = 12.dp),
        )
        SettingSwitch("Show habit insights on Home", habitMessages, { habitMessages = it })

        FormSectionTitle("Meal hours")
        Text(
            text = "Breakfast, lunch, and dinner start/end times. Overnight dinner is allowed; windows must not overlap.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        MealWindowRow("Breakfast", bStart, bEnd, { bStart = it }, { bEnd = it })
        MealWindowRow("Lunch", lStart, lEnd, { lStart = it }, { lEnd = it })
        MealWindowRow("Dinner", dStart, dEnd, { dStart = it }, { dEnd = it })
        if (windowError != null) {
            Text(
                text = windowError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        val validationError = when {
            !monthlyValid -> "Enter a monthly food ceiling greater than 0 EGP."
            !dailyValid -> "Enter a daily food average greater than 0 EGP."
            !weeklyValid -> "Enter a weekly food target greater than 0 EGP."
            !kosharyPriceValid -> "Enter a valid koshary price (0 EGP or more)."
            windowError != null -> windowError
            else -> null
        }
        if (validationError != null) {
            Text(
                text = validationError,
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton("Save settings", {
            draft.submission.submit { complete ->
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
                        foodPreferences = foodPreferences.trim(),
                        foodExclusions = foodExclusions.trim(),
                        habitMessagesEnabled = habitMessages,
                        breakfastStart = bStart,
                        breakfastEnd = bEnd,
                        lunchStart = lStart,
                        lunchEnd = lEnd,
                        dinnerStart = dStart,
                        dinnerEnd = dEnd,
                    ),
                    onComplete = complete,
                )
            }
        }, enabled = valid && !draft.submission.isSaving, icon = AppIcons.Check)
    }
}

@Composable
private fun MealWindowRow(
    mealName: String,
    startTime: LocalTime,
    endTime: LocalTime,
    onStartChange: (LocalTime) -> Unit,
    onEndChange: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = mealName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> onStartChange(LocalTime.of(h, m)) },
                            startTime.hour,
                            startTime.minute,
                            true,
                        ).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "%02d:%02d".format(Locale.US, startTime.hour, startTime.minute),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> onEndChange(LocalTime.of(h, m)) },
                            endTime.hour,
                            endTime.minute,
                            true,
                        ).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "%02d:%02d".format(Locale.US, endTime.hour, endTime.minute),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
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
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DarkSurfaceHigh,
                uncheckedBorderColor = DarkBorder,
            ),
        )
    }
}
