package com.livetube.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.livetube.tv.R
import com.livetube.tv.data.Channel

/**
 * Compact channel card used by the guide's channel row.
 *
 * The card carries only what a viewer needs to recognise a channel: its logo, its name and, when
 * relevant, a small live or favorite marker. OK starts playback; holding OK opens the channel
 * actions, which is also how a channel is added to favorites.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(
    channel: Channel,
    selected: Boolean,
    favorite: Boolean,
    isLive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember(channel.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(110),
        label = "channelFocus",
    )
    val shape = RoundedCornerShape(TvMetrics.CornerMedium)
    val description = buildString {
        append(channel.name)
        if (favorite) append(", favorite")
        if (isLive) append(", live")
        if (selected) append(", now playing")
        append(", hold OK for channel actions")
    }

    Column(
        modifier = modifier
            .height(TvMetrics.CardHeight)
            .scale(scale)
            .clip(shape)
            .background(if (focused) TvPalette.CardFocused else TvPalette.Card)
            .border(
                width = 1.dp,
                color = when {
                    focused -> TvPalette.FocusRing
                    selected -> TvPalette.Red
                    else -> TvPalette.Border
                },
                shape = shape,
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                val isFocused = it.isFocused
                if (isFocused == focused) return@onFocusChanged
                focused = isFocused
                if (isFocused) onFocus()
            }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .combinedClickable(
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(TvMetrics.CardLogo),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(TvMetrics.CornerSmall))
                    .background(TvPalette.SurfaceStrong),
                contentScale = ContentScale.Fit,
                // A clean placeholder keeps the row aligned when a channel has no usable logo.
                error = painterResource(R.drawable.livetube_icon),
                placeholder = painterResource(R.drawable.livetube_icon),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.SemiBold,
                color = TvPalette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (favorite) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = TvPalette.Red,
                        modifier = Modifier.size(11.dp),
                    )
                }
                when {
                    isLive -> LiveBadge()
                    selected -> Text(
                        text = "PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = TvPalette.RedBright,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )

                    else -> Text(
                        text = channel.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = TvPalette.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Subtle live indicator shared by the guide and the playback overlay. */
@Composable
internal fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TvPalette.Red),
        )
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            color = TvPalette.RedBright,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
