package com.example.radioplayer.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.example.radioplayer.manager.RadioPlaybackManager
import com.example.radioplayer.manager.RadioStationFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import androidx.media3.common.PlaybackException
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import com.example.radioplayer.ui.MainActivity
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

class RadioMediaService : MediaSessionService() {
    // Dual Deck pra crossfade
    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var activePlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playbackManager: RadioPlaybackManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var fadeOutJob: Job? = null
    private var fadeInJob: Job? = null
    // Memória para saber se o último áudio foi um comercial
    private var wasLastTrackAd = false
    private var isFirstTrackOfStation = false
    private var isTuningTransition = false
    private var pendingStationId: String? = null

    override fun onCreate() {
        super.onCreate()

        playerA = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            setHandleAudioBecomingNoisy(true)
        }
        playerB = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            setHandleAudioBecomingNoisy(true)
        }
        activePlayer = playerA



        val playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {

                // --- tuning effect ---
                if (playbackState == Player.STATE_READY && isFirstTrackOfStation) {
                    isFirstTrackOfStation = false
                    val duration = activePlayer?.duration ?: 0L

                    if (duration > 60000L) {
                        // Para faixas com mais de 1 minuto
                        val randomStartPosition = (10000L..(duration - 30000L)).random()
                        activePlayer?.seekTo(randomStartPosition)
                    }
                    // Para o resto dos arquivos (com trava de segurança contra crash em jingles curtos)
                    else if (duration > 5000L) {
                        val randomStartPosition = (2000L..(duration - 3000L)).random()
                        activePlayer?.seekTo(randomStartPosition)
                    }
                }

                // Só avança a fila se o player ATIVO terminar.
                // (Se a pickup do fundo terminar o áudio, ela morre em silêncio)
                if (playbackState == Player.STATE_ENDED) {
                    if (isTuningTransition) {
                        // Os 2 áudios de rádio sintonizando ACABARAM! Hora de soltar a rádio real
                        isTuningTransition = false
                        startPendingStation()
                    } else if (activePlayer?.playbackState == Player.STATE_ENDED) {
                        // Avanço normal de faixas da rádio
                        playNextTrack()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Se o usuário pausar o app durante a mistura de 2 segundos,
                // precisamos forçar a pickup do fundo a pausar também
                if (!isPlaying) {
                    if (activePlayer == playerA) playerB?.pause() else playerA?.pause()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                println("ERRO DA RÁDIO: Arquivo falhou -> ${error.message}")
                playNextTrack()
            }
        }

        playerA?.addListener(playerListener)
        playerB?.addListener(playerListener)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, getForwardingPlayer(activePlayer!!))
            .setSessionActivity(pendingIntent)
            .setCallback(mediaSessionCallback)
            .build()
        val station = RadioStationFactory.createFromAssets(this, "k-dst")
        if (station != null) {
            playbackManager = RadioPlaybackManager(station)
            isFirstTrackOfStation = true
        }

        playInitialStatic()
        startCrossfadeMonitor()
    }

    private fun playInitialStatic() {
        val assetUri = Uri.parse("file:///android_asset/general/ruido.mp3")
        val metadata = MediaMetadata.Builder()
            .setTitle("Estação não sintonizada")
            .setArtist("Ruído Estático")
            .setSubtitle("---")
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(assetUri)
            .setMediaId("general_static")
            .setMediaMetadata(metadata)
            .build()

        activePlayer?.repeatMode = Player.REPEAT_MODE_ONE // Liga o loop infinito para o chiado
        activePlayer?.volume = 0.125f
        activePlayer?.setMediaItem(mediaItem)
        activePlayer?.prepare()
        activePlayer?.play()
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand("SWITCH_STATION", Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(availableCommands, connectionResult.availablePlayerCommands)
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (customCommand.customAction == "SWITCH_STATION") {
                val stationId = args.getString("STATION_ID")
                if (stationId != null) {
                    // Guarda qual rádio o usuário quer para ligar depois do efeito
                    pendingStationId = stationId
                    isTuningTransition = true

                    // Desliga o loop de chiado e limpa os decks
                    playerA?.repeatMode = Player.REPEAT_MODE_OFF
                    playerB?.repeatMode = Player.REPEAT_MODE_OFF
                    playerA?.stop()
                    playerB?.stop()
                    playerA?.clearMediaItems()
                    playerB?.clearMediaItems()

                    // Abaixa o volume dos efeitos de sintonia para não estourar os ouvidos (ex: 30%)
                    playerA?.volume = 0.15f
                    playerB?.volume = 0.15f

                    // Dispara a playlist sequencial aleatória dos efeitos de sintonia
                    playTuningEffects()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun playTuningEffects() {
        // Embaralha a ordem de sintonizando1 e sintonizando2
        val fxSequence = listOf("general/sintonizando1.mp3", "general/sintonizando2.mp3").shuffled()

        val mediaItems = fxSequence.mapIndexed { index, path ->
            MediaItem.Builder()
                .setUri(Uri.parse("file:///android_asset/$path"))
                .setMediaId("general_tuning_$index")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Buscando Sinal...")
                        .setArtist("Sintonizando...")
                        .setSubtitle("⚡ AM/FM")
                        .build()
                )
                .build()
        }

        // Adiciona a sequência inteira ao player ativo
        activePlayer?.setMediaItems(mediaItems)
        activePlayer?.prepare()
        activePlayer?.play()
    }

    private fun startPendingStation() {
        val stationId = pendingStationId ?: return
        val newStation = RadioStationFactory.createFromAssets(this, stationId)
        if (newStation != null) {
            playbackManager = RadioPlaybackManager(newStation)
            isFirstTrackOfStation = true
            playNextTrack()
        }
    }

    private fun startCrossfadeMonitor() {
        fadeOutJob = serviceScope.launch {
            while (isActive) {
                val player = activePlayer ?: break

                if (player.isPlaying && player.duration > 0) {
                    val timeLeft = player.duration - player.currentPosition
                    val mediaId = player.currentMediaItem?.mediaId ?: ""

                    if (mediaId.contains("_music_")) {
                        if (timeLeft in 1..5000) {
                            player.volume = (timeLeft / 5000f).coerceIn(0f, 1f)
                            if (timeLeft <= 500) playNextTrack()
                        }
                    }
                    else if (mediaId.contains("_dj_")) {
                        val nextTrack = playbackManager?.peekNextTrack()
                        val nextIsMusic = nextTrack?.filePath?.contains("/music/") == true

                        if (nextIsMusic) {
                            if (timeLeft in 1..2000) playNextTrack()
                        } else {
                            if (timeLeft in 1..500) playNextTrack()
                        }
                    }
                    else if (isTuningTransition && mediaId.contains("general_tuning_")) {
                        if (!player.hasNextMediaItem()) {

                            if (timeLeft in 1..1250) {
                                // Como o teto do volume da sintonia é 0.3f, a regra de três acompanha esse limite
                                player.volume = (timeLeft / 1250f) * 0.3f

                                // Faltando meio segundo pro chiado sumir, engatilha a rádio real (que já nasce com fade-in!)
                                if (timeLeft <= 500) {
                                    isTuningTransition = false
                                    startPendingStation()
                                }
                            }
                        }
                    }
                }
                delay(200)
            }
        }
    }

    fun playNextTrack(isManualSkip: Boolean = false) {
        val nextTrack = playbackManager?.getNextTrack() ?: return
        val station = playbackManager?.station

        var artworkData: ByteArray? = null
        try {
            val inputStream = assets.open(station?.iconPath ?: "")
            artworkData = inputStream.readBytes()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val assetUri = Uri.parse("file:///android_asset/${nextTrack.filePath}")

        val metadata = MediaMetadata.Builder()
            .setTitle(nextTrack.title)
            .setArtist(station?.name)
            .setSubtitle(station?.frequency)
            .setArtworkUri(Uri.parse(station?.iconPath))
            // Usa o ByteArray em vez do Uri para garantir que a imagem apareça no sistema
            .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(assetUri)
            .setMediaId(nextTrack.id)
            .setMediaMetadata(metadata)
            .build()

        val nextPlayer = if (activePlayer == playerA) playerB else playerA

        if (isManualSkip) {
            fadeInJob?.cancel() // Cancela qualquer subida de volume antiga ativa
            activePlayer?.stop()
            activePlayer?.clearMediaItems()
        }

        nextPlayer?.setMediaItem(mediaItem)
        nextPlayer?.prepare()

        val isMusic = nextTrack.filePath.contains("/music/")
        val isAd = nextTrack.filePath.contains("/ads/")

        if ((isMusic && !wasLastTrackAd) || isFirstTrackOfStation || (isManualSkip && isMusic)) {
            nextPlayer?.volume = 0.0f
            startFadeIn(nextPlayer)
        } else {
            fadeInJob?.cancel()
            nextPlayer?.volume = 1.0f
        }

        wasLastTrackAd = isAd
        nextPlayer?.play()

        activePlayer = nextPlayer
        mediaSession?.player = getForwardingPlayer(activePlayer!!)
    }

    private fun startFadeIn(targetPlayer: ExoPlayer?) {
        fadeInJob?.cancel()
        fadeInJob = serviceScope.launch {
            val fadeDuration = 3000L
            val steps = 30
            val delayTime = fadeDuration / steps
            for (i in 1..steps) {
                if (!isActive) break
                targetPlayer?.volume = i.toFloat() / steps
                delay(delayTime)
            }
            targetPlayer?.volume = 1.0f
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    @OptIn(UnstableApi::class)
    private fun getForwardingPlayer(player: ExoPlayer): ForwardingPlayer {
        return object : ForwardingPlayer(player) {

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) // Libera o NEXT na notificação
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) // Esconde o PREV na notificação
                    .build()
            }

            // O Android pode chamar esta
            override fun seekToNext() {
                if (!isTuningTransition && playbackManager != null) {
                    playNextTrack(isManualSkip = true)
                }
            }

            // ou esta função quando o botão da notificação for clicado
            override fun seekToNextMediaItem() {
                if (!isTuningTransition && playbackManager != null) {
                    playNextTrack(isManualSkip = true)
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        super.onTaskRemoved(rootIntent)
        playerA?.stop()
        playerB?.stop()
        stopSelf()
    }
    override fun onDestroy() {
        serviceScope.cancel()
        playerA?.release()
        playerB?.release()
        mediaSession?.release()
        super.onDestroy()
    }

}