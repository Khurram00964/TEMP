package com.confused.anikuta.feature.animebrowse

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.designsystem.theme.Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ── The physics constants (components/SuggestionField.tsx — every constant
//    is named there; the port keeps the names) ────────────────────────────────

/** The 7×5 honeycomb — 35 covers, the odd rows sit half a cell to the right. */
private const val FIELD_COLS = 7
private const val FIELD_ROWS = 5
private const val FIELD_TILE_COUNT = FIELD_COLS * FIELD_ROWS

/** The tile's design size + corner radius, in the 350-unit reference frame. */
private const val TILE_BASE_W_DP = 60f
private const val TILE_BASE_H_DP = 90f
private const val TILE_RADIUS_DP = 14f

/** The honeycomb gutter — the solver's hard-enforced minimum gap. */
private const val TILE_GAP_DP = 10f

/** The lens: center cover 1.5×, corners 0.72×, the falloff steepened ×1.25. */
private const val LENS_CENTER_SCALE = 1.5f
private const val LENS_CORNER_SCALE = 0.72f
private const val LENS_TIGHT = 1.25f

/** The min-gap solver: up to 30 pairwise passes, early exit once settled. */
private const val SOLVER_PASSES = 30

/** The drift walk: 16px/s base, runs of 6–11s, per-run speed ±27%. */
private const val DRIFT_SPEED_DP = 16f
private const val TURN_MIN_MS = 6000L
private const val TURN_MAX_MS = 11000L

/** The solver corrections' hard rate cap (px/s) — kills the jitter; do NOT remove. */
private const val CORRECTION_SPEED_DP = 100f

/** The fling glide: exponential decay 3/s, velocity cap 2600px/s. */
private const val FLING_DECAY = 3f
private const val MAX_FLING_DP = 2600f
private const val FLING_THRESHOLD = 2f
private const val FLING_WINDOW_MS = 90L
private const val FLING_HOLD_MS = 300L
private const val VEL_SMOOTHING = 0.35f

/** The manual hold-offs (the TSX pointerdown/move + endDrag values). */
private const val DRAG_HOLD_MS = 3000L
private const val DRAG_END_HOLD_MS = 2200L

/** The border melt: the fade band per overflowing tile (per-unit px). */
private const val MELT_BAND_DP = 26f

/**
 * CENTER-VS-OPEN: a tap opens the cover when its center sits within
 * [OPEN_RADIUS_FACTOR] × the CENTER tile's half-diagonal of the frame
 * center; beyond that the field glides so the cover centers instead.
 */
private const val OPEN_RADIUS_FACTOR = 1.15f

/** The tap-to-center glide: ~420ms smoothstep ease (the handoff's 300–500ms band). */
private const val PAN_GLIDE_MS = 420f
private const val GLIDE_SECONDS = PAN_GLIDE_MS / 1000f

/** The unit scale: frame width / 350, clamped — the TSX `unit = clamp(w/350, .78, 1.4)`. */
private const val UNIT_REF_WIDTH_DP = 350f
private const val UNIT_MIN = 0.78f
private const val UNIT_MAX = 1.4f

/** The frame: near-square (slightly taller than wide), 30dp radius, 20dp edges. */
private const val FIELD_FRAME_ASPECT = 0.92f

/** The deterministic layout seed + its golden-ratio refresh step (the TSX values). */
private const val FIELD_SEED = 0x51ed270b.toInt()
private const val SEED_STEP = 0x9e3779b9.toInt()

/** The vignette: 2.5% side melts + the 90% radial (globals.css .suggest-vignette). */
private const val VIGNETTE_EDGE = 0.025f
private const val VIGNETTE_RADIAL_START = 0.90f
private const val VIGNETTE_Z = 5000f

/** The field-in entrance: opacity 0.2 → 1 over 300ms (globals.css @keyframes field-in). */
private const val FIELD_IN_MS = 300
private const val FIELD_IN_FROM = 0.2f

/** The directional melt edges (the TSX mask's `to right|left|bottom|top`). */
private const val MELT_RIGHT = 0
private const val MELT_LEFT = 1
private const val MELT_BOTTOM = 2
private const val MELT_TOP = 3

/**
 * One suggestion-field cover — the AniList id (the tap-to-open routing) and
 * the poster URL.
 */
internal data class HomeFieldCover(
    val anilistId: Int,
    val url: String?,
)

/**
 * One honeycomb tile. The physics columns are PLAIN fields (written + read by
 * the frame loop only); the render columns are snapshot states so the tiles'
 * `graphicsLayer`/`zIndex` read them per frame without recomposition.
 */
private class FieldTile {
    // Physics.
    var bx = 0f
    var by = 0f
    var tx0 = 0f
    var ty0 = 0f
    var x = 0f
    var y = 0f
    var crx = 0f
    var cry = 0f
    var rx = 0f
    var ry = 0f
    var mob = 1f
    var k = 1f
    var w = 0f
    var h = 0f
    var zv = 100f

    // Render (snapshot states).
    val item = mutableIntStateOf(0)
    val px = mutableFloatStateOf(0f)
    val py = mutableFloatStateOf(0f)
    val scale = mutableFloatStateOf(1f)
    val z = mutableFloatStateOf(100f)

    // The melt brush cache (rebuilt only when the rounded cut/band changes).
    var meltKey = -1L
    var meltBrush: Brush? = null
}

/** The frame metrics the composition side needs (tile sizes in Dp). */
private class FieldFrameMetrics(
    val widthPx: Float,
    val heightPx: Float,
    val tileW: Dp,
    val tileH: Dp,
    val tileRadius: Dp,
)

/**
 * The suggestion field's physics engine — a faithful port of the TSX
 * SuggestionField effect body. All coordinates are SCREEN px; the design's
 * CSS-px constants are density-scaled here (`unit` keeps the design's
 * 350-reference clamping, so the proportions match any device).
 */
private class FieldEngine {
    val tiles = Array(FIELD_TILE_COUNT) { FieldTile() }

    // Frame + derived metrics.
    var frameW = 0f
    var frameH = 0f
    var pxPerDp = 1f
    var unit = 1f
    var baseW = 0f
    var baseH = 0f
    var gap = 0f
    var cellW = 0f
    var cellH = 0f
    var periodX = 0f
    var periodY = 0f
    var marginX = 0f
    var marginY = 0f
    var driftSpeed = 0f
    var corrSpeed = 0f
    var maxFling = 0f
    var meltBand = 0f
    var openRadius = 0f

    // Motion state.
    var seed = FIELD_SEED
    var panX = 0f
    var panY = 0f
    var angle = 0f
    var targetAngle = 0f
    var targetSpeed = 0f
    var speed = 0f
    var turnAt = 0L
    var manualUntil = 0L
    var dragging = false
    var flingX = 0f
    var flingY = 0f
    var velX = 0f
    var velY = 0f
    var lastMoveTime = 0L
    var lastPointerX = 0f
    var lastPointerY = 0f

    // The refresh token bridge (composition writes, the loop applies — the TSX appliedRefresh).
    var requestedRefresh = 0
    var appliedRefresh = 0

    // The tap-to-center glide.
    var gliding = false
    var glideFromX = 0f
    var glideFromY = 0f
    var glideDeltaX = 0f
    var glideDeltaY = 0f
    var glideT = 1f

    // The cover pool (shuffled queue, the TSX refill/nextItem).
    var poolSize = 0
    private var queue = IntArray(0)
    private var cursor = 0

    /** The frame entrance token — bumped per rebuild, drives the field-in fade. */
    val entranceToken = mutableIntStateOf(0)

    // ── Layout ───────────────────────────────────────────────────────────────

    /**
     * (Re)configures the metrics from the frame size; builds the honeycomb
     * when the size actually moved (the TSX ResizeObserver's 1px guard).
     */
    fun onFrameSize(w: Float, h: Float, density: Float, nowMs: Long): Boolean {
        val changed = abs(w - frameW) >= 1f || abs(h - frameH) >= 1f || density != pxPerDp
        pxPerDp = density
        if (!changed) return false
        frameW = w
        frameH = h
        build(nowMs)
        return true
    }

    /** The TSX build(): metrics + the seeded jittered honeycomb + drift reset. */
    fun build(nowMs: Long) {
        if (frameW <= 0f || frameH <= 0f) return
        unit = (frameW / pxPerDp / UNIT_REF_WIDTH_DP).coerceIn(UNIT_MIN, UNIT_MAX)
        baseW = TILE_BASE_W_DP * unit * pxPerDp
        baseH = TILE_BASE_H_DP * unit * pxPerDp
        gap = TILE_GAP_DP * unit * pxPerDp
        cellW = baseW + gap
        cellH = baseH + gap
        periodX = FIELD_COLS * cellW
        periodY = FIELD_ROWS * cellH
        marginX = (periodX - frameW) / 2f
        marginY = (periodY - frameH) / 2f
        driftSpeed = DRIFT_SPEED_DP * pxPerDp
        corrSpeed = CORRECTION_SPEED_DP * pxPerDp
        maxFling = MAX_FLING_DP * pxPerDp
        meltBand = MELT_BAND_DP * unit * pxPerDp
        openRadius = OPEN_RADIUS_FACTOR *
            hypot(baseW * LENS_CENTER_SCALE * 0.5f, baseH * LENS_CENTER_SCALE * 0.5f)

        val rng = Mulberry32(seed)
        refill()
        var index = 0
        for (r in 0 until FIELD_ROWS) {
            for (c in 0 until FIELD_COLS) {
                val t = tiles[index++]
                // Honeycomb: odd rows sit half a cell right, so no cover ever
                // lines up into a straight column; ±3%/±2% seeded jitter.
                val offset = (r % 2) * cellW * 0.5f
                t.bx = (c + 0.5f) * cellW + offset - periodX / 2f +
                    (rng.next() - 0.5f) * cellW * 0.06f
                t.by = (r + 0.5f) * cellH - periodY / 2f +
                    (rng.next() - 0.5f) * cellH * 0.04f
                t.x = 0f
                t.y = 0f
                t.tx0 = 0f
                t.ty0 = 0f
                t.crx = 0f
                t.cry = 0f
                t.rx = 0f
                t.ry = 0f
                t.mob = 1f
                t.k = 1f
                t.w = baseW
                t.h = baseH
                if (poolSize > 0) t.item.intValue = nextItem()
                t.meltKey = -1L
                t.meltBrush = null
            }
        }

        targetSpeed = driftSpeed
        angle = rng.next() * 2f * PI.toFloat()
        targetAngle = angle
        turnAt = nowMs + TURN_MIN_MS + (rng.next() * (TURN_MAX_MS - TURN_MIN_MS)).toLong()
        entranceToken.intValue += 1
    }

    /** The cover pool changed — reshuffle the deal queue and reassign items. */
    fun setPool(size: Int) {
        poolSize = size
        if (frameW > 0f && poolSize > 0) {
            refill()
            for (t in tiles) t.item.intValue = nextItem()
        }
    }

    private fun refill() {
        queue = IntArray(poolSize) { it }
        for (i in queue.size - 1 downTo 1) {
            val j = Random.Default.nextInt(i + 1)
            val tmp = queue[i]
            queue[i] = queue[j]
            queue[j] = tmp
        }
        cursor = 0
    }

    private fun nextItem(): Int {
        if (cursor >= queue.size) refill()
        return queue[cursor++]
    }

    // ── Pan + drift ──────────────────────────────────────────────────────────

    private fun mod(v: Float, m: Float): Float {
        val r = v % m
        return if (r < 0f) r + m else r
    }

    fun panBy(dx: Float, dy: Float) {
        if (periodX <= 0f || periodY <= 0f) return
        panX = mod(panX + dx, periodX)
        panY = mod(panY + dy, periodY)
    }

    private fun wrapX(x: Float): Float {
        var v = x
        while (v < -marginX) v += periodX
        while (v > frameW + marginX) v -= periodX
        return v
    }

    private fun wrapY(y: Float): Float {
        var v = y
        while (v < -marginY) v += periodY
        while (v > frameH + marginY) v -= periodY
        return v
    }

    /**
     * The wandering drift: 55% gentle arcs (±28–90°), 30% hard turns
     * (±95–170°), 15% near-straight; per-run speed ±27%; runs of 6–11s.
     */
    private fun pickAngle(nowMs: Long) {
        val roll = Random.Default.nextFloat()
        val deltaDeg: Float = when {
            roll < 0.55f ->
                (if (Random.Default.nextBoolean()) 1f else -1f) *
                    (28f + Random.Default.nextFloat() * 62f)
            roll < 0.85f ->
                (if (Random.Default.nextBoolean()) 1f else -1f) *
                    (95f + Random.Default.nextFloat() * 75f)
            else -> (Random.Default.nextFloat() - 0.5f) * 26f
        }
        targetAngle = angle + deltaDeg * PI.toFloat() / 180f
        targetSpeed = driftSpeed * (0.75f + Random.Default.nextFloat() * 0.55f)
        turnAt = nowMs + TURN_MIN_MS + (Random.Default.nextFloat() * (TURN_MAX_MS - TURN_MIN_MS)).toLong()
    }

    // ── Solver + render ──────────────────────────────────────────────────────

    /**
     * The TSX place(): torus wrap → lens (scale + mobility) → the min-gap
     * solver (mobility-weighted pairwise separation, up to [SOLVER_PASSES]
     * passes with the handoff's early exit).
     */
    private fun place() {
        val cx = frameW / 2f
        val cy = frameH / 2f
        val maxR = hypot(cx, cy)

        for (t in tiles) {
            val x = wrapX(t.bx + panX + cx)
            val y = wrapY(t.by + panY + cy)
            val t0 = (hypot(x - cx, y - cy) / maxR).coerceIn(0f, 1f)
            val lens = smoothstep(min(1f, t0 * LENS_TIGHT))
            t.k = LENS_CENTER_SCALE - (LENS_CENTER_SCALE - LENS_CORNER_SCALE) * lens
            t.mob = 0.06f + 0.94f * lens
            t.tx0 = x
            t.ty0 = y
            t.x = x
            t.y = y
            t.w = baseW * t.k
            t.h = baseH * t.k
        }

        for (pass in 0 until SOLVER_PASSES) {
            var fixed = false
            for (i in tiles.indices) {
                val a = tiles[i]
                for (j in i + 1 until tiles.size) {
                    val b = tiles[j]
                    var dx = b.x - a.x
                    if (dx > periodX / 2f) dx -= periodX else if (dx < -periodX / 2f) dx += periodX
                    var dy = b.y - a.y
                    if (dy > periodY / 2f) dy -= periodY else if (dy < -periodY / 2f) dy += periodY

                    val overlapX = (a.w + b.w) / 2f + gap - abs(dx)
                    val overlapY = (a.h + b.h) / 2f + gap - abs(dy)
                    if (overlapX <= 0f || overlapY <= 0f) continue

                    // Centre covers barely give way; outer covers absorb.
                    val share = a.mob / (a.mob + b.mob)
                    if (overlapX < overlapY) {
                        val move = overlapX * (if (dx < 0f) -1f else 1f)
                        a.x -= move * share
                        b.x += move * (1f - share)
                    } else {
                        val move = overlapY * (if (dy < 0f) -1f else 1f)
                        a.y -= move * share
                        b.y += move * (1f - share)
                    }
                    fixed = true
                }
            }
            if (!fixed) break
        }

        for (t in tiles) {
            t.x = wrapX(t.x)
            t.y = wrapY(t.y)
        }
    }

    /**
     * The TSX render(): the pan rode 1:1 through (tx0, ty0); only the solver's
     * displacement is applied, RATE-CAPPED at [CORRECTION_SPEED_DP] px/s —
     * covers make space smoothly instead of jumping (the jitter killer).
     */
    private fun writeRender(dt: Float) {
        val cap = corrSpeed * dt
        for (t in tiles) {
            val wantX = t.x - t.tx0
            val wantY = t.y - t.ty0
            t.crx += (wantX - t.crx).coerceIn(-cap, cap)
            t.cry += (wantY - t.cry).coerceIn(-cap, cap)
            t.rx = t.tx0 + t.crx
            t.ry = t.ty0 + t.cry
            t.px.floatValue = t.rx
            t.py.floatValue = t.ry
            t.scale.floatValue = t.k
            val z = 100f + t.k * 100f
            t.zv = z
            if (t.z.floatValue != z) t.z.floatValue = z
        }
    }

    // ── The step (per frame) ─────────────────────────────────────────────────

    fun step(nowMs: Long, dt: Float) {
        // Wait for the frame's first layout (the TSX build guard) — the solver
        // and the lens math are undefined against a zero-sized frame.
        if (frameW <= 0f || frameH <= 0f) return
        if (requestedRefresh != appliedRefresh) {
            appliedRefresh = requestedRefresh
            seed += SEED_STEP
            build(nowMs)
        }

        // The tap-to-center glide (smoothstep ease over the glide period).
        if (gliding) {
            glideT += dt / GLIDE_SECONDS
            val u = smoothstep(glideT.coerceIn(0f, 1f))
            panX = mod(glideFromX + glideDeltaX * u, periodX)
            panY = mod(glideFromY + glideDeltaY * u, periodY)
            if (glideT >= 1f) gliding = false
        }

        // A flick keeps going, decaying like a scrolled page; the drift stays away.
        val flinging = !dragging && (abs(flingX) > FLING_THRESHOLD || abs(flingY) > FLING_THRESHOLD)
        if (flinging) {
            panBy(flingX * dt, flingY * dt)
            val decay = exp(-dt * FLING_DECAY)
            flingX *= decay
            flingY *= decay
            if (nowMs + FLING_HOLD_MS > manualUntil) manualUntil = nowMs + FLING_HOLD_MS
        }

        val autoOn = !paused && !dragging && !flinging && !gliding && nowMs > manualUntil
        val wanted = if (autoOn) targetSpeed else 0f
        speed += (wanted - speed) * min(1f, dt * 2.2f)

        if (nowMs > turnAt) pickAngle(nowMs)
        angle += wrapAngle(targetAngle - angle) * min(1f, dt * 1.1f)

        val vx = cos(angle) * speed
        val vy = sin(angle) * speed
        if (speed > 0.05f) panBy(vx * dt, vy * dt)

        place()
        writeRender(dt)
    }

    /** Whether the auto drift is frozen (the pause button — drag still works). */
    var paused = false

    // ── Pointer handlers (the TSX onPointerDown/Move/endDrag) ────────────────

    fun dragStart(x: Float, y: Float, nowMs: Long) {
        dragging = true
        gliding = false
        flingX = 0f
        flingY = 0f
        velX = 0f
        velY = 0f
        lastMoveTime = nowMs
        lastPointerX = x
        lastPointerY = y
        manualUntil = nowMs + DRAG_HOLD_MS
    }

    fun dragBy(dx: Float, dy: Float, nowMs: Long) {
        if (!dragging) return
        val dtm = max(8f, (nowMs - lastMoveTime).toFloat()) / 1000f
        lastMoveTime = nowMs
        lastPointerX += dx
        lastPointerY += dy
        panBy(dx, dy)
        // Smoothed instantaneous velocity, handed to the fling on release.
        velX += (dx / dtm - velX) * VEL_SMOOTHING
        velY += (dy / dtm - velY) * VEL_SMOOTHING
        manualUntil = nowMs + DRAG_HOLD_MS
    }

    fun dragEnd(nowMs: Long) {
        if (!dragging) return
        dragging = false
        // Only a flick carries momentum; a slow drag stops in place.
        if (nowMs - lastMoveTime < FLING_WINDOW_MS) {
            flingX = velX.coerceIn(-maxFling, maxFling)
            flingY = velY.coerceIn(-maxFling, maxFling)
        }
        manualUntil = nowMs + DRAG_END_HOLD_MS
    }

    // ── CENTER-VS-OPEN (the tap contract) ────────────────────────────────────

    /**
     * A tap on a cover: within [OPEN_RADIUS_FACTOR] × the center tile's
     * half-diagonal of the frame center → OPEN (fires [onOpen] with the
     * cover's AniList id); otherwise → the field GLIDES so that cover centers
     * (an already-centered cover always opens).
     */
    fun tap(
        x: Float,
        y: Float,
        covers: List<HomeFieldCover>,
        onOpen: (Int) -> Unit,
        nowMs: Long,
    ): Boolean {
        if (poolSize == 0 || covers.isEmpty() || frameW <= 0f) return false
        var best: FieldTile? = null
        var bestZ = -1f
        for (t in tiles) {
            val hw = t.w / 2f
            val hh = t.h / 2f
            if (x >= t.rx - hw && x <= t.rx + hw && y >= t.ry - hh && y <= t.ry + hh) {
                if (t.zv > bestZ) {
                    best = t
                    bestZ = t.zv
                }
            }
        }
        val tile = best ?: return false
        val dist = hypot(tile.rx - frameW / 2f, tile.ry - frameH / 2f)
        if (dist <= openRadius) {
            val cover = covers.getOrNull(tile.item.intValue % covers.size)
            if (cover != null) onOpen(cover.anilistId)
            return cover != null
        }
        glideTo(tile, nowMs)
        return true
    }

    private fun glideTo(tile: FieldTile, nowMs: Long) {
        glideFromX = panX
        glideFromY = panY
        glideDeltaX = frameW / 2f - tile.rx
        glideDeltaY = frameH / 2f - tile.ry
        glideT = 0f
        gliding = true
        flingX = 0f
        flingY = 0f
        manualUntil = nowMs + DRAG_HOLD_MS
    }

    // ── The per-tile border melt ─────────────────────────────────────────────

    /**
     * The TSX directional per-tile mask, adapted for the frame's clip: the
     * frame clips the part beyond the border, so the melt band runs INWARD
     * from the border line (the part beyond fades to nothing, the inner part
     * stays fully clear — the handoff's visual contract). `cut` is the border
     * line in the tile's LOCAL unscaled px (screen distance ÷ scale), measured
     * from the tile's leading edge along the mask direction.
     */
    fun meltBrush(tile: FieldTile, dir: Int, cut: Float, extent: Float): Brush? {
        val c = (cut * 4f).toInt()
        val b = (meltBand * 4f).toInt()
        val key = (dir * 100_000_000L) + (c.toLong() * 10_000L) + b.toLong()
        if (key == tile.meltKey) return tile.meltBrush
        tile.meltKey = key
        val brush = if (dir == MELT_RIGHT || dir == MELT_BOTTOM) {
            // to right / to bottom: clear up to the band start, ramping to
            // fully erased at the border line.
            val start = ((cut - meltBand) / extent).coerceIn(0f, 1f)
            val end = (cut / extent).coerceIn(0f, 1f)
            if (end <= 0f) {
                null
            } else {
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        start to Color.Transparent,
                        end to Color.Black,
                        1f to Color.Black,
                    ),
                )
            }
        } else {
            // to left / to top: fully erased up to the border line, ramping
            // back to clear over the inner band.
            val start = (cut / extent).coerceIn(0f, 1f)
            val end = ((cut + meltBand) / extent).coerceIn(0f, 1f)
            Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Black,
                    start to Color.Black,
                    end to Color.Transparent,
                    1f to Color.Transparent,
                ),
            )
        }
        tile.meltBrush = brush
        return brush
    }
}

/** The TSX mulberry32 — the seeded layout RNG, bit-for-bit. */
private class Mulberry32(seed: Int) {
    private var a = seed

    fun next(): Float {
        a += 0x6d2b79f5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = ((t xor (t ushr 7)) * (61 or t) + t) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}

/** Hermite smoothstep — the TSX helper. */
private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** The TSX wrapAngle — keep the drift's angle arithmetic on the unit circle. */
private fun wrapAngle(a: Float): Float = atan2(sin(a), cos(a))

/**
 * The suggestion field — the handoff §5 signature component, ported from
 * components/SuggestionField.tsx: a 7×5 honeycomb of covers on an infinite
 * torus, the center-magnifying lens, the mobility-weighted min-gap solver
 * with the rate-capped corrections, the wandering drift, drag + fling, and
 * the per-tile border melt behind a 2.5%/90% vignette.
 *
 * ## The Compose architecture (the OnboardingWelcomeArt frame-loop pattern)
 * ONE `withFrameNanos` loop drives a [FieldEngine]; the per-frame writes land
 * in snapshot states each tile's `graphicsLayer` reads (translation/scale/z),
 * so a frame updates layer properties only — no recomposition. 35 tiles max,
 * each an [AsyncImage] in a sized Box; z-order rides the scale (the biggest
 * cover on top). The melt is a per-tile DstOut gradient inside the tile's own
 * offscreen layer, so the fade reveals what sits UNDER the tile (the TSX
 * mask semantics), not the frame background.
 *
 * ## Covers
 * (a) [pool]'s top library genres → [coversForGenres] (the VM's fetchByGenre
 * mix, 50 covers); (b) empty pool/empty library → [fallbackCovers] (the
 * trending payload). [refreshToken] bumps rebuild the field with a new seed
 * (re-jitter + reshuffled deal queue — no refetch); [paused] freezes the
 * drift but keeps the drag; a tap opens (near center) or glides the tapped
 * cover to center (CENTER-VS-OPEN, [OPEN_RADIUS_FACTOR]).
 */
@Composable
internal fun HomeSuggestionField(
    pool: HomeSuggestionPool,
    paused: Boolean,
    refreshToken: Int,
    fallbackCovers: List<AniListAnime>,
    onOpenCover: (Int) -> Unit,
    coversForGenres: suspend (List<String>) -> List<AniListAnime>,
    modifier: Modifier = Modifier,
) {
    val pxPerDp = LocalDensity.current.density
    val engine = remember { FieldEngine() }
    var metrics by remember {
        mutableStateOf(FieldFrameMetrics(0f, 0f, 60.dp, 90.dp, TILE_RADIUS_DP.dp))
    }
    var covers by remember { mutableStateOf<List<HomeFieldCover>>(emptyList()) }
    val fieldIn = remember { Animatable(1f) }
    val onOpenCoverLatest by rememberUpdatedState(onOpenCover)

    // The pause flag + the refresh token ride into the engine (the TSX refs).
    LaunchedEffect(paused) { engine.paused = paused }
    LaunchedEffect(refreshToken) { engine.requestedRefresh = refreshToken }
    LaunchedEffect(covers) { engine.setPool(covers.size) }

    // The cover pool: the library-genre affinity fetch, the trending fallback.
    LaunchedEffect(pool.genres, fallbackCovers) {
        val base = fallbackCovers.map { HomeFieldCover(it.id, it.coverUrl) }
        val loaded = if (pool.genres.isEmpty()) {
            emptyList()
        } else {
            runCatching { coversForGenres(pool.genres) }
                .getOrElse { emptyList() }
                .map { HomeFieldCover(it.id, it.coverUrl) }
        }
        covers = loaded.ifEmpty { base }
    }

    // The ONE frame loop (dt clamped to 50ms — backgrounding pauses, no leap).
    LaunchedEffect(engine) {
        var lastFrame = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastFrame >= 0L) {
                    ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                } else {
                    0f
                }
                lastFrame = now
                engine.step(now / 1_000_000L, dt)
            }
        }
    }

    // The field-in fade on mount + every rebuild (refresh token / new pool).
    LaunchedEffect(refreshToken, covers.size, engine.entranceToken.intValue) {
        fieldIn.snapTo(FIELD_IN_FROM)
        fieldIn.animateTo(1f, tween(FIELD_IN_MS, easing = Motion.EasingStandard))
    }

    // The near-square frame (the placeholder chrome: 20dp edges, 30dp radius).
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .aspectRatio(FIELD_FRAME_ASPECT)
            .onSizeChanged { size ->
                val changed = engine.onFrameSize(
                    size.width.toFloat(),
                    size.height.toFloat(),
                    pxPerDp,
                    SystemClock.uptimeMillis(),
                )
                if (changed) {
                    metrics = FieldFrameMetrics(
                        size.width.toFloat(),
                        size.height.toFloat(),
                        (engine.baseW / pxPerDp).dp,
                        (engine.baseH / pxPerDp).dp,
                        (TILE_RADIUS_DP * engine.unit).dp,
                    )
                }
            }
            .clip(RoundedCornerShape(30.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(30.dp),
            )
            .background(MaterialTheme.colorScheme.background),
    ) {
        // The pannable field — the tiles + the tap/drag detectors. Drag wins
        // over the page scroll (the TSX's touch-action: none).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fieldIn.value }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            engine.tap(
                                offset.x,
                                offset.y,
                                covers,
                                onOpenCoverLatest,
                                SystemClock.uptimeMillis(),
                            )
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            engine.dragStart(offset.x, offset.y, SystemClock.uptimeMillis())
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            engine.dragBy(dragAmount.x, dragAmount.y, change.uptimeMillis)
                        },
                        onDragEnd = { engine.dragEnd(SystemClock.uptimeMillis()) },
                        onDragCancel = { engine.dragEnd(SystemClock.uptimeMillis()) },
                    )
                },
        ) {
            if (covers.isNotEmpty()) {
                repeat(FIELD_TILE_COUNT) { i ->
                    FieldTileBox(engine.tiles[i], engine, covers, metrics)
                }
            }
        }

        // The vignette: the 2.5% side melts + the 90% radial, ABOVE the tiles.
        val frameBg = MaterialTheme.colorScheme.background
        val vignette = remember(metrics.widthPx, metrics.heightPx, frameBg) {
            buildVignetteBrushes(frameBg, metrics)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(VIGNETTE_Z)
                .drawBehind { vignette.forEach { drawRect(it) } },
        )
    }
}

/** One honeycomb cover — positioned + scaled entirely in the layer block. */
@Composable
private fun FieldTileBox(
    tile: FieldTile,
    engine: FieldEngine,
    covers: List<HomeFieldCover>,
    metrics: FieldFrameMetrics,
) {
    Box(
        modifier = Modifier
            .zIndex(tile.z.floatValue)
            .size(metrics.tileW, metrics.tileH)
            .graphicsLayer {
                translationX = tile.px.floatValue - engine.baseW / 2f
                translationY = tile.py.floatValue - engine.baseH / 2f
                val k = tile.scale.floatValue
                scaleX = k
                scaleY = k
                // The offscreen layer is only needed once the tile overflows
                // the frame (the melt's DstOut must isolate the tile's pixels).
                val rx = tile.px.floatValue
                val ry = tile.py.floatValue
                val halfW = engine.baseW * k / 2f
                val halfH = engine.baseH * k / 2f
                val overflows = rx + halfW > engine.frameW || rx - halfW < 0f ||
                    ry + halfH > engine.frameH || ry - halfH < 0f
                compositingStrategy = if (overflows) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
            }
            .clip(RoundedCornerShape(metrics.tileRadius))
            .drawWithContent {
                drawContent()
                // The directional border melt (the TSX per-tile mask).
                val k = tile.scale.floatValue
                val rx = tile.px.floatValue
                val ry = tile.py.floatValue
                val scaledHalfW = size.width / 2f * k
                val scaledHalfH = size.height / 2f * k
                val oR = rx + scaledHalfW - engine.frameW
                val oL = scaledHalfW - rx
                val oB = ry + scaledHalfH - engine.frameH
                val oT = scaledHalfH - ry
                val deepest = max(oR, max(oL, max(oB, oT)))
                if (deepest <= 0f) return@drawWithContent
                val band = engine.meltBand / k
                val brush = when {
                    oR >= deepest -> engine.meltBrush(
                        tile, MELT_RIGHT, (engine.frameW - rx + scaledHalfW) / k, size.width,
                    )
                    oL >= deepest -> engine.meltBrush(
                        tile, MELT_LEFT, (scaledHalfW - rx) / k, size.width,
                    )
                    oB >= deepest -> engine.meltBrush(
                        tile, MELT_BOTTOM, (engine.frameH - ry + scaledHalfH) / k, size.height,
                    )
                    else -> engine.meltBrush(
                        tile, MELT_TOP, (scaledHalfH - ry) / k, size.height,
                    )
                }
                if (brush != null) drawRect(brush = brush, blendMode = BlendMode.DstOut)
            },
    ) {
        AsyncImage(
            model = covers.getOrNull(tile.item.intValue % covers.size)?.url,
            contentDescription = null, // decorative — the TSX tiles are aria-hidden
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The vignette brush set (globals.css .suggest-vignette): the four 2.5% edge
 * melts + the radial that dissolves the outer 10% into the frame background.
 */
private fun buildVignetteBrushes(bg: Color, metrics: FieldFrameMetrics): List<Brush> {
    if (metrics.widthPx <= 0f || metrics.heightPx <= 0f) return emptyList()
    val clear = bg.copy(alpha = 0f)
    return listOf(
        Brush.horizontalGradient(
            colorStops = arrayOf(0f to bg, VIGNETTE_EDGE to clear, 1f to clear),
        ),
        Brush.horizontalGradient(
            colorStops = arrayOf(0f to clear, 1f - VIGNETTE_EDGE to clear, 1f to bg),
        ),
        Brush.verticalGradient(
            colorStops = arrayOf(0f to bg, VIGNETTE_EDGE to clear, 1f to clear),
        ),
        Brush.verticalGradient(
            colorStops = arrayOf(0f to clear, 1f - VIGNETTE_EDGE to clear, 1f to bg),
        ),
        Brush.radialGradient(
            colorStops = arrayOf(0f to clear, VIGNETTE_RADIAL_START to clear, 1f to bg),
            center = Offset(metrics.widthPx / 2f, metrics.heightPx / 2f),
            radius = hypot(metrics.widthPx, metrics.heightPx) / 2f,
        ),
    )
}
