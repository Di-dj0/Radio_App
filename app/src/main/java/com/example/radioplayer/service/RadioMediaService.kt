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
import kotlinx.coroutines.withContext
import androidx.media3.common.PlaybackException
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import com.example.radioplayer.ui.MainActivity
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.media.MediaMetadataRetriever
import kotlin.random.Random
import kotlin.coroutines.coroutineContext

class RadioMediaService : MediaSessionService() {
    // Dual Deck pra crossfade
    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var playerStatic: ExoPlayer? = null
    private var activePlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playbackManager: RadioPlaybackManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var fadeOutJob: Job? = null
    private var fadeInJob: Job? = null
    private var staticCrackleJob: Job? = null
    private val staticBaseVolume = 0.10f
    private var isFirstTrackOfStation = false
    private var isTuningTransition = false
    private var pendingStationId: String? = null
    private var pendingGameFolder: String? = null
    private var currentGameFolder: String = ""

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
                    val currentMediaId = activePlayer?.currentMediaItem?.mediaId ?: ""
                    val isSpecialIntro = currentMediaId.contains("_intro")

                    // Não aplicamos o pulo aleatório em cima do intro de uma faixa
                    // especial: o intro precisa tocar do começo, do jeito que foi produzido.
                    if (!isSpecialIntro) {
                        val duration = activePlayer?.duration ?: 0L

                        if (duration > 60000L) {
                            val randomStartPosition = (10000L..(duration - 30000L)).random()
                            activePlayer?.seekTo(randomStartPosition)
                        } else if (duration > 5000L) {
                            val randomStartPosition = (2000L..(duration - 3000L)).random()
                            activePlayer?.seekTo(randomStartPosition)
                        }
                    }
                }

                if (playbackState == Player.STATE_ENDED) {
                    if (isTuningTransition) {
                        isTuningTransition = false
                        startPendingStation()
                    } else if (activePlayer?.playbackState == Player.STATE_ENDED) {
                        playNextTrack()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
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
        val assetUri = assetUri("$currentGameFolder/general/ruido.mp3")
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

        activePlayer?.repeatMode = Player.REPEAT_MODE_ONE
        activePlayer?.volume = 0.125f
        activePlayer?.setMediaItem(mediaItem)
        activePlayer?.prepare()
        activePlayer?.play()
    }

    private fun startPersistentStatic() {
        val assetUri = assetUri("$currentGameFolder/general/ruido.mp3")
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
                // Frequência maior: espera menor entre os picos de estática
                val waitMs = Random.nextLong(1500L, 6000L)
                delay(waitMs)

                // Pico mais alto pra realmente atrapalhar a música
                val peakVolume = Random.nextFloat() * (0.90f - 0.55f) + 0.55f
                val riseMs = Random.nextLong(150L, 300L)
                val fallMs = Random.nextLong(400L, 700L)
                // Chance maior de segurar o pico, e por mais tempo
                val holdMs = if (Random.nextFloat() < 0.45f) Random.nextLong(150L, 400L) else 0L

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

    private fun playTuningEffects() {
        val fxSequence = listOf(
            "$currentGameFolder/general/sintonizando1.mp3",
            "$currentGameFolder/general/sintonizando2.mp3"
        ).shuffled()

        val mediaItems = fxSequence.mapIndexed { index, path ->
            MediaItem.Builder()
                .setUri(assetUri(path))
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
                        // Se estamos no intro ou em um dos mids de uma faixa especial,
                        // não fazemos NADA aqui: o ExoPlayer já está tocando a playlist
                        // interna (intro -> mid -> outro) e avança sozinho, sem cortes.
                        val isMidComposite = mediaId.contains("_intro") || mediaId.contains("_mid_")

                        if (!isMidComposite) {
                            if (nextIsDj) {
                                val djDuration = getAssetDuration(nextTrack!!.filePath)
                                val triggerTime = (djDuration + 2000L).coerceAtMost(15000L)

                                if (timeLeft in 1..triggerTime && timeLeft > triggerTime - 500) {
                                    startBackgroundFadeOut(player, triggerTime)
                                    playNextTrack()
                                }
                            } else if (nextIsJingle) {
                                val jingleFadeMs = Random.nextLong(1700L, 2400L)
                                if (timeLeft in 1..jingleFadeMs && timeLeft > jingleFadeMs - 500) {
                                    startBackgroundFadeOut(player, jingleFadeMs)
                                    playNextTrack()
                                }
                            } else {
                                val fallbackFadeMs = Random.nextLong(4500L, 5500L)
                                if (timeLeft in 1..fallbackFadeMs && timeLeft > fallbackFadeMs - 500) {
                                    startBackgroundFadeOut(player, fallbackFadeMs)
                                    playNextTrack()
                                }
                            }
                        }
                    }

                    // DJ TOCANDO
                    else if (mediaId.contains("_dj_")) {
                        if (nextIsMusic) {
                            // Faixas especiais já têm DJ embutido no próprio intro,
                            // então usamos um blend bem mais curto pra não duplicar a locução
                            val nextIsSpecialMusic = nextTrack?.isSpecialTrack == true
                            val musicBlendMs = if (nextIsSpecialMusic) 2500L else 4000L

                            if (timeLeft in 1..musicBlendMs && timeLeft > musicBlendMs - 500) {
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
                        if (timeLeft in 1..500 && timeLeft > 0) {
                            playNextTrack()
                        }
                    }

                    // COMERCIAL TOCANDO
                    else if (mediaId.contains("_ad_")) {
                        if (timeLeft in 1..1000 && timeLeft > 500) {
                            playNextTrack()
                            playNextTrack()
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

        val extras = Bundle().apply {
            putString("icon_path", station?.iconPath)
        }

        val nextPlayer = if (activePlayer == playerA) playerB else playerA

        if (nextPlayer == playerA) fadeOutJobA?.cancel() else fadeOutJobB?.cancel()

        if (isManualSkip) {
            fadeInJob?.cancel()
            activePlayer?.stop()
            activePlayer?.clearMediaItems()
        }

        if (nextTrack.isSpecialTrack) {
            // Sorteia intro e outro entre as opções disponíveis a cada execução
            val chosenIntro = nextTrack.introOptions!!.random()
            val chosenOutro = nextTrack.outroOptions!!.random()

            val segmentPaths = mutableListOf(chosenIntro)
            segmentPaths.addAll(nextTrack.midSegments ?: emptyList())
            segmentPaths.add(chosenOutro)

            val mediaItems = segmentPaths.mapIndexed { index, path ->
                val segmentMediaId = when (index) {
                    0 -> "${nextTrack.id}_intro"
                    segmentPaths.lastIndex -> "${nextTrack.id}_outro"
                    else -> "${nextTrack.id}_mid_$index"
                }

                val metadata = MediaMetadata.Builder()
                    .setTitle(nextTrack.title)
                    .setArtist(station?.name)
                    .setSubtitle(station?.frequency)
                    .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .setExtras(extras)
                    .build()

                MediaItem.Builder()
                    .setUri(assetUri(path))
                    .setMediaId(segmentMediaId)
                    .setMediaMetadata(metadata)
                    .build()
            }

            nextPlayer?.setMediaItems(mediaItems)
            nextPlayer?.prepare()
        } else {
            val trackUri = assetUri(nextTrack.filePath)

            val metadata = MediaMetadata.Builder()
                .setTitle(nextTrack.title)
                .setArtist(station?.name)
                .setSubtitle(station?.frequency)
                .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .setExtras(extras)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(trackUri)
                .setMediaId(nextTrack.id)
                .setMediaMetadata(metadata)
                .build()

            nextPlayer?.setMediaItem(mediaItem)
            nextPlayer?.prepare()
        }

        val isMusic = nextTrack.filePath.contains("/music/")
        val isJingle = nextTrack.filePath.contains("/jingles/")

        if (isJingle) {
            nextPlayer?.volume = 0.0f
            startFadeIn(nextPlayer, 1500L)
        } else if (isMusic || isFirstTrackOfStation || (isManualSkip && isMusic)) {
            nextPlayer?.volume = 0.0f
            val musicFadeInMs = if (nextTrack.isSpecialTrack) 2500L else 4000L
            startFadeIn(nextPlayer, musicFadeInMs)
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
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun seekToNext() {
                if (!isTuningTransition && playbackManager != null) {
                    playNextTrack(isManualSkip = true)
                }
            }

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
            fadeOutJobA?.cancel()
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

private fun assetUri(assetPath: String): Uri {
    val encodedPath = assetPath.split("/").joinToString("/") { segment -> Uri.encode(segment) }
    return Uri.parse("file:///android_asset/$encodedPath")
}