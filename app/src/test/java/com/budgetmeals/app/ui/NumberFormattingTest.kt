package com.budgetmeals.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberFormattingTest {
    @Test
    fun acceptsLocaleDecimalSeparators() {
        val arabicDecimal = "\u0661\u0662\u066B\u0665"

        assertEquals("12.5", numberInput("12,5", decimal = true))
        assertEquals("12.5", numberInput(arabicDecimal, decimal = true))
        assertEquals(12.5, arabicDecimal.asDouble(), 0.0)
    }

    @Test
    fun invalidNumbersStayDistinctFromZeroAndArabicDecimalsAreAccepted() {
        assertNull("".asDoubleOrNull())
        assertNull("not a number".asDoubleOrNull())
        assertNull("NaN".asDoubleOrNull())
        assertNull("Infinity".asDoubleOrNull())

        assertEquals(0.0, requireNotNull("0".asDoubleOrNull()), 0.0)
        assertEquals(12.5, requireNotNull("\u0661\u0662\u066B\u0665".asDoubleOrNull()), 0.0)
    }

    @Test
    fun rejectsMultipleDecimalSeparators() {
        assertNull(numberInput("12.3,4", decimal = true))
    }

    @Test
    fun keepsTrailingZeroWhenEditing() {
        assertEquals("10", numberInput("10", decimal = true))
        assertEquals("0.05", numberInput("0.05", decimal = true))
        assertEquals("5", numberInput("005", decimal = false))
    }

    @Test
    fun largeNumbersDoNotOverflowInt() {
        assertEquals("3000000000", 3_000_000_000.0.cleanNumber())
        assertEquals("3000000000", 3_000_000_000.0.compact())
    }

    @Test
    fun preservesSmallPortions() {
        assertEquals("0.025", 0.025.cleanNumber())
    }

    @Test
    fun rejectsNonFiniteAmounts() {
        assertEquals(0.0, "NaN".asDouble(), 0.0)
        assertEquals(0.0, "Infinity".asDouble(), 0.0)
    }
}
