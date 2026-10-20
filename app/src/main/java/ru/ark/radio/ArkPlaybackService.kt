package ru.ark.radio

import android.media.audiofx.Equalizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class ArkPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var equalizer: Equalizer? = null

    override fun onCreate() {
        super.onCreate()
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) = attachEqualizer(audioSessionId)
            })
        }
        mediaSession = MediaSession.Builder(this, exoPlayer!!).build()
        if (exoPlayer!!.audioSessionId != C.AUDIO_SESSION_ID_UNSET) attachEqualizer(exoPlayer!!.audioSessionId)
    }

    private fun attachEqualizer(id: Int) {
        try {
            equalizer?.release()
            equalizer = Equalizer(0, id).apply { enabled = true }
            audioSessionId = id
        } catch (_: Exception) { audioSessionId = 0 }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        equalizer?.release()
        mediaSession?.run { player.release(); release() }
        mediaSession = null; exoPlayer = null; equalizer = null
        super.onDestroy()
    }

    companion object { @Volatile var audioSessionId: Int = 0; private set }
}
