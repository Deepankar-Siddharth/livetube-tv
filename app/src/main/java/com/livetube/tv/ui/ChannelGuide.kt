package com.livetube.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.livetube.tv.data.CategoryDefinition
import com.livetube.tv.data.Channel
import com.livetube.tv.data.ChannelCatalog

private val GuideRed = Color(0xFFFF1F3D)
private val GuideSurface = Color(0xFF111A25)
private val GuideBorder = Color(0x33FFFFFF)

@Composable
fun ChannelGuide(
    channels: List<Channel>,
    favoriteChannelIds: Set<String>,
    selectedChannelId: String?,
    liveChannelId: String?,
    onChannelSelected: (Channel) -> Unit,
    onInteraction: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playingChannel = channels.firstOrNull { it.id == selectedChannelId }
    val initialCategoryId = if (playingChannel != null && playingChannel.id in favoriteChannelIds) {
        ChannelCatalog.FAVORITES_CATEGORY_ID
    } else {
        playingChannel?.category?.let(ChannelCatalog::categoryByName)?.id
            ?: ChannelCatalog.categories.first { it.sourceBacked }.id
    }
    var selectedCategoryId by remember(channels) { mutableStateOf(initialCategoryId) }
    var focusedCategoryId by remember(channels) { mutableStateOf(initialCategoryId) }
    var selectedSubcategoryId by remember { mutableStateOf(ChannelCatalog.ALL_SUBCATEGORY_ID) }
    var focusedSubcategoryId by remember { mutableStateOf(ChannelCatalog.ALL_SUBCATEGORY_ID) }

    val availableSubcategories = remember(selectedCategoryId) {
        if (selectedCategoryId == ChannelCatalog.FAVORITES_CATEGORY_ID) {
            listOf(CategoryFilter(ChannelCatalog.ALL_SUBCATEGORY_ID, "All"))
        } else {
            listOf(CategoryFilter(ChannelCatalog.ALL_SUBCATEGORY_ID, "All")) +
                ChannelCatalog.subcategoriesFor(selectedCategoryId).map {
                    CategoryFilter(it.id, it.name)
                }
        }
    }
    val visibleChannels = remember(channels, selectedCategoryId, selectedSubcategoryId, favoriteChannelIds) {
        ChannelCatalog.filter(
            channels = channels,
            categoryId = selectedCategoryId,
            subcategoryId = selectedSubcategoryId,
            favoriteChannelIds = favoriteChannelIds,
        )
    }
    val selectedCategory = ChannelCatalog.category(selectedCategoryId) ?: ChannelCatalog.categories.first()

    val categoryFocusRequesters = remember(ChannelCatalog.categories) {
        ChannelCatalog.categories.associate { it.id to FocusRequester() }
    }
    val subcategoryFocusRequesters = remember(selectedCategoryId) {
        availableSubcategories.associate { it.id to FocusRequester() }
    }
    val firstChannelFocusRequester = remember(selectedCategoryId, selectedSubcategoryId) {
        FocusRequester()
    }

    fun selectCategory(categoryId: String) {
        selectedCategoryId = categoryId
        focusedCategoryId = categoryId
        selectedSubcategoryId = ChannelCatalog.ALL_SUBCATEGORY_ID
        focusedSubcategoryId = ChannelCatalog.ALL_SUBCATEGORY_ID
        onInteraction()
    }

    fun selectSubcategory(subcategoryId: String) {
        selectedSubcategoryId = subcategoryId
        focusedSubcategoryId = subcategoryId
        onInteraction()
    }

    LaunchedEffect(Unit) {
        runCatching { categoryFocusRequesters.getValue(initialCategoryId).requestFocus() }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xD9070B12), Color(0xE6070B12), Color(0xF2111A25)),
                ),
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                onInteraction()
                if (event.key == Key.Back) {
                    onClose()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CategorySidebar(
            categories = ChannelCatalog.categories,
            selectedCategoryId = selectedCategoryId,
            focusedCategoryId = focusedCategoryId,
            focusRequesters = categoryFocusRequesters,
            onFocused = { categoryId ->
                focusedCategoryId = categoryId
                selectCategory(categoryId)
            },
            onSelected = { categoryId -> selectCategory(categoryId) },
            onMoveRight = { categoryId ->
                selectCategory(categoryId)
                runCatching { subcategoryFocusRequesters.getValue(ChannelCatalog.ALL_SUBCATEGORY_ID).requestFocus() }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedCategory.name.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${visibleChannels.size} channel${if (visibleChannels.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedCategory.sourceBacked) Color(0xFF9EDFFF) else GuideRed,
                )
            }

            SubcategoryRow(
                filters = availableSubcategories,
                selectedId = selectedSubcategoryId,
                focusedId = focusedSubcategoryId,
                focusRequesters = subcategoryFocusRequesters,
                onFocused = { subcategoryId ->
                    focusedSubcategoryId = subcategoryId
                    selectSubcategory(subcategoryId)
                },
                onSelected = ::selectSubcategory,
                onMoveLeft = {
                    runCatching { categoryFocusRequesters.getValue(focusedCategoryId).requestFocus() }
                },
                onMoveRight = {
                    if (visibleChannels.isNotEmpty()) {
                        runCatching { firstChannelFocusRequester.requestFocus() }
                    }
                },
            )

            if (visibleChannels.isEmpty()) {
                GuideEmptyState(
                    favorites = selectedCategoryId == ChannelCatalog.FAVORITES_CATEGORY_ID,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 196.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                runCatching {
                                    subcategoryFocusRequesters.getValue(focusedSubcategoryId).requestFocus()
                                }
                                true
                            } else {
                                false
                            }
                        },
                    contentPadding = PaddingValues(top = 12.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visibleChannels, key = Channel::id) { channel ->
                        ChannelCard(
                            channel = channel,
                            selected = channel.id == selectedChannelId,
                            favorite = channel.id in favoriteChannelIds,
                            isLive = channel.id == liveChannelId,
                            onClick = {
                                onInteraction()
                                onChannelSelected(channel)
                            },
                            onFocus = onInteraction,
                            focusRequester = if (channel == visibleChannels.firstOrNull()) {
                                firstChannelFocusRequester
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySidebar(
    categories: List<CategoryDefinition>,
    selectedCategoryId: String,
    focusedCategoryId: String,
    focusRequesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onSelected: (String) -> Unit,
    onMoveRight: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .width(210.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(categories, key = CategoryDefinition::id) { category ->
            var focused by remember(category.id) { mutableStateOf(category.id == focusedCategoryId) }
            val selected = category.id == selectedCategoryId
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .then(
                        if (category.id == focusedCategoryId) {
                            Modifier.focusRequester(focusRequesters.getValue(category.id))
                        } else {
                            Modifier
                        },
                    )
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onFocused(category.id)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                            onMoveRight(category.id)
                            true
                        } else {
                            false
                        }
                    }
                    .clickable(role = Role.Button) { onSelected(category.id) },
                shape = RoundedCornerShape(9.dp),
                color = when {
                    selected -> GuideRed.copy(alpha = 0.92f)
                    focused -> GuideSurface
                    else -> Color(0xB3111A25)
                },
                contentColor = if (selected) Color.White else Color(0xFFE8EEF5),
                border = BorderStroke(
                    if (focused) 2.dp else 1.dp,
                    when {
                        selected && focused -> Color.White
                        selected -> GuideRed
                        focused -> GuideRed
                        else -> GuideBorder
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = categoryIcon(category.id),
                        contentDescription = null,
                        tint = if (selected) Color.White else GuideRed,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubcategoryRow(
    filters: List<CategoryFilter>,
    selectedId: String,
    focusedId: String,
    focusRequesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onSelected: (String) -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionUp -> {
                        onMoveLeft()
                        true
                    }

                    Key.DirectionRight, Key.DirectionDown -> {
                        onMoveRight()
                        true
                    }

                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 3.dp),
    ) {
        items(filters, key = CategoryFilter::id) { filter ->
            var focused by remember(filter.id) { mutableStateOf(filter.id == focusedId) }
            val selected = filter.id == selectedId
            Surface(
                modifier = Modifier
                    .then(
                        if (filter.id == focusedId) {
                            Modifier.focusRequester(focusRequesters.getValue(filter.id))
                        } else {
                            Modifier
                        },
                    )
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onFocused(filter.id)
                    }
                    .clickable(role = Role.Button) { onSelected(filter.id) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) GuideRed else Color(0xFF1A2836),
                contentColor = if (selected) Color.White else Color(0xFFE8EEF5),
                border = BorderStroke(
                    if (focused) 2.dp else 1.dp,
                    if (focused) Color.White else GuideBorder,
                ),
            ) {
                Text(
                    text = filter.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GuideEmptyState(favorites: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = if (favorites) Icons.Outlined.FavoriteBorder else Icons.AutoMirrored.Outlined.Article,
                contentDescription = null,
                tint = GuideRed,
                modifier = Modifier.size(46.dp),
            )
            Text(
                text = if (favorites) "No favorite channels yet" else "No channels available",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = if (favorites) {
                    "Press OK on a channel and choose Add to Favorites."
                } else {
                    "Try another category or refresh channel data."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB8C8D8),
            )
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
