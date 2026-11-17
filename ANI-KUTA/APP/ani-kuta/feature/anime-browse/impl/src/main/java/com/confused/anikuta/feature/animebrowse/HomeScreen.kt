package com.confused.anikuta.feature.animebrowse

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.component.EmptyState
import com.confused.anikuta.core.designsystem.component.bottomBarClearance // round 151
import com.confused.anikuta.core.designsystem.theme.DisplayFamily // ROUND 152: the handoff's display face
import com.confused.anikuta.core.designsystem.theme.LocalCardDescriptionColor
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.debugapi.DebugContext
import com.confused.anikuta.core.debugapi.LocalDebugContextUpdater
import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey
import com.confused.anikuta.feature.animehistory.HistoryKey
import com.confused.anikuta.feature.updates.UpdatesKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

// ── The handoff's fixed on-scrim whites (theme-invariant, literal Colors) ─────

/** --on-scrim: the white that sits over photographic scrims, never themes. */
private val OnScrim = Color(0xFFFFFFFF)

/** --on-scrim-tint: the warm coral-white for tracked eyebrow labels. */
private val OnScrimTint = Color(0xFFFFD9CF)

/** --on-scrim-score: the star/score tint inside the glass chips. */
private val OnScrimScore = Color(0xFFFFB4A4)

/** The photographic-scrim base the gradients use (rgba(10,6,7)). */
private fun scrim(alpha: Float) = Color(0xFF0A0607).copy(alpha = alpha)

/** The dark touch-scrim behind floating play chips (rgba(12,7,8,0.55)). */
private val BadgeScrim = Color(0x8C0C0708)

/** Glass chip fill (rgba(255,255,255,0.14)) + its 1px border (0.18). */
private val GlassBg = Color(0x24FFFFFF)
private val GlassBorder = Color(0x2EFFFFFF)

/** The progress track hugging the episode card's bottom (white 22%). */
private val ProgressTrack = Color(0x38FFFFFF)

// ── Shape scale (globals.css §2.2 — one scale, no invented values) ───────────

private val HeroShape = RoundedCornerShape(28.dp)
private val ThumbShape = RoundedCornerShape(12.dp)
private val ThumbTightShape = RoundedCornerShape(6.dp)
private val PillShape = RoundedCornerShape(50)

/** ROUND 152: the airs timeline row cell (.t-row border-radius: 14px) — the
 * pulsing NEXT-row outline rides this shape. */
private val AirsRowShape = RoundedCornerShape(14.dp)

/** ROUND 152: the always-on mood chip — "Surprise me" is the ACTIVE chip on entry. */
private const val MOOD_SURPRISE = "Surprise me"

// ── Motion constants (Hero.tsx) ──────────────────────────────────────────────

/** The hero auto-advance cycle (5.5s) + the hold-off after ANY manual touch. */
private const val HERO_CYCLE_MS = 5500L
private const val HERO_HOLD_OFF_MS = 8000L

/** The hero neighbours peek at 0.88 / 0.66 (globals.css .hero-card). */
private const val HERO_PEEK_SCALE = 0.88f
private const val HERO_PEEK_ALPHA = 0.66f

/** The mood chips (lib/data.ts `moods.genres`). */
private val MOOD_GENRES = listOf(
    "Action", "Romance", "Comedy", "Fantasy", "Sports", "Mystery", "Slice of Life",
)

/**
 * HOME screen — ROUND 151, the design-handoff rebuild (this round REPLACES
 * the round-148 "My screen" wholesale; the CollapsingHeader + the
 * NextUp/Releasing/RecentlyReleased/Popular/TopRated sections are scrapped).
 *
 * The composition mirrors the handoff's page.tsx exactly, top → bottom:
 * TopBar → Hero → ContinueWatching → AirsToday → TrendingRail →
 * SuggestedForYou → MoodChips.
 *
 * ## The token mapping
 * The handoff's tokens map onto the app theme: --bg→background, --surface→
 * surface, --primary→primary, --outline→outlineVariant, --on-surface-variant→
 * onSurfaceVariant. ROUND 152: the DISPLAY face is BUNDLED — [DisplayFamily]
 * (Bricolage Grotesque variable) carries the wordmark, the section heads and
 * the hero lockup; the BODY stays [RobotoFamily]. The on-scrim whites are
 * LITERAL colors above. The radii: hero 28dp / card 24dp / thumb 12dp /
 * pill 999. The content edge is 20dp on EVERY rail. The body background draws
 * the handoff's subtle radial primary-tint at the top (~7% primary mix).
 *
 * ## The Hero (Hero.tsx mechanics, ported)
 * A LazyRow of 3 copies × 5 slides, re-anchored to the middle copy when it
 * crosses a boundary — infinite wrap in both directions. Active card centered
 * at scale 1, neighbours peek at 0.88 (graphicsLayer by the fractional
 * distance). Auto-advance every 5.5s with an 8s hold-off after any manual
 * interaction (dots/swipes). Dots are loop controls with the direction-aware
 * seam (forward wraps to dot 1 while the last slide shows; dot 1 = skip back,
 * the last dot = skip next) + the countdown fill inside the lit dot. Tap a
 * peeking neighbour → it centers; tap the CENTER card (or its Play) → details.
 *
 * ## Routing
 * Every [NavKey] passes up through [onNavigate] (the ad gate runs in
 * MainActivity — `is AnimeDetailsKey -> navigateToDetails(navKey)` else direct
 * push; the TopBar profile rides a dedicated callback since ProfileKey lives
 * app-side). The mainId-based rows (Continue watching / Airs today) go through
 * [onOpenDetailsByMainId] — MainActivity's `openDetailsByMainId` semantics,
 * with the third arg = the autoplay intent (the Continue card tap STARTS the
 * episode: the details key carries autoPlayEpisode → the details page
 * auto-resolves and opens the player). The title block under the art is a
 * SEPARATE click target that opens the details page WITHOUT autoplay.
 *
 * CORE_RULES §22: quiet motion. §23: reactive state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (NavKey) -> Unit,
    // ROUND 151: the TopBar's profile button — the feature module can't see
    // ProfileKey (it lives app-side), so the host hands the click down.
    onProfileClick: () -> Unit = {},
    onOpenDetailsByMainId: (String, Int?, Boolean) -> Unit = { _, _, _ -> },
    // ROUND 152: the brand chip's art — the APP's real logo (app-side drawable
    // res id; this module can't see app resources). Null = the glyph fallback.
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val trending by viewModel.trending.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val airsToday by viewModel.airsToday.collectAsState()
    val suggestedToken by viewModel.suggestedRefreshToken.collectAsState()
    val fieldCovers by viewModel.fieldCovers.collectAsState()

    val heroSlides = remember(trending) { trending.take(HomeViewModel.HERO_COUNT) }
    val trendingRanked = remember(trending) {
        trending.mapIndexed { index, anime -> index + 1 to anime }
    }

    // DB-7: provide debug context for the Current Screen tab.
    val updateDebugContext = LocalDebugContextUpdater.current
    val homeCtx = remember(
        state, trending.size, continueWatching.size, airsToday.size,
        fieldCovers.size, isRefreshing,
    ) {
        DebugContext(
            screenName = "Home",
            screenData = mapOf(
                "state" to (state::class.simpleName ?: "Unknown"),
                "trendingCount" to trending.size.toString(),
                "continueCount" to continueWatching.size.toString(),
                "airsCount" to airsToday.size.toString(),
                "fieldCovers" to fieldCovers.size.toString(),
                "isRefreshing" to isRefreshing.toString(),
            ),
        )
    }
    LaunchedEffect(homeCtx) { updateDebugContext(homeCtx) }
    DisposableEffect(Unit) {
        onDispose { updateDebugContext(null) }
    }

    val ptrState = rememberPullToRefreshState()
    val context = LocalContext.current

    val thresholdCrossed = ptrState.distanceFraction >= 1f
    LaunchedEffect(thresholdCrossed) {
        if (thresholdCrossed) {
            HapticHelper.stageCross(context)
        }
    }

    // ── The body background: the handoff's radial primary-tint at the top ──
    // (radial-gradient(60% 80% at 50% 0%, primary 7% into background → background 62%)).
    val bgColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                val mixed = lerp(bgColor, primaryColor, 0.07f)
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to mixed,
                            0.62f to bgColor,
                            1f to bgColor,
                        ),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.height * 0.8f,
                    ),
                )
            },
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = ptrState,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (state) {
                HomeState.Loading -> BrowseSkeleton()
                is HomeState.Error -> HomeErrorState(
                    message = (state as HomeState.Error).message,
                    onRetry = { viewModel.refresh() },
                )
                HomeState.Content -> {
                    val listState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    // ── ROUND 152: the profile avatar + display initial, read
                    // from the SAME prefs the Profile page writes (the home
                    // re-enters composition on every back-navigation, so a
                    // remember read picks up avatar/name changes). ──
                    val preferenceStore = koinInject<PreferenceStore>()
                    val profileAvatarUrl = remember {
                        preferenceStore.getString("profile_avatar_url", "").takeIf { it.isNotBlank() }
                    }
                    val profileInitial = remember {
                        preferenceStore.getString("profile_display_name", "").trim()
                            .firstOrNull()?.uppercaseChar()?.toString()
                    }
                    // ── ROUND 152: the mood state lives at the HOME level so
                    // the rail survives the LazyColumn disposing the moods item
                    // on a far scroll — and "Surprise me" is the ACTIVE chip on
                    // entry ("" = the rail hidden; the seed reshuffles per tap). ──
                    // ROUND 154 (the device round): the rail is HIDDEN on entry —
                    // Surprise me stays LIT (its brand style never turns off) but
                    // the rail only appears after the tap (which reshuffles). ""
                    // = no rail; MOOD_SURPRISE = the rail shown.
                    var activeMood by remember { mutableStateOf("") }
                    var surpriseSeed by remember { mutableIntStateOf(0) }
                    // ROUND 154 (the user's order): the mood section COLLAPSES when
                    // it leaves the viewport — "when I scroll to the very top and
                    // then to the very bottom again, only the genres and surprise me
                    // button will show". The derived visibility watches the home
                    // list's scroll window against the moods item's bounds; the
                    // exit resets activeMood to "" (the rail collapses; the chips
                    // stay; a tap re-opens fresh).
                    val moodsVisible by remember {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val moodsItem = info.visibleItemsInfo.firstOrNull {
                                it.key == "moods"
                            }
                            if (moodsItem == null) {
                                false
                            } else {
                                val viewportTop = info.viewportStartOffset - 200
                                val viewportBottom = info.viewportEndOffset + 200
                                moodsItem.offset + moodsItem.size > viewportTop &&
                                    moodsItem.offset < viewportBottom
                            }
                        }
                    }
                    LaunchedEffect(moodsVisible) {
                        if (!moodsVisible && activeMood != "") {
                            activeMood = ""
                        }
                    }
                    // The mounted clock driving the 1s countdown chips; the VM's
                    // per-minute ticker recomputes the released flags + the row
                    // identities, so the release transition fires on its own.
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            now = System.currentTimeMillis()
                            delay(1000)
                        }
                    }
                    // ── Coil warm-up — the SectionPreloader contract (exact
                    // render sizes + no transformations → memory-cache hits
                    // on first compose): the hero cards, the trending posters,
                    // the 16:9 continue art, the timeline thumbs, and ROUND
                    // 152's field covers at the EXACT tile size (the pool is
                    // hoisted to the VM, so re-entry composes instantly). ──
                    SectionPreloader(
                        urls = heroSlides.map { it.coverUrl },
                        width = 340.dp,
                        height = 454.dp,
                    )
                    SectionPreloader(urls = trending.map { it.coverUrl }, width = 104.dp, height = 156.dp)
                    SectionPreloader(urls = continueWatching.map { it.artUrl }, width = 188.dp, height = 106.dp)
                    SectionPreloader(urls = airsToday.map { it.thumbUrl }, width = 48.dp, height = 68.dp)
                    SectionPreloader(urls = fieldCovers.map { it.url }, width = 60.dp, height = 90.dp)
                    // ROUND 152: the mood chip tap auto-scrolls the feed so the
                    // moods section (chips + rail) is fully visible. The item
                    // order below is FIXED: topbar, hero, continue, airs head,
                    // airs rows…, trending, suggested, moods.
                    val moodsIndex = 6 + airsToday.size
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomBarClearance(90.dp)),
                    ) {
                        // ── TopBar (the CollapsingHeader is SCRAPPED) ──
                        item(key = "topbar") {
                            HomeTopBar(
                                onProfileClick = onProfileClick,
                                avatarUrl = profileAvatarUrl,
                                avatarInitial = profileInitial,
                            )
                        }

                        // ── Hero — the one moving thing (5.5s auto-advance) ──
                        item(key = "hero") {
                            HomeHero(
                                slides = heroSlides,
                                onOpen = { anime ->
                                    onNavigate(
                                        AnimeDetailsKey.AniList(
                                            anime.id,
                                            coverUrl = anime.coverUrl,
                                            title = anime.displayName,
                                        ),
                                    )
                                },
                            )
                        }

                        // ── Continue watching ──
                        item(key = "continue_watching") {
                            HomeContinueSection(
                                items = continueWatching,
                                onSeeAll = { onNavigate(HistoryKey) },
                                onPlay = { item -> onOpenDetailsByMainId(item.mainId, item.episodeNumber, true) },
                                onOpenDetails = { item -> onOpenDetailsByMainId(item.mainId, item.episodeNumber, false) },
                            )
                        }

                        // ── Airs today — the head + ONE list item per row, keyed
                        // by the episode identity, so the release transition
                        // (Modifier.animateItem) plays at the home-list level:
                        // the released top row fades out, the rest glide up, the
                        // bottom gains the next episode. ──
                        item(key = "airs_head") {
                            HomeSectionHead(
                                title = "Airs today",
                                actionLabel = "Schedule",
                                // ROUND 152: the Schedule push LANDS on the updates
                                // screen's Schedule tab (the key carries the tab).
                                onAction = { onNavigate(UpdatesKey(initialTab = 1)) },
                            )
                        }
                        itemsIndexed(
                            airsToday,
                            key = { _, item -> "airs_row_${item.episodeKey}" },
                        ) { index, item ->
                            HomeAirsRow(
                                item = item,
                                now = now,
                                isFirst = index == 0,
                                isLast = index == airsToday.lastIndex,
                                isNext = !item.released && index == airsToday.indexOfFirst { !it.released },
                                onClick = {
                                    // ROUND 152: GLOBAL-source rows carry an AniList
                                    // id (no library mainId) — they open the details
                                    // page directly; library rows keep the mainId path.
                                    val anilistId = item.anilistId
                                    if (anilistId != null) {
                                        onNavigate(
                                            AnimeDetailsKey.AniList(
                                                anilistId,
                                                coverUrl = item.thumbUrl,
                                                title = item.title,
                                            ),
                                        )
                                    } else {
                                        onOpenDetailsByMainId(item.mainId, item.episodeNumber, false)
                                    }
                                },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = 20.dp),
                            )
                        }

                        // ── Trending this week (ranked posters) ──
                        item(key = "trending_rail") {
                            HomeTrendingRail(
                                ranked = trendingRanked,
                                onSeeAll = {
                                    onNavigate(
                                        HomeCatalogKey(
                                            query = "TRENDING_DESC",
                                            title = "Trending this week",
                                        ),
                                    )
                                },
                                onOpen = { anime ->
                                    onNavigate(
                                        AnimeDetailsKey.AniList(
                                            anime.id,
                                            coverUrl = anime.coverUrl,
                                            title = anime.displayName,
                                        ),
                                    )
                                },
                            )
                        }

                        // ── Suggested for you (the honeycomb cover field) ──
                        item(key = "suggested") {
                            HomeSuggestedSection(
                                covers = fieldCovers,
                                refreshToken = suggestedToken,
                                onRefresh = { viewModel.refreshSuggestions() },
                                onOpenCover = { anilistId ->
                                    onNavigate(AnimeDetailsKey.AniList(anilistId))
                                },
                            )
                        }

                        // ── Browse by mood (surprise + genre chips) ──
                        item(key = "moods") {
                            HomeMoodChips(
                                trending = trending,
                                activeMood = activeMood,
                                surpriseSeed = surpriseSeed,
                                onSelect = { mood ->
                                    // ROUND 153 (the device round): Surprise me is the
                                    // ALWAYS-HIGHLIGHTED brand button — tapping it keeps
                                    // it lit, shows its rail, and RESHUFFLES every time.
                                    // The genre chips keep the toggle semantics (the
                                    // active genre chip tapped again hides its rail).
                                    if (mood == MOOD_SURPRISE) {
                                        surpriseSeed += 1
                                        activeMood = MOOD_SURPRISE
                                    } else {
                                        val wasActive = mood == activeMood
                                        activeMood = if (wasActive) "" else mood
                                    }
                                    // The chip tap scrolls the feed SMOOTHLY to the
                                    // bottom — the rail is the feed's tail, so the
                                    // section + the rail land fully in view ("it will
                                    // smoothly scroll to the very bottom").
                                    val last = listState.layoutInfo.totalItemsCount - 1
                                    scope.launch {
                                        listState.animateScrollToItem(last.coerceAtLeast(0))
                                    }
                                },
                                onBrowseAll = { onNavigate(HomeGenresKey) },
                                onOpen = { anime ->
                                    onNavigate(
                                        AnimeDetailsKey.AniList(
                                            anime.id,
                                            coverUrl = anime.coverUrl,
                                            title = anime.displayName,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Shared section chrome (PosterRail.tsx RailSection) ───────────────────────

/**
 * One section head — the 20sp bold title on the left, the quiet coral action
 * on the right (44dp tap area while the label sits on the 20dp content edge).
 * [trailing] hosts extra icon buttons (the suggestion head's refresh/pause).
 */
@Composable
private fun HomeSectionHead(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            // ROUND 152: the section heads ride the handoff's DISPLAY face
            // (the CSS .section-head h2 is font-display 20px/700).
            fontFamily = DisplayFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.36.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

// ── TopBar (TopBar.tsx) ──────────────────────────────────────────────────────

/**
 * The brand row: the wordmark + the brand chip on the left, the 44dp round
 * profile button on the right.
 *
 * ROUND 152: the chip carries the APP'S REAL LOGO ART ([logoRes] — the
 * host passes `R.drawable.icon_current`; the module can't see app resources)
 * instead of the play glyph, and the wordmark rides the handoff's DISPLAY
 * face ([DisplayFamily] ExtraBold). The profile button shows the USER'S
 * SAVED AVATAR ([avatarUrl] — `profile_avatar_url`, the same pref the
 * Profile page writes; AsyncImage handles both http(s) and the crop flow's
 * file:// paths) and falls back to the display name's first letter
 * ([avatarInitial] — the handoff's TopBar fallback), then the Person glyph.
 */
@Composable
private fun HomeTopBar(
    onProfileClick: () -> Unit,
    avatarUrl: String? = null,
    avatarInitial: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Text(
                text = "Ani-Kuta",
                // ROUND 152: the wordmark is a font-display spot — Bricolage.
                // ROUND 153: the logo chip is GONE (the user's order — the icon
                // fought the wordmark) and the name grew to carry the brand alone.
                fontFamily = DisplayFamily,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        Spacer(Modifier.weight(1f))
        // The 44dp round profile button → ProfileKey (pushed by MainActivity).
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                // The user's saved avatar — the Profile page's display precedent.
                avatarUrl != null -> AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // The handoff's fallback: the display name's first letter.
                avatarInitial != null -> Text(
                    text = avatarInitial,
                    fontFamily = DisplayFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                else -> Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── Hero (Hero.tsx — the exact mechanics) ────────────────────────────────────

/**
 * The trending hero carousel: 3 copies × N slides in a snapping LazyRow,
 * re-anchored to the middle copy when it crosses a boundary (infinite wrap
 * both directions), auto-advance every 5.5s with the 8s manual hold-off, the
 * direction-aware dot seam with the countdown fill, neighbours peeking at
 * 0.88/0.66 while the active card sits centered at scale 1.
 */
@Composable
private fun HomeHero(
    slides: List<AniListAnime>,
    onOpen: (AniListAnime) -> Unit,
) {
    if (slides.isEmpty()) return
    val n = slides.size
    val copies = 3
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // ── Manual-interaction bookkeeping (the TSX noteManual) ──
    var cycleId by remember { mutableIntStateOf(0) }
    var manualUntil by remember { mutableLongStateOf(0L) }
    fun noteManual() {
        manualUntil = System.currentTimeMillis() + HERO_HOLD_OFF_MS
        cycleId += 1
    }

    // ── The stride: card width (container − 48dp edges) + the 10dp gap ──
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        // ROUND 153 (the device round): the center card narrows (100% - 88dp —
        // the user: the main cover "should not take up way too much space") so
        // the neighbours' corners peek properly on both sides.
        val cardWidth = maxWidth - 88.dp
        val stridePx = with(density) { (cardWidth + 10.dp).toPx() }

        // The fractional absolute position (items) — round() = the settled card.
        val fractionalPos by remember(stridePx) {
            derivedStateOf {
                if (stridePx <= 0f) {
                    listState.firstVisibleItemIndex.toFloat()
                } else {
                    listState.firstVisibleItemIndex +
                        listState.firstVisibleItemScrollOffset / stridePx
                }
            }
        }
        val active = ((fractionalPos.roundToInt() % n) + n) % n

        // Travel direction decides the end-dot seam mapping (the TSX onScroll
        // delta guard excludes the silent re-anchor jumps).
        var dir by remember { mutableIntStateOf(1) }
        LaunchedEffect(stridePx) {
            var lastPos = n.toFloat()
            snapshotFlow { fractionalPos }.collect { pos ->
                val delta = pos - lastPos
                if (delta != 0f && abs(delta) < 1.5f) {
                    dir = if (delta > 0) 1 else -1
                }
                lastPos = pos
            }
        }

        // Start centered on the middle copy.
        LaunchedEffect(n) {
            listState.scrollToItem(n)
        }

        // The silent re-anchor: on settle, jump into the middle copy (the
        // stride guard in the direction tracker excludes the jump's delta).
        LaunchedEffect(Unit) {
            snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                if (!scrolling) {
                    val pos = listState.firstVisibleItemIndex
                    when {
                        pos < n -> listState.scrollToItem(pos + n, listState.firstVisibleItemScrollOffset)
                        pos >= copies * n - n -> listState.scrollToItem(pos - n, listState.firstVisibleItemScrollOffset)
                    }
                }
            }
        }

        // The jump helper: ±1 rides the copy seam so the wrap animates in the
        // travel direction; 0 jumps straight to the middle copy's target.
        fun jump(target: Int, manual: Boolean, step: Int) {
            if (manual) noteManual()
            scope.launch {
                val current = fractionalPos.roundToInt()
                val next = when (step) {
                    1 -> current + 1
                    -1 -> current - 1
                    else -> n + target
                }
                listState.animateScrollToItem(next)
            }
        }

        // Auto-advance forever — ROUND 152 (the SNAP fix): ONE never-cancelled
        // loop. The old effect keyed on `active`: while animateScrollToItem
        // flew one stride, fractionalPos crossed the halfway mark → active
        // flipped → the effect was CANCELLED mid-flight → the row parked
        // between covers for a whole cycle. Keyed on Unit it survives the
        // whole animation; the target is computed AT FIRE TIME from the
        // settled position, and the manual hold-off (dots/swipes/taps) is
        // re-read each pass.
        LaunchedEffect(Unit) {
            while (true) {
                val holdOff = (manualUntil - System.currentTimeMillis()).coerceAtLeast(0L)
                delay(holdOff + HERO_CYCLE_MS)
                // A touch DURING the wait re-armed the hold-off (the old effect
                // restarted on the cycle bump) — respect it: loop back and
                // re-arm instead of firing under the user's finger.
                if (System.currentTimeMillis() < manualUntil) continue
                if (listState.isScrollInProgress) continue
                dir = 1
                val current = fractionalPos.roundToInt()
                listState.animateScrollToItem(current + 1)
            }
        }

        // ROUND 154 (the device round): the cover-derived gradient wash BEHIND
        // the carousel — the ACTIVE slide's art, blurred + dimmed, fading to
        // the background (a subtle read: "not that much, but a very subtle
        // looking kind of one"). Rides the same fractional position as the
        // cards, so the wash crossfades as the carousel advances. The wash is
        // an UNDERLAY: the carousel Column stacks on top (sibling order), the
        // wash shows through the 10dp gaps + the peeking neighbour edges.
        Box(modifier = Modifier.fillMaxWidth()) {
            val washIndex = fractionalPos.roundToInt().coerceIn(0, n - 1)
            val washSlide = slides.getOrNull(washIndex)
            Box(
                modifier = Modifier
                    .matchParentSize(),
            ) {
                if (washSlide != null) {
                    AsyncImage(
                        model = washSlide.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(28.dp),
                    )
                }
                // The dim + the fade to the screen background (subtle, not a poster).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                    0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                    1f to MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                // ROUND 153: re-derived for the narrower card — the centering
                // padding is (width − card)/2 = a constant 44dp (88dp of edge).
                contentPadding = PaddingValues(horizontal = 44.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        // Every touch (swipe or tap) holds off the auto-advance.
                        detectTapGestures(onPress = { noteManual() })
                    },
            ) {
                items(copies * n, key = { it }) { index ->
                    val slide = slides[index % n]
                    HeroCard(
                        anime = slide,
                        index = index,
                        listState = listState,
                        stridePx = stridePx,
                        cardWidth = cardWidth,
                        total = n,
                        onClick = {
                            val slideIndex = index % n
                            if (slideIndex == active) {
                                // The center card (or its Play) opens details.
                                onOpen(slide)
                            } else {
                                // A peeking neighbour centers itself.
                                val forwardDist = (slideIndex - active + n) % n
                                val nextDir = if (forwardDist <= n / 2) 1 else -1
                                val adjacent = forwardDist == 1 || forwardDist == n - 1
                                dir = nextDir
                                jump(slideIndex, manual = true, step = if (adjacent) nextDir else 0)
                            }
                        },
                        onPlay = { onOpen(slide) },
                    )
                }
            }
        }

            // ── The dots — loop controls with the direction-aware seam ──
            val lit = if (dir < 0) {
                if (active == 0) 2 else active + 1
            } else {
                if (active == n - 1) 1 else active + 1
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                slides.forEachIndexed { i, _ ->
                    val dot = i + 1
                    HeroDot(
                        lit = lit == dot,
                        cycleId = cycleId,
                        manualUntil = manualUntil,
                        onClick = {
                            when (dot) {
                                1 -> {
                                    dir = -1
                                    jump((active + n - 1) % n, manual = true, step = -1)
                                }
                                n -> {
                                    dir = 1
                                    jump((active + 1) % n, manual = true, step = 1)
                                }
                                else -> {
                                    val diff = (dot - 1 - active + n) % n
                                    val nextDir = if (diff <= n / 2) 1 else -1
                                    val step = when (diff) {
                                        1 -> 1
                                        n - 1 -> -1
                                        else -> 0
                                    }
                                    dir = nextDir
                                    jump(dot - 1, manual = true, step)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** One hero card — art (top-crop 14%), the two-layer scrim, glass chips, the
 * dynamic title lockup, the Play button, and the peek scale by distance. The
 * scale/alpha read the scroll state INSIDE graphicsLayer (per-frame layer
 * updates, no recomposition). */
@Composable
private fun HeroCard(
    anime: AniListAnime,
    index: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    stridePx: Float,
    cardWidth: androidx.compose.ui.unit.Dp,
    total: Int,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(302f / 404f)
            .graphicsLayer {
                // Continuous peek: scale/alpha fall off with the fractional
                // distance to the settled card (1.0/0.66 at rest → 0.88 on the
                // immediate neighbours).
                val pos = if (stridePx > 0f) {
                    listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset / stridePx
                } else {
                    listState.firstVisibleItemIndex.toFloat()
                }
                val distance = abs(index - pos).coerceIn(0f, 1f)
                scaleX = 1f - (1f - HERO_PEEK_SCALE) * distance
                scaleY = scaleX
                alpha = 1f - (1f - HERO_PEEK_ALPHA) * distance
            }
            .clip(HeroShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        // The cover art — objectPosition 50% 14% ≈ a top-crop with a slight
        // downward bias (BiasAlignment -1 = top, +1 = bottom → -0.72).
        AsyncImage(
            model = anime.coverUrl,
            contentDescription = anime.displayName,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, -0.72f),
            modifier = Modifier.fillMaxSize(),
        )
        // The two-layer scrim (the TSX gradient, verbatim stops).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to scrim(0.06f),
                            0.30f to scrim(0.06f),
                            0.58f to scrim(0.46f),
                            0.92f to scrim(0.92f),
                            1f to scrim(0.92f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to scrim(0.40f),
                            0.5f to scrim(0f),
                        ),
                    ),
                ),
        )

        // The bottom content column: lockup, glass chips, Play + eyebrow.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            HeroTitleLockup(title = anime.displayName)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                anime.averageScore?.let { score ->
                    GlassChip {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = OnScrimScore,
                            modifier = Modifier.size(9.dp),
                        )
                        Text(
                            text = "$score%",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.22.sp,
                            color = OnScrim,
                            maxLines = 1,
                        )
                    }
                }
                anime.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    GlassChip {
                        Text(
                            text = genres.take(2).joinToString(" · "),
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.22.sp,
                            color = OnScrim,
                            maxLines = 1,
                        )
                    }
                }
                HeroNoteChip(anime)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onPlay,
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "Play",
                            fontFamily = RobotoFamily,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = heroEyebrow(index, total),
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.9.sp,
                    color = OnScrimTint,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/** "#1 TRENDING" — the carousel's rank eyebrow (uppercase tracked label). */
private fun heroEyebrow(index: Int, total: Int): String {
    val rank = (index % total) + 1
    return "#$rank TRENDING"
}

/** The episode-note glass chip — season year, or the status when unyearned. */
@Composable
private fun HeroNoteChip(anime: AniListAnime) {
    val note = anime.seasonYear?.toString()
        ?: anime.status?.lowercase()?.replaceFirstChar { it.uppercase() }
        ?: return
    if (note.isBlank()) return
    GlassChip {
        Text(
            text = note,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.22.sp,
            color = OnScrim,
            maxLines = 1,
        )
    }
}

/** The glass chip over photography — rgba(255,255,255,.14) bg, 1px .18
 * border, on-scrim text (theme-invariant). */
@Composable
private fun GlassChip(content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(PillShape)
            .border(BorderStroke(1.dp, GlassBorder), PillShape)
            .background(GlassBg)
            // ROUND 153: the SQUISHED pill — the vertical padding 4→2dp (the
            // user: "a squished kind of pill, not a thick one"; the height drops,
            // the width keeps).
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        content()
    }
}

/**
 * The DYNAMIC title lockup — ROUND 152's restructure (the device report: the
 * old one-line tail ELLIPSIZED; ugly). The handoff's pattern, generated for
 * ANY title: the small tracked EYEBROW line (the words before the last 1-2),
 * then the BIG main line — the remaining words + the season token rendered in
 * the ACCENT (primary-text) at its end, flowing INLINE via one
 * AnnotatedString. NOTHING ellipsizes: the eyebrow clips to one line, the
 * main SOFT-WRAPS to two (the handoff lockups wrap, never dot).
 */
@Composable
private fun HeroTitleLockup(title: String) {
    val parts = remember(title) { splitHeroTitle(title) }
    Column {
        if (parts.eyebrow.isNotBlank()) {
            Text(
                text = parts.eyebrow.uppercase(Locale.US),
                fontFamily = DisplayFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp, // the handoff's 0.2em tracked eyebrow
                lineHeight = 14.sp,
                color = OnScrimTint,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        Text(
            text = buildAnnotatedString {
                if (parts.main.isNotBlank()) {
                    withStyle(SpanStyle(color = OnScrim)) {
                        append(parts.main.uppercase(Locale.US))
                    }
                }
                if (parts.accent != null) {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(" · ${parts.accent.uppercase(Locale.US)}")
                    }
                }
            },
            fontFamily = DisplayFamily,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.1).sp,
            lineHeight = 34.sp,
            color = OnScrim,
            maxLines = 2,
            softWrap = true,
        )
    }
}

/** The lockup's split — eyebrow words / main words / the accent numeral. */
internal data class HeroTitleParts(
    val eyebrow: String,
    val main: String,
    val accent: String?,
)

/**
 * The trailing season/volume token: "Season 3", "S3", "Part 2", "Vol 2",
 * "Volume 4", "Cour 2", a bare trailing "3", a roman "II", and the "· 3"
 * separator form — case-insensitive, anchored at the title's end.
 */
private val TRAILING_SEASON = Regex(
    pattern = """(?:[·•\-–—]\s*)?\b(?:(?:season|part|vol(?:ume)?|cour)s?\.?\s*)?(\d{1,4}|[IVXivx]{1,5})\s*$""",
    options = setOf(RegexOption.IGNORE_CASE),
)

/**
 * Splits the title into the eyebrow (the words BEFORE the last 1-2) + the
 * main (the last 1-2 words — 2 when there are 4+, else 1) + the season token
 * ("The Apothecary Diaries Season 3" → "The Apothecary" / "Diaries" / "3",
 * i.e. the handoff's "DIARIES · 3" read).
 */
internal fun splitHeroTitle(title: String): HeroTitleParts {
    val match = TRAILING_SEASON.find(title)
    val accent = match?.groupValues?.get(1)
    val base = if (match != null) {
        title.removeRange(match.range).trimEnd(' ', '·', '•', '-', '–', '—')
    } else {
        title
    }
    val words = base.split(' ').filter { it.isNotBlank() }
    return when {
        // A pure-token title ("86") would leave an empty main — keep the whole
        // title and drop the accent rather than rendering a bare " · N".
        base.isBlank() -> HeroTitleParts(eyebrow = "", main = title, accent = null)
        words.isEmpty() -> HeroTitleParts(eyebrow = "", main = base, accent = accent)
        words.size == 1 -> HeroTitleParts(eyebrow = "", main = words[0], accent = accent)
        else -> {
            val mainCount = if (words.size >= 4) 2 else 1
            HeroTitleParts(
                eyebrow = words.dropLast(mainCount).joinToString(" "),
                main = words.takeLast(mainCount).joinToString(" "),
                accent = accent,
            )
        }
    }
}

/** One hero dot — 24×34dp tap target; lit = a 22×7dp pill track carrying the
 * auto-advance countdown fill (restarts per cycle, waits out the hold-off). */
@Composable
private fun HeroDot(
    lit: Boolean,
    cycleId: Int,
    manualUntil: Long,
    onClick: () -> Unit,
) {
    val fill = remember { Animatable(0f) }
    LaunchedEffect(cycleId, lit) {
        if (!lit) {
            fill.snapTo(0f)
            return@LaunchedEffect
        }
        val holdOff = (manualUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        fill.snapTo(0f)
        if (holdOff > 0) delay(holdOff)
        fill.animateTo(1f, tween(HERO_CYCLE_MS.toInt(), easing = LinearEasing))
    }
    Box(
        modifier = Modifier
            .size(width = 24.dp, height = 34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = if (lit) 1f else 7f / 22f
                    scaleY = if (lit) 1f else 1f
                }
                .size(width = 22.dp, height = 7.dp)
                .clip(PillShape)
                .background(
                    if (lit) {
                        lerp(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.primary, 0.22f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (lit) {
                // The countdown fill — primary, left-to-right over the cycle.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fill.value)
                        .fillMaxHeight()
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

// ── Continue watching (ContinueWatching.tsx) ─────────────────────────────────

/**
 * The 188dp 16:9 episode cards: the left-to-right scrim, the EP badge
 * bottom-start, the 30dp play chip top-end, the 4dp progress bar hugging the
 * bottom (the 300ms-delayed 800ms width ease on first show), the 2-line
 * title, "N min left". BEHAVIOR: the art tap STARTS the episode playing
 * (the autoplay intent rides [HomeScreen]'s details callback); the title
 * block is a separate target that opens the details page. See all → History.
 */
@Composable
private fun HomeContinueSection(
    items: List<HomeContinueItem>,
    onSeeAll: () -> Unit,
    onPlay: (HomeContinueItem) -> Unit,
    onOpenDetails: (HomeContinueItem) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHead(
            title = "Continue watching",
            actionLabel = "See all",
            onAction = onSeeAll,
        )
        LazyRow(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items.size) { index ->
                val item = items[index]
                HomeContinueCard(
                    item = item,
                    onPlay = { onPlay(item) },
                    onOpenDetails = { onOpenDetails(item) },
                )
            }
        }
    }
}

@Composable
private fun HomeContinueCard(
    item: HomeContinueItem,
    onPlay: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    Column(modifier = Modifier.width(188.dp)) {
        // The art box IS the play target — the tap gets the user to the
        // episode PLAYING (the details key's autoPlayEpisode → auto-resolve
        // → the player, DetailsScreen's Phase-3 path).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(ThumbShape)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ThumbShape,
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onPlay),
        ) {
            AsyncImage(
                model = item.artUrl,
                contentDescription = "${item.title}, episode ${item.episodeNumber}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // The left-to-right scrim (the TSX gradient, verbatim stops).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to scrim(0.05f),
                                0.30f to scrim(0.05f),
                                0.58f to scrim(0.30f),
                                1f to scrim(0.82f),
                            ),
                        ),
                    ),
            )
            // The EP badge, bottom-start.
            Text(
                text = "EP ${item.episodeNumber}",
                fontFamily = RobotoFamily,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.42.sp,
                color = OnScrim,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 10.dp),
            )
            // The 30dp play chip, top-end.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(BadgeScrim),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = OnScrim,
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = 1.dp),
                )
            }
            // The 4dp progress bar hugging the bottom — animates in after
            // first paint (300ms delay + 800ms width ease).
            var armed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(300)
                armed = true
            }
            val fraction by animateFloatAsState(
                targetValue = if (armed) item.progressFraction.coerceIn(0f, 1f) else 0f,
                animationSpec = tween(800, easing = Motion.EasingEmphasized),
                label = "continueProgress",
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(ProgressTrack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(
                            RoundedCornerShape(topStart = 0.dp, topEnd = 2.dp, bottomStart = 0.dp, bottomEnd = 2.dp),
                        )
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        // The title block — a SEPARATE click target: opens the details page
        // (no autoplay) exactly like tapping the row in History.
        Column(
            modifier = Modifier
                // ROUND 153: the top gap 8→4dp — the user: the card-to-text
                // spacing "was taking up way too much space".
                .padding(top = 4.dp)
                .clickable(onClick = onOpenDetails),
        ) {
            Text(
                text = item.title,
                fontFamily = RobotoFamily,
                fontSize = 12.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                    ?: MaterialTheme.colorScheme.onBackground,
                // ROUND 153 (the user's explicit rule): ONE line, no more.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${item.minutesLeft} min left",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified }
                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                // ROUND 153: the title→time gap 2→1dp (the two lines hug).
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

// ── Airs today (AirsToday.tsx) ───────────────────────────────────────────────

/**
 * The vertical timeline on a 2dp rail: each row = the time node (the dot +
 * air time in one pill — the coral fill when released, the pulsing outline on
 * the NEXT episode) + the 48×68 thumb + the 2-line title + the state chip
 * (Watch / Aired / the countdown "in Xh Ym"). The VM guarantees EXACTLY 6
 * rows (2 released + next + 3 after). Tap a row → the details.
 *
 * ROUND 152: the section head + the rows render as separate HOME-list items
 * (keyed by the episode identity) so [Modifier.animateItem] animates the
 * release transition — the released top row fades out, the rest glide up, the
 * bottom gains the next episode. The VM re-emits the rows every minute (its
 * ticker recomputes the released flags), so the transition fires while the
 * user watches.
 */

/** The 1200ms reverse pulse the NEXT row shares — the outline's alpha and the node halo ride one transition. */
@Composable
private fun nodePulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "airsNodePulse")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "airsNodePulseAlpha",
    )
    return alpha
}

@Composable
private fun HomeAirsRow(
    item: HomeAirsItem,
    now: Long,
    isFirst: Boolean,
    isLast: Boolean,
    isNext: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // The rail trims to the node centers on the timeline's first/last rows.
    val railColor = MaterialTheme.colorScheme.outlineVariant
    val railWidthPx = with(density) { 2.dp.toPx() }
    val railEndInsetPx = with(density) { 20.dp.toPx() }
    // ROUND 152: the NEXT row's animated outline — a 1.5dp primary border whose
    // alpha rides the SAME 1200ms pulse as the node halo (the device report:
    // the next row was not highlighted properly). The halo keeps its glow.
    val pulsing = isNext && !item.released
    val pulse = if (pulsing) nodePulseAlpha() else 0f
    // ROUND 153 (the device round): the row outline is GONE — the user: the
    // highlight belongs ONLY to the release-time node (the pulsing halo stays).
    // THE RAIL CONTINUITY: the old segment drew INSIDE the 7dp-padded row (the
    // paddings gapped the line). The rail now draws on the UNPADDED outer Box —
    // one continuous line across the rows (the first/last insets still trim
    // the ends), with the padded content inside.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .defaultMinSize(minHeight = 44.dp)
            .clip(AirsRowShape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(78.dp)
                .fillMaxHeight()
                .drawBehind {
                    val top = if (isFirst) railEndInsetPx else 0f
                    val bottom = if (isLast) size.height - railEndInsetPx else size.height
                    drawRect(
                        color = railColor,
                        topLeft = Offset(size.width / 2f - railWidthPx / 2f, top),
                        size = Size(railWidthPx, (bottom - top).coerceAtLeast(0f)),
                    )
                },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp)
                .padding(start = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        // The node column: the dot-and-time pill rides the continuous rail.
        // ROUND 152: 68 → 78dp so "10:05 PM" NEVER wraps.
        Box(modifier = Modifier.width(78.dp).fillMaxHeight()) {
            val released = item.released
            val (hm, ampm) = remember(item.scheduledAt) { formatClock(item.scheduledAt) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                // The pulsing halo on the NEXT episode (node-pulse, shared alpha).
                // ROUND 154 (the device round): the swing DEEPENS (0.10→0.45 — the
                // report: "the time was highlighted, but very subtly") and the
                // pill's own background rides the pulse (a primary wash) so the
                // next node is unmistakable.
                if (pulsing) {
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 28.dp)
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f + 0.35f * pulse)),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(PillShape)
                        .background(
                            when {
                                released -> MaterialTheme.colorScheme.primary
                                pulsing -> MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.10f + 0.18f * pulse,
                                )
                                else -> MaterialTheme.colorScheme.background
                            },
                        )
                        .border(
                            BorderStroke(
                                1.5.dp,
                                when {
                                    released -> MaterialTheme.colorScheme.primary
                                    pulsing -> MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.30f + 0.55f * pulse,
                                    )
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                            PillShape,
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (released) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            ),
                    )
                    Text(
                        text = "$hm $ampm",
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.2.sp,
                        color = when {
                            released -> MaterialTheme.colorScheme.onPrimary
                            pulsing -> lerp(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                MaterialTheme.colorScheme.primary,
                                pulse,
                            )
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        // nowrap enforced — the 78dp column guarantees the fit.
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        // The 48×68 thumb (--r-thumb-tight 6dp).
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 68.dp)
                .clip(ThumbTightShape)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ThumbTightShape,
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = item.thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The 2-line title + the episode sub.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                    ?: MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
            )
            Text(
                text = if (item.released) "Ep ${item.episodeNumber} · released" else "Ep ${item.episodeNumber}",
                fontFamily = RobotoFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified }
                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // The state chip: Watch (released) / Aired (just hit 0) / countdown.
        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
            if (item.released) {
                StateChip(
                    background = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    label = "Watch",
                )
            } else {
                val remaining = item.scheduledAt - now
                if (remaining <= 0) {
                    StateChip(
                        background = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        label = "Aired",
                        leadingIcon = Icons.Filled.Check,
                    )
                } else {
                    // ROUND 152: the countdown chip on the glass-chip metrics —
                    // 26dp min-height, 10/4 padding, 11sp/700 + 0.02em (the
                    // report: the chips rendered too tall/thick).
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.defaultMinSize(minWidth = 74.dp, minHeight = 26.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "in ${countdownLabel(item.scheduledAt, now)}",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.22.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/** The Watch/Aired chip (secondary-container pill; ROUND 152: 26dp min-height,
 * 10/4 padding, 11sp/700 + 0.02em — the glass-chip metrics). */
@Composable
private fun StateChip(
    background: Color,
    contentColor: Color,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        shape = PillShape,
        color = background,
        modifier = Modifier.defaultMinSize(minHeight = 26.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.22.sp,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** lib/time.ts timeParts — "1:30" + "PM" for the timeline rail. */
private val clockFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

private fun formatClock(atMillis: Long): Pair<String, String> {
    val text = Instant.ofEpochMilli(atMillis)
        .atZone(ZoneId.systemDefault())
        .format(clockFormatter)
    val parts = text.split(' ')
    return if (parts.size >= 2) {
        parts[0] to parts.last().uppercase(Locale.US)
    } else {
        text to ""
    }
}

/** lib/time.ts countdown — "2h 05m" / "5m 12s" / "3d 4h". */
private fun countdownLabel(untilMillis: Long, nowMillis: Long): String {
    val total = (untilMillis - nowMillis).coerceAtLeast(0L)
    val days = total / 86_400_000
    val hh = (total % 86_400_000) / 3_600_000
    val mm = (total % 3_600_000) / 60_000
    val ss = (total % 60_000) / 1000
    val pad: (Long) -> String = { v -> if (v < 10L) "0$v" else "$v" }
    return when {
        days > 0 -> "${days}d ${hh}h"
        hh > 0 -> "${hh}h ${pad(mm)}m"
        else -> "${mm}m ${pad(ss)}s"
    }
}

// ── Trending rail (PosterRail.tsx TrendingRail) ──────────────────────────────

/**
 * The ranked poster rail: 104×156 posters, the rank badge, the one-line
 * title, the score with the star. See all → [HomeCatalogKey] (the catalog
 * agent owns the destination screen + its dispatch).
 */
@Composable
private fun HomeTrendingRail(
    ranked: List<Pair<Int, AniListAnime>>,
    onSeeAll: () -> Unit,
    onOpen: (AniListAnime) -> Unit,
) {
    if (ranked.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHead(
            title = "Trending this week",
            actionLabel = "See all",
            onAction = onSeeAll,
        )
        LazyRow(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ranked.size) { index ->
                val (rank, anime) = ranked[index]
                HomePosterCard(
                    anime = anime,
                    rank = rank,
                    onClick = { onOpen(anime) },
                )
            }
        }
    }
}

/**
 * One ranked poster card (104×156dp, --r-thumb 12dp): the rank badge
 * top-start, the one-line title, the score with the star. [rank] null = the
 * mood-chip rails' unranked variant.
 */
@Composable
private fun HomePosterCard(
    anime: AniListAnime,
    rank: Int?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(ThumbShape)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ThumbShape,
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = anime.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (rank != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(PillShape)
                        .background(BadgeScrim)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$rank",
                        fontFamily = RobotoFamily,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.42.sp,
                        color = OnScrim,
                    )
                }
            }
        }
        Text(
            text = anime.displayName,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            // ROUND 153: the explicit tight line-height kills the font's built-in
            // descent gap (the report: the title-rating distance "feels like a
            // glitch"); the title hugs the cover and the score hugs the title.
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        anime.averageScore?.let { score ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 0.dp),
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

// ── Suggested for you (SuggestedForYou.tsx) ──────────────────────────────────

/**
 * The suggestion head (refresh + pause icon buttons) + the [HomeSuggestionField]
 * honeycomb. ROUND 152: the cover pool is HOISTED to the ViewModel
 * ([covers] — [HomeViewModel.fieldCovers], fetched once, surviving re-entry);
 * the field takes it as a param instead of refetching. Tap routing rides
 * [onOpenCover] (an AniList id that goes through the home's [onNavigate]
 * details path, ad gate included).
 */
@Composable
private fun HomeSuggestedSection(
    covers: List<HomeFieldCover>,
    refreshToken: Int,
    onRefresh: () -> Unit,
    onOpenCover: (Int) -> Unit,
) {
    var paused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHead(
            title = "Suggested for you",
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconActionButton(
                        contentDescription = "Refresh suggestions",
                        icon = Icons.Filled.Refresh,
                        // The head's refresh spins once per token bump.
                        spinToken = refreshToken,
                        onClick = onRefresh,
                    )
                    IconActionButton(
                        contentDescription = if (paused) "Resume auto-scroll" else "Pause auto-scroll",
                        icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        active = paused,
                        onClick = { paused = !paused },
                    )
                }
            },
        )
        HomeSuggestionField(
            covers = covers,
            paused = paused,
            refreshToken = refreshToken,
            onOpenCover = onOpenCover,
        )
    }
}

/**
 * The 44dp round icon button (.icon-btn): 1dp outline border, surface
 * container bg, 18dp glyph. [active] = the pressed state (primary-tint bg +
 * primary glyph). [spinToken] bumps → the glyph spins once (520ms).
 */
@Composable
private fun IconActionButton(
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean = false,
    spinToken: Int? = null,
    onClick: () -> Unit = {},
) {
    val spin = remember { Animatable(0f) }
    LaunchedEffect(spinToken) {
        if (spinToken != null && spinToken > 0) {
            spin.snapTo(0f)
            spin.animateTo(1f, tween(520, easing = Motion.EasingEmphasized))
        }
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (active) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                ),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = spin.value * 360f },
        )
    }
}

// ── Browse by mood (MoodChips.tsx) ───────────────────────────────────────────

/**
 * "Surprise me" (the dice icon — a COMPLETELY random shuffle of the trending
 * payload, NOT genre-based) + the genre chips (v1: the chip filters the
 * TRENDING pool by that genre locally). ROUND 152: the head's "All genres"
 * action opens the category index ([HomeGenresKey] → HomeGenresScreen). The
 * mood-chip GENRE taps keep the local trending filter; the FULL genre browse
 * lives on the index. Tapping the active chip again hides the rail.
 *
 * ROUND 152 behavior fixes (the device report):
 * - "Surprise me" is the ACTIVE chip on entry ([activeMood] initializes to
 *   it at the HOME level, so the rail also survives far-scroll disposal).
 * - a chip tap auto-scrolls the feed (the home list) so the section lands
 *   at the viewport top — the caller's [onSelect] handles that.
 * - the chip rail re-anchors to its START whenever the active chip changes
 *   (and on section re-entry — the fresh composition starts at 0 anyway).
 */
@Composable
private fun HomeMoodChips(
    trending: List<AniListAnime>,
    activeMood: String,
    surpriseSeed: Int,
    onSelect: (String) -> Unit,
    onBrowseAll: () -> Unit,
    onOpen: (AniListAnime) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHead(
            title = "Browse by mood",
            actionLabel = "All genres",
            onAction = onBrowseAll,
        )
        val chipListState = rememberLazyListState()
        // ROUND 153: the tapped chip CENTERS itself in the rail (the smooth
        // scroll needs a scope in this composable).
        val scope = rememberCoroutineScope()
        // ROUND 154 (the device round): the reset-on-active-change is GONE — it
        // yanked the rail back to the left AFTER the centering (the report:
        // tapping the rightmost genre scrolled everything to the left). The
        // centering below is the only rail motion on tap.
        LazyRow(
            state = chipListState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = MOOD_SURPRISE) {
                MoodChip(
                    label = MOOD_SURPRISE,
                    icon = Icons.Filled.Casino,
                    // ROUND 153: Surprise me is the ALWAYS-HIGHLIGHTED brand
                    // button (its own active style never turns off).
                    active = true,
                    onClick = { onSelect(MOOD_SURPRISE) },
                )
            }
            itemsIndexed(MOOD_GENRES, key = { _, genre -> genre }) { index, genre ->
                val chipIndex = index + 1 // after the Surprise item
                MoodChip(
                    label = genre,
                    icon = null,
                    active = activeMood == genre,
                    onClick = {
                        onSelect(genre)
                        // ROUND 153 (the device round): the tapped chip CENTERS in
                        // the rail — the old behavior scrolled to the first genre.
                        scope.launch {
                            chipListState.animateScrollToItem(chipIndex)
                        }
                    },
                )
            }
        }
        // Keyed on the active chip so switching rebuilds the rail fresh (the
        // poster rail's scroll resets with the chip rail's).
        key(activeMood) {
            when (activeMood) {
                "" -> Unit
                // The completely random set — reshuffled per fresh Surprise tap.
                MOOD_SURPRISE -> HomeMoodRail(
                    title = MOOD_SURPRISE,
                    items = remember(trending, surpriseSeed) {
                        trending.shuffled(Random(surpriseSeed))
                    },
                    onOpen = onOpen,
                )
                // v1: the local trending-pool filter (the payload carries genres).
                // The LIVE genre query lands with the catalog agent's work.
                else -> HomeMoodRail(
                    title = activeMood,
                    items = remember(activeMood, trending) {
                        trending.filter { current -> current.genres?.contains(activeMood) == true }
                    },
                    onOpen = onOpen,
                )
            }
        }
    }
}

/** One active mood's poster rail — keyed so switching chips rebuilds fresh. */
@Composable
private fun HomeMoodRail(
    title: String,
    items: List<AniListAnime>,
    onOpen: (AniListAnime) -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            text = "No $title titles trending right now.",
            fontFamily = RobotoFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 10.dp),
        )
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            HomeSectionHead(title = title)
            LazyRow(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items.size) { index ->
                    val anime = items[index]
                    HomePosterCard(
                        anime = anime,
                        rank = null,
                        onClick = { onOpen(anime) },
                    )
                }
            }
        }
    }
}

/** The mood chip (.mood-chip): 44dp min-height pill, secondary-container bg;
 * active = primary bg + on-primary text. */
@Composable
private fun MoodChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = PillShape,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        border = if (active) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minHeight = 44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.13.sp,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
    }
}

// ── Error state (kept from the round-148 home — the retry idiom) ─────────────

/**
 * Home's error state — the retry EmptyState idiom (no dead-end text). Reached
 * only when EVERYTHING failed: the trending fetch settled with nothing cached
 * AND both personal rails empty.
 */
@Composable
private fun HomeErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                title = "Failed to load",
                description = message,
                icon = Icons.Filled.Warning,
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                onClick = onRetry,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Retry",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
