package com.livetube.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.livetube.tv.data.CategoryDefinition
import com.livetube.tv.data.Channel
import com.livetube.tv.data.ChannelCatalog
import kotlinx.coroutines.launch

private val GuideRed = Color(0xFFFF1F3D)
private val GuideSurface = Color(0xFF121C29)
private val GuideBorder = Color(0x33FFFFFF)
private val GuideMuted = Color(0xFF9FB1C2)
private val GuideRowSpacing = 16.dp
private val GuideEdgeInset = 26.dp

private enum class GuideRow {
    CATEGORIES,
    CHANNELS,
}

/**
 * Compact two-line TV guide rendered over the lower edge of the video.
 *
 * The guide intentionally has no permanent third navigation row. Subcategories stay
 * available through the small filter action and are applied to the channel row only.
 */
@Composable
fun ChannelGuide(
    channels: List<Channel>,
    favoriteChannelIds: Set<String>,
    selectedChannelId: String?,
    liveChannelId: String?,
    preferredCategoryId: String?,
    subcategoryFilterId: String,
    onCategoryChange: (String) -> Unit,
    onSubcategoryFilterChange: (String) -> Unit,
    onModalChanged: (Boolean) -> Unit,
    onChannelSelected: (Channel) -> Unit,
    onChannelActions: (Channel) -> Unit,
    onShowAbout: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playingChannel = channels.firstOrNull { it.id == selectedChannelId }
    val fallbackCategoryId = if (playingChannel != null && playingChannel.id in favoriteChannelIds) {
        ChannelCatalog.FAVORITES_CATEGORY_ID
    } else {
        playingChannel?.category?.let(ChannelCatalog::categoryByName)?.id
            ?: ChannelCatalog.categories.first { it.sourceBacked }.id
    }
    val initialCategoryIndex = remember(fallbackCategoryId) {
        ChannelCatalog.categories.indexOfFirst { it.id == preferredCategoryId }
            .takeIf { it >= 0 }
            ?: ChannelCatalog.categories.indexOfFirst { it.id == fallbackCategoryId }
                .coerceAtLeast(0)
    }

    var focusedCategoryIndex by remember { mutableIntStateOf(initialCategoryIndex) }
    var focusedChannelIndex by remember { mutableIntStateOf(0) }
    var focusedRow by remember { mutableStateOf(GuideRow.CATEGORIES) }
    var showSubcategoryDialog by remember { mutableStateOf(false) }
    var pendingCategoryFocus by remember { mutableStateOf<Int?>(null) }
    var pendingChannelFocus by remember { mutableStateOf<Int?>(null) }

    val selectedCategoryId = ChannelCatalog.categories[focusedCategoryIndex].id
    val availableSubcategories = remember(selectedCategoryId) {
        buildList {
            add(CategoryFilter(ChannelCatalog.ALL_SUBCATEGORY_ID, "All subcategories"))
            if (selectedCategoryId != ChannelCatalog.FAVORITES_CATEGORY_ID) {
                ChannelCatalog.subcategoriesFor(selectedCategoryId).forEach { add(CategoryFilter(it.id, it.name)) }
            }
        }
    }
    // A remembered filter from another category is meaningless; fall back to "All".
    val activeSubcategoryId = subcategoryFilterId
        .takeIf { id -> availableSubcategories.any { it.id == id } }
        ?: ChannelCatalog.ALL_SUBCATEGORY_ID
    val visibleChannels = remember(channels, selectedCategoryId, activeSubcategoryId, favoriteChannelIds) {
        ChannelCatalog.filter(
            channels = channels,
            categoryId = selectedCategoryId,
            subcategoryId = activeSubcategoryId,
            favoriteChannelIds = favoriteChannelIds,
        )
    }

    val categoryFocusRequesters = remember {
        ChannelCatalog.categories.associate { it.id to FocusRequester() }
    }
    val channelFocusRequesters = remember(visibleChannels) {
        visibleChannels.associate { it.id to FocusRequester() }
    }
    val categoryListState = rememberLazyListState()
    val channelListState = rememberLazyListState()
    val guideScope = rememberCoroutineScope()
    val categoriesCanScrollLeft by remember { derivedStateOf { categoryListState.canScrollBackward } }
    val categoriesCanScrollRight by remember { derivedStateOf { categoryListState.canScrollForward } }
    val channelsCanScrollLeft by remember { derivedStateOf { channelListState.canScrollBackward } }
    val channelsCanScrollRight by remember { derivedStateOf { channelListState.canScrollForward } }

    fun selectCategory(index: Int) {
        val clamped = index.coerceIn(0, ChannelCatalog.categories.lastIndex)
        if (clamped != focusedCategoryIndex) {
            focusedCategoryIndex = clamped
            focusedChannelIndex = 0
            guideScope.launch { channelListState.scrollToItem(0) }
            onCategoryChange(ChannelCatalog.categories[clamped].id)
        }
        onInteraction()
    }

    fun requestCategoryFocus(index: Int) {
        focusedCategoryIndex = index.coerceIn(0, ChannelCatalog.categories.lastIndex)
        focusedRow = GuideRow.CATEGORIES
        pendingCategoryFocus = focusedCategoryIndex
    }

    fun requestChannelFocus(index: Int) {
        focusedChannelIndex = index.coerceIn(0, (visibleChannels.size - 1).coerceAtLeast(0))
        focusedRow = GuideRow.CHANNELS
        pendingChannelFocus = focusedChannelIndex
    }

    // Scroll the target row so the requested item is composed before asking for focus.
    // LazyRow disposes off-screen items, so requesting focus without this dead-ends D-pad.
    LaunchedEffect(pendingCategoryFocus) {
        val index = pendingCategoryFocus ?: return@LaunchedEffect
        val category = ChannelCatalog.categories.getOrNull(index) ?: return@LaunchedEffect
        categoryListState.scrollToItem(index)
        val requester = categoryFocusRequesters.getValue(category.id)
        var attached = false
        repeat(MAX_FOCUS_ATTEMPTS) {
            if (attached) return@LaunchedEffect
            withFrameNanos { }
            attached = runCatching { requester.requestFocus() }.isSuccess
        }
        pendingCategoryFocus = null
    }

    LaunchedEffect(pendingChannelFocus) {
        val index = pendingChannelFocus ?: return@LaunchedEffect
        val channel = visibleChannels.getOrNull(index) ?: return@LaunchedEffect
        channelListState.scrollToItem(index)
        val requester = channelFocusRequesters.getValue(channel.id)
        var attached = false
        repeat(MAX_FOCUS_ATTEMPTS) {
            if (attached) return@LaunchedEffect
            withFrameNanos { }
            attached = runCatching { requester.requestFocus() }.isSuccess
        }
        pendingChannelFocus = null
    }

    LaunchedEffect(Unit) { requestCategoryFocus(initialCategoryIndex) }

    LaunchedEffect(showSubcategoryDialog) { onModalChanged(showSubcategoryDialog) }
    DisposableEffect(Unit) {
        onDispose { onModalChanged(false) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xE60A1018), Color(0xF2121C29), Color(0xFA070B12)),
                ),
            )
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 12.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> if (focusedRow == GuideRow.CHANNELS) {
                        requestCategoryFocus(focusedCategoryIndex)
                        onInteraction()
                        true
                    } else {
                        false
                    }

                    Key.DirectionDown -> if (focusedRow == GuideRow.CATEGORIES && visibleChannels.isNotEmpty()) {
                        requestChannelFocus(focusedChannelIndex)
                        onInteraction()
                        true
                    } else {
                        false
                    }

                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = categoryListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentPadding = PaddingValues(horizontal = GuideEdgeInset),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = ChannelCatalog.categories,
                    key = { _, category -> category.id },
                ) { index, category ->
                    CategoryChip(
                        category = category,
                        selected = index == focusedCategoryIndex,
                        focusRequester = categoryFocusRequesters.getValue(category.id),
                        onFocused = {
                            focusedCategoryIndex = index
                            focusedRow = GuideRow.CATEGORIES
                            selectCategory(index)
                        },
                        onClick = { selectCategory(index) },
                    )
                }
                item(key = "guide-utilities") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (availableSubcategories.size > 1) {
                            val filterActive = activeSubcategoryId != ChannelCatalog.ALL_SUBCATEGORY_ID
                            val filterName = availableSubcategories
                                .firstOrNull { it.id == activeSubcategoryId }
                                ?.name
                                .orEmpty()
                            GuideUtilityButton(
                                icon = Icons.Outlined.Tune,
                                contentDescription = if (filterActive) {
                                    "Subcategory filter active: $filterName"
                                } else {
                                    "Filter subcategory"
                                },
                                active = filterActive,
                                onClick = {
                                    onInteraction()
                                    showSubcategoryDialog = true
                                },
                                onFocused = {
                                    focusedRow = GuideRow.CATEGORIES
                                    onInteraction()
                                },
                            )
                        }
                        GuideUtilityButton(
                            icon = Icons.Outlined.Info,
                            contentDescription = "About LiveTube TV",
                            active = false,
                            onClick = {
                                onInteraction()
                                onShowAbout()
                            },
                            onFocused = {
                                focusedRow = GuideRow.CATEGORIES
                                onInteraction()
                            },
                        )
                    }
                }
            }
            HorizontalScrollIndicator(
                icon = Icons.Outlined.ChevronLeft,
                visible = categoriesCanScrollLeft,
                modifier = Modifier.align(Alignment.CenterStart),
                alignToStart = true,
            )
            HorizontalScrollIndicator(
                icon = Icons.Outlined.ChevronRight,
                visible = categoriesCanScrollRight,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
        ) {
            if (visibleChannels.isEmpty()) {
                Text(
                    text = if (selectedCategoryId == ChannelCatalog.FAVORITES_CATEGORY_ID) {
                        "No favorites yet. Focus a channel and hold OK to add it."
                    } else {
                        "No channels in this category"
                    },
                    color = GuideMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyRow(
                    state = channelListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = GuideEdgeInset,
                        end = GuideEdgeInset,
                        top = 6.dp,
                        bottom = 6.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(GuideRowSpacing),
                ) {
                    itemsIndexed(
                        items = visibleChannels,
                        key = { _, channel -> channel.id },
                    ) { index, channel ->
                        ChannelCard(
                            channel = channel,
                            selected = channel.id == selectedChannelId,
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
                                focusedChannelIndex = index
                                focusedRow = GuideRow.CHANNELS
                                onInteraction()
                            },
                            focusRequester = channelFocusRequesters.getValue(channel.id),
                            modifier = Modifier.width(190.dp),
                        )
                    }
                }
            }
            HorizontalScrollIndicator(
                icon = Icons.Outlined.ChevronLeft,
                visible = visibleChannels.size > 1 && channelsCanScrollLeft,
                modifier = Modifier.align(Alignment.CenterStart),
                alignToStart = true,
            )
            HorizontalScrollIndicator(
                icon = Icons.Outlined.ChevronRight,
                visible = visibleChannels.size > 1 && channelsCanScrollRight,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }

    if (showSubcategoryDialog) {
        SubcategoryFilterDialog(
            filters = availableSubcategories,
            selectedId = activeSubcategoryId,
            onSelect = { subcategoryId ->
                onSubcategoryFilterChange(subcategoryId)
                showSubcategoryDialog = false
                onInteraction()
            },
            onFocusChanged = onInteraction,
            onDismiss = {
                showSubcategoryDialog = false
                onInteraction()
            },
        )
    }
}

private const val MAX_FOCUS_ATTEMPTS = 5

@Composable
private fun CategoryChip(
    category: CategoryDefinition,
    selected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember(category.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(120),
        label = "categoryFocus",
    )
    Surface(
        modifier = Modifier
            .height(44.dp)
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = when {
            selected -> GuideRed.copy(alpha = 0.95f)
            focused -> GuideSurface
            else -> Color(0xB3121C29)
        },
        contentColor = if (selected) Color.White else Color(0xFFE8EEF5),
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) Color.White else if (selected) GuideRed else GuideBorder,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = categoryIcon(category.id),
                contentDescription = null,
                tint = if (selected) Color.White else GuideRed,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GuideUtilityButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                focused -> Color.White
                active -> GuideRed
                else -> GuideMuted
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun BoxScope.HorizontalScrollIndicator(
    icon: ImageVector,
    visible: Boolean,
    modifier: Modifier = Modifier,
    alignToStart: Boolean = false,
) {
    if (!visible) return
    val scrim = if (alignToStart) {
        Brush.horizontalGradient(listOf(Color.Transparent, Color(0xF2070B12)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xF2070B12), Color.Transparent))
    }
    // matchParentSize keeps the scrim out of the guide's height calculation.
    Box(
        modifier = modifier
            .matchParentSize()
            .background(scrim),
        contentAlignment = if (alignToStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier
                .padding(horizontal = 7.dp)
                .size(24.dp),
        )
    }
}

@Composable
private fun SubcategoryFilterDialog(
    filters: List<CategoryFilter>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onFocusChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocusRequester = rememberDialogFocusRequester()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 480.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF121C29),
            border = BorderStroke(1.dp, GuideBorder),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Filter channels",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "Choose a subcategory for the channel row.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GuideMuted,
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    filters.forEachIndexed { index, filter ->
                        val selected = filter.id == selectedId
                        var focused by remember(filter.id) { mutableStateOf(false) }
                        val scale by animateFloatAsState(
                            targetValue = if (focused) 1.02f else 1f,
                            animationSpec = tween(120),
                            label = "filterFocus",
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .scale(scale)
                                .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier)
                                .onFocusChanged {
                                    focused = it.isFocused
                                    if (it.isFocused) onFocusChanged()
                                }
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) GuideRed.copy(alpha = 0.92f) else Color(0x661A2836))
                                .then(
                                    if (focused) {
                                        Modifier.border(BorderStroke(2.dp, Color.White), RoundedCornerShape(9.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(role = Role.Button) { onSelect(filter.id) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = filter.name,
                                color = if (selected) Color.White else Color(0xFFE8EEF5),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

private fun categoryIcon(categoryId: String): ImageVector = when (categoryId) {
    ChannelCatalog.FAVORITES_CATEGORY_ID -> Icons.Outlined.Star
    "news" -> Icons.AutoMirrored.Outlined.Article
    "regional" -> Icons.Outlined.Public
    "devotional" -> Icons.Outlined.Spa
    "kids_family" -> Icons.Outlined.ChildCare
    "knowledge" -> Icons.Outlined.Science
    "music_entertainment" -> Icons.Outlined.MusicNote
    "sports_live" -> Icons.Outlined.SportsSoccer
    else -> Icons.AutoMirrored.Outlined.Article
}

private data class CategoryFilter(val id: String, val name: String)
