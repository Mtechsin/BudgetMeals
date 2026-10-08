package com.budgetmeals.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmeals.app.data.BatchType
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.Motion
import com.budgetmeals.app.ui.theme.extendedColors
import java.util.Locale

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = AccentMintLight),
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MoneyLabel(
    amount: Double,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    color: Color = TextPrimary,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
) {
    Text(
        text = BudgetMath.money(amount, decimals),
        modifier = modifier,
        color = color,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    SoftCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = 16.dp,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBadge(icon = icon, tint = tint, size = 40.dp, iconSize = 20.dp)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftCard(
        onClick = onClick,
        modifier = modifier,
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = icon, tint = tint, size = 44.dp, iconSize = 22.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(20.dp)
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 170.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = tint.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CategoryIcon(
    iconName: String,
    modifier: Modifier = Modifier,
    tint: Color = AccentMint,
) {
    val icon = when (iconName.trim().lowercase(Locale.US)) {
        "rice", "staples", "food_staple" -> AppIcons.RiceBowl
        "egg", "fresh", "food_fresh" -> AppIcons.Nutrition
        "restaurant", "street", "food", "food_street" -> AppIcons.Restaurant
        "wifi", "internet" -> AppIcons.Wifi
        "call", "calling" -> AppIcons.Call
        "home", "household" -> AppIcons.Home
        "celebration" -> AppIcons.Celebration
        "spares" -> AppIcons.Savings
        "inventory", "inventory_2", "stock" -> AppIcons.Inventory2
        "shopping", "shop" -> AppIcons.ShoppingCart
        "cookie", "snack" -> AppIcons.Cookie
        "cleaning" -> AppIcons.CleaningServices
        "kitchen" -> AppIcons.Kitchen
        "receipt", "receipt_long" -> AppIcons.ReceiptLong
        "transport" -> AppIcons.DirectionsCar
        "health" -> AppIcons.LocalHospital
        "personal" -> AppIcons.Person
        else -> AppIcons.Category
    }
    Icon(icon, contentDescription = null, modifier = modifier, tint = tint)
}

@Composable
fun MealIcon(mealType: MealType, modifier: Modifier = Modifier, tint: Color = AccentMint) {
    val icon = when (mealType) {
        MealType.BREAKFAST -> AppIcons.BreakfastDining
        MealType.LUNCH -> AppIcons.Restaurant
        MealType.DINNER -> AppIcons.DinnerDining
        MealType.SNACK -> AppIcons.Cookie
    }
    Icon(icon, contentDescription = mealType.label, modifier = modifier, tint = tint)
}

@Composable
fun ChipLabel(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
) {
    if (onClick == null) {
        AssistChip(
            onClick = {},
            label = { Text(text) },
            leadingIcon = leadingIcon?.let { { Icon(it, null, Modifier.size(16.dp)) } },
            enabled = false,
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = if (selected) AccentMint.copy(alpha = 0.15f) else DarkSurface,
                disabledLabelColor = if (selected) AccentMintLight else TextSecondary,
            ),
            border = BorderStroke(1.dp, if (selected) AccentMint.copy(alpha = 0.35f) else DarkBorder),
        )
    } else {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(text) },
            leadingIcon = leadingIcon?.let { { Icon(it, null, Modifier.size(16.dp)) } },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentMint.copy(alpha = 0.15f),
                selectedLabelColor = AccentMintLight,
                selectedLeadingIconColor = AccentMintLight,
                containerColor = DarkSurface,
                labelColor = TextSecondary,
                iconColor = TextSecondary,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = DarkBorder,
                selectedBorderColor = AccentMint.copy(alpha = 0.35f),
            ),
        )
    }
}

@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else cardSurfaceColor()
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.extendedColors.cardBorder

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(99.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
fun MealItemCard(
    title: String,
    mealType: MealType,
    cost: Double,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isEaten: Boolean = false,
    onClick: (() -> Unit)? = null,
    onEatenToggle: (() -> Unit)? = null,
) {
    val containerColor = if (isEaten) AccentMint.copy(alpha = 0.08f) else DarkSurface
    val borderColor = if (isEaten) AccentMint.copy(alpha = 0.3f) else DarkBorder
    val typeColor = when (mealType) {
        MealType.BREAKFAST -> AccentMintLight
        MealType.LUNCH -> AccentMint
        MealType.DINNER -> AccentIndigo
        MealType.SNACK -> AccentAmber
    }

    val cardShape = RoundedCornerShape(20.dp)
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = typeColor.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MealIcon(mealType, Modifier.size(20.dp), tint = typeColor)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = mealType.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            MoneyLabel(
                amount = cost,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            if (onEatenToggle != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onEatenToggle,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isEaten) AppIcons.Check else AppIcons.Add,
                        contentDescription = if (isEaten) "Eaten" else "Mark eaten",
                        tint = if (isEaten) AccentMintLight else TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun MealItemCard(
    template: MealTemplate,
    modifier: Modifier = Modifier,
    isEaten: Boolean = false,
    onClick: (() -> Unit)? = null,
    onEatenToggle: (() -> Unit)? = null,
) {
    MealItemCard(
        title = template.name,
        mealType = template.mealType,
        cost = template.cost,
        modifier = modifier,
        subtitle = template.notes.takeIf { it.isNotBlank() },
        isEaten = isEaten,
        onClick = onClick,
        onEatenToggle = onEatenToggle,
    )
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = AccentMint,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: androidx.compose.ui.unit.Dp = 8.dp,
) {
    val safeProgress by animateFloatAsState(
        targetValue = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = Motion.ProgressTween,
        label = "budgetProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f) }
            .clip(RoundedCornerShape(99.dp))
            .background(trackColor),
    ) {
        if (safeProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(99.dp))
                    .background(color),
            )
        }
    }
}

@Composable
fun StockProgressRow(
    item: StockItem,
    modifier: Modifier = Modifier,
    catalogLabel: String? = null,
    onClick: () -> Unit,
    onLogUsage: () -> Unit,
) {
    val low = item.isLow
    val progress = item.remainingPercent.toFloat()
    val statusColor = when {
        item.isFinished -> MaterialTheme.colorScheme.outline
        low -> MaterialTheme.colorScheme.error
        item.remainingPercent <= 0.45 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val containerColor = cardSurfaceColor()
    val borderStroke = if (low && !item.isFinished) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder)
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CategoryIcon(item.category.icon, tint = statusColor, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${item.remainingQuantity.cleanNumber()} ${item.unit} left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = item.daysLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (low && !item.isFinished) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (low && !item.isFinished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${BudgetMath.money(item.costPerDay, 1)}/day",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onLogUsage,
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            text = "Log use",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }

            // Slim elegant progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(statusColor),
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: ImageVector = AppIcons.ShoppingCart,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun budgetTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedPrefixColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPrefixColor = MaterialTheme.colorScheme.outline,
    focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline,
)

@Composable
fun BudgetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    prefix: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = TextMuted) } },
        prefix = prefix?.let { { Text(it) } },
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = budgetTextFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    decimal: Boolean = true,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> numberInput(raw, decimal)?.let(onValueChange) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = TextMuted) } },
        prefix = prefix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = budgetTextFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun <T> DropdownField(
    label: String,
    value: T,
    values: List<T>,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = cardSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(labelOf(value), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            Icon(AppIcons.KeyboardArrowDown, contentDescription = "Choose", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder), RoundedCornerShape(12.dp)),
        ) {
            values.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option), color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun FormSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelMedium.copy(
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = TextSecondary,
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun SmallIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = contentDescription, tint = TextSecondary) }
}

@Composable
fun CategoryPill(categoryName: String, color: Color = AccentMint) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
    ) {
        Text(
            categoryName,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun MealTypePill(mealType: MealType) {
    val color = when (mealType) {
        MealType.BREAKFAST -> AccentMintLight
        MealType.LUNCH -> AccentMint
        MealType.DINNER -> AccentIndigo
        MealType.SNACK -> AccentAmber
    }
    CategoryPill(mealType.label, color)
}

@Composable
fun CompactExpenseRow(
    title: String,
    subtitle: String,
    amount: Double,
    iconName: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder),
        ) {
            Box(contentAlignment = Alignment.Center) { CategoryIcon(iconName, modifier = Modifier.size(20.dp), tint = AccentMint) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        MoneyLabel(amount, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

@Composable
fun QuickAddFloatingButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = AccentMint,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(20.dp),
    ) {
        Icon(AppIcons.Add, contentDescription = "Add")
    }
}

@Composable
fun AnimatedSaveHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(Motion.CardExpandTween),
        shape = RoundedCornerShape(14.dp),
        color = AccentMint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, AccentMint.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = AccentMint.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.Savings,
                        contentDescription = null,
                        tint = AccentMintLight,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenuContainer(expanded: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder), RoundedCornerShape(12.dp)),
    ) {
        content()
    }
}
