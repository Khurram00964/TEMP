package com.confused.anikuta.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.confused.anikuta.core.common.HapticHelper
import kotlinx.coroutines.delay

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 176 (the user's EXACT model — this replaces the round-175 shape):
//  THE PULL INDICATOR'S THREE STATES:
//  • THE SMALL DRAG — ONE dot cycling the three colors, growing with the
//    drag ("when they are smaller… only a single dot switching between the
//    three colors… when it fully becomes the biggest one… it will refresh").
//  • THE FULL DRAG (the threshold reached) — THREE dots in a row, STATIC
//    ("they won't be having any animation").
//  • THE LIFT (the refresh active) — the three dots JUMPING ("the animation
//    would only start after I have lifted my finger").
//  THE THRESHOLD CROSSING fires the vibration (the details page's
//  HapticHelper pattern — the user's explicit order).
// ════════════════════════════════════════════════════════════════════════════

/**
 * The pull's refresh threshold — ROUND 178 (the audit, S1-A): M3 1.3.1's
 * onRelease commits the refresh when the RAW pull crosses its 80dp
 * threshold, which reads as distanceFraction ≈ 0.5 (the state halves the
 * raw distance while mapping it to the fraction). The old 0.92 let a
 * medium pull (0.5-0.92) refresh on lift with ONLY the single dot — no
 * static three, no vibration: the model broke exactly at the commit point.
 * At 0.5 the three static dots + the haptic fire exactly when a lift WILL
 * refresh — the visual model and the actual commit agree everywhere.
 */
private const val PULL_THRESHOLD = 0.5f

/**
 * The capsule-hosted pull/refresh indicator.
 *
 * @param dragFraction 0..1 — the pull's live progress (the caller reads the
 *   M3 state's distanceFraction); 1f while refreshing.
 * @param refreshing true = the lift happened (the three dots jumping).
 */
@Composable
fun RefreshCapsuleIndicator(
    modifier: Modifier = Modifier,
    dragFraction: Float = 1f,
    refreshing: Boolean = true,
) {
    val context = LocalContext.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // THE THRESHOLD VIBRATION — fires once per upward crossing (the user's
    // "implement a vibration here, just like the details page").
    var hapticFired by remember { mutableStateOf(false) }
    LaunchedEffect(dragFraction, refreshing) {
        if (!refreshing && dragFraction >= PULL_THRESHOLD && !hapticFired) {
            HapticHelper.stageCross(context)
            hapticFired = true
        }
        if (dragFraction < PULL_THRESHOLD / 2f) hapticFired = false
    }

    // The capsule's follow — the bouncy spring toward the drag distance (the
    // resting spot = the full pull).
    val eased by animateFloatAsState(
        targetValue = dragFraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "capsuleFollow",
    )

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
            .offset { IntOffset(0, (eased * 16.dp.roundToPx()).toInt()) }
            .scale(if (refreshing) 1f else 0.6f + 0.4f * eased),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            when {
                // ── THE LIFT (the refresh active): the three dots JUMPING ──
                refreshing -> JumpingDotsIndicator(dotSize = 8.dp, jumpHeight = 8.dp)

                // ── THE FULL DRAG: the three dots STATIC in a row ──
                dragFraction >= PULL_THRESHOLD -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val colors = dotColors()
                        colors.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color, CircleShape),
                            )
                            if (index < colors.lastIndex) Spacer(Modifier.width(6.dp))
                        }
                    }
                }

                // ── THE SMALL DRAG: ONE dot cycling the colors, growing ──
                else -> {
                    val cycleTransition = rememberInfiniteTransition(label = "cycle")
                    val cycle by cycleTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 3f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 1400
                                0f at 0 using LinearEasing
                                3f at 1400 using LinearEasing
                            },
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "cycleFloat",
                    )
                    val colors = dotColors()
                    val activeColor = colors[(cycle.toInt()).coerceIn(0, 2)]
                    val grow = (dragFraction / PULL_THRESHOLD).coerceIn(0.25f, 1f)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(0.5f + 0.5f * grow)
                            .background(activeColor, CircleShape),
                    )
                }
            }
        }
    }
}

/** The three cycling colors (the scheme's primary/secondary/tertiary). */
@Composable
private fun dotColors(): List<androidx.compose.ui.graphics.Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.tertiary,
)

/** The load-more's resting capsule (the same language, always refreshing). */
@Composable
fun LoadingCapsuleIndicator(modifier: Modifier = Modifier, dotSize: Dp = 8.dp) {
    Row(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JumpingDotsIndicator(dotSize = dotSize, jumpHeight = 8.dp)
    }
}
