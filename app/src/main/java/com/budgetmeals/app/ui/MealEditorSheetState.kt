package com.budgetmeals.app.ui

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import com.budgetmeals.app.data.MealDataCodec
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import java.time.DayOfWeek

/** Keeps the original meal and editor route available when the Activity is recreated. */
internal val mealEditorSheetSaver = Saver<AppSheet?, Bundle>(
    save = { sheet ->
        Bundle().apply {
            when (sheet) {
                AppSheet.AddTemplate -> putString("editor", "add")
                is AppSheet.EditTemplate -> {
                    putString("editor", "edit")
                    with(sheet.template) {
                        putString("id", id)
                        putString("name", name)
                        putString("type", mealType.name)
                        putDouble("cost", cost)
                        putBoolean("recurring", isRecurring)
                        putString("day", dayOfWeek?.name)
                        putString("notes", notes)
                        putBoolean("custom", isCustom)
                        putString("components", MealDataCodec.encodeComponents(components))
                    }
                }
                else -> Unit
            }
        }
    },
    restore = { saved ->
        runCatching {
            when (saved.getString("editor")) {
                "add" -> AppSheet.AddTemplate
                "edit" -> AppSheet.EditTemplate(
                    MealTemplate(
                        id = requireNotNull(saved.getString("id")),
                        name = saved.getString("name").orEmpty(),
                        mealType = MealType.valueOf(requireNotNull(saved.getString("type"))),
                        cost = saved.getDouble("cost"),
                        isRecurring = saved.getBoolean("recurring"),
                        dayOfWeek = saved.getString("day")?.let(DayOfWeek::valueOf),
                        notes = saved.getString("notes").orEmpty(),
                        isCustom = saved.getBoolean("custom"),
                        components = MealDataCodec.decodeComponents(saved.getString("components").orEmpty()),
                    ),
                )
                else -> null
            }
        }.getOrNull()
    },
)
