package com.livetube.tv.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ExitDialog(onExit: () -> Unit, onStay: () -> Unit) {
    val stayFocusRequester = rememberDialogFocusRequester()
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text("EXIT LIVETUBE TV?", fontWeight = FontWeight.Bold) },
        text = { Text("You can return to the live guide at any time.") },
        confirmButton = { Button(onClick = onExit) { Text("EXIT") } },
        dismissButton = {
            OutlinedButton(
                onClick = onStay,
                modifier = Modifier.focusRequester(stayFocusRequester),
            ) { Text("STAY") }
        },
    )
}
