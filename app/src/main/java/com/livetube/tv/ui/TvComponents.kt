package com.livetube.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Shared dialog shell.
 *
 * Every dialog in the app uses this so they share one compact size, one spacing scale and one
 * focus treatment: a hairline border, a small lift and a bright outline instead of a large
 * system-coloured selection box.
 */
@Composable
internal fun TvDialog(
    title: String,
    subtitle: String? = null,
    onDismissRequest: () -> Unit,
    maxWidth: androidx.compose.ui.unit.Dp = TvMetrics.DialogMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth(0.72f),
                shape = RoundedCornerShape(TvMetrics.CornerLarge),
                color = TvPalette.Surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, TvPalette.Border),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TvPalette.TextPrimary,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TvPalette.TextSecondary,
                        )
                    }
                    Spacer(Modifier.padding(top = 6.dp))
                    content()
                }
            }
        }
    }
}

/** Visual weight of a dialog action. */
internal enum class TvActionStyle { PRIMARY, NEUTRAL, DANGER }

/**
 * Dialog action with an obvious but restrained focus state.
 *
 * Focused actions lift slightly, brighten their label and gain a hairline outline. The primary
 * action additionally carries the LiveTube accent, so the default action is obvious without a
 * bright filled block.
 */
@Composable
internal fun TvDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TvActionStyle = TvActionStyle.NEUTRAL,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    var focused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = tween(110),
        label = "dialogButtonFocus",
    )
    val shape = RoundedCornerShape(TvMetrics.CornerMedium)
    val background = when {
        !enabled -> TvPalette.Card
        focused && style == TvActionStyle.PRIMARY -> TvPalette.Red
        focused && style == TvActionStyle.DANGER -> Color(0x33FF6B6B)
        focused -> TvPalette.CardFocused
        style == TvActionStyle.PRIMARY -> TvPalette.RedWash
        else -> Color.Transparent
    }
    val label = when {
        !enabled -> TvPalette.TextMuted
        focused -> Color.White
        style == TvActionStyle.PRIMARY -> TvPalette.RedBright
        style == TvActionStyle.DANGER -> TvPalette.Danger
        else -> TvPalette.TextSecondary
    }
    val outline = when {
        focused -> TvPalette.FocusRing
        style == TvActionStyle.PRIMARY -> TvPalette.Red
        style == TvActionStyle.DANGER -> Color(0x40FF6B6B)
        else -> TvPalette.Border
    }

    Box(
        modifier = modifier
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                val isFocused = it.isFocused
                if (isFocused == focused) return@onFocusChanged
                focused = isFocused
                if (isFocused) onFocused()
            }
            .clip(shape)
            .background(background)
            .border(1.dp, outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
            color = label,
            textAlign = TextAlign.Center,
        )
    }
}

/** Right-aligned dialog action row with consistent spacing. */
@Composable
internal fun TvDialogActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}


