package com.confused.anikuta.feature.animesearch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * ROUND 169 (the v1.1.104 device order): the METADATA DETAILS page — the
 * details OF the tapped data-source result, loaded from THAT source's data
 * (the tap no longer switches to the CS search — the user's explicit
 * redirect). The design-language anatomy: the pinned collapsing header
 * (2.1), the hero cover, the meta row, the overview, the actions.
 *
 * The actions: WATCH → the CS-first playback route (the search screen's
 * staged title — the provider sheet auto-opens there); TRACK → the
 * serving tracker (MAL for the anime, Simkl otherwise) — the fire-and-forget
 * plan-to-watch seed.
 */
@Composable
fun MetadataDetailsScreen(
    onBack: () -> Unit,
    key: MetadataDetailsKey,
    viewModel: MetadataDetailsViewModel,
    onWatchViaCs: (MetadataDetailsKey) -> Unit,
) {
    val trackState by viewModel.trackState.collectAsState()
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            CollapsingHeader(
                title = MetadataDetailsKey.displaySourceOf(key.source),
                collapsed = scrollState.value > 20,
                onBack = onBack,
            )

            // ── The hero row: the cover + the title/meta column ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    if (key.posterUrl != null) {
                        AsyncImage(
                            model = key.posterUrl,
                            contentDescription = key.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (key.rating != null) {
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shadowElevation = 2.dp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        ) {
                            Text(
                                text = "%.1f".format(key.rating),
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = key.title,
                        fontFamily = RobotoFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val meta = buildString {
                        append(MetadataDetailsKey.displaySourceOf(key.source))
                        key.year?.let { append("  ·  $it") }
                        key.mediaType.takeIf { it.isNotBlank() && it != "simkl" }?.let { append("  ·  $it") }
                    }
                    Text(
                        text = meta,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    // ── The actions ──
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onWatchViaCs(key) },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Watch", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.trackOnServingTracker() },
                            shape = RoundedCornerShape(10.dp),
                            enabled = trackState != MetadataDetailsViewModel.TrackState.Saving,
                        ) {
                            when (trackState) {
                                MetadataDetailsViewModel.TrackState.Saving ->
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                else -> Text(
                                    text = when (trackState) {
                                        MetadataDetailsViewModel.TrackState.Tracked -> "Tracked ✓"
                                        MetadataDetailsViewModel.TrackState.Failed -> "Retry"
                                        else -> "Track on ${viewModel.trackTrackerName}"
                                    },
                                    fontFamily = RobotoFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    }
                    when (trackState) {
                        MetadataDetailsViewModel.TrackState.Failed -> Text(
                            text = "Couldn't track it — check the connection and retry.",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> {}
                    }
                }
            }

            // ── The overview ──
            val overviewText = key.overview
            if (!overviewText.isNullOrBlank()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = "Overview",
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = overviewText,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(110.dp)) // the bottom-bar clearance
        }
    }
}
