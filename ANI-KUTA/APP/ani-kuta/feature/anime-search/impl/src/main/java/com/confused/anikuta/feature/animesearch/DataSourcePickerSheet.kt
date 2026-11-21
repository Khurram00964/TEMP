package com.confused.anikuta.feature.animesearch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * ROUND 163 (STEP-3): the DATA-SOURCE picker — the bottom-up sheet the
 * "Data source" segment opens (the ExtensionSourcePickerSheet's shell
 * cloned: ModalBottomSheet skipPartiallyExpanded, dragHandle = null, the
 * 0.78-height cap, icon+name+check rows). Rows: AniList (always) + TMDB /
 * TVmaze (grayed when the user turned the source OFF in Settings).
 *
 * The picker SELECTS a backend; it never plays anything — data-source results
 * are metadata and hand off to the CloudStream playback search.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DataSourcePickerSheet(
    current: DataSourceKind,
    tmdbEnabled: Boolean,
    tvmazeEnabled: Boolean,
    onSelect: (DataSourceKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.78f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Pick a data source",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    listOf(
                        // ROUND 165 (the device order): the simplest subtitles
                        // (no GraphQL/keyless tech-speak) + the branded marks.
                        DataSourceRow(DataSourceKind.ANILIST, "AniList", "Anime", Icons.Filled.Search, androidx.compose.ui.graphics.Color(0xFF02A9FF), enabled = true, logoRes = com.confused.anikuta.feature.animesearch.R.drawable.ic_source_anilist),
                        DataSourceRow(DataSourceKind.TMDB, "TMDB", "Movies & series", Icons.Filled.Theaters, androidx.compose.ui.graphics.Color(0xFF0D253F), enabled = tmdbEnabled),
                        DataSourceRow(DataSourceKind.TVMAZE, "TVMaze", "Series & schedule", Icons.Filled.Tv, androidx.compose.ui.graphics.Color(0xFFD8B938), enabled = tvmazeEnabled),
                        // ROUND 168 (Phase E): the tracker-backed sources —
                        // their searches are app/client-id auth (they work
                        // whether or not the user LINKED the tracker).
                        DataSourceRow(DataSourceKind.MAL, "MyAnimeList", "Anime", Icons.Filled.Search, androidx.compose.ui.graphics.Color(0xFF2E51A2), enabled = true, logoRes = com.confused.anikuta.feature.animesearch.R.drawable.ic_source_mal),
                        DataSourceRow(DataSourceKind.SIMKL, "Simkl", "Movies & TV", Icons.Filled.Search, androidx.compose.ui.graphics.Color(0xFF0D0D0D), enabled = true, logoRes = com.confused.anikuta.feature.animesearch.R.drawable.ic_source_simkl),
                    ),
                    key = { it.kind.id },
                ) { row ->
                    DataSourcePickerRow(
                        row = row,
                        selected = current == row.kind,
                        onSelect = { onSelect(row.kind) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private data class DataSourceRow(
    val kind: DataSourceKind,
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val brandColor: androidx.compose.ui.graphics.Color,
    val enabled: Boolean,
    // ROUND 169 (the device order): the REAL favicon renders when the asset
    // exists; the branded tile stays the fallback (the TMDB/TVmaze assets
    // need proper sourcing - the CDN grabs failed).
    val logoRes: Int? = null,
)

@Composable
private fun DataSourcePickerRow(
    row: DataSourceRow,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val containerAlpha = if (row.enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(containerAlpha)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            )
            .clickable(enabled = row.enabled) { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ROUND 165: the BRANDED mark — each source's own color + initial
        // (the device order: proper cover images, not the generic icons).
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(row.brandColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (row.logoRes != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(row.logoRes),
                    contentDescription = row.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = row.name.first().toString(),
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name,
                fontFamily = RobotoFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = row.subtitle,
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
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
