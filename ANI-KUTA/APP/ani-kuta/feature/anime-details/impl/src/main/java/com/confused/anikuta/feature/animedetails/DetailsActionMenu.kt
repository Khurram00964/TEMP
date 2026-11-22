package com.confused.anikuta.feature.animedetails

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.model.DataSourcePriority
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.designsystem.theme.SuccessDark
import com.confused.anikuta.core.designsystem.theme.SuccessLight
import com.confused.anikuta.core.share.ShareLink
import com.confused.anikuta.core.share.ShareTargetKind

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-1) → ROUND 104 (WS-A): DetailsActionMenu — the width + the
//  color hierarchy + the tracking row's geometry.
// ════════════════════════════════════════════════════════════════════════════
//
//  The v1.1.60 device round kept the round-103 crimped STRUCTURE (the cards,
//  the chips, the discs — all approved) and ordered three refinements:
//
//  • THE WIDTH — "it is way too wide, even more wider than it should
//    actually be": the content column shrinks 300dp → 264dp on BOTH pages
//    (the main menu + the share submenu — one width, one anchor rhythm).
//    ROUND 105 (WS-A): the v1.1.61 round approved everything but asked for
//    "a little bit smaller, like a bit more" — 264dp → 240dp.
//    ROUND 106 (WS-A): the final trim — the v1.1.62 round's "make it a
//    little bit more less wider and a little bit more properly handled":
//    240dp → 220dp. The chip pair, the discs, and every label keep their
//    metrics — 220dp still leaves a comfortable 184dp of row width inside
//    the 10dp padding + 16dp cards.
//
//  • THE COLOR HIERARCHY — "the background color is a bit on the lighter
//    side, while the actual buttons are on the darker side, which is
//    apparently not a good idea." The round-103 panel was flat `surface`
//    while the section cards sat on a surfaceVariant overlay that read
//    DARKER than the panel in light theme (and near-invisible in dark).
//    The hierarchy INVERTS to the inset-grouped truth in BOTH themes —
//    the CARDS are always BRIGHTER than the PANEL:
//      dark  → panel = surface,        cards = surfaceVariant        (a lift)
//      light → panel = surfaceContainerHigh (grey), cards = surface (near-white)
//    The explicit colors carry the tone (tonalElevation 0 — no overlay
//    fighting the picked containerColor).
//
//  • THE TRACKING ROW — "the UI of the tracking button is looking a bit
//    different, and it is not looking that proper": the round-103 row
//    double-inset (a 6dp outer padding, then its own rounded wash — a pill
//    INSIDE the card, a geometry no other row has). The row now wears
//    EXACTLY MenuDiscRow's metrics (fillMaxWidth → clip(12) → wash →
//    clickable → padding(h10, v8)) with the state tone INSIDE its own
//    rounded rect; the idle grey wash is theme-aware (dark: a 4.5% onSurface
//    lift over the card — the old surfaceVariant overlay was same-color
//    and therefore invisible).
//
//  Everything else from round 103 stands: the crimped sections, the
//  side-by-side data-source chips, the discs, "Open in Web View", and the
//  share submenu's byte-identical behavior.

/**
 * The three-dot anchored menu. [expanded] is the PARENT's open state (the
 * three-dots button toggles it); the Share page swap is INTERNAL so the
 * transition stays smooth at one anchor. Call [onDismissRequest] for any
 * full close (outside tap, back, after an action).
 *
 * [extensionSourceName] is the linked extension's display name (the RIGHT
 * data-source chip's text; falls back to "Extension" when unknown).
 */
@Composable
fun DetailsActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    contentTitle: String,
    // ── Data source section (ROUND 174: THE N-WAY SWITCH — the chips are
    // the ACTUAL linked identities from content_identity; the 2-slot
    // AniList|Extension pair retired) ──
    displayChips: List<Triple<String, String, Int?>>,
    selectedDisplayKind: String?,
    onSelectDisplaySource: (String) -> Unit,
    // ── Actions ──
    onViewInWebView: () -> Unit,
    canViewInWebView: Boolean,
    // ── Tracking (ROUND 103 WS-2: the color-coded state) ──
    onOpenTracking: () -> Unit,
    isTracked: Boolean,
    // ── The "Link to other" section (extension AND metadata entries — the
    //    metadata rows have no extension identity yet but MUST still offer
    //    the links; ROUND 172) ──
    isExtensionEntry: Boolean,
    isMetadataEntry: Boolean,
    // ROUND 172: the LINKED identities ("anilist"/"mal"/"simkl"/"tmdb"/
    // "tvmaze") — the per-source Unlink rows render from this set.
    linkedIdentities: Set<String>,
    onLinkAniList: () -> Unit,
    // ROUND 170: the "Link to other" source picks — ALL 5 open the unified
    // link sheet (ONE UI, the different backends).
    onLinkMal: () -> Unit = {},
    onLinkSimkl: () -> Unit = {},
    onLinkTmdb: () -> Unit = {},
    onLinkTvmaze: () -> Unit = {},
    onUnlinkAniList: () -> Unit,
    // ROUND 172: the per-source unlinks (the identity rows).
    onUnlinkMal: () -> Unit = {},
    onUnlinkSimkl: () -> Unit = {},
    onUnlinkTmdb: () -> Unit = {},
    onUnlinkTvmaze: () -> Unit = {},
    // ── Share: the targets, built on demand when the submenu opens ──
    buildShareLinks: () -> List<ShareLink>,
) {
    val context = LocalContext.current
    // ROUND 104 (WS-A): the theme-aware panel/card hierarchy — the CARDS are
    // always BRIGHTER than the PANEL (the inverted relationship the device
    // round ordered; see the file header).
    // SA1-F1 fix (lead-verified): the polarity derives from the ACTIVE
    // SCHEME's background luminance, NOT isSystemInDarkTheme() — the app
    // can FORCE a theme opposite the system (MainActivity's ThemeMode), and
    // a forced-DARK app on a system-light device would otherwise pick the
    // light-mode panel (the exact inverted hierarchy this round fixes). The
    // scheme IS what renders — its luminance is the truth (also correct for
    // the D-254 custom themes + AMOLED).
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val panelColor = if (darkTheme) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    // The Share page swap — internal so both menus share ONE anchor Box.
    // Reset when fully closed so the next open starts on the main page.
        var linkToOtherExpanded by remember { mutableStateOf(false) }
    var sharePage by remember { mutableStateOf(false) }
    // ROUND 172 (the device order): the menu ALWAYS opens in its DEFAULT
    // state — the v1.1.171 reset keyed on onDismissRequest was dead (the
    // lambda's identity never changes); the reset rides `expanded` like the
    // Share page's does. Closing the expanded source list reopens the
    // plain three-dot menu.
    LaunchedEffect(expanded) {
        if (!expanded) {
            sharePage = false
            linkToOtherExpanded = false
        }
    }

    // ── The MAIN menu (hidden while the Share page shows) ──
    DropdownMenu(
        expanded = expanded && !sharePage,
        onDismissRequest = onDismissRequest,
        // ROUND 104 (WS-A): 20dp corners + the explicit deeper panel (the
        // crimped cards inside are the brighter layer now).
        shape = RoundedCornerShape(20.dp),
        containerColor = panelColor,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .padding(10.dp)
                // ROUND 172: the Link-to-other expansion ANIMATES (the
                // device order — the height change springs, no snap).
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // ══ SECTION: DATA SOURCE — the chip row + the link/unlink rows ══
            // (shows when there is a choice to make OR a list to link/unlink;
            // the METADATA rows offer it too — ROUND 172)
            if (displayChips.isNotEmpty() || isExtensionEntry || isMetadataEntry) {
                MenuSectionLabel("Data source")
                MenuSectionCard {
                    // ROUND 176 (the UI-history rebuild — the user's order:
                    // "analyze from the older implementations… keep things
                    // minimal"): the approved round-103/104 wide TEXT chips
                    // resurrected (MenuSourceChip — the device-approved chip
                    // that survived dead in this file), chunked into rows of
                    // two; the odd chip takes the full-width closing row.
                    // TEXT-ONLY: the extension can never carry an icon (the
                    // AnimeSource icon isn't exposed) — any icon scheme
                    // guarantees the mixed logo/glyph row the device report
                    // flagged. FIXED canonical order (never selection-driven
                    // — the approved pair never re-ordered). The selection IS
                    // the fill — no border, no checkmark.
                    if (displayChips.size >= 2) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            displayChips.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    pair.forEach { (kind, label, _) ->
                                        MenuSourceChip(
                                            label = label,
                                            selected = kind == selectedDisplayKind,
                                            onClick = {
                                                onDismissRequest()
                                                onSelectDisplaySource(kind)
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // ROUND 170 (the device order): "Link to other" — the
                    // row expands IN-PLACE into the sources list (AniList →
                    // MyAnimeList → Simkl → TMDB → TVmaze), each row with
                    // its REAL logo. ROUND 172: the pick dismisses the menu
                    // first (the unified sheet opens over a closed menu).
                    if (linkToOtherExpanded) {
                        MenuDiscRowWithLogo(
                            label = "AniList",
                            logoRes = com.confused.anikuta.feature.animedetails.R.drawable.ic_tracker_anilist,
                            onClick = { onDismissRequest(); onLinkAniList() },
                        )
                        MenuDiscRowWithLogo(
                            label = "MyAnimeList",
                            logoRes = com.confused.anikuta.feature.animedetails.R.drawable.ic_tracker_mal,
                            onClick = { onDismissRequest(); onLinkMal() },
                        )
                        MenuDiscRowWithLogo(
                            label = "Simkl",
                            logoRes = com.confused.anikuta.feature.animedetails.R.drawable.ic_tracker_simkl,
                            onClick = { onDismissRequest(); onLinkSimkl() },
                        )
                        MenuDiscRowWithLogo(
                            label = "TMDB",
                            logoRes = com.confused.anikuta.feature.animedetails.R.drawable.ic_source_tmdb,
                            onClick = { onDismissRequest(); onLinkTmdb() },
                        )
                        MenuDiscRowWithLogo(
                            label = "TVMaze",
                            logoRes = com.confused.anikuta.feature.animedetails.R.drawable.ic_source_tvmaze,
                            onClick = { onDismissRequest(); onLinkTvmaze() },
                        )
                    } else {
                        MenuDiscRow(
                            icon = Icons.Filled.Link,
                            label = "Link to other",
                            onClick = { linkToOtherExpanded = true },
                        )
                    }
                    // ROUND 172: the LINKED identities — the per-source
                    // Unlink rows (the user's model: after linking, the menu
                    // shows "Unlink {source}"; the v1.1.107 bug hid the row
                    // behind an AniList-only check that lied on metadata).
                    val unlinkTargets = listOf(
                        Triple("anilist", "Unlink AniList", onUnlinkAniList),
                        Triple("mal", "Unlink MyAnimeList", onUnlinkMal),
                        Triple("simkl", "Unlink Simkl", onUnlinkSimkl),
                        Triple("tmdb", "Unlink TMDB", onUnlinkTmdb),
                        Triple("tvmaze", "Unlink TVMaze", onUnlinkTvmaze),
                    )
                    unlinkTargets.forEach { (key, label, onUnlink) ->
                        if (key in linkedIdentities) {
                            MenuDiscRow(
                                icon = Icons.Filled.LinkOff,
                                label = label,
                                destructive = true,
                                onClick = {
                                    onDismissRequest()
                                    onUnlink()
                                },
                            )
                        }
                    }
                }
            }

            // ══ SECTION: ACTIONS ══ (ALWAYS visible — the v1.1.107 bug hid
            // it while the source list was expanded; the user ordered the
            // FULL menu every time)
            MenuSectionLabel("Actions")
            MenuSectionCard {
                MenuDiscRow(
                    icon = Icons.Filled.Share,
                    label = "Share",
                    onClick = { sharePage = true },
                )
                if (canViewInWebView) {
                    MenuDiscRow(
                        icon = Icons.Filled.Language,
                        // ROUND 103: the label the user ordered.
                        label = "Open in Web View",
                        onClick = {
                            onDismissRequest()
                            onViewInWebView()
                        },
                    )
                }
            }

            // ══ SECTION: TRACKING — the state speaks through color ══
            MenuSectionLabel("Tracking")
            MenuSectionCard {
                MenuTrackingRow(
                    isTracked = isTracked,
                    onClick = {
                        onDismissRequest()
                        onOpenTracking()
                    },
                )
            }
        }
    }

    // ── The SHARE submenu — the same anchor, the three targets, the OS
    // chooser fired DIRECTLY on pick (no sheet, no copy button). Behavior
    // unchanged from round 102 (the user approved it); only the rows wear
    // the disc language now. ──
    DropdownMenu(
        expanded = expanded && sharePage,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = panelColor,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            MenuSectionLabel("Share via")
            // Built on open (not per recomposition of the whole page): the
            // links reflect the content's CURRENT state at the moment of
            // sharing.
            val links = remember(sharePage, expanded) { buildShareLinks() }
            MenuSectionCard {
                if (links.isEmpty()) {
                    Text(
                        text = "Nothing to share for this content yet",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                } else {
                    links.forEach { link ->
                        // The round-102 icon mapping, unchanged (the user
                        // approved this submenu's content).
                        val icon = when (link.kind) {
                            ShareTargetKind.EXTENSION_URL -> Icons.Filled.Language
                            ShareTargetKind.DATA_SOURCE -> Icons.Filled.Star
                            ShareTargetKind.APP_DEEP_LINK -> Icons.Filled.AppShortcut
                        }
                        MenuDiscRow(
                            icon = icon,
                            label = link.label,
                            onClick = {
                                onDismissRequest()
                                fireOsShareChooser(context, link, contentTitle)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * WS-D: hand the picked link straight to the DEVICE's share sheet —
 * `Intent.ACTION_SEND` wrapped in `createChooser`, with "title — url" as
 * the payload (the same text shape the round-101 sheet shared). Called from
 * a click handler with the composition's context (the activity is alive and
 * foregrounded at that moment).
 */
private fun fireOsShareChooser(
    context: android.content.Context,
    link: ShareLink,
    contentTitle: String,
) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            if (contentTitle.isBlank()) link.url else "${contentTitle} — ${link.url}",
        )
    }
    context.startActivity(Intent.createChooser(send, "Share via"))
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103: the crimped-menu vocabulary — labels, cards, chips, discs
// ════════════════════════════════════════════════════════════════════════════

/** A small all-caps section label ABOVE each crimped card (the grouping rhythm). */
/** A small all-caps section label ABOVE each crimped card (the grouping rhythm). */
// ── ROUND 171: the source-row variant carrying the REAL logo (the device
// order: the icons, not the generic discs).
@Composable
private fun MenuDiscRowWithLogo(
    label: String,
    logoRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(logoRes),
            contentDescription = label,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 4.dp, bottom = 1.dp),
    )
}

/**
 * ONE crimped section — "crimp the menu a bit where the separations are":
 * an inset rounded card holding its rows; the gaps BETWEEN the cards are
 * the menu's separations. ROUND 104 (WS-A): the card is the BRIGHTER
 * layer — surfaceVariant over the dark panel, near-white surface over the
 * grey light panel (the inverted hierarchy the device round ordered).
 */
@Composable
private fun MenuSectionCard(content: @Composable () -> Unit) {
    // SA1-F1: scheme-luminance polarity (see the file header's note).
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        color = if (darkTheme) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

/**
 * ROUND 103 (WS-1): one data-source chip of the side-by-side pair — the
 * round-101 sheet's segmented look exactly (10dp corners; selected = the
 * filled primary + onPrimary ExtraBold; unselected = the quiet tint +
 * onSurfaceVariant Medium). The selection IS the fill — no checkmark.
 * ROUND 104 (WS-A): the quiet tone is theme-aware — the round-103
 * surfaceVariant overlay was same-color as the new dark card (invisible);
 * dark lifts via surfaceContainerHighest@40%, light steps via
 * surfaceContainer.
 */
@Composable
private fun MenuSourceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // SA1-F1: scheme-luminance polarity (see the file header's note).
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val quietTone = if (darkTheme) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else quietTone,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        )
    }
}

/**
 * ROUND 103 (WS-1): the action row wearing the PREVIOUS logos — the
 * round-101 DetailsActionSheet's icon DISC: a 38dp tinted circle carrying a
 * 19dp glyph, then the label (14sp Bold — label-only, no descriptions).
 * [destructive] shifts the disc to the error language (Unlink).
 */
@Composable
private fun MenuDiscRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .background(tint.copy(alpha = 0.12f), CircleShape),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * ROUND 103 (WS-2) → ROUND 104 (WS-A): the Tracking row — the text says
 * ONLY "Tracking"; the STATE speaks through color, exactly per the device
 * rounds:
 *  • NOT connected — the greyed disc + a greyish row wash + greyish text.
 *  • Connected — a greenish tone on the wash, the icon and the text.
 * ROUND 104 (WS-A): the row wears EXACTLY MenuDiscRow's metrics (the
 * round-103 double-inset — a 6dp outer padding then its own wash — read
 * as "looking a bit different"); the state tone lives INSIDE the row's own
 * rounded rect. The idle wash is theme-aware (dark: a 4.5% onSurface lift —
 * the old surfaceVariant overlay was same-color as the card, invisible).
 */
@Composable
private fun MenuTrackingRow(
    isTracked: Boolean,
    onClick: () -> Unit,
) {
    // SA1-F1: scheme-luminance polarity (see the file header's note).
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val trackingGreen = if (darkTheme) SuccessDark else SuccessLight
    val rowTone = if (isTracked) {
        trackingGreen.copy(alpha = 0.13f)
    } else if (darkTheme) {
        // A neutral lift over the (already elevated) dark card.
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val tint = if (isTracked) trackingGreen
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowTone)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .background(tint.copy(alpha = 0.14f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.TrackChanges,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Tracking",
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = if (isTracked) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
