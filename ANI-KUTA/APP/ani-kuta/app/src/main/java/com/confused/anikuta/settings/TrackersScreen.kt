package com.confused.anikuta.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.content.ContentResolver
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.R
import com.confused.anikuta.core.trackeranilist.AniListTracker
import com.confused.anikuta.core.trackerapi.TrackerLoginState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val TAG = "Anikuta:Settings:Trackers"

/**
 * D-220: Trackers settings page.
 *
 * Shows the list of supported trackers (currently only AniList).
 * - If NOT linked: shows a "Link AniList" button.
 * - If linked: shows the username + an "Unlink" button + a "Populate Library" button.
 *
 * The "Populate Library" button fetches the user's AniList anime library + creates
 * local categories (Watching/Completed/Paused/Dropped/Planning) + saves each anime
 * to the matching category.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Settings:Trackers".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackersScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val anilistTracker = koinInject<AniListTracker>()
    val contentRepository = koinInject<ContentRepository>()
    val contentResolver = koinInject<ContentResolver>()

    val loginState by anilistTracker.observeLoginState().collectAsState(initial = TrackerLoginState.LoggedOut)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    // D-221: populate library state.
    var populateState by remember { mutableStateOf<PopulateState>(PopulateState.Idle) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    var showSimklUnlinkConfirm by remember { mutableStateOf(false) }
    var showMalUnlinkConfirm by remember { mutableStateOf(false) }
    // D-222: populate confirmation dialog.
    var showPopulateConfirm by remember { mutableStateOf(false) }
    // ROUND 179 (THE POPULATE — the Simkl/MAL parity, the user's order): the
    // same D-221/D-222 machinery the AniList card carries, now on all three.
    var simklPopulateState by remember { mutableStateOf<PopulateState>(PopulateState.Idle) }
    var showSimklPopulateConfirm by remember { mutableStateOf(false) }
    var malPopulateState by remember { mutableStateOf<PopulateState>(PopulateState.Idle) }
    var showMalPopulateConfirm by remember { mutableStateOf(false) }
    val trackEntryRepository = koinInject<com.confused.anikuta.core.trackeranilist.TrackEntryRepository>()

    // ROUND 179: the populate results SURFACE — the D-221 summary string was
    // discarded before (the import completed silently; the one-notice-system
    // doctrine). All three trackers' results ride the themed AppToast now.
    androidx.compose.runtime.LaunchedEffect(populateState) {
        when (val s = populateState) {
            is PopulateState.Done -> {
                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                    s.result, com.confused.anikuta.core.designsystem.component.toast.AppToastTone.SUCCESS,
                )
                populateState = PopulateState.Idle
            }
            is PopulateState.Error -> {
                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                    s.message, com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                )
                populateState = PopulateState.Idle
            }
            else -> {}
        }
    }
    androidx.compose.runtime.LaunchedEffect(simklPopulateState) {
        when (val s = simklPopulateState) {
            is PopulateState.Done -> {
                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                    s.result, com.confused.anikuta.core.designsystem.component.toast.AppToastTone.SUCCESS,
                )
                simklPopulateState = PopulateState.Idle
            }
            is PopulateState.Error -> {
                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                    s.message, com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                )
                simklPopulateState = PopulateState.Idle
            }
            else -> {}
        }
    }
    androidx.compose.runtime.LaunchedEffect(malPopulateState) {
        when (val s = malPopulateState) {
            is PopulateState.Done -> {
                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                    s.result, com.confused.anikuta.core.designsystem.component.toast.AppToastTone.SUCCESS,
                )
                malPopulateState = PopulateState.Idle
            }
            is PopulateState.Error -> {
                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                    s.message, com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                )
                malPopulateState = PopulateState.Idle
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Header ──
            item {
                CollapsingHeader(
                    title = "Trackers",
                    collapsed = collapsed,
                    onBack = onBack,
                )
            }

            // ── AniList tracker card ──
            item {
                AniListCard(
                    // The AniList client id is baked in (48714) — always configured.
                    isConfigured = true,
                    loginState = loginState,
                    populateState = populateState,
                    onLink = {
                        scope.launch {
                            val url = anilistTracker.startLogin()
                            if (url != null) {
                                // Open the OAuth URL in the browser.
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    },
                    onUnlink = { showUnlinkConfirm = true },
                    onPopulate = { showPopulateConfirm = true },
                    onDismissResult = { populateState = PopulateState.Idle },
                )
            }

            // ── Simkl tracker card (ROUND 163 STEP-2: movies + TV series) ──
            item {
                val simklTracker = koinInject<com.confused.anikuta.core.trackersimkl.SimklTracker>()
                val simklLogin by simklTracker.observeLoginState().collectAsState(
                    initial = TrackerLoginState.LoggedOut,
                )
                SimklCard(
                    isConfigured = simklTracker.isConfigured,
                    loginState = simklLogin,
                    populateState = simklPopulateState,
                    onLink = {
                        scope.launch {
                            val url = simklTracker.startLogin()
                            if (url != null) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    },
                    onUnlink = { showSimklUnlinkConfirm = true },
                    onPopulate = { showSimklPopulateConfirm = true },
                )
            }

            // ── MAL tracker card (ROUND 168: the anime tracker redo — AniList + MAL per the user's split) ──
            item {
                val malTracker = koinInject<com.confused.anikuta.core.trackermal.MalTracker>()
                val malLogin by malTracker.observeLoginState().collectAsState(
                    initial = TrackerLoginState.LoggedOut,
                )
                MalCard(
                    isConfigured = malTracker.isConfigured,
                    loginState = malLogin,
                    populateState = malPopulateState,
                    onLink = {
                        scope.launch {
                            val url = malTracker.startLogin()
                            if (url != null) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    },
                    onUnlink = { showMalUnlinkConfirm = true },
                    onPopulate = { showMalPopulateConfirm = true },
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

    // ── Unlink confirmation dialog ──
    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirm = false },
            title = { Text("Unlink AniList?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "This will remove your AniList connection. Your library entries will NOT be deleted.",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUnlinkConfirm = false
                    scope.launch { anilistTracker.logout() }
                }) {
                    Text("Unlink", color = MaterialTheme.colorScheme.error, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }

    // ── Simkl unlink confirmation (ROUND 163 STEP-2) ──
    if (showSimklUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showSimklUnlinkConfirm = false },
            title = { Text("Unlink Simkl?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "This will remove your Simkl connection. Your movie/series entries will NOT be deleted.",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSimklUnlinkConfirm = false
                    scope.launch {
                        org.koin.core.context.GlobalContext.get()
                            .get<com.confused.anikuta.core.trackersimkl.SimklTracker>()
                            .logout()
                    }
                }) {
                    Text("Unlink", color = MaterialTheme.colorScheme.error, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSimklUnlinkConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }

    // ── MAL unlink confirmation (ROUND 168) ──
    if (showMalUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showMalUnlinkConfirm = false },
            title = { Text("Unlink MyAnimeList?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "This will remove your MyAnimeList connection. Your anime entries will NOT be deleted.",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMalUnlinkConfirm = false
                    scope.launch {
                        org.koin.core.context.GlobalContext.get()
                            .get<com.confused.anikuta.core.trackermal.MalTracker>()
                            .logout()
                    }
                }) { Text("Unlink", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showMalUnlinkConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── ROUND 179: the Simkl/MAL populate confirmations (the D-222 parity) ──
    if (showSimklPopulateConfirm) {
        AlertDialog(
            onDismissRequest = { showSimklPopulateConfirm = false },
            title = { Text("Populate Library from Simkl?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "This will fetch your complete Simkl library (anime, series and movies) and create " +
                        "local categories (Watching, Planning, Completed, Dropped, Paused). " +
                        "Each entry will be saved to the matching category. This may take " +
                        "a moment depending on the size of your library.",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSimklPopulateConfirm = false
                    scope.launch {
                        simklPopulateState = PopulateState.Fetching
                        try {
                            val tracker = org.koin.core.context.GlobalContext.get()
                                .get<com.confused.anikuta.core.trackersimkl.SimklTracker>()
                            val result = populateLibraryFromSimkl(tracker, contentRepository, trackEntryRepository)
                            simklPopulateState = PopulateState.Done(result)
                        } catch (e: Exception) {
                            Logger.e(TAG, e) { "Simkl populate failed: ${e.message}" }
                            simklPopulateState = PopulateState.Error(e.message ?: "Unknown error")
                        }
                    }
                }) {
                    Text("Populate", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSimklPopulateConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }

    if (showMalPopulateConfirm) {
        AlertDialog(
            onDismissRequest = { showMalPopulateConfirm = false },
            title = { Text("Populate Library from MyAnimeList?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "This will fetch your complete MyAnimeList and create " +
                        "local categories (Watching, Planning, Completed, Dropped, Paused). " +
                        "Each anime will be saved to the matching category. This may take " +
                        "a moment depending on the size of your list.",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMalPopulateConfirm = false
                    scope.launch {
                        malPopulateState = PopulateState.Fetching
                        try {
                            val tracker = org.koin.core.context.GlobalContext.get()
                                .get<com.confused.anikuta.core.trackermal.MalTracker>()
                            val result = populateLibraryFromMal(tracker, contentRepository, trackEntryRepository)
                            malPopulateState = PopulateState.Done(result)
                        } catch (e: Exception) {
                            Logger.e(TAG, e) { "MAL populate failed: ${e.message}" }
                            malPopulateState = PopulateState.Error(e.message ?: "Unknown error")
                        }
                    }
                }) {
                    Text("Populate", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMalPopulateConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }

    // ── D-222: Populate library confirmation dialog ──
    if (showPopulateConfirm) {
        AlertDialog(
            onDismissRequest = { showPopulateConfirm = false },
            title = { Text("Populate Library?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "This will fetch your complete AniList anime library and create " +
                        "local categories (Watching, Planning, Completed, Dropped, Paused). " +
                        "Each anime will be saved to the matching category. This may take " +
                        "a moment depending on the size of your library.",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPopulateConfirm = false
                    scope.launch {
                        populateState = PopulateState.Fetching
                        try {
                            val result = populateLibraryFromAniList(
                                anilistTracker, contentRepository, contentResolver,
                            )
                            populateState = PopulateState.Done(result)
                        } catch (e: Exception) {
                            Logger.e(TAG, e) { "Populate library failed: ${e.message}" }
                            populateState = PopulateState.Error(e.message ?: "Unknown error")
                        }
                    }
                }) {
                    Text("Populate", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPopulateConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

// ── AniList card ──

// ════════════════════════════════════════════════════════════════════════
//  ROUND 170 (the device order): THE COMPACT TRACKER CARD — one base for
//  the three trackers. The icon leads; the name + the status chip in one
//  row; NO "Connected as" line, NO role descriptions. The actions colored:
//  the Populate button in the primary container, the delete icon in the
//  error tint.
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun CompactTrackerCard(
    name: String,
    logoRes: Int?,
    isConfigured: Boolean,
    linked: Boolean,
    username: String,
    populateLabel: String? = null,
    onPopulate: (() -> Unit)? = null,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (logoRes != null) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(logoRes),
                            contentDescription = name,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = name.first().toString(),
                            fontFamily = RobotoFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (linked && username.isNotBlank()) {
                        Text(
                            text = username,
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                // ROUND 172 (the device order): the populate-less trackers
                // (Simkl/MAL) show NO tag — the LINK button when not linked,
                // the UNLINK button when linked, BOTH at the NAME ROW's
                // rightmost (aligned right, not the bottom-right). The
                // AniList (with the populate) keeps the chip + the bottom
                // actions row.
                if (populateLabel == null) {
                    when {
                        !isConfigured -> {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            ) {
                                Text(
                                    text = "Setup pending",
                                    fontFamily = RobotoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        !linked -> Button(
                            onClick = onLink,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 14.dp,
                                vertical = 8.dp,
                            ),
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Link", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                        }
                        else -> androidx.compose.material3.OutlinedButton(
                            onClick = onUnlink,
                            shape = RoundedCornerShape(8.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 14.dp,
                                vertical = 8.dp,
                            ),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Unlink", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    // The AniList chip (unchanged).
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            !isConfigured -> MaterialTheme.colorScheme.outlineVariant
                            linked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                    ) {
                        Text(
                            text = when {
                                !isConfigured -> "Setup pending"
                                linked -> "Linked"
                                else -> "Not linked"
                            },
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (linked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            // ── The actions row: the Populate + the wide unlink (the AniList
            // only — the populate-less trackers' buttons moved INLINE to the
            // name row; no bottom row for them) ──
            if (populateLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        !isConfigured -> {}
                        !linked -> Button(
                            onClick = onLink,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Link", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                        }
                        else -> {
                            if (onPopulate != null) {
                                androidx.compose.material3.FilledTonalButton(
                                    onClick = onPopulate,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(populateLabel, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                                }
                                Spacer(Modifier.size(8.dp))
                            }
                            // ROUND 171: the WIDE unlink button (the outlined
                            // error — not the bare trash-can icon).
                            androidx.compose.material3.OutlinedButton(
                                onClick = onUnlink,
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Unlink", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun AniListCard(
    isConfigured: Boolean,
    loginState: TrackerLoginState,
    populateState: PopulateState,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onPopulate: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val linked = loginState is TrackerLoginState.LoggedIn
    val username = (loginState as? TrackerLoginState.LoggedIn)?.username ?: ""
    CompactTrackerCard(
        name = "AniList",
        logoRes = R.drawable.ic_tracker_anilist,
        isConfigured = isConfigured,
        linked = linked,
        username = username,
        populateLabel = if (populateState == PopulateState.Fetching) "Fetching..." else "Populate Library",
        onPopulate = if (populateState == PopulateState.Fetching) null else onPopulate,
        onLink = onLink,
        onUnlink = onUnlink,
    )
}

// ── Populate state ──

sealed class PopulateState {
    data object Idle : PopulateState()
    data object Fetching : PopulateState()
    data class Done(val result: String) : PopulateState()
    data class Error(val message: String) : PopulateState()
}

// ── Populate library logic ──

/**
 * D-221: Fetches the user's AniList anime library + creates local categories
 * (Watching/Completed/Paused/Dropped/Planning) + saves each anime to the
 * matching category.
 *
 * @return a summary string for the UI.
 */
private suspend fun populateLibraryFromAniList(
    tracker: AniListTracker,
    contentRepository: ContentRepository,
    contentResolver: ContentResolver,
): String = withContext(Dispatchers.IO) {
    Logger.i(TAG) { "populateLibraryFromAniList: START" }

    // 1. Fetch the user's anime library from AniList.
    val mediaListMap = tracker.fetchUserMediaList()
    if (mediaListMap.isEmpty()) {
        Logger.w(TAG) { "populateLibraryFromAniList: AniList library is empty or fetch failed" }
        return@withContext "Your AniList library is empty or could not be fetched."
    }

    // 2. Create the 5 standard categories if they don't exist.
    val categoryMap = mutableMapOf<String, Long>()
    for ((anilistStatus, categoryName) in AniListStatusMapping) {
        val existing = contentRepository.getAllCategories().find { it.name == categoryName }
        val catId = existing?.id ?: contentRepository.createCategory(categoryName)
        categoryMap[anilistStatus] = catId
        Logger.i(TAG) { "Category '$categoryName' (status=$anilistStatus) → id=$catId" }
    }

    // 3. For each status, fetch + save each anime.
    var totalImported = 0
    val statusCounts = mutableMapOf<String, Int>()

    for ((anilistStatus, entries) in mediaListMap) {
        val categoryId = categoryMap[anilistStatus] ?: continue
        Logger.i(TAG) { "Processing status=$anilistStatus (${entries.size} entries)" }

        for (entry in entries) {
            try {
                // D-222 FIX: Build the ContentDetails BEFORE calling the resolver,
                // then pass it via `anilistDetail` so resolveOrCreateForAniList
                // inserts the content_details row immediately (via upsertContentDetailsForAniList).
                // The old code called resolveOrCreateForAniList WITHOUT anilistDetail → no
                // content_details row was created → updateDataSourceAxis was a silent no-op
                // → the Library page showed entries as empty when switching categories.
                // (Same bug pattern as D-206 for resolveOrCreateForExtension.)
                val details = com.confused.anikuta.core.content.ContentDetails(
                    mainId = "", // resolver overwrites with the real mainId
                    dataSourceType = "anilist",
                    dataSourceRefId = entry.mediaId.toString(),
                    dataCoverUrl = entry.coverUrl,
                    dataBannerUrl = entry.bannerUrl,
                    dataSynopsis = entry.description,
                    dataGenres = entry.genres.joinToString(", "),
                    dataEpisodes = entry.episodes?.toLong(),
                    dataSeason = entry.season,
                    dataSeasonYear = entry.seasonYear?.toLong(),
                    dataStatus = entry.mediaStatus,
                    dataScore = entry.averageScore?.toLong(),
                    dataUpdatedAt = System.currentTimeMillis(),
                )

                // Resolve or create the main_entry + content_details for this AniList anime.
                val mainId = contentResolver.resolveOrCreateForAniList(
                    anilistId = entry.mediaId,
                    title = entry.title,
                    anilistDetail = details, // D-222: pass the detail so the row is created
                )

                // Add to the matching category (if not already in the library).
                if (!contentRepository.isInLibrary(mainId)) {
                    contentRepository.addToCategory(mainId, categoryId)
                    totalImported++
                    statusCounts[anilistStatus] = (statusCounts[anilistStatus] ?: 0) + 1
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Failed to import anime ${entry.mediaId} (${entry.title}): ${e.message}" }
            }
        }
    }

    Logger.i(TAG) { "populateLibraryFromAniList: DONE — imported $totalImported anime" }

    // Build the summary string.
    val summary = buildString {
        appendLine("Imported $totalImported anime from AniList.")
        for ((status, count) in statusCounts) {
            val categoryName = AniListStatusMapping[status] ?: status
            appendLine("  • $categoryName: $count")
        }
    }
    summary
}

/**
 * D-221: Maps AniList MediaListStatus → our category names.
 * CURRENT → Watching, PLANNING → Planning, COMPLETED → Completed,
 * DROPPED → Dropped, PAUSED → Paused, REPEATING → (merged into Watching).
 */
private val AniListStatusMapping = mapOf(
    "CURRENT" to "Watching",
    "REPEATING" to "Watching",   // D-242-fix: AniList's rewatching status → Watching category
    "PLANNING" to "Planning",
    "COMPLETED" to "Completed",
    "DROPPED" to "Dropped",
    "PAUSED" to "Paused",
)

// ── ROUND 179: THE POPULATE — the Simkl/MAL parity ──
// The user's order: the AniList D-221 import machinery on ALL THREE trackers.
// The doctrine is identical: the SHARED 5 status categories (find-or-create),
// the reverse-index dedupe (a row already linked to the remote entry is
// skipped, never duplicated), the row created as a metadata main under the
// tracker's OWN id convention (simkl:<id> / mal:<id> — the details page
// resolves them exactly like the search-persisted rows), the tracking seed
// (the mapping makes the row honestly tracked — the relay maintains it), and
// the entry saved to the matching category.

private val SimklCategoryMapping = linkedMapOf(
    "watching" to "Watching",
    "plantowatch" to "Planning",
    "completed" to "Completed",
    "hold" to "Paused",
    "dropped" to "Dropped",
)

private val MalCategoryMapping = linkedMapOf(
    "watching" to "Watching",
    "plan_to_watch" to "Planning",
    "completed" to "Completed",
    "on_hold" to "Paused",
    "dropped" to "Dropped",
)

private suspend fun populateLibraryFromSimkl(
    tracker: com.confused.anikuta.core.trackersimkl.SimklTracker,
    contentRepository: ContentRepository,
    trackEntryRepository: com.confused.anikuta.core.trackeranilist.TrackEntryRepository,
): String = withContext(Dispatchers.IO) {
    Logger.i(TAG) { "populateLibraryFromSimkl: START" }
    val entries = try {
        tracker.userLibrary()
    } catch (e: Exception) {
        Logger.w(TAG) { "populateLibraryFromSimkl: fetch failed: ${e.message}" }
        return@withContext "Your Simkl library could not be fetched — check your connection."
    }
    if (entries.isEmpty()) {
        Logger.w(TAG) { "populateLibraryFromSimkl: the Simkl library is empty" }
        return@withContext "Your Simkl library is empty."
    }

    // The SHARED categories (the AniList populate created them — find-or-create).
    val categoryMap = mutableMapOf<String, Long>()
    for ((rawStatus, categoryName) in SimklCategoryMapping) {
        val existing = contentRepository.getAllCategories().find { it.name == categoryName }
        val catId = existing?.id ?: contentRepository.createCategory(categoryName)
        categoryMap[rawStatus] = catId
    }

    var imported = 0
    var skipped = 0
    val statusCounts = mutableMapOf<String, Int>()
    for (entry in entries) {
        try {
            val mainId = "simkl:" + entry.simklId
            val ref = entry.simklId.toString()
            // The reverse index is the dedupe authority: a row already linked
            // to this Simkl entry (from the search flow, an earlier import —
            // any path) is never duplicated.
            if (contentRepository.lookupIdentityMain("simkl", ref) != null) {
                skipped++
                continue
            }
            val now = System.currentTimeMillis()
            contentRepository.insertMainEntryIfMissing(
                com.confused.anikuta.core.content.ContentRecord(
                    mainId = mainId,
                    contentId = mainId,
                    title = entry.title,
                    contentType = "anime",
                    contentFormat = "video",
                    displaySource = "metadata",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            contentRepository.upsertContentDetails(
                com.confused.anikuta.core.content.ContentDetails(
                    mainId = mainId,
                    dataSourceType = "simkl",
                    dataSourceRefId = ref,
                    dataCoverUrl = entry.posterUrl,
                    dataScore = entry.userRating?.times(10)?.toLong(),
                    dataUpdatedAt = now,
                ),
            )
            contentRepository.upsertIdentity(
                com.confused.anikuta.core.content.ContentIdentity(
                    mainId = mainId,
                    sourceKind = "simkl",
                    sourceRefId = ref,
                    displayTitle = entry.title,
                    coverUrl = entry.posterUrl,
                    synopsis = null,
                    score = entry.userRating,
                    episodes = null,
                    status = entry.rawStatus,
                    genres = null,
                    year = null,
                    displayRank = 0,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            // The tracking seed: the mapping makes the row honestly Simkl-
            // tracked (the relay maintains it from here).
            trackEntryRepository.upsert(
                com.confused.anikuta.core.trackerapi.TrackEntry(
                    contentKey = mainId,
                    trackerId = entry.simklId.toInt(),
                    status = entry.status,
                    progress = entry.progress,
                    totalEpisodes = null,
                    listId = null,
                    startedAt = null,
                    completedAt = null,
                    updatedAt = now,
                ),
                com.confused.anikuta.core.trackerapi.TrackerType.SIMKL,
            )
            val categoryId = categoryMap[entry.rawStatus]
            if (categoryId != null && !contentRepository.isInLibrary(mainId)) {
                contentRepository.addToCategory(mainId, categoryId)
                imported++
                statusCounts[entry.rawStatus] = (statusCounts[entry.rawStatus] ?: 0) + 1
            }
        } catch (e: Exception) {
            skipped++
            Logger.w(TAG) { "populateLibraryFromSimkl: entry ${entry.simklId} failed: ${e.message}" }
        }
    }
    Logger.i(TAG) { "populateLibraryFromSimkl: DONE — imported $imported, skipped $skipped" }
    buildString {
        appendLine("Imported $imported entries from Simkl${if (skipped > 0) " ($skipped skipped)" else ""}.")
        for ((rawStatus, count) in statusCounts) {
            appendLine("  • ${SimklCategoryMapping[rawStatus] ?: rawStatus}: $count")
        }
    }
}

private suspend fun populateLibraryFromMal(
    tracker: com.confused.anikuta.core.trackermal.MalTracker,
    contentRepository: ContentRepository,
    trackEntryRepository: com.confused.anikuta.core.trackeranilist.TrackEntryRepository,
): String = withContext(Dispatchers.IO) {
    Logger.i(TAG) { "populateLibraryFromMal: START" }
    val entries = try {
        tracker.userAnimelist()
    } catch (e: Exception) {
        Logger.w(TAG) { "populateLibraryFromMal: fetch failed: ${e.message}" }
        return@withContext "Your MyAnimeList could not be fetched — check your connection."
    }
    if (entries.isEmpty()) {
        Logger.w(TAG) { "populateLibraryFromMal: the MAL list is empty" }
        return@withContext "Your MyAnimeList is empty."
    }

    val categoryMap = mutableMapOf<String, Long>()
    for ((rawStatus, categoryName) in MalCategoryMapping) {
        val existing = contentRepository.getAllCategories().find { it.name == categoryName }
        val catId = existing?.id ?: contentRepository.createCategory(categoryName)
        categoryMap[rawStatus] = catId
    }

    var imported = 0
    var skipped = 0
    val statusCounts = mutableMapOf<String, Int>()
    for (entry in entries) {
        try {
            val mainId = "mal:" + entry.anime.id
            val ref = entry.anime.id.toString()
            if (contentRepository.lookupIdentityMain("mal", ref) != null) {
                skipped++
                continue
            }
            val now = System.currentTimeMillis()
            contentRepository.insertMainEntryIfMissing(
                com.confused.anikuta.core.content.ContentRecord(
                    mainId = mainId,
                    contentId = mainId,
                    title = entry.anime.title ?: "MAL ${entry.anime.id}",
                    contentType = "anime",
                    contentFormat = "video",
                    displaySource = "metadata",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            contentRepository.upsertContentDetails(
                com.confused.anikuta.core.content.ContentDetails(
                    mainId = mainId,
                    dataSourceType = "mal",
                    dataSourceRefId = ref,
                    dataCoverUrl = entry.anime.main_picture?.large ?: entry.anime.main_picture?.medium,
                    dataScore = entry.anime.mean?.let { (it * 10).toLong() },
                    dataEpisodes = entry.anime.num_episodes?.toLong(),
                    dataSeasonYear = entry.anime.start_season?.year?.toLong(),
                    dataUpdatedAt = now,
                ),
            )
            contentRepository.upsertIdentity(
                com.confused.anikuta.core.content.ContentIdentity(
                    mainId = mainId,
                    sourceKind = "mal",
                    sourceRefId = ref,
                    displayTitle = entry.anime.title,
                    coverUrl = entry.anime.main_picture?.large ?: entry.anime.main_picture?.medium,
                    synopsis = null,
                    score = entry.anime.mean?.toInt(),
                    episodes = entry.anime.num_episodes,
                    status = entry.rawStatus,
                    genres = null,
                    year = entry.anime.start_season?.year,
                    displayRank = 0,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            trackEntryRepository.upsert(
                com.confused.anikuta.core.trackerapi.TrackEntry(
                    contentKey = mainId,
                    trackerId = entry.anime.id,
                    status = entry.status,
                    progress = entry.anime.my_list_status?.num_episodes_watched ?: 0,
                    totalEpisodes = entry.anime.num_episodes,
                    listId = null,
                    startedAt = null,
                    completedAt = null,
                    updatedAt = now,
                ),
                com.confused.anikuta.core.trackerapi.TrackerType.MAL,
            )
            val categoryId = categoryMap[entry.rawStatus]
            if (categoryId != null && !contentRepository.isInLibrary(mainId)) {
                contentRepository.addToCategory(mainId, categoryId)
                imported++
                statusCounts[entry.rawStatus] = (statusCounts[entry.rawStatus] ?: 0) + 1
            }
        } catch (e: Exception) {
            skipped++
            Logger.w(TAG) { "populateLibraryFromMal: entry ${entry.anime.id} failed: ${e.message}" }
        }
    }
    Logger.i(TAG) { "populateLibraryFromMal: DONE — imported $imported, skipped $skipped" }
    buildString {
        appendLine("Imported $imported anime from MyAnimeList${if (skipped > 0) " ($skipped skipped)" else ""}.")
        for ((rawStatus, count) in statusCounts) {
            appendLine("  • ${MalCategoryMapping[rawStatus] ?: rawStatus}: $count")
        }
    }
}

// ── Simkl card (ROUND 163 STEP-2) — the AniListCard's visual language, one
// role text richer: Simkl carries the MOVIES + TV SERIES tracking (anime
// stays AniList/MAL per the user's order). An unregistered client id renders
// the honest "not configured" state (no half-links).

@Composable
private fun SimklCard(
    isConfigured: Boolean,
    loginState: TrackerLoginState,
    populateState: PopulateState,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onPopulate: () -> Unit,
) {
    val linked = loginState is TrackerLoginState.LoggedIn
    val username = (loginState as? TrackerLoginState.LoggedIn)?.username ?: ""
    CompactTrackerCard(
        name = "Simkl",
        logoRes = R.drawable.ic_tracker_simkl,
        isConfigured = isConfigured,
        linked = linked,
        username = username,
        populateLabel = if (populateState == PopulateState.Fetching) "Fetching..." else "Populate Library",
        onPopulate = if (populateState == PopulateState.Fetching) null else onPopulate,
        onLink = onLink,
        onUnlink = onUnlink,
    )
}

// ── MAL card (ROUND 168: the anime tracker redo — the Simkl card's anatomy,
//    the anime/manga role line per the approved round-165 shape) ──
@Composable
private fun MalCard(
    isConfigured: Boolean,
    loginState: TrackerLoginState,
    populateState: PopulateState,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onPopulate: () -> Unit,
) {
    val linked = loginState is TrackerLoginState.LoggedIn
    val username = (loginState as? TrackerLoginState.LoggedIn)?.username ?: ""
    CompactTrackerCard(
        name = "MyAnimeList",
        logoRes = R.drawable.ic_tracker_mal,
        isConfigured = isConfigured,
        linked = linked,
        username = username,
        populateLabel = if (populateState == PopulateState.Fetching) "Fetching..." else "Populate Library",
        onPopulate = if (populateState == PopulateState.Fetching) null else onPopulate,
        onLink = onLink,
        onUnlink = onUnlink,
    )
}
