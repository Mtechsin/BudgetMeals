package com.budgetmeals.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/** Keep the entered values available for retry until persistence succeeds. */
internal class FormSubmissionState(private val onSaved: () -> Unit) {
    var isSaving by mutableStateOf(false)
        private set

    fun submit(operation: ((Boolean) -> Unit) -> Unit) {
        if (isSaving) return
        isSaving = true
        var completed = false
        operation { success ->
            if (!completed) {
                completed = true
                if (success) onSaved() else isSaving = false
            }
        }
    }
}

@Composable
internal fun rememberFormSubmission(onSaved: () -> Unit): FormSubmissionState {
    val currentOnSaved by rememberUpdatedState(onSaved)
    return remember { FormSubmissionState { currentOnSaved() } }
}
