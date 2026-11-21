package com.confused.anikuta.feature.animedetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * ROUND 168 (the tracker redo, Phase D): the >1-LINKED TRACKER PICKER —
 * the spec 05-T-2's 3-dot surface. When MORE than one tracker is linked,
 * the details' track entry point opens THIS; the pick targets the track
 * action (the TrackSheet drives the chosen tracker).
 *
 * The rows: AniList (Anime and manga) + MyAnimeList (Anime and manga) —
 * the branded-mark tiles (the DataSourcePickerSheet's anatomy; the logo
 * assets land with the search picker's pass).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TrackerPickerSheet(
    anilistUsername: String,
    malUsername: String,
    simklUsername: String,
    onPick: (com.confused.anikuta.core.trackerapi.TrackerType) -> Unit,
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
                text = "Track on",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            // ROUND 170 (the device order): the rows show the USERNAME ONLY
            // below the name (the role text is gone); Simkl joins the picker
            // when linked. The order: AniList → MAL → Simkl.
            TrackerPickerRow(
                name = "AniList",
                subtitle = anilistUsername,
                logoRes = com.confused.anikuta.feature.animedetails.R.drawable.ic_tracker_anilist,
                onClick = { onPick(com.confused.anikuta.core.trackerapi.TrackerType.ANILIST) },
            )
            Spacer(Modifier.height(4.dp))
            TrackerPickerRow(
                name = "MyAnimeList",
                subtitle = malUsername,
                logoRes = com.confused.anikuta.feature.animedetails.R.drawable.ic_tracker_mal,
                onClick = { onPick(com.confused.anikuta.core.trackerapi.TrackerType.MAL) },
            )
            Spacer(Modifier.height(4.dp))
            TrackerPickerRow(
                name = "Simkl",
                subtitle = simklUsername,
                logoRes = com.confused.anikuta.feature.animedetails.R.drawable.ic_tracker_simkl,
                onClick = { onPick(com.confused.anikuta.core.trackerapi.TrackerType.SIMKL) },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TrackerPickerRow(
    name: String,
    subtitle: String,
    logoRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF2A2A2A), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(logoRes),
                contentDescription = name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontFamily = RobotoFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
