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
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

class RadioMediaService : MediaSessionService() {
    // Dual Deck pra crossfade
    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var playerStatic: ExoPlayer? = null
    private var staticCrackleJob: Job? = null
    private val staticBaseVolume = 0.05f
    private var activePlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playbackManager: RadioPlaybackManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var fadeOutJob: Job? = null
    private var fadeInJob: Job? = null
    // Memória para saber se o último áudio foi um comercial
    private var isFirstTrackOfStation = false
    private var isTuningTransition = false
    private var pendingStationId: String? = null
    private var currentGameFolder: String = ""
    private var pendingGameFolder: String? = null

    private var fadeOutJobA: Job? = null
    private var fadeOutJobB: Job? = null

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
        playerStatic = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = staticBaseVolume
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
                    playerStatic?.pause()
                    staticCrackleJob?.cancel()
                } else {
                    if (playerStatic?.isPlaying == false) {
                        playerStatic?.play()
                    }
                    if (staticCrackleJob?.isActive != true) {
                        startStaticCrackleEffect()
                    }
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

        val availableGames = RadioStationFactory.getAvailableGames(this)
        currentGameFolder = availableGames.firstOrNull() ?: "GTA San Andreas"

        val gameStations = RadioStationFactory.getAllAvailableStations(this, currentGameFolder)
        val defaultStationId = gameStations.firstOrNull { it.id == "k-dst" }?.id
            ?: gameStations.firstOrNull()?.id

        val station = defaultStationId?.let {
            RadioStationFactory.createFromAssets(this, currentGameFolder, it)
        }
        if (station != null) {
            playbackManager = RadioPlaybackManager(station)
            isFirstTrackOfStation = true
        }

        playInitialStatic()
        startCrossfadeMonitor()
        startPersistentStatic()
        startStaticCrackleEffect()
    }

    private fun playInitialStatic() {
        val assetUri = Uri.parse("file:///android_asset/$currentGameFolder/general/ruido.mp3")
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

    private fun startPersistentStatic() {
        val assetUri = Uri.parse("file:///android_asset/$currentGameFolder/general/ruido.mp3")
        val mediaItem = MediaItem.Builder()
            .setUri(assetUri)
            .setMediaId("static_loop_persistent")
            .build()

        playerStatic?.setMediaItem(mediaItem)
        playerStatic?.prepare()
        playerStatic?.volume = staticBaseVolume
        playerStatic?.play()
    }

    private fun startStaticCrackleEffect() {
        staticCrackleJob?.cancel()
        staticCrackleJob = serviceScope.launch {
            while (isActive) {
                // Espera aleatória entre "estalos" de estática
                val waitMs = Random.nextLong(3000L, 12000L)
                delay(waitMs)

                // Pico de volume e durações de subida/descida aleatórias
                val peakVolume = Random.nextFloat() * (0.65f - 0.30f) + 0.30f
                val riseMs = Random.nextLong(120L, 220L)
                val fallMs = Random.nextLong(220L, 350L)
                // Pequena chance de segurar o pico por um instante antes de descer
                val holdMs = if (Random.nextFloat() < 0.25f) Random.nextLong(50L, 150L) else 0L

                fadeStaticVolume(from = staticBaseVolume, to = peakVolume, durationMs = riseMs)
                if (holdMs > 0) delay(holdMs)
                fadeStaticVolume(from = peakVolume, to = staticBaseVolume, durationMs = fallMs)
            }
        }
    }

    private suspend fun fadeStaticVolume(from: Float, to: Float, durationMs: Long) {
        val steps = 20
        val stepDelay = (durationMs / steps).coerceAtLeast(1L)
        for (i in 0..steps) {
            if (!coroutineContext.isActive) return
            val fraction = i.toFloat() / steps
            playerStatic?.volume = from + (to - from) * fraction
            delay(stepDelay)
        }
        playerStatic?.volume = to
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand("SWITCH_STATION", Bundle.EMPTY))
                .add(SessionCommand("SWITCH_GAME", Bundle.EMPTY))
                .add(SessionCommand("SKIP_NEXT", Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(availableCommands, connectionResult.availablePlayerCommands)
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "SWITCH_STATION" -> {
                    val stationId = args.getString("STATION_ID")
                    if (stationId != null) {
                        pendingStationId = stationId
                        pendingGameFolder = null
                        triggerTuningTransition()
                    }
                }
                "SWITCH_GAME" -> {
                    val gameFolder = args.getString("GAME_FOLDER")
                    if (gameFolder != null && gameFolder != currentGameFolder) {
                        pendingGameFolder = gameFolder
                        pendingStationId = null
                        triggerTuningTransition()
                    }
                }
                "SKIP_NEXT" -> {
                    if (!isTuningTransition && playbackManager != null) {
                        playNextTrack(isManualSkip = true)
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        private fun triggerTuningTransition() {
            isTuningTransition = true

            playerA?.repeatMode = Player.REPEAT_MODE_OFF
            playerB?.repeatMode = Player.REPEAT_MODE_OFF
            playerA?.stop()
            playerB?.stop()
            playerA?.clearMediaItems()
            playerB?.clearMediaItems()

            playerA?.volume = 0.15f
            playerB?.volume = 0.15f

            playTuningEffects()
        }
    }

    private fun playTuningEffects() {
        // Embaralha a ordem de sintonizando1 e sintonizando2
        val fxSequence = listOf(
            "$currentGameFolder/general/sintonizando1.mp3",
            "$currentGameFolder/general/sintonizando2.mp3"
        ).shuffled()

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
        val targetGameFolder = pendingGameFolder ?: currentGameFolder
        val gameStations = RadioStationFactory.getAllAvailableStations(this, targetGameFolder)

        val stationId = pendingStationId
            ?: gameStations.firstOrNull { it.id == "k-dst" }?.id
            ?: gameStations.firstOrNull()?.id
            ?: run { pendingGameFolder = null; pendingStationId = null; return }

        val newStation = RadioStationFactory.createFromAssets(this, targetGameFolder, stationId)
        if (newStation != null) {
            currentGameFolder = targetGameFolder
            playbackManager = RadioPlaybackManager(newStation)
            isFirstTrackOfStation = true
            playNextTrack()
        }

        pendingGameFolder = null
        pendingStationId = null
    }

    private fun startCrossfadeMonitor() {
        fadeOutJob = serviceScope.launch {
            while (isActive) {
                val player = activePlayer ?: break

                if (player.isPlaying && player.duration > 0) {
                    val timeLeft = player.duration - player.currentPosition
                    val mediaId = player.currentMediaItem?.mediaId ?: ""

                    val nextTrack = playbackManager?.peekNextTrack()
                    val nextIsMusic = nextTrack?.filePath?.contains("/music/") == true
                    val nextIsDj = nextTrack?.filePath?.contains("/dj_talks/") == true
                    val nextIsJingle = nextTrack?.filePath?.contains("/jingles/") == true

                    // MÚSICA TOCANDO
                    if (mediaId.contains("_music_")) {

                        if (nextIsDj) {
                            // Caiu no sorteio de 50%: O DJ vai falar em cima da música
                            val djDuration = getAssetDuration(nextTrack!!.filePath)
                            val triggerTime = (djDuration + 2000L).coerceAtMost(15000L)

                            if (timeLeft in 1..triggerTime && timeLeft > triggerTime - 500) {
                                startBackgroundFadeOut(player, triggerTime)
                                playNextTrack()
                            }
                        }
                        else if (nextIsJingle) {
                            // NÃO caiu no sorteio do DJ: A música cruza direto para a Vinheta
                            val jingleFadeMs = Random.nextLong(1700L, 2400L)
                            if (timeLeft in 1..jingleFadeMs && timeLeft > jingleFadeMs - 500) {
                                startBackgroundFadeOut(player, jingleFadeMs)
                                playNextTrack()
                            }
                        }
                        else {
                            // Fallback de segurança
                            val fallbackFadeMs = Random.nextLong(4500L, 5500L)
                            if (timeLeft in 1..fallbackFadeMs && timeLeft > fallbackFadeMs - 500) {
                                startBackgroundFadeOut(player, fallbackFadeMs)
                                playNextTrack()
                            }
                        }
                    }

                    // DJ TOCANDO
                    else if (mediaId.contains("_dj_")) {
                        if (nextIsMusic) {
                            // Se o próximo é música (caso sem comercial), entra cruzando 4s antes
                            if (timeLeft in 1..4000 && timeLeft > 3500) {
                                playNextTrack()
                            }
                        } else {
                            // Se o próximo for vinheta ou comercial, o DJ fala até o fim seco
                            if (timeLeft in 1..500 && timeLeft > 0) {
                                playNextTrack()
                            }
                        }
                    }

                    // VINHETA (JINGLE) TOCANDO
                    else if (mediaId.contains("_jingle")) {
                        // Passa para o segundo DJ assim que a vinheta terminar
                        if (timeLeft in 1..500 && timeLeft > 0) {
                            playNextTrack()
                        }
                    }

                    // COMERCIAL TOCANDO
                    else if (mediaId.contains("_ad_")) {
                        // Faltando 1 segundo: Dispara o DJ na pickup livre E a música por baixo imediatamente!
                        if (timeLeft in 1..1000 && timeLeft > 500) {
                            playNextTrack() // Carrega e liga o DJ (Pega o deck oposto)
                            playNextTrack() // Carrega e liga a música em fade-in (Corta o resto do comercial)
                        }
                    }

                    // EFEITO DE SINTONIZANDO
                    else if (isTuningTransition && mediaId.contains("general_tuning")) {
                        if (timeLeft in 1..1500 && timeLeft > 1250) {
                            startBackgroundFadeOut(player, 1250L)
                            isTuningTransition = false
                            startPendingStation()
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

        val extras = Bundle().apply {
            putString("icon_path", station?.iconPath)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(nextTrack.title)
            .setArtist(station?.name)
            .setSubtitle(station?.frequency)
            .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .setExtras(extras)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(assetUri)
            .setMediaId(nextTrack.id)
            .setMediaMetadata(metadata)
            .build()

        val nextPlayer = if (activePlayer == playerA) playerB else playerA

        // tells the other pick to cancel the fadeout if the same one is responsible for multiple tracks
        // (such as dj + jingle + dj)
        if (nextPlayer == playerA) fadeOutJobA?.cancel() else fadeOutJobB?.cancel()

        if (isManualSkip) {
            fadeInJob?.cancel() // Cancela qualquer subida de volume antiga ativa
            activePlayer?.stop()
            activePlayer?.clearMediaItems()
        }

        nextPlayer?.setMediaItem(mediaItem)
        nextPlayer?.prepare()

        val isMusic = nextTrack.filePath.contains("/music/")
        val isAd = nextTrack.filePath.contains("/ads/")
        val isJingle = nextTrack.filePath.contains("/jingles/")
        val isDj = nextTrack.filePath.contains("/dj_talks/")

        if (isJingle) {
            nextPlayer?.volume = 0.0f
            startFadeIn(nextPlayer, 1500L)
        } else if (isMusic || isFirstTrackOfStation || (isManualSkip && isMusic)) {
            nextPlayer?.volume = 0.0f
            startFadeIn(nextPlayer, 4000L)
        } else {
            fadeInJob?.cancel()
            nextPlayer?.volume = 1.0f
        }

        nextPlayer?.play()

        activePlayer = nextPlayer
        mediaSession?.player = getForwardingPlayer(activePlayer!!)
    }

    private fun startFadeIn(targetPlayer: ExoPlayer?, durationMs: Long = 3000L) {
        fadeInJob?.cancel()
        fadeInJob = serviceScope.launch {
            val steps = 30
            val delayTime = durationMs / steps
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

    private suspend fun getAssetDuration(filePath: String): Long = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            val afd = assets.openFd(filePath)
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val timeString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            afd.close()
            timeString?.toLong() ?: 10000L
        } catch (e: Exception) {
            10000L
        }
    }

    private fun startBackgroundFadeOut(backgroundPlayer: ExoPlayer?, durationMs: Long) {
        val job = serviceScope.launch {
            val steps = 50
            val delayTime = durationMs / steps
            for (i in steps downTo 0) {
                if (!isActive) break
                backgroundPlayer?.volume = i.toFloat() / steps
                delay(delayTime)
            }
        }

        if (backgroundPlayer == playerA) {
            fadeOutJobA?.cancel() // Cancela qualquer outro que estivesse rodando
            fadeOutJobA = job
        } else {
            fadeOutJobB?.cancel()
            fadeOutJobB = job
        }
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        super.onTaskRemoved(rootIntent)
        playerA?.stop()
        playerB?.stop()
        playerStatic?.stop()
        stopSelf()
    }
    override fun onDestroy() {
        staticCrackleJob?.cancel()
        serviceScope.cancel()
        playerA?.release()
        playerB?.release()
        playerStatic?.release()
        mediaSession?.release()
        super.onDestroy()
    }

}