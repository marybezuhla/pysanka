package ua.pysanka.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.patrickgold.florisboard.R

/**
 * Pysanka: keeps reading aloud with the window closed or the screen off, only when the person turned
 * on «Читати у фоні»: the controls in the notification and on the lock screen, headphone buttons, a
 * pause when the headphones come out or a call comes in. The text itself is never shown outside the
 * reading window (the notification only says that Pysanka is reading).
 */
class ReadAloudService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSession
    private lateinit var audio: AudioManager
    private var focus: AudioFocusRequest? = null
    private var foreground = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) ReadAloud.pause() }
    }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        session = MediaSession(this, "Pysanka").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = ReadAloud.resume()
                override fun onPause() = ReadAloud.pause()
                override fun onSkipToNext() = ReadAloud.skip(1)
                override fun onSkipToPrevious() = ReadAloud.skip(-1)
                override fun onStop() = ReadAloud.stop()
            })
            isActive = true
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.pysanka__speech__title), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        androidx.core.content.ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            ReadAloud.state.map { it.active to it.playing }.distinctUntilChanged().collect { (active, playing) ->
                if (!active) { stopSelf(); return@collect }
                if (playing) takeFocus() else dropFocus()
                session.setPlaybackState(
                    PlaybackState.Builder()
                        .setActions(
                            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP,
                        )
                        .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build(),
                )
                session.setMetadata(
                    MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, getString(R.string.pysanka__speech__title))
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.app_name)).build(),
                )
                show(playing)
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) ReadAloud.pause()
    }

    private fun takeFocus() {
        if (focus != null) return
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(focusListener).build().also { audio.requestAudioFocus(it) }
    }

    private fun dropFocus() { focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null }

    private fun action(code: Int, action: String) =
        PendingIntent.getService(this, code, Intent(this, ReadAloudService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun show(playing: Boolean) {
        // Back to the reading window (it shows the reading under way)
        val open = PendingIntent.getActivity(
            this, 9,
            Intent(this, ReadAloudActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(getString(R.string.pysanka__speech__title))
            .setContentText(getString(R.string.app_name))
            .setContentIntent(open)
            .setOngoing(playing)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_previous), getString(R.string.pysanka__speech__previous), action(1, ACTION_PREVIOUS)).build())
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
                    getString(if (playing) R.string.pysanka__speech__pause else R.string.pysanka__speech__play), action(2, ACTION_TOGGLE),
                ).build(),
            )
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_next), getString(R.string.pysanka__speech__next), action(3, ACTION_NEXT)).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), getString(R.string.pysanka__speech__stop), action(4, ACTION_STOP)).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
        if (!foreground) {
            // Android may refuse it from the background: then the controls are only shown
            foreground = try {
                if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(ID, n)
                true
            } catch (e: Exception) {
                // Not allowed in the foreground now: Android would stop the process; reading goes on only while the window is open
                runCatching { getSystemService(NotificationManager::class.java).notify(ID, n) }
                stopSelf()
                false
            }
        } else {
            runCatching { getSystemService(NotificationManager::class.java).notify(ID, n) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> ReadAloud.toggle()
            ACTION_NEXT -> ReadAloud.skip(1)
            ACTION_PREVIOUS -> ReadAloud.skip(-1)
            ACTION_STOP -> ReadAloud.stop()
        }
        // In the foreground at once, every time it is started (Android asks for it within seconds)
        if (!foreground) show(ReadAloud.state.value.playing)
        if (!ReadAloud.state.value.active) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        dropFocus()
        try { unregisterReceiver(noisy) } catch (_: Exception) {}
        session.release()
        getSystemService(NotificationManager::class.java).cancel(ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "reading"
        private const val ID = 7
        private const val ACTION_TOGGLE = "ua.pysanka.read.TOGGLE"
        private const val ACTION_NEXT = "ua.pysanka.read.NEXT"
        private const val ACTION_PREVIOUS = "ua.pysanka.read.PREVIOUS"
        private const val ACTION_STOP = "ua.pysanka.read.STOP"

        fun show(context: Context) {
            try { context.startForegroundService(Intent(context, ReadAloudService::class.java)) } catch (_: Exception) {}
        }

        fun hide(context: Context) {
            try { context.stopService(Intent(context, ReadAloudService::class.java)) } catch (_: Exception) {}
        }
    }
}
