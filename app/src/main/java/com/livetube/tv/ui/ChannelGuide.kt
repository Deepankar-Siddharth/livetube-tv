package com.livetube.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.livetube.tv.data.Channel
import com.livetube.tv.data.GuideDirectory
import com.livetube.tv.data.GuideDirectoryFactory
import com.livetube.tv.data.GuideNode



private const val MAX_FOCUS_ATTEMPTS = 6

/**
 * Three-row TV guide rendered over the lower edge of the video.
 *
 * Language, category and channels all come from [directory], which the repository derives from the
 * active channel document, so the guide never carries its own channel list and a newer catalogue
 * can add languages or categories without a code change.
 *
 * Opening the guide is a pure read: it resolves the playing channel into a language, a category and
 * a card, scrolls that card into view and focuses it. Playback is only touched when the viewer
 * presses OK on a channel, so LEFT and RIGHT can be used to browse freely.
 */
@Composable
fun ChannelGuide(
    directory: GuideDirectory,
    favoriteChannelIds: Set<String>,
    favoriteChannels: List<Channel>,
    playingChannelId: String?,
    liveChannelId: String?,
    showFavorites: Boolean,
    preferredLanguage: String?,
    preferredCategory: String?,
    onLanguageChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onFavoritesChange: (Boolean) -> Unit,
    onShowSettings: () -> Unit,
    onChannelSelected: (Channel) -> Unit,
    onChannelActions: (Channel) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val languageState = rememberLazyListState()
    val categoryState = rememberLazyListState()
    val channelState = rememberLazyListState()

    // Opening position: the playing channel wins, the remembered position is only a fallback for a
    // device that is not playing anything yet.
    val openingTarget = remember(directory, playingChannelId, preferredLanguage, preferredCategory) {
        GuideDirectoryFactory.resolve(
            directory = directory,
            playingChannelId = playingChannelId,
            preferredLanguage = preferredLanguage,
            preferredCategory = preferredCategory,
        )
    }

    var currentLanguage by remember { mutableStateOf(openingTarget.language) }
    var currentCategory by remember { mutableStateOf(openingTarget.category) }
    var languageIndex by remember {
        mutableStateOf(
            directory.languageNames().indexOf(openingTarget.language).coerceAtLeast(0),
        )
    }
    var categoryIndex by remember {
        mutableStateOf(
            directory.categoryNames(openingTarget.language).indexOf(openingTarget.category)
                .coerceAtLeast(0),
        )
    }
    var channelIndex by remember { mutableStateOf(openingTarget.channelIndex) }

    val languages = directory.languageNodes
    val categories = directory.categoriesFor(currentLanguage)
    val visibleChannels = if (showFavorites) {
        favoriteChannels
    } else {
        directory.channelsFor(currentLanguage, currentCategory)
    }

    fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /** Applies a language change: categories are rebuilt and a fitting category is selected. */
    fun applyLanguage(languageName: String) {
        if (languageName == currentLanguage) return
        currentLanguage = languageName
        val category = GuideDirectoryFactory.categoryAfterLanguageChange(
            directory = directory,
            languageName = languageName,
            preferredCategory = currentCategory,
        )
        currentCategory = category
        categoryIndex = directory.categoryNames(languageName).indexOf(category).coerceAtLeast(0)
        // Keep the playing channel selected when it lives in the new language and category.
        val channels = directory.channelsFor(languageName, category)
        channelIndex = channels.indexOfFirst { it.id == playingChannelId }.takeIf { it >= 0 } ?: 0
        onLanguageChange(languageName)
        onCategoryChange(category)
    }

    /** Applies a category change: only the channel row follows, playback is untouched. */
    fun applyCategory(categoryName: String) {
        if (categoryName == currentCategory) return
        currentCategory = categoryName
        val channels = directory.channelsFor(currentLanguage, categoryName)
        channelIndex = channels.indexOfFirst { it.id == playingChannelId }.takeIf { it >= 0 } ?: 0
        onCategoryChange(categoryName)
    }

    /**
     * Focuses one item of a row, retrying over a few frames while the item is attached.
     *
     * A LazyRow disposes items that scroll out of view, and a request aimed at a disposed node is
     * dropped, so the caller scrolls first and the request is simply retried. This is only used
     * for the initial focus on the playing channel: every later D-pad move is handled by Compose
     * focus search, which moves between the rows with UP and DOWN and along a row with LEFT and
     * RIGHT.
     */
    suspend fun requestFocusNow(key: String) {
        repeat(MAX_FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { requesterFor(key).requestFocus() }.isSuccess) return
        }
    }
    // On open the playing channel is focused; nothing is selected and playback is untouched.
    LaunchedEffect(Unit) {
        val index = if (showFavorites && favoriteChannels.isNotEmpty()) {
            favoriteChannels.indexOfFirst { it.id == playingChannelId }.takeIf { it >= 0 } ?: 0
        } else {
            openingTarget.channelIndex
        }
        val channel = visibleChannels.getOrNull(index) ?: return@LaunchedEffect
        channelIndex = index
        val key = channelKey(channel)
        channelState.scrollToItem(index)
        requestFocusNow(key)
    }

    // The catalogue can change under an open guide; keep the focus index inside the new list.
    LaunchedEffect(visibleChannels) {
        if (visibleChannels.isEmpty()) return@LaunchedEffect
        if (channelIndex !in visibleChannels.indices) channelIndex = 0
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF20A0C10), Color(0xFA0A0C10), Color(0xFF07090C)),
                ),
            )
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(TvMetrics.GuideRowSpacing),
    ) {
        GuideHeader(
            showFavorites = showFavorites,
            favoritesEnabled = favoriteChannelIds.isNotEmpty(),
            onToggleFavorites = {
                onInteraction()
                onFavoritesChange(!showFavorites)
            },
            onShowSettings = {
                onInteraction()
                onShowSettings()
            },
        )

        NodeRow(
            label = "Language",
            nodes = languages,
            selectedName = currentLanguage,
            listState = languageState,
            requesters = requesters,
            onIndexFocused = { index ->
                val node = languages.getOrNull(index) ?: return@NodeRow
                languageIndex = index
                // Browsing the catalogue again leaves the local favorites view.
                if (showFavorites) onFavoritesChange(false)
                applyLanguage(node.name)
                onInteraction()
            },
            onSelected = { node ->
                if (showFavorites) onFavoritesChange(false)
                applyLanguage(node.name)
                onInteraction()
            },
            keyPrefix = LANGUAGE_PREFIX,
        )

        NodeRow(
            label = "Category",
            nodes = categories,
            selectedName = currentCategory,
            listState = categoryState,
            requesters = requesters,
            onIndexFocused = { index ->
                val node = categories.getOrNull(index) ?: return@NodeRow
                categoryIndex = index
                if (showFavorites) onFavoritesChange(false)
                applyCategory(node.name)
                onInteraction()
            },
            onSelected = { node ->
                if (showFavorites) onFavoritesChange(false)
                applyCategory(node.name)
                onInteraction()
            },
            keyPrefix = CATEGORY_PREFIX,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TvMetrics.CardHeight + 8.dp),
        ) {
            if (visibleChannels.isEmpty()) {
                Text(
                    text = if (showFavorites) {
                        "No favorites yet. Hold OK on a channel to add it."
                    } else {
                        "No channels available"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TvPalette.TextMuted,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyRow(
                    state = channelState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        count = visibleChannels.size,
                        key = { index -> channelKey(visibleChannels[index]) },
                    ) { index ->
                        val channel = visibleChannels[index]
                        ChannelCard(
                            channel = channel,
                            selected = channel.id == playingChannelId,
                            favorite = channel.id in favoriteChannelIds,
                            isLive = channel.id == liveChannelId,
                            onClick = {
                                onInteraction()
                                onChannelSelected(channel)
                            },
                            onLongClick = {
                                onInteraction()
                                onChannelActions(channel)
                            },
                            onFocus = {
                                channelIndex = index
                                onInteraction()
                            },
                            focusRequester = requesterFor(channelKey(channel)),
                            modifier = Modifier.width(TvMetrics.CardWidth),
                        )
                    }
                }
            }
            val canScrollLeft by remember { derivedStateOf { channelState.canScrollBackward } }
            val canScrollRight by remember { derivedStateOf { channelState.canScrollForward } }
            ScrollEdge(
                icon = Icons.Outlined.ChevronLeft,
                visible = visibleChannels.size > 1 && canScrollLeft,
                modifier = Modifier.align(Alignment.CenterStart),
                atStart = true,
            )
            ScrollEdge(
                icon = Icons.Outlined.ChevronRight,
                visible = visibleChannels.size > 1 && canScrollRight,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

private const val LANGUAGE_PREFIX = "language:"
private const val CATEGORY_PREFIX = "category:"
private const val CHANNEL_PREFIX = "channel:"

private fun languageKey(node: GuideNode): String = LANGUAGE_PREFIX + node.name

private fun categoryKey(node: GuideNode): String = CATEGORY_PREFIX + node.name

private fun channelKey(channel: Channel): String = CHANNEL_PREFIX + channel.id

/** Slim guide header with the local favorites view and the settings entry. */
@Composable
private fun GuideHeader(
    showFavorites: Boolean,
    favoritesEnabled: Boolean,
    onToggleFavorites: () -> Unit,
    onShowSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (showFavorites) "Favorites" else "Guide",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = TvPalette.TextSecondary,
        )
        Spacer(Modifier.weight(1f))
        FavoritesToggle(
            active = showFavorites,
            enabled = favoritesEnabled,
            onClick = onToggleFavorites,
        )
        Spacer(Modifier.width(8.dp))
        GuideIconAction(
            icon = Icons.Outlined.Settings,
            contentDescription = "Settings",
            onClick = onShowSettings,
        )
    }
}

@Composable
private fun FavoritesToggle(
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvMetrics.CornerSmall)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (active) TvPalette.RedWash else Color.Transparent)
            .border(
                1.dp,
                when {
                    focused -> TvPalette.FocusRing
                    active -> TvPalette.Red
                    enabled -> TvPalette.Border
                    else -> Color.Transparent
                },
                shape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = when {
                !enabled -> TvPalette.TextMuted
                active -> TvPalette.Red
                else -> TvPalette.TextSecondary
            },
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = "Favorites",
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> TvPalette.TextMuted
                active -> TvPalette.TextPrimary
                else -> TvPalette.TextSecondary
            },
        )
    }
}

@Composable
private fun GuideIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvMetrics.CornerSmall)
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(shape)
            .background(if (focused) TvPalette.CardFocused else Color.Transparent)
            .border(1.dp, if (focused) TvPalette.FocusRing else TvPalette.Border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) TvPalette.TextPrimary else TvPalette.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * One compact navigation row of languages or categories.
 *
 * Items are text only: the selected item is underlined with the LiveTube accent and the focused
 * item gains a hairline outline, which keeps these rows visually lighter than the channel cards.
 */
@Composable
private fun NodeRow(
    label: String,
    nodes: List<GuideNode>,
    selectedName: String,
    listState: LazyListState,
    requesters: MutableMap<String, FocusRequester>,
    onIndexFocused: (Int) -> Unit,
    onSelected: (GuideNode) -> Unit,
    keyPrefix: String,
) {
    val canScrollLeft by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollRight by remember { derivedStateOf { listState.canScrollForward } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TvPalette.TextMuted,
            modifier = Modifier.width(64.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (nodes.isEmpty()) {
                Text(
                    text = "None available",
                    style = MaterialTheme.typography.labelMedium,
                    color = TvPalette.TextMuted,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            } else {
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TvMetrics.GuideRowHeight),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(count = nodes.size, key = { index -> keyPrefix + nodes[index].name }) { index ->
                        val node = nodes[index]
                        NodeChip(
                            text = node.name,
                            selected = node.name == selectedName,
                            focusRequester = requesters.getOrPut(keyPrefix + node.name) {
                                FocusRequester()
                            },
                            onFocused = { onIndexFocused(index) },
                            onClick = { onSelected(node) },
                        )
                    }
                }
            }
            ScrollEdge(
                icon = Icons.Outlined.ChevronLeft,
                visible = canScrollLeft,
                modifier = Modifier.align(Alignment.CenterStart),
                atStart = true,
            )
            ScrollEdge(
                icon = Icons.Outlined.ChevronRight,
                visible = canScrollRight,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun NodeChip(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var isFocused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(110),
        label = "nodeChipFocus",
    )
    val shape = RoundedCornerShape(TvMetrics.CornerSmall)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(if (isFocused) TvPalette.CardFocused else Color.Transparent)
            .border(
                if (isFocused) 1.5.dp else 1.dp,
                when {
                    isFocused -> TvPalette.FocusRing
                    else -> Color.Transparent
                },
                shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                val value = it.isFocused
                if (value == isFocused) return@onFocusChanged
                isFocused = value
                if (value) onFocused()
            }
            .semantics(mergeDescendants = true) {
                contentDescription = if (selected) "$text, selected" else text
            }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) TvPalette.TextPrimary else TvPalette.TextSecondary,
            maxLines = 1,
        )
        // The accent underline marks the selected language or category.
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(if (selected) 16.dp else 0.dp)
                .height(2.dp)
                .background(if (selected) TvPalette.Red else Color.Transparent),
        )
    }
}

@Composable
private fun BoxScope.ScrollEdge(
    icon: ImageVector,
    visible: Boolean,
    atStart: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val scrim = if (atStart) {
        Brush.horizontalGradient(listOf(Color.Transparent, Color(0xF20A0C10)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xF20A0C10), Color.Transparent))
    }
    Box(
        modifier = modifier
            .matchParentSize()
            .background(scrim),
        contentAlignment = if (atStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TvPalette.TextSecondary,
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(18.dp),
        )
    }
}
