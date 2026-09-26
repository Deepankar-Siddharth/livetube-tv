package com.livetube.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Focus requester for a dialog's primary action.
 *
 * Compose does not focus anything when a dialog appears, which leaves D-pad users without a
 * target. The request is retried for a few frames because the dialog content is attached after
 * the first composition pass.
 */
@Composable
fun rememberDialogFocusRequester(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) {
        repeat(DIALOG_FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    return requester
}

private const val DIALOG_FOCUS_ATTEMPTS = 6
