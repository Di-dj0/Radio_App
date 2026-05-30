package com.example.radioplayer.service

import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.radioplayer.manager.RadioPlaybackManager
import com.example.radioplayer.manager.RadioStationFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class RadioMediaService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playbackManager: RadioPlaybackManager? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var fadeOutJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }

        mediaSession = MediaSession.Builder(this, player!!).build()

        var station = RadioStationFactory.createFromAssets(this, "K-DST")
        if (station != null) {
            playbackManager = RadioPlaybackManager(station)
        }

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playNextTrack()
                }
            }

            // Se o player não conseguir ler um arquivo, ele avisa aqui
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                println("ERRO DA RÁDIO K-DST: O arquivo falhou -> ${error.message}")
                // Força a rádio a pular para o próximo áudio para a transmissão não cair
                playNextTrack()
            }
        })

        startFadeOutMonitor()
    }

    private fun startFadeOutMonitor() {
        fadeOutJob = serviceScope.launch {
            while (isActive) {
                val exoPlayer = player ?: break

                if (exoPlayer.isPlaying) {
                    // Verifica se o áudio tocando agora é uma MÚSICA (através do ID que criamos)
                    val currentMediaId = exoPlayer.currentMediaItem?.mediaId ?: ""
                    val isMusic = currentMediaId.contains("_music_")

                    if (isMusic && exoPlayer.duration > 0) {
                        val timeLeft = exoPlayer.duration - exoPlayer.currentPosition

                        // Se falta 4 segundos
                        if (timeLeft in 1..4000) {
                            // Regra de três: 5000ms = volume 1.0 | 0ms = volume 0.0
                            val newVolume = (timeLeft / 4000f).coerceIn(0f, 1f)
                            exoPlayer.volume = newVolume

                            // Se faltar menos de meio segundo, pula pro Jingle para não ficar um silêncio total chato
                            if (timeLeft <= 500) {
                                exoPlayer.seekTo(exoPlayer.duration)
                            }
                        } else if (timeLeft > 5000 && exoPlayer.volume < 1.0f) {
                            // Garantia de segurança: se voltarmos a música, o volume volta pra 100%
                            exoPlayer.volume = 1.0f
                        }
                    }
                }
                delay(200) // Faz a checagem 5 vezes por segundo
            }
        }
    }

    fun playNextTrack() {
        val nextTrack = playbackManager?.getNextTrack() ?: return

        val station = playbackManager?.station

        val assetUri = Uri.parse("file:///android_asset/${nextTrack.filePath}")

        val metadata = MediaMetadata.Builder()
            .setTitle(nextTrack.title)
            .setArtist(station?.name)
            .setArtworkUri(Uri.parse(station?.iconPath))
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(assetUri)
            .setMediaId(nextTrack.id)
            .setMediaMetadata(metadata)
            .build()

        player?.volume = 1.0f
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        player?.release()
        mediaSession?.release()
        player = null
        mediaSession = null
        super.onDestroy()
    }

}