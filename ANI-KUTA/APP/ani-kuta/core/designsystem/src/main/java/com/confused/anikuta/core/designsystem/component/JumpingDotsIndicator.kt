package com.confused.anikuta.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 174 (T1.4 — the user's order): THE JUMPING DOTS — the app's ONE
//  loading-activity animation, replacing the bare spinners on the
//  pull-refresh indicator AND the load-more footer (one consistent
//  language). THREE colorful dots (the scheme's primary/secondary/tertiary)
//  jumping up and down with a staggered rhythm; the bounded lifetime comes
//  from the CALLER (the indicator renders while isRefreshing/pulling — the
//  completion signals remove it).
// ════════════════════════════════════════════════════════════════════════════

/**
 * The colorful jumping dots — the loading indicator.
 *
 * @param dotSize The dot diameter (10dp default).
 * @param jumpHeight The jump distance (10dp default).
 * @param periodMillis One full jump cycle (720ms default; the dots stagger
 *   by a third each).
 */
@Composable
fun JumpingDotsIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 10.dp,
    jumpHeight: Dp = 12.dp,
    periodMillis: Int = 720,
) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
    )
    Row(
        modifier = modifier.height(dotSize + jumpHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEachIndexed { index, color ->
            // The staggered jump: each dot's cycle starts a third of the
            // period after the previous — the wave.
            val transition = rememberInfiniteTransition(label = "dot$index")
            val jump by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = periodMillis
                        0f at 0 using LinearEasing
                        -1f at (periodMillis / 2) using LinearEasing
                        0f at periodMillis using LinearEasing
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(
                        index * periodMillis / 3,
                    ),
                ),
                label = "jump$index",
            )
            // M3 Surface requires a content lambda — the plain Box carries
            // the circle.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(dotSize)
                    .offset { IntOffset(0, (jump * jumpHeight.toPx()).toInt()) }
                    .background(color, CircleShape),
            )
            if (index < colors.lastIndex) Spacer(Modifier.width(6.dp))
        }
    }
}
