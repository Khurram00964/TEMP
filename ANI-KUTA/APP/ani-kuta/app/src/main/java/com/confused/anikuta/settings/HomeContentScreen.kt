package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.HomeContentKind
import com.confused.anikuta.core.preferences.HomePreferences

/**
 * ROUND 164 (STEP-3d): the HOME CONTENT selection — the user's order: "a big
 * bold UI kind of selection" for what shows on the home page, applied
 * INSTANTLY.
 *
 * Four large cards in a 2×2 grid (Anime / Movies / Series / Donghua) — a tap
 * toggles the kind and the home re-derives its merged feed live (the VM
 * observes the kinds flow; no screen reload). The layout-parity rule holds:
 * the home's sections stay, only the content changes.
 */
@Composable
fun HomeContentScreen(
    onBack: () -> Unit,
    homePreferences: HomePreferences,
) {
    val enabled by homePreferences.contentKindsFlow.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                CollapsingHeader(
                    title = "Home content",
                    collapsed = collapsed,
                    onBack = onBack,
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "What shows on your home page",
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "Mix anything together — the layout stays, the content follows. " +
                            "Changes apply instantly.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(16.dp))
                }
            }
            item {
                // Four fixed cards — two rows of two (no nested scrolling).
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.weight(1f)) { ContentKindCell(HomeContentKind.ANIME, enabled, homePreferences) }
                        Box(modifier = Modifier.weight(1f)) { ContentKindCell(HomeContentKind.MOVIES, enabled, homePreferences) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.weight(1f)) { ContentKindCell(HomeContentKind.SERIES, enabled, homePreferences) }
                        Box(modifier = Modifier.weight(1f)) { ContentKindCell(HomeContentKind.DONGHUA, enabled, homePreferences) }
                    }
                }
            }
            item {
                Text(
                    text = if (enabled.size == 1) {
                        "Showing ${enabled.first().displayName} only"
                    } else {
                        "Mixing ${enabled.size} sources — ${enabled.joinToString(\" + \") { it.displayName }}"
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
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

@Composable
private fun HomeContentCard(
    kind: HomeContentKind,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .aspectRatio(0.85f)
            .then(border)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(20.dp),
            )
            .clickable { onToggle() }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.22f else 0.12f),
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = kind.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.size(6.dp))
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Column {
            Text(
                text = kind.displayName,
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = kind.subtitle,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

private val HomeContentKind.icon: ImageVector
    get() = when (this) {
        HomeContentKind.ANIME -> Icons.Filled.AutoAwesome
        HomeContentKind.MOVIES -> Icons.Filled.Theaters
        HomeContentKind.SERIES -> Icons.Filled.Tv
        HomeContentKind.DONGHUA -> Icons.Filled.Language
    }

@Composable
private fun androidx.compose.foundation.layout.RowScope.ContentKindCell(
    kind: HomeContentKind,
    enabled: Set<HomeContentKind>,
    homePreferences: HomePreferences,
) {
    HomeContentCard(
        kind = kind,
        selected = kind in enabled,
        onToggle = { homePreferences.toggleContentKind(kind) },
        modifier = Modifier.fillMaxWidth(),
    )
}
