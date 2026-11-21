package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * ROUND 165: the SEARCH PAGE's appearance settings (the user's path:
 * Settings → Appearance → Search page). The result card's title placement
 * applies to EVERY search grid (AniList, TMDB, TVmaze, extensions) — the
 * user's consistency order.
 */
@Composable
fun SearchPageSettingsScreen(
    onBack: () -> Unit,
    searchPreferences: SearchPreferences,
) {
    val style by searchPreferences.resultTitleStyleFlow.collectAsState()
    // ROUND 168 (Phase G): the user's customization set.
    val showRating by searchPreferences.showRatingBadgeFlow.collectAsState()
    val showDetails by searchPreferences.showDetailsLineFlow.collectAsState()
    val gridColumns by searchPreferences.gridColumnsFlow.collectAsState()
    val showRecents by searchPreferences.showRecentsFlow.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                CollapsingHeader(
                    title = "Search page",
                    collapsed = collapsed,
                    onBack = onBack,
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "Result card title",
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.padding(2.dp))
                    Text(
                        text = "Where the title sits on every search result card.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.padding(12.dp))
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ResultTitleStyle.entries.forEach { candidate ->
                        val selected = style == candidate
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable { searchPreferences.updateResultTitleStyle(candidate) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.displayName,
                                    fontFamily = RobotoFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = candidate.subtitle,
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            // ── ROUND 168 (Phase G): the card info toggles ──
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "Card info",
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.padding(2.dp))
                    Text(
                        text = "What shows on every search result card.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.padding(12.dp))
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingToggleRow(
                        title = "Show ratings",
                        subtitle = "The score badge on the result cards.",
                        checked = showRating,
                        onToggle = { searchPreferences.updateShowRatingBadge(it) },
                    )
                    SettingToggleRow(
                        title = "Show details line",
                        subtitle = "The year and the type meta under the title.",
                        checked = showDetails,
                        onToggle = { searchPreferences.updateShowDetailsLine(it) },
                    )
                    SettingToggleRow(
                        title = "Show recent searches",
                        subtitle = "The recents strip above the results.",
                        checked = showRecents,
                        onToggle = { searchPreferences.updateShowRecents(it) },
                    )
                }
            }

            // ── ROUND 168 (Phase G): the grid density ──
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "Grid density",
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.padding(2.dp))
                    Text(
                        text = "How many covers per row.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.padding(12.dp))
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(0 to "Adaptive", 1 to "1", 2 to "2", 3 to "3", 4 to "4", 5 to "5").forEach { (count, label) ->
                        val selected = gridColumns == count
                        Row(
                            modifier = Modifier
                                .weight(1f)
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
                                .clickable { searchPreferences.updateGridColumns(count) }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = label,
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        ScrollBlurOverlay(
            scrollOffset = {
                if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                else listState.firstVisibleItemScrollOffset.toFloat()
            },
            backgroundColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

// ── ROUND 168 (Phase G): the toggle row (the settings' standard anatomy) ──
@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onToggle)
    }
}
