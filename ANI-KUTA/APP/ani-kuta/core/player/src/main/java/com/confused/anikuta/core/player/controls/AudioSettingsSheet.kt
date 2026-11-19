package com.confused.anikuta.core.player.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.NumericEntrySheet
import com.confused.anikuta.core.preferences.PlayerPreferences

/**
 * ROUND 157 (WS-E1): the audio-delay sheet — the subtitle-delay pattern's
 * audio twin. Same stepper anatomy (±100ms taps + a tap-to-type numeric
 * entry), same range, persisted per change and applied LIVE by the host via
 * [AnikutaMPVView.applyAudioPreferences]. MPV only — the CS stack's media3
 * engine has no stock audio offset (its AudioProcessor path is a later
 * round), so this sheet is opened from the MPV stack's audio sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsSheet(
    playerPreferences: PlayerPreferences,
    onApplySettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var delay by remember { mutableIntStateOf(playerPreferences.audioDelay) }
    var editing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Audio delay",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Shift the audio earlier (−) or later (+) to fix dub sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            AudioDelayStepperRow(
                delay = delay,
                onChange = {
                    delay = it
                    playerPreferences.audioDelay = it
                    onApplySettings()
                },
                onTapValue = { editing = true },
            )
        }
    }

    if (editing) {
        NumericEntrySheet(
            title = "Audio delay",
            initial = delay,
            suffix = "ms",
            min = -5000,
            max = 5000,
            onLiveChange = { v ->
                delay = v.coerceIn(-5000, 5000)
                playerPreferences.audioDelay = delay
                onApplySettings()
            },
            onConfirm = { v ->
                delay = v
                playerPreferences.audioDelay = v
                onApplySettings()
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun AudioDelayStepperRow(
    delay: Int,
    onChange: (Int) -> Unit,
    onTapValue: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Delay",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(32.dp).clickable { onChange((delay - 100).coerceIn(-5000, 5000)) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, contentDescription = "−100ms", modifier = Modifier.size(18.dp))
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.clickable(onClick = onTapValue),
            ) {
                Text(
                    text = "${delay}ms",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(32.dp).clickable { onChange((delay + 100).coerceIn(-5000, 5000)) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "+100ms", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
