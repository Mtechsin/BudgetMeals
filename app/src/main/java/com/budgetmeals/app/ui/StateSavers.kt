package com.budgetmeals.app.ui

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.budgetmeals.app.data.MealType
import java.time.LocalDate

internal object FormDraftStore {
    private val generations = mutableMapOf<String, Int>()

    fun draftKey(editorIdentity: String): String =
        "${editorIdentity}_${generations.getOrDefault(editorIdentity, 0)}"

    fun reset(editorIdentity: String) {
        generations[editorIdentity] = generations.getOrDefault(editorIdentity, 0) + 1
    }

    fun clearAll() {
        generations.clear()
    }
}

internal class SheetDraftContext(
    val draftKey: String,
    val submission: FormSubmissionState,
    val dismiss: () -> Unit,
)

@Composable
internal fun rememberSheetDraft(
    editorIdentity: String,
    onDismiss: () -> Unit,
): SheetDraftContext {
    val draftKey = rememberSaveable(editorIdentity) {
        FormDraftStore.draftKey(editorIdentity)
    }
    var cleanedUp by remember { mutableStateOf(false) }
    val performCleanupAndDismiss: () -> Unit = remember(editorIdentity, onDismiss) {
        {
            if (!cleanedUp) {
                cleanedUp = true
                FormDraftStore.reset(editorIdentity)
            }
            onDismiss()
        }
    }
    val submission = rememberFormSubmission(performCleanupAndDismiss)
    return remember(draftKey, submission, performCleanupAndDismiss) {
        SheetDraftContext(
            draftKey = draftKey,
            submission = submission,
            dismiss = performCleanupAndDismiss,
        )
    }
}

internal val mutableLongStateSaver = Saver<MutableLongState, Long>(
    save = { it.longValue },
    restore = { mutableLongStateOf(it) },
)

internal val localDateSaver = Saver<LocalDate, Long>(
    save = { it.toEpochDay() },
    restore = { LocalDate.ofEpochDay(it) },
)

internal inline fun <reified T : Enum<T>> enumSaver(): Saver<T, String> = Saver(
    save = { it.name },
    restore = { value ->
        enumValues<T>().firstOrNull { it.name == value } ?: enumValues<T>().first()
    },
)

internal val mealReviewChoicesSaver = Saver<Map<MealType, MealReviewChoice>, Bundle>(
    save = { map ->
        Bundle().apply {
            map.forEach { (type, choice) ->
                putString(type.name, choice.name)
            }
        }
    },
    restore = { bundle ->
        bundle.keySet().mapNotNull { key ->
            val type = runCatching { MealType.valueOf(key) }.getOrNull() ?: return@mapNotNull null
            val choiceStr = bundle.getString(key) ?: return@mapNotNull null
            val choice = runCatching { MealReviewChoice.valueOf(choiceStr) }.getOrNull() ?: return@mapNotNull null
            type to choice
        }.toMap()
    },
)

internal val mealLeftoverPercentSaver = Saver<Map<MealType, String>, Bundle>(
    save = { map ->
        Bundle().apply {
            map.forEach { (type, percent) ->
                putString(type.name, percent)
            }
        }
    },
    restore = { bundle ->
        bundle.keySet().mapNotNull { key ->
            val type = runCatching { MealType.valueOf(key) }.getOrNull() ?: return@mapNotNull null
            val percent = bundle.getString(key) ?: return@mapNotNull null
            type to percent
        }.toMap()
    },
)
