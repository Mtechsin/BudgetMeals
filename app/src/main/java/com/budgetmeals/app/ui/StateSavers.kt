package com.budgetmeals.app.ui

import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.Saver

internal val mutableLongStateSaver = Saver<MutableLongState, Long>(
    save = { it.longValue },
    restore = { mutableLongStateOf(it) },
)
