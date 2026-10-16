package com.budgetmeals.app.ui

import java.util.Locale

fun String.asDouble(): Double = asDoubleOrNull() ?: 0.0

internal fun String.asDoubleOrNull(): Double? = trim().normalizeNumber().toDoubleOrNull()
    ?.takeIf { it.isFinite() }

private fun String.normalizeNumber(): String = map { character ->
    when {
        character == ',' || character == '\u066B' -> '.'
        character.isDigit() -> Character.forDigit(Character.digit(character, 10), 10)
        else -> character
    }
}.joinToString("")

internal fun numberInput(raw: String, decimal: Boolean): String? {
    val filtered = raw.trim().normalizeNumber()
    // Removing arbitrary characters changes the amount (e.g. -25 -> 25 or 1e3 -> 13).
    if (filtered.any { it !in '0'..'9' && !(decimal && it == '.') }) return null
    if (filtered.count { it == '.' } > 1) return null
    return if (filtered.startsWith('0') && filtered.length > 1 && filtered[1] != '.') {
        filtered.trimStart('0').ifEmpty { "0" }
    } else filtered
}

internal fun String.isNonNegativeNumber(allowBlank: Boolean = false): Boolean =
    (allowBlank && isBlank()) || asDoubleOrNull()?.let { it >= 0.0 } == true

internal fun percentInput(raw: String): String? {
    val sanitized = numberInput(raw, decimal = false) ?: return null
    if (sanitized.isEmpty()) return ""
    val parsed = sanitized.toIntOrNull() ?: return null
    return parsed.coerceIn(0, 100).toString()
}

internal fun String?.parsePercent(default: Int = 25): Int {
    if (this.isNullOrBlank()) return default
    return trim().normalizeNumber().toIntOrNull()?.coerceIn(0, 100) ?: default
}

fun Double.cleanNumber(): String = when {
    this % 1.0 == 0.0 -> String.format(Locale.US, "%.0f", this)
    this < 0.1 && this > 0.0 -> String.format(Locale.US, "%.3f", this).trimEnd('0').trimEnd('.')
    else -> String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')
}

internal fun Double.compact(): String = if (this % 1.0 == 0.0) {
    String.format(Locale.US, "%.0f", this)
} else String.format(Locale.US, "%.1f", this)
