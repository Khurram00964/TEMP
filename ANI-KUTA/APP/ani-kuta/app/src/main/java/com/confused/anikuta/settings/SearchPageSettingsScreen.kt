package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.ResultTitleStyle
import com.confused.anikuta.core.preferences.SearchPreferences

/**
 * ROUND 170 (the device report): the Search page settings — REBUILT on the
 * Details-background anatomy (the user's pointer): the proper SECTION
 * separation (SettingsSectionLabel + the card groups), the ONE-LINE
 * descriptions (the rule), the accent-highlighted section headings, the
 * background fill.
 */
@Composable
fun SearchPageSettingsScreen(
    onBack: () -> Unit,
    searchPreferences: SearchPreferences,
) {
    val style by searchPreferences.resultTitleStyleFlow.collectAsState()
    val showRating by searchPreferences.showRatingBadgeFlow.collectAsState()
    val showDetails by searchPreferences.showDetailsLineFlow.collectAsState()
    val gridColumns by searchPreferences.gridColumnsFlow.collectAsState()
    val showRecents by searchPreferences.showRecentsFlow.collectAsState()

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Search page",
                collapsed = collapsed,
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── SECTION: Data sources (ROUND 184 / CHUNK 2 — the
                    // user's order: "the ability in the settings to pick
                    // which data sources to turn on and which to turn off") ──
                    item { SettingsSectionLabel("Data sources") }
                    item {
                        SettingsCard {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(
                                    text = "Enabled sources",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.padding(2.dp))
                                Text(
                                    text = "The search picker offers only the enabled ones. At least one stays on.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.padding(10.dp))
                                val enabledSet by searchPreferences.enabledDataSourcesFlow.collectAsState()
                                SourceToggleRow("AniList", "Anime", com.confused.anikuta.feature.animesearch.R.drawable.ic_source_anilist, "anilist", enabledSet) { id, on ->
                                    searchPreferences.updateDataSourceEnabled(id, on)
                                }
                                SourceToggleRow("TMDB", "Movies & series", com.confused.anikuta.feature.animesearch.R.drawable.ic_source_tmdb, "tmdb", enabledSet) { id, on ->
                                    searchPreferences.updateDataSourceEnabled(id, on)
                                }
                                SourceToggleRow("TVMaze", "Series & schedule", com.confused.anikuta.feature.animesearch.R.drawable.ic_source_tvmaze, "tvmaze", enabledSet) { id, on ->
                                    searchPreferences.updateDataSourceEnabled(id, on)
                                }
                                SourceToggleRow("MAL", "Anime", com.confused.anikuta.feature.animesearch.R.drawable.ic_source_mal, "mal", enabledSet) { id, on ->
                                    searchPreferences.updateDataSourceEnabled(id, on)
                                }
                                SourceToggleRow("Simkl", "Movies & TV", com.confused.anikuta.feature.animesearch.R.drawable.ic_source_simkl, "simkl", enabledSet) { id, on ->
                                    searchPreferences.updateDataSourceEnabled(id, on)
                                }
                            }
                        }
                    }

                    // ── SECTION: Result cards ──
                    item { SettingsSectionLabel("Result cards") }

                    // The title placement choice (the two options in one card).
                    item {
                        SettingsCard {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            ) {
                                Text(
                                    text = "Title placement",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.padding(2.dp))
                                Text(
                                    text = "Where the title sits on the card.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.padding(10.dp))
                                ResultTitleStyle.entries.forEach { candidate ->
                                    val selected = style == candidate
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .then(
                                                if (selected) {
                                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                RoundedCornerShape(12.dp),
                                            )
                                            .clickable { searchPreferences.updateResultTitleStyle(candidate) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = candidate.displayName,
                                                fontFamily = RobotoFamily,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = candidate.subtitle,
                                                fontFamily = RobotoFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // The card-info toggles (the SwitchCards).
                    item {
                        SwitchCard(
                            title = "Show ratings",
                            subtitle = "The score pill on the cards",
                            checked = showRating,
                            onCheckedChange = { searchPreferences.updateShowRatingBadge(it) },
                        )
                    }
                    item {
                        SwitchCard(
                            title = "Show details line",
                            subtitle = "The year beside the title",
                            checked = showDetails,
                            onCheckedChange = { searchPreferences.updateShowDetailsLine(it) },
                        )
                    }

                    // ── SECTION: Layout ──
                    item { SettingsSectionLabel("Layout") }

                    item {
                        SettingsCard {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            ) {
                                Text(
                                    text = "Grid density",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.padding(2.dp))
                                Text(
                                    text = "Covers per row.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.padding(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(0 to "Auto", 1 to "1", 2 to "2", 3 to "3", 4 to "4", 5 to "5").forEach { (count, label) ->
                                        val selected = gridColumns == count
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(
                                                    if (selected) {
                                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                                    } else {
                                                        Modifier
                                                    },
                                                )
                                                .background(
                                                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    RoundedCornerShape(10.dp),
                                                )
                                                .clickable { searchPreferences.updateGridColumns(count) }
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                text = label,
                                                fontFamily = RobotoFamily,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SwitchCard(
                            title = "Show recent searches",
                            subtitle = "The recents strip above results",
                            checked = showRecents,
                            onCheckedChange = { searchPreferences.updateShowRecents(it) },
                        )
                    }
                }

                ScrollBlurOverlay(
                    scrollOffset = {
                        if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else lazyListState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

// ── ROUND 170: the local copies of the settings components (the
//    per-file private pattern — the Details-background screen's anatomy) ──

/**
 * ROUND 184 (CHUNK 2): one DATA-SOURCE toggle row — the real logo tile, the
 * one-line name + subtitle, the Switch. The at-least-one guard lives in
 * SearchPreferences.updateDataSourceEnabled (the last source refuses OFF).
 */
@Composable
private fun SourceToggleRow(
    name: String,
    subtitle: String,
    logoRes: Int,
    sourceId: String,
    enabledSet: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    val checked = sourceId in enabledSet
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(logoRes),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle(sourceId, it) })
    }
}

@Composable
private fun SwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp),
            ),
    ) {
        content()
    }
}
