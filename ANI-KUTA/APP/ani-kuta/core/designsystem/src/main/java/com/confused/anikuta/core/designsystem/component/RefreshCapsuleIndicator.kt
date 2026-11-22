package com.confused.anikuta.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.confused.anikuta.core.common.HapticHelper

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 175 (F3+F4 — the user's order): THE REFRESH CAPSULE — the app's ONE
//  loading-activity language, upgraded from the bare dots:
//  • THE CAPSULE — the AppToast pill's anatomy (the elevated surface, the
//    22dp shape, the border) hosting the jumping dots — "the three bare dots
//    floating over the content look unfinished".
//  • THE FOLLOW — the capsule springs toward the drag distance (the M3
//    default indicator's lost behavior: "the drag shows nothing until
//    release"); the threshold crossing fires the HapticHelper stage haptic.
//  • ONE COMPONENT for the pull indicator AND the load-more footer
//    (dragFraction = 1f = the resting/refreshing position).
// ════════════════════════════════════════════════════════════════════════════

/**
 * The capsule-hosted jumping dots — the app's loading indicator.
 *
 * @param dragFraction 0..1 — the pull's drag progress (the capsule follows
 *   it with a bouncy spring, the threshold haptic fires on the crossing);
 *   the resting/refreshing position is 1f (the load-more passes nothing).
 * @param visible false collapses the capsule (the caller's gate).
 */
@Composable
fun RefreshCapsuleIndicator(
    modifier: Modifier = Modifier,
    dragFraction: Float = 1f,
) {
    // The bouncy follow — the release springs the capsule into its resting
    // spot while the dots jump (the F3 order).
    val eased by animateFloatAsState(
        targetValue = dragFraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "capsuleFollow",
    )

    // The threshold haptic — the edge fires ONCE per crossing (the M3
    // default's snap, restored).
    val context = LocalContext.current
    var hapticFired by remember { mutableStateOf(false) }
    LaunchedEffect(dragFraction) {
        if (dragFraction >= 0.95f && !hapticFired) {
            HapticHelper.stageCross(context)
            hapticFired = true
        }
        if (dragFraction < 0.5f) hapticFired = false
    }

    // The capsule — the AppToast pill's anatomy (the user's reference).
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (darkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        modifier = modifier
            .offset { IntOffset(0, (eased * 20.dp.roundToPx()).toInt()) }
            .scale(0.55f + 0.45f * eased),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            JumpingDotsIndicator(dotSize = 8.dp, jumpHeight = 8.dp)
        }
    }
}
