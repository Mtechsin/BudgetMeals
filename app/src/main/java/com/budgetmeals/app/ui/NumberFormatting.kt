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
    val filtered = raw.normalizeNumber().filter { it in '0'..'9' || (decimal && it == '.') }
    if (filtered.count { it == '.' } > 1) return null
    return if (filtered.startsWith('0') && filtered.length > 1 && filtered[1] != '.') {
        filtered.trimStart('0').ifEmpty { "0" }
    } else filtered
}

fun Double.cleanNumber(): String = when {
    this % 1.0 == 0.0 -> String.format(Locale.US, "%.0f", this)
    this < 0.1 && this > 0.0 -> String.format(Locale.US, "%.3f", this).trimEnd('0').trimEnd('.')
    else -> String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')
}

internal fun Double.compact(): String = if (this % 1.0 == 0.0) {
    String.format(Locale.US, "%.0f", this)
} else String.format(Locale.US, "%.1f", this)
