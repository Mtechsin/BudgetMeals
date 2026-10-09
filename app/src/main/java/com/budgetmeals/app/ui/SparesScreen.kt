package com.budgetmeals.app.ui

import com.budgetmeals.app.ui.icons.AppIcons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.SparesTransaction
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.ui.theme.Spacing
import com.budgetmeals.app.ui.theme.extendedColors

@Composable
fun SparesScreen(
    snapshot: AppSnapshot,
    onBack: () -> Unit,
    onAddSpares: () -> Unit,
    onSaveToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val balance = snapshot.sparesBalance
    val streak = BudgetMath.sparesStreak(snapshot)
    val sortedHistory = remember(snapshot.spares) { snapshot.spares.sortedByDescending { it.date } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.bottomClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            ScreenHeader(
                title = "Spares",
                onBack = onBack,
                action = {
                    IconButton(onClick = onAddSpares) {
                        Icon(
                            AppIcons.Add,
                            contentDescription = "Add spares transaction",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }

        // Hero Balance Card
        item {
            SoftCard(
                borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                contentPadding = Spacing.xl,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        icon = AppIcons.Savings,
                        tint = MaterialTheme.colorScheme.secondary,
                        size = 52.dp,
                        iconSize = 28.dp,
                    )
                    if (streak > 0) {
                        StatusPill(
                            text = "$streak day streak ðŸ”¥",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = "SPARES BALANCE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = BudgetMath.money(balance),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Guilt-free savings for treats, extra snacks, or coffee.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Actions
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = onAddSpares,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(AppIcons.Savings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add money", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onSaveToday,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = cardSurfaceColor(),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        AppIcons.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Move spare", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item {
            SectionTitle("Treat ideas")
        }

        item {
            TreatRow("Chips", 10.0, balance, AppIcons.Fastfood)
        }
        item {
            TreatRow("Extra koshary", 60.0, balance, AppIcons.RiceBowl)
        }
        item {
            TreatRow("Konafa", 40.0, balance, AppIcons.Cake)
        }
        item {
            TreatRow("Shawarma", 80.0, balance, AppIcons.LunchDining)
        }

        item {
            SoftCard(
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                contentPadding = Spacing.md,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        icon = AppIcons.Favorite,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 40.dp,
                        iconSize = 20.dp,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        "Spending here is a reward. The app will never treat it as an overspend.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionTitle(
                title = "History",
                trailing = {
                    if (sortedHistory.isNotEmpty()) {
                        Text(
                            "${sortedHistory.size} items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        if (sortedHistory.isEmpty()) {
            item {
                EmptyState("Your first spare starts here", "When you finish under budget, move the difference here.", AppIcons.Savings)
            }
        } else {
            items(sortedHistory, key = { it.id }, contentType = { "spares_row" }) { transaction ->
                SparesRow(transaction)
            }
        }
    }
}

@Composable
private fun TreatRow(
    name: String,
    cost: Double,
    balance: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val affordable = balance >= cost
    SoftCard(
        borderColor = if (affordable) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.extendedColors.cardBorder,
        contentPadding = Spacing.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = icon,
                tint = if (affordable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                size = 44.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                if (affordable) {
                    StatusPill(
                        text = "Affordable now",
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        "Save ${BudgetMath.money(cost - balance)} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                BudgetMath.money(cost),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (affordable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SparesRow(transaction: SparesTransaction) {
    val positive = transaction.amount >= 0
    SoftCard(
        contentPadding = Spacing.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = if (positive) AppIcons.TrendingUp else AppIcons.ShoppingBasket,
                tint = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                size = 42.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    transaction.reason,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    BudgetMath.formatDate(transaction.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                (if (positive) "+" else "") + BudgetMath.money(transaction.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
