package com.budgetmeals.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Design language: "Warm Pantry"
// Warm stone neutrals + one confident garden-green accent. Amber is reserved for
// "attention / leftovers", red for "problem", slate-blue for evening meals.
// Constant names are kept stable so existing call sites keep working.
// ============================================================================

// ---- Dark: warm charcoal (never pure black, never blue-grey) ---------------
val DarkBackground = Color(0xFF14120F)
val DarkSurface = Color(0xFF14120F)
val DarkSurfaceContainerLowest = Color(0xFF0F0D0B)
val DarkSurfaceContainerLow = Color(0xFF1B1814)
val DarkSurfaceContainer = Color(0xFF211E19)
val DarkSurfaceContainerHigh = Color(0xFF2A2621)
val DarkSurfaceContainerHighest = Color(0xFF34302A)
val DarkSurfaceVariant = Color(0xFF2A2621)

val DarkOnSurface = Color(0xFFF3EFE9)
val DarkOnSurfaceVariant = Color(0xFFB3AB9F)
val DarkOnBackground = Color(0xFFF3EFE9)
val DarkOutline = Color(0xFF8C8477)
val DarkOutlineVariant = Color(0xFF3A352E)

// Primary: garden green
val EmeraldMint = Color(0xFF7FD1A4)
val EmeraldMintLight = Color(0xFF9BE0BC)
val EmeraldMintContainer = Color(0xFF1E4D37)
val OnEmeraldMintContainer = Color(0xFFC4EED8)
val OnEmeraldMint = Color(0xFF06301D)

// Secondary: honey amber
val AmberGold = Color(0xFFEBB45C)
val AmberGoldContainer = Color(0xFF3B2E12)
val OnAmberGoldContainer = Color(0xFFF8DFA6)
val OnAmberGold = Color(0xFF3A2400)

// Tertiary: dusk slate-blue
val SoftIndigo = Color(0xFF9DAAF0)
val SoftIndigoContainer = Color(0xFF2E3A73)
val OnSoftIndigoContainer = Color(0xFFD9DEFF)
val OnSoftIndigo = Color(0xFF1B2350)

// Error / semantic
val ErrorRed = Color(0xFFF2766B)
val ErrorRedContainer = Color(0xFF4A1914)
val OnErrorRedContainer = Color(0xFFFFD7D2)
val OnErrorRed = Color(0xFF3B0A06)

// Extended (dark)
val SparesGoldDark = Color(0xFFEBB45C)
val SparesGoldContainerDark = Color(0xFF3B2E12)
val OnSparesGoldContainerDark = Color(0xFFF8DFA6)
val SnackColorDark = Color(0xFFF3EFE9)
val OnSnackColorDark = Color(0xFF211E19)
val CardBorderDark = Color(0xFF36312A)
val TipContainerDark = Color(0xFF1A2B22)
val OnTipContainerDark = Color(0xFFC4EED8)

// ---- Light: warm paper -----------------------------------------------------
val LightBackground = Color(0xFFFAF8F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF5F2EE)
val LightSurfaceContainer = Color(0xFFEFEBE6)
val LightSurfaceContainerHigh = Color(0xFFE8E3DD)
val LightSurfaceContainerHighest = Color(0xFFE0DAD3)
val LightSurfaceVariant = Color(0xFFEDE9E3)

val LightOnSurface = Color(0xFF1F1B16)
val LightOnSurfaceVariant = Color(0xFF5F574D)
val LightOnBackground = Color(0xFF1F1B16)
val LightOutline = Color(0xFF7A7164)
val LightOutlineVariant = Color(0xFFE3DDD5)

val LightPrimary = Color(0xFF1F6F50)
val LightOnPrimary = Color.White
val LightPrimaryContainer = Color(0xFFD7EBDF)
val LightOnPrimaryContainer = Color(0xFF0F3D2B)

val LightSecondary = Color(0xFF9A5B00)
val LightOnSecondary = Color.White
val LightSecondaryContainer = Color(0xFFFBEBCB)
val LightOnSecondaryContainer = Color(0xFF5C3500)

val LightTertiary = Color(0xFF55639E)
val LightOnTertiary = Color.White
val LightTertiaryContainer = Color(0xFFE1E5F5)
val LightOnTertiaryContainer = Color(0xFF262F5C)

val LightSparesGold = LightSecondary
val LightSparesGoldContainer = Color(0xFFFBEBCB)
val LightOnSparesGoldContainer = Color(0xFF5C3500)
val LightSnackColor = Color(0xFF2B2620)
val LightOnSnackColor = Color(0xFFFAF8F5)
val LightCardBorder = Color(0xFFE8E2DA)
val LightTipContainer = Color(0xFFE6F1EA)
val LightOnTipContainer = Color(0xFF0F3D2B)
