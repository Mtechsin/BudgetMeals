package com.budgetmeals.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormSubmissionStateTest {
    @Test
    fun repeatedTapDuringSaveDoesNotRepeatTheOperation() {
        var calls = 0
        var closes = 0
        var completion: ((Boolean) -> Unit)? = null
        val state = FormSubmissionState { closes++ }

        state.submit { calls++; completion = it }
        state.submit { calls++ }

        assertTrue(state.isSaving)
        assertEquals(1, calls)
        assertEquals(0, closes)
        completion!!(true)
        assertTrue(state.isSaving)
        assertEquals(1, closes)
        state.submit { calls++ }
        assertEquals(1, calls)
    }

    @Test
    fun failedSaveKeepsFormOpenAndAllowsRetry() {
        var closes = 0
        val state = FormSubmissionState { closes++ }

        state.submit { it(false) }
        assertFalse(state.isSaving)
        assertEquals(0, closes)

        state.submit { it(true) }
        assertEquals(1, closes)
    }
}
