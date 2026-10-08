package com.budgetmeals.app.ui

import androidx.compose.foundation.background
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val AccentMint: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

val AccentMintLight: Color
    @Composable @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        com.budgetmeals.app.ui.theme.EmeraldMintLight
    } else MaterialTheme.colorScheme.primary

val AccentAmber: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.secondary

val AccentIndigo: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tertiary

val AccentPurple: Color
    @Composable @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFFE39A82)
    } else Color(0xFFA6502F)

val ErrorRed: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.error

val ErrorBorder: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)

// Theme-aware Dynamic Colors (crisp contrast in light and dark themes)
val DarkBgBase: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

val DarkSurfaceLow: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceContainerLow

val DarkSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceContainer

val DarkSurfaceHigh: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceContainerHigh

val DarkSurfaceHighest: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceContainerHighest

val DarkBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outlineVariant

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outline

@Composable
fun sheetCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = AccentMint,
    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
    uncheckedColor = TextMuted,
)
