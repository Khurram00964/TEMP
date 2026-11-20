package com.confused.anikuta.core.player.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.common.playback.PlaybackCommandBus
import com.confused.anikuta.core.common.playback.PlaybackControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ROUND 159 (WS-BG): the background-playback service — a FOREGROUND
 * `mediaPlayback` service whose only job is (a) keeping the PROCESS alive
 * while an episode plays with the app backgrounded and (b) carrying the
 * media notification (play/pause/±10s/next). Stack-agnostic: it talks ONLY
 * to [PlaybackCommandBus] — the MPV and CS screens register their controls
 * there, so one service serves both.
 *
 * Lifecycle:
 * - MainActivity's ON_STOP starts it (when the pref is on + something is
 *   playing); ON_START stops it (the user is back — the player UI owns
 *   everything again).
 * - The service itself stops when the controls unregister (the episode
 *   screen left) or the playback stops (nothing to protect).
 *
 * NOT a MediaSession yet: the notification carries the action buttons
 * (modern Android shows them on the lockscreen too); a full
 * media3-session integration (the lockscreen chrome + the wearable layer)
 * is the planned upgrade.
 */
class PlaybackService : Service() {

    companion object {
        private const val TAG = "Anikuta:Core:PlaybackService"
        private const val CHANNEL_ID = "playback_background"
        private const val NOTIFICATION_ID = 4242

        /** The service start/stop entry points (MainActivity + the screens). */
        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val actionPlay = "com.confused.anikuta.playback.PLAY"
    private val actionPause = "com.confused.anikuta.playback.PAUSE"
    private val actionSeekBack = "com.confused.anikuta.playback.SEEK_BACK"
    private val actionSeekForward = "com.confused.anikuta.playback.SEEK_FORWARD"
    private val actionNext = "com.confused.anikuta.playback.NEXT"
    private val actionStop = "com.confused.anikuta.playback.STOP"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // The state watcher: rebuild the notification on any playback change;
        // stop when the episode's controls go away (the screen disposed).
        scope.launch {
            PlaybackCommandBus.controls.collect { controls ->
                if (controls == null) {
                    Logger.i(TAG) { "controls unregistered — stopping" }
                    stopForegroundCompat()
                    stopSelf()
                } else {
                    postNotification(controls)
                }
            }
        }
        scope.launch {
            PlaybackCommandBus.version.collect {
                // ROUND 159 (the round-3 critic's P1): the playback ENDED in
                // the background (paused/EOF with the app away) — the FGS +
                // the notification would linger forever (and the notification
                // has no close action). Stop the service; the app's return
                // restarts it on the next leave if still playing.
                val controls = PlaybackCommandBus.controls.value
                if (controls != null && !controls.isPlaying) {
                    Logger.i(TAG) { "playback stopped in background — stopping the service" }
                    stopForegroundCompat()
                    stopSelf()
                    return@collect
                }
                postNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The foreground promotion MUST happen in onStartCommand (the
        // startForegroundService contract).
        startForegroundCompat()
        when (intent?.action) {
            actionPlay -> PlaybackCommandBus.controls.value?.play()
            actionPause -> PlaybackCommandBus.controls.value?.pause()
            actionSeekBack -> PlaybackCommandBus.controls.value?.seekRelative(-10)
            actionSeekForward -> PlaybackCommandBus.controls.value?.seekRelative(10)
            actionNext -> PlaybackCommandBus.controls.value?.playNext()
            actionStop -> {
                PlaybackCommandBus.controls.value?.pause()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        postNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun startForegroundCompat() {
        val controls = PlaybackCommandBus.controls.value
        val notification = buildNotification(controls)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun postNotification() {
        val controls = PlaybackCommandBus.controls.value ?: return
        postNotification(controls)
    }

    private fun postNotification(controls: PlaybackControls) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(controls))
    }

    private fun buildNotification(controls: PlaybackControls?): Notification {
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun action(action: String, icon: Int, title: String): NotificationCompat.Action {
            return NotificationCompat.Action(
                icon, title,
                PendingIntent.getService(
                    this, action.hashCode(),
                    Intent(this, PlaybackService::class.java).setAction(action),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        val playing = controls?.isPlaying == true
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(controls?.title ?: "ANI-KUTA")
            .setContentText(controls?.subtitle ?: "")
            .setContentIntent(contentIntent)
            // ROUND 159 (the critic's P2): ALWAYS ongoing — a swipe-dismissed
            // notification left a running FGS with no way to end it.
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                action(actionSeekBack, android.R.drawable.ic_media_rew, "-10s"),
            )
            .addAction(
                action(
                    if (playing) actionPause else actionPlay,
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (playing) "Pause" else "Play",
                ),
            )
            .addAction(
                action(actionSeekForward, android.R.drawable.ic_media_ff, "+10s"),
            )

        // NOTE (ROUND 159): the "next episode" action needs a NON-mutating
        // has-next probe (the PlaybackControls.playNext() ADVANCES — probing
        // it here would skip the episode on every notification rebuild). The
        // PlaybackControls interface gains hasNext(): Boolean in the follow-up
        // commit; the action lands with it.
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background playback",
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = "Keeps your episode playing when the app is in the background"
            manager.createNotificationChannel(channel)
        }
    }
}
