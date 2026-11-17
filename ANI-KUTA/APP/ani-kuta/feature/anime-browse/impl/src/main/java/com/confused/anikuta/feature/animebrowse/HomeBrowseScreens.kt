package com.confused.anikuta.feature.animebrowse

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.anilist.model.AniListMediaPage
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.theme.DisplayFamily // ROUND 152: the handoff's display face
import com.confused.anikuta.core.designsystem.theme.LocalCardDescriptionColor
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.HomePreferences
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

/**
 * ROUND 152 — the home's browse destinations (the handoff §4 screens):
 * [HomeGenresScreen] ("All genres": the 19 AniList genres, Hentai pinned
 * last, + the 30 aggregated popular tags), [HomeGenreScreen] (one category's
 * title grid, 24-per-page infinite pagination) and [HomeCatalogScreen] (the
 * same grid driven by a fixed sort constant — the trending rail's "See all").
 *
 * Visual source: components/GenresScreen.tsx + GenreScreen.tsx + BrowseHead —
 * the 44dp back circle + the 22sp/800 title + the uppercase meta line, the
 * 3-column grids, the 44dp genre tiles (12dp radius — the tile radius of the
 * one shape scale), skeletons mirroring the layout, retry on failure.
 */
private const val CATEGORY_PER_PAGE = 24

/** The poster grid's scroll-approach margin — load the next page this many items before the end. */
private const val LOAD_MORE_APPROACH = 6

// ── Shared chrome (BrowseHead.tsx) ───────────────────────────────────────────

/**
 * The browse head: the 44dp round back button (outline border, surface
 * container bg) + the 22sp/800 title + the uppercase tracked meta line.
 */
@Composable
private fun HomeBrowseHead(
    title: String,
    meta: String?,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                    CircleShape,
                )
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                // ROUND 152: the CSS .browse-title is a font-display spot —
                // Bricolage ExtraBold.
                fontFamily = DisplayFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp,
                lineHeight = 25.sp,
                color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                    ?: MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                Text(
                    text = meta.uppercase(Locale.US),
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.55.sp,
                    color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** The browse-group label (globals.css .browse-group — 11px/800 uppercase). */
@Composable
private fun HomeBrowseGroup(title: String, isFirst: Boolean) {
    Text(
        text = title.uppercase(Locale.US),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.88.sp,
        color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified }
            ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = if (isFirst) 8.dp else 18.dp, bottom = 10.dp),
    )
}

/** The skeleton pulse (the BrowseSkeleton idiom — alpha only, theme-adaptive). */
@Composable
private fun skeletonPulse(): Float {
    val transition = rememberInfiniteTransition(label = "homeBrowseSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = Motion.EasingStandard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "homeBrowseSkeletonAlpha",
    )
    return pulse
}

/** The soft error row with its Retry action (the design's screen-error). */
@Composable
private fun HomeBrowseErrorRow(message: String, retryLabel: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = retryLabel,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.36.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .defaultMinSize(minHeight = 44.dp)
                .clickable(onClick = onRetry)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

// ── The category index ("All genres") ────────────────────────────────────────

/** HomeGenresScreen's load state (the design's loading/error/done trio). */
sealed interface HomeGenresUiState {
    data object Loading : HomeGenresUiState
    data class Error(val message: String) : HomeGenresUiState
    data class Content(val genres: List<String>, val tags: List<String>) : HomeGenresUiState
}

/**
 * The "All genres" index ViewModel. ROUND 152 (the "couldn't reach AniList"
 * report): the GENRES are a fixed 19-name AniList set (served instantly,
 * never fetched) and the TAGS cache PERSISTENTLY — the first successful
 * [AniListApi.fetchPopularTags] write-through to [HomePreferences]
 * (`popular_tags_json`, 7-day TTL), every entry reads the cache first, a
 * stale cache refreshes in the BACKGROUND and a failing refresh keeps the
 * served cache. The screen only errors on the very first run (no cache AND
 * no network). The fetch itself is a single round trip; the observed device
 * failures were transient network / rate-limit blips — they now log their
 * real cause and can no longer blank the index.
 */
class HomeGenresViewModel(
    private val anilistApi: AniListApi,
    private val homePreferences: HomePreferences, // ROUND 152: the tags cache
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Browse:Genres"

        /**
         * ROUND 152: the 19 AniList genres are a FIXED set (Hentai pinned
         * last — the API layer's rule) — served INSTANTLY, never fetched.
         */
        private val STATIC_GENRES = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
            "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
            "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports",
            "Supernatural", "Thriller",
            // Hentai — pinned last (the handoff's GenresScreen rule).
            "Hentai",
        )
    }

    private val _state = MutableStateFlow<HomeGenresUiState>(HomeGenresUiState.Loading)
    val state: StateFlow<HomeGenresUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Idempotent per successful load — recomposition can call it freely. */
    fun load(force: Boolean = false) {
        if (!force && _state.value is HomeGenresUiState.Content) return
        val cachedTags = homePreferences.getPopularTags()
        if (cachedTags != null) {
            _state.value = HomeGenresUiState.Content(STATIC_GENRES, cachedTags)
            if (homePreferences.isPopularTagsStale()) fetchTags()
        } else {
            _state.value = HomeGenresUiState.Loading
            fetchTags()
        }
    }

    /** The ONE tags fetch (the genres are static) + the write-through cache. */
    private fun fetchTags() {
        viewModelScope.launch {
            runCatching { anilistApi.fetchPopularTags() }
                .onSuccess { result ->
                    Logger.i(TAG) { "browse categories: ${result.tags.size} tags fetched" }
                    // Write-through: the next entry — and every offline one —
                    // serves from the cache.
                    homePreferences.setPopularTags(result.tags)
                    _state.value = HomeGenresUiState.Content(STATIC_GENRES, result.tags)
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Logger.e(TAG, e) { "browse categories failed: ${e.message}" }
                    // A served cache keeps the screen on Content (the failure
                    // logs for diagnosis); only an EMPTY cache surfaces the
                    // retry row (first-ever run, offline).
                    if (_state.value is HomeGenresUiState.Loading) {
                        _state.value = HomeGenresUiState.Error(e.message ?: "Couldn't reach AniList.")
                    }
                }
        }
    }

    fun retry() {
        load(force = true)
    }
}

/**
 * "All genres" (GenresScreen.tsx): the 3-column grid of 44dp genre tiles
 * (Hentai pinned last by the API layer) + the "Popular tags" section, each
 * tap opening its title grid. Live data with skeleton + retry.
 */
@Composable
fun HomeGenresScreen(
    onBack: () -> Unit,
    onOpen: (kind: String, name: String) -> Unit,
    viewModel: HomeGenresViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val pulse = skeletonPulse()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        val meta = when (val s = state) {
            is HomeGenresUiState.Content -> "${s.genres.size} genres · ${s.tags.size} tags · AniList"
            else -> null
        }
        HomeBrowseHead(title = "All genres", meta = meta, onBack = onBack)

        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            when (val current = state) {
                is HomeGenresUiState.Error -> HomeBrowseErrorRow(
                    message = "Couldn't reach AniList.",
                    retryLabel = "Retry",
                    onRetry = { viewModel.retry() },
                )
                HomeGenresUiState.Loading -> {
                    HomeGenreSkeletonRows(title = "Genres", rows = 7, isFirst = true, pulse = pulse)
                    HomeGenreSkeletonRows(title = "Popular tags", rows = 5, isFirst = false, pulse = pulse)
                }
                is HomeGenresUiState.Content -> {
                    HomeBrowseGroup("Genres", isFirst = true)
                    val genreRows = remember(current.genres) { current.genres.chunked(3) }
                    genreRows.forEach { row ->
                        HomeCategoryTileRow(labels = row, onOpen = { onOpen(HomeGenreKey.KIND_GENRE, it) })
                    }
                    if (current.tags.isNotEmpty()) {
                        HomeBrowseGroup("Popular tags", isFirst = false)
                        val tagRows = remember(current.tags) { current.tags.chunked(3) }
                        tagRows.forEach { row ->
                            HomeCategoryTileRow(labels = row, onOpen = { onOpen(HomeGenreKey.KIND_TAG, it) })
                        }
                    }
                }
            }
        }
    }
}

/** The index's skeleton block: a group label + [rows] rows of 44dp tile skels. */
@Composable
private fun HomeGenreSkeletonRows(
    title: String,
    rows: Int,
    isFirst: Boolean,
    pulse: Float,
) {
    val tileShape = RoundedCornerShape(12.dp)
    HomeBrowseGroup(title, isFirst = isFirst)
    repeat(rows) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(tileShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = pulse)),
                )
            }
        }
    }
}

/** One 3-wide row of 44dp category tiles (the .genre-tile). */
@Composable
private fun HomeCategoryTileRow(
    labels: List<String>,
    onOpen: (String) -> Unit,
) {
    val tileShape = RoundedCornerShape(12.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        labels.forEach { label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(tileShape)
                    .border(
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                        tileShape,
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onOpen(label) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Fill the trailing columns so short rows keep the 3-column rhythm.
        repeat(3 - labels.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

// ── The category grids (GenreScreen.tsx + the catalog page) ──────────────────

/** Which paged feed a category grid serves. */
sealed interface HomeCategorySource {
    /** An AniList genre — the genre_in filter, popularity order. */
    data class Genre(val name: String) : HomeCategorySource

    /** An aggregated popular tag — the tag_in filter. */
    data class Tag(val name: String) : HomeCategorySource

    /** A fixed browse sort (e.g. TRENDING_DESC) — the home rail's "See all". */
    data class Catalog(val sort: String) : HomeCategorySource
}

/** HomeGenreScreen/HomeCatalogScreen's state (the design's load + more pair). */
sealed interface HomeCategoryUiState {
    data object Loading : HomeCategoryUiState
    data class Error(val message: String) : HomeCategoryUiState
    data object Empty : HomeCategoryUiState
    data class Content(
        val titles: List<AniListAnime>,
        val page: Int,
        val hasMore: Boolean,
        val loadingMore: Boolean,
        val moreError: Boolean,
    ) : HomeCategoryUiState
}

/**
 * The paged category grid ViewModel — ONE instance shared by the genre, tag
 * and catalog pages (the custom backstack keeps one screen visible at a
 * time; [load] re-seeds per source, the CsCategory idiom). 24 per page; the
 * approach-bottom load-more dedupes by AniList id and soft-fails (the footer
 * disappears, the next trigger retries).
 */
class HomeCategoryViewModel(private val anilistApi: AniListApi) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Browse:Category"
    }

    private val _state = MutableStateFlow<HomeCategoryUiState>(HomeCategoryUiState.Loading)
    val state: StateFlow<HomeCategoryUiState> = _state.asStateFlow()

    /** The loaded identity — the load() idempotence key. */
    private var loadedSource: HomeCategorySource? = null

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    /** Idempotent per successful load of the SAME source. */
    fun load(source: HomeCategorySource) {
        if (loadedSource == source && _state.value is HomeCategoryUiState.Content) return
        loadedSource = source
        loadJob?.cancel()
        _state.value = HomeCategoryUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val page = fetchPage(source, 1)
                _state.value = if (page.media.isEmpty()) {
                    HomeCategoryUiState.Empty
                } else {
                    HomeCategoryUiState.Content(
                        titles = page.media,
                        page = 1,
                        hasMore = page.hasNextPage,
                        loadingMore = false,
                        moreError = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.e(TAG, e) { "category load failed for $source: ${e.message}" }
                _state.value = HomeCategoryUiState.Error(e.message ?: "Couldn't reach AniList.")
            }
        }
    }

    /** The Error state's Retry — forces the reload. */
    fun retry() {
        val source = loadedSource ?: return
        loadedSource = null
        load(source)
    }

    /** The approach-bottom load-more: appends page+1, dedupes, soft-fails. */
    fun loadMore() {
        val content = _state.value as? HomeCategoryUiState.Content ?: return
        if (content.loadingMore || !content.hasMore) return
        val source = loadedSource ?: return
        val nextPage = content.page + 1
        _state.value = content.copy(loadingMore = true, moreError = false)
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            try {
                val page = fetchPage(source, nextPage)
                val existing = content.titles.mapTo(HashSet()) { it.id }
                val fresh = page.media.filter { it.id !in existing }
                val current = _state.value as? HomeCategoryUiState.Content ?: return@launch
                _state.value = current.copy(
                    titles = current.titles + fresh,
                    page = nextPage,
                    hasMore = page.hasNextPage,
                    loadingMore = false,
                )
                Logger.i(TAG) { "loadMore($source page=$nextPage): +${fresh.size} titles" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.w(TAG) { "category loadMore failed — footer retries on the next trigger" }
                val current = _state.value as? HomeCategoryUiState.Content ?: return@launch
                _state.value = current.copy(loadingMore = false, moreError = true)
            }
        }
    }

    private suspend fun fetchPage(source: HomeCategorySource, page: Int) = when (source) {
        is HomeCategorySource.Genre -> anilistApi.fetchByGenre(source.name, page, CATEGORY_PER_PAGE)
        is HomeCategorySource.Tag -> anilistApi.fetchByTag(source.name, page, CATEGORY_PER_PAGE)
        is HomeCategorySource.Catalog -> {
            // fetchBrowseSection is the home_trending contract — its shape
            // stays untouched; the catalog derives hasNext from a full page.
            val media = anilistApi.fetchBrowseSection(source.sort, page, CATEGORY_PER_PAGE)
            AniListMediaPage(
                media = media,
                hasNextPage = media.size >= CATEGORY_PER_PAGE,
            )
        }
    }
}

/**
 * One category's title grid (GenreScreen.tsx): back, title, count meta, the
 * 3-column poster grid, 24 per page, infinite pagination on the
 * scroll-approach trigger, skeletons + retry. [emptyLabel] renders when
 * AniList has no titles for the category.
 */
@Composable
private fun HomeCategoryGridScreen(
    title: String,
    source: HomeCategorySource,
    emptyLabel: String,
    onBack: () -> Unit,
    onOpenDetails: (AnimeDetailsKey) -> Unit,
    viewModel: HomeCategoryViewModel,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(source) { viewModel.load(source) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val meta = when (val s = state) {
            is HomeCategoryUiState.Content -> "${s.titles.size} titles · popularity"
            else -> null
        }
        HomeBrowseHead(title = title, meta = meta, onBack = onBack)

        when (val current = state) {
            is HomeCategoryUiState.Error -> Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp),
            ) {
                HomeBrowseErrorRow("Couldn't reach AniList.", "Retry") { viewModel.retry() }
            }
            HomeCategoryUiState.Loading -> HomeCategorySkeleton(
                count = 12,
                pulse = skeletonPulse(),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            HomeCategoryUiState.Empty -> Text(
                text = emptyLabel,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified }
                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
            is HomeCategoryUiState.Content -> {
                val gridState = rememberLazyGridState()
                // The scroll-approach trigger: near the grid's end → load more
                // (the VM guards re-entry; an exhausted feed no-ops).
                val nearEnd by remember {
                    derivedStateOf {
                        val info = gridState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= info.totalItemsCount - LOAD_MORE_APPROACH
                    }
                }
                LaunchedEffect(nearEnd) {
                    if (nearEnd) viewModel.loadMore()
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(current.titles, key = { it.id }) { anime ->
                        HomeCategoryPosterCard(anime = anime) {
                            onOpenDetails(
                                AnimeDetailsKey.AniList(
                                    anime.id,
                                    coverUrl = anime.coverUrl,
                                    title = anime.displayName,
                                ),
                            )
                        }
                    }
                    if (current.loadingMore) {
                        items(3, key = { "more-skel-$it" }) {
                            HomeCategorySkeletonItem(pulse = skeletonPulse())
                        }
                    }
                    if (current.moreError) {
                        item(key = "more-error", span = { GridItemSpan(3) }) {
                            HomeBrowseErrorRow("Couldn't load more.", "Retry") { viewModel.loadMore() }
                        }
                    }
                }
            }
        }
    }
}

/** One genre/tag page (HomeGenreKey) — the VM source rides the kind string. */
@Composable
fun HomeGenreScreen(
    kind: String,
    name: String,
    onBack: () -> Unit,
    onOpenDetails: (AnimeDetailsKey) -> Unit,
    viewModel: HomeCategoryViewModel = koinViewModel(),
) {
    val source = remember(kind, name) {
        if (kind == HomeGenreKey.KIND_TAG) HomeCategorySource.Tag(name) else HomeCategorySource.Genre(name)
    }
    HomeCategoryGridScreen(
        title = name,
        source = source,
        emptyLabel = "No $name titles on AniList.",
        onBack = onBack,
        onOpenDetails = onOpenDetails,
        viewModel = viewModel,
    )
}

/**
 * The home catalog page (HomeCatalogKey): the GenreScreen grid machinery over
 * a FIXED sort constant (the key's query string — e.g. the trending rail's
 * "See all" passes TRENDING_DESC), titled from the key.
 */
@Composable
fun HomeCatalogScreen(
    query: String,
    title: String,
    onBack: () -> Unit,
    onOpenDetails: (AnimeDetailsKey) -> Unit,
    viewModel: HomeCategoryViewModel = koinViewModel(),
) {
    val source = remember(query) { HomeCategorySource.Catalog(query) }
    HomeCategoryGridScreen(
        title = title,
        source = source,
        emptyLabel = "Nothing here yet — try again later.",
        onBack = onBack,
        onOpenDetails = onOpenDetails,
        viewModel = viewModel,
    )
}

// ── The grid pieces ──────────────────────────────────────────────────────────

/** One poster card (the .poster-card): 2:3 cover in a 12dp frame + 2-line title + score. */
@Composable
private fun HomeCategoryPosterCard(
    anime: AniListAnime,
    onClick: () -> Unit,
) {
    val thumbShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(thumbShape)
                .border(
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                    thumbShape,
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = anime.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = anime.displayName,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 15.sp,
            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // ROUND 152: the report — the title sat too far under the cover.
            // 6 → 4dp (the score keeps its 2dp gap).
            modifier = Modifier.padding(top = 4.dp),
        )
        anime.averageScore?.let { score ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(9.dp),
                )
                Text(
                    text = "$score%",
                    fontFamily = RobotoFamily,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** One skeleton cell: the 2:3 art block + two text lines (the .rail-skel). */
@Composable
private fun HomeCategorySkeletonItem(pulse: Float) {
    val shape = RoundedCornerShape(12.dp)
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = pulse)),
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .size(height = 10.dp, width = 60.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = pulse)),
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth(0.55f)
                .size(height = 10.dp, width = 40.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = pulse)),
        )
    }
}

/** The first-page skeleton grid (the design's 12 rail-skels). */
@Composable
private fun HomeCategorySkeleton(
    count: Int,
    pulse: Float,
    modifier: Modifier = Modifier,
) {
    val rows = (count + 2) / 3
    Column(modifier = modifier) {
        repeat(rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                repeat(3) {
                    Box(modifier = Modifier.weight(1f)) {
                        HomeCategorySkeletonItem(pulse = pulse)
                    }
                }
            }
        }
    }
}
