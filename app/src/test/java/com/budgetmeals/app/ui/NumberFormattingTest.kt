package com.budgetmeals.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberFormattingTest {
    @Test
    fun rejectsPastedTextThatWouldSilentlyChangeTheAmount() {
        assertNull(numberInput("-25", decimal = true))
        assertNull(numberInput("1e3", decimal = true))
        assertNull(numberInput("25 EGP", decimal = true))
        assertNull(numberInput("1.5", decimal = false))
    }

    @Test
    fun nonNegativeAmountDistinguishesFreeFromInvalidOrOverflowingPrice() {
        assertTrue("0".isNonNegativeNumber())
        assertTrue("12,5".isNonNegativeNumber())
        assertFalse("".isNonNegativeNumber())
        assertFalse(".".isNonNegativeNumber())
        assertFalse("Infinity".isNonNegativeNumber())
        assertFalse("9".repeat(400).isNonNegativeNumber())
        assertFalse("-1".isNonNegativeNumber())
        assertTrue("".isNonNegativeNumber(allowBlank = true))
        assertFalse(".".isNonNegativeNumber(allowBlank = true))
    }

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

    @Test
    fun percentInputAllowsRemovingAllNumbersAndEditingFrom90To80() {
        // User starts at 90, backspaces to 9, removes all numbers to empty string, then enters 80
        assertEquals("9", percentInput("9"))
        assertEquals("", percentInput(""))
        assertEquals("8", percentInput("8"))
        assertEquals("80", percentInput("80"))
    }

    @Test
    fun percentInputClampsAndHandlesEdgeCases() {
        assertEquals("0", percentInput("0"))
        assertEquals("100", percentInput("100"))
        assertEquals("100", percentInput("150"))
        assertEquals("8", percentInput("08"))
        assertEquals("80", percentInput("\u0668\u0660"))
    }

    @Test
    fun parsePercentHandlesFallbacksAndRanges() {
        assertEquals(25, null.parsePercent(25))
        assertEquals(25, "".parsePercent(25))
        assertEquals(25, "   ".parsePercent(25))
        assertEquals(80, "80".parsePercent(25))
        assertEquals(0, "0".parsePercent(25))
        assertEquals(100, "100".parsePercent(25))
        assertEquals(100, "150".parsePercent(25))
        assertEquals(80, "\u0668\u0660".parsePercent(25))
    }
}

