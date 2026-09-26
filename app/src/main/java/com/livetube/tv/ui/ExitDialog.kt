package com.livetube.tv.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Exit confirmation.
 *
 * Focus starts on Cancel, so a stray OK never closes the app, and the focused action is marked
 * with a bright outline plus a red tint rather than a large filled selection box.
 */
@Composable
fun ExitDialog(onExit: () -> Unit, onStay: () -> Unit) {
    val cancelFocusRequester = rememberDialogFocusRequester()
    TvDialog(
        title = "Exit LiveTube TV?",
        subtitle = "You can return to the live guide at any time.",
        onDismissRequest = onStay,
    ) {
        Spacer(Modifier.height(4.dp))
        TvDialogActions {
            TvDialogButton(
                text = "Cancel",
                onClick = onStay,
                focusRequester = cancelFocusRequester,
            )
            TvDialogButton(
                text = "Exit",
                onClick = onExit,
                style = TvActionStyle.DANGER,
            )
        }
    }
}
