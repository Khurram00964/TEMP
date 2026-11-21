"""ROUND 168 Phase D: the MAL card + the unlink dialog on TrackersScreen (the Simkl card's anatomy mirrored)."""
p = r"C:\Users\khurr\Desktop\ANI-KUTA\project\ANI-KUTA\ANI-KUTA\APP\ani-kuta\app\src\main\java\com\confused\anikuta\settings\TrackersScreen.kt"
s = open(p, encoding="utf-8").read()

# 1. The MAL card item after the Simkl item (inside the items block)
old = """                    onUnlink = { showSimklUnlinkConfirm = true },
                )
            }
        }
"""
assert old in s, "simkl item tail not found"
new = """                    onUnlink = { showSimklUnlinkConfirm = true },
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
                )
            }
        }
"""
s = s.replace(old, new)

# 2. The state flag beside the Simkl one
old = "    var showSimklUnlinkConfirm by remember { mutableStateOf(false) }\n"
assert old in s, "state flag anchor not found"
s = s.replace(old, old + "    var showMalUnlinkConfirm by remember { mutableStateOf(false) }\n")

# 3. The MAL unlink confirm dialog — inserted before the Populate confirm comment
pop_anchor = "    // ── Populate"
assert pop_anchor in s, "populate anchor not found"
mal_dialog = """    // ── MAL unlink confirmation (ROUND 168) ──
    if (showMalUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showMalUnlinkConfirm = false },
            title = { Text("Unlink MyAnimeList?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "This will remove your MyAnimeList connection. Your anime entries will NOT be deleted.",
                    fontFamily = RobotoFamily,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMalUnlinkConfirm = false
                        scope.launch {
                            runCatching {
                                org.koin.core.context.GlobalContext.get()
                                    .get<com.confused.anikuta.core.trackermal.MalTracker>()
                                    .logout()
                            }
                        }
                    },
                ) { Text("Unlink", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showMalUnlinkConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

"""
idx = s.index(pop_anchor)
s = s[:idx] + mal_dialog + s[idx:]

# 4. The MalCard composable appended after SimklCard's end (the file's last composable)
tail_anchor = """                else -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onUnlink,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
"""
assert tail_anchor in s, "simkl card tail not found"
mal_card = """

// ── MAL card (ROUND 168: the anime tracker redo — the Simkl card's anatomy,
//    the anime/manga role line per the approved round-165 shape) ──
@Composable
private fun MalCard(
    isConfigured: Boolean,
    loginState: TrackerLoginState,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
) {
    val linked = loginState is TrackerLoginState.LoggedIn
    val username = (loginState as? TrackerLoginState.LoggedIn)?.username ?: ""

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Title row (the AniList card's anatomy) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MyAnimeList",
                        fontFamily = RobotoFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Anime and manga",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

            // ── Username (if linked) ──
            if (linked) {
                Text(
                    text = "Connected as: $username",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Action buttons ──
            when {
                !isConfigured -> Text(
                    text = "Waiting on the MAL app registration (the client id) — " +
                        "the link button lights up as soon as it lands.",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                !linked -> Button(
                    onClick = onLink,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Link MyAnimeList", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
                else -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onUnlink,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
"""
s = s.replace(tail_anchor, tail_anchor + mal_card, 1)

open(p, "w", encoding="utf-8", newline="\n").write(s)
print("MalCard wired OK")
