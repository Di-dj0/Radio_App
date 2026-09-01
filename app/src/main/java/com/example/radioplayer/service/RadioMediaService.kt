package com.example.radioplayer.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.radioplayer.manager.RadioPlaybackManager
import com.example.radioplayer.manager.RadioStationFactory
import com.example.radioplayer.models.RadioStation
import com.example.radioplayer.ui.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

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
    private var fadeInJobA: Job? = null
    private var fadeInJobB: Job? = null
    private var duckedMusicPlayer: ExoPlayer? = null
    private var staticCrackleJob: Job? = null
    private val staticBaseVolume = 0.06f
    private var isFirstTrackOfStation = false
    private var isTuningTransition = false
    private var pendingStationId: String? = null
    private var pendingGameFolder: String? = null
    private var currentGameFolder: String = ""
    private var fadeOutJobA: Job? = null
    private var fadeOutJobB: Job? = null
    private var isDjFollowingAd = false
    private val specialBlendAfterAdMs = 2000L
    private var isStaticEnabled = true
    private var duckingForPlayer: ExoPlayer? = null
    private val djToMusicOverlapMs = 2500L
    private val newsAndHelloVolume = 0.80f
    private val artworkCache = mutableMapOf<String, ByteArray>()

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

        playerA?.addListener(createPlayerListener { playerA })
        playerB?.addListener(createPlayerListener { playerB })

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

    private fun createPlayerListener(selfProvider: () -> ExoPlayer?): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val self = selfProvider() ?: return

                if (playbackState == Player.STATE_ENDED && self == duckingForPlayer) {
                    duckingForPlayer = null
                    rampDuckedMusicToFull()
                }

                if (self != activePlayer) return

                // --- tuning effect ---
                if (playbackState == Player.STATE_READY) {
                    val currentMediaId = activePlayer?.currentMediaItem?.mediaId ?: ""
                    val isRadioHello = currentMediaId.contains("radio_hello_") || currentMediaId.contains("radiohello")

                    // Radio Hello clips should play from the start (0ms), NOT seek randomly
                    if (isFirstTrackOfStation && !isRadioHello) {
                        isFirstTrackOfStation = false
                        val skipRandomSeek = currentMediaId.contains("_intro") || currentMediaId.endsWith("_seg_0")

                        if (!skipRandomSeek) {
                            seekToRandomPositionIfLongEnough(activePlayer)
                        }
                    }
                }

                if (playbackState == Player.STATE_ENDED) {
                    if (isTuningTransition) {
                        isTuningTransition = false
                        startPendingStation()
                    } else {
                        val endedMediaId = self.currentMediaItem?.mediaId ?: ""
                        when {
                            endedMediaId.contains("_ad_") -> handleAdEnded()
                            else -> playNextTrack()
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val self = selfProvider() ?: return
                if (self != activePlayer) return

                if (!isPlaying) {
                    if (activePlayer == playerA) playerB?.pause() else playerA?.pause()
                    playerStatic?.pause()
                    staticCrackleJob?.cancel()
                } else {
                    if (isStaticEnabled) {
                        if (playerStatic?.isPlaying == false) {
                            playerStatic?.play()
                        }
                        if (staticCrackleJob?.isActive != true) {
                            startStaticCrackleEffect()
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val self = selfProvider() ?: return
                if (self != activePlayer) return
                println("ERRO DA RÁDIO: Arquivo falhou -> ${error.message}")
                playNextTrack()
            }
        }
    }

    private fun rampDuckedMusicToFull() {
        val player = duckedMusicPlayer ?: return
        startFadeIn(player, durationMs = 1500L, targetVolume = 1.0f, fromVolume = player.volume)
        duckedMusicPlayer = null
    }

    private fun assetUri(assetPath: String): Uri {
        val encodedPath = assetPath.split("/").joinToString("/") { segment -> Uri.encode(segment) }
        return "file:///android_asset/$encodedPath".toUri()
    }

    private fun seekToRandomPositionIfLongEnough(player: ExoPlayer?) {
        val duration = player?.duration ?: return

        // Guard against C.TIME_UNSET or invalid durations
        if (duration <= 0L) return

        if (duration > 60000L) {
            val minBound = 10000L
            val maxBound = (duration - 30000L)
            player.seekTo((minBound..maxBound).random())
        } else if (duration > 10000L) {
            val minBound = 2000L
            val maxBound = (duration - 3000L)
            player.seekTo((minBound..maxBound).random())
        }
    }

    private fun playInitialStatic() {
        val uri = assetUri("$currentGameFolder/general/ruido.mp3")
        val metadata = MediaMetadata.Builder()
            .setTitle("Estação não sintonizada")
            .setArtist("Ruído Estático")
            .setSubtitle("---")
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
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
        val uri = assetUri("$currentGameFolder/general/ruido.mp3")
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
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
                val waitMs = Random.nextLong(4000L, 10000L)
                delay(waitMs)

                val peakVolume = Random.nextFloat() * (0.55f - 0.30f) + 0.30f
                val riseMs = Random.nextLong(150L, 300L)
                val fallMs = Random.nextLong(300L, 500L)
                val holdMs = if (Random.nextFloat() < 0.30f) Random.nextLong(100L, 250L) else 0L

                fadeStaticVolume(from = staticBaseVolume, to = peakVolume, durationMs = riseMs)
                if (holdMs > 0) delay(holdMs)
                fadeStaticVolume(from = peakVolume, to = staticBaseVolume, durationMs = fallMs)
            }
        }
    }

    private fun getStationArtwork(station: RadioStation?): ByteArray? {
        val iconPath = station?.iconPath ?: return null
        artworkCache[iconPath]?.let { return it }

        return try {
            val originalBitmap = assets.open(iconPath).use { BitmapFactory.decodeStream(it) } ?: return null

            val maxDimension = 512
            val scale = minOf(1f, maxDimension.toFloat() / maxOf(originalBitmap.width, originalBitmap.height))
            val resizedBitmap = if (scale < 1f) {
                originalBitmap.scale(
                    (originalBitmap.width * scale).toInt().coerceAtLeast(1),
                    (originalBitmap.height * scale).toInt().coerceAtLeast(1)
                )
            } else {
                originalBitmap
            }

            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()

            if (resizedBitmap !== originalBitmap) resizedBitmap.recycle()
            originalBitmap.recycle()

            artworkCache[iconPath] = bytes
            bytes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun setStaticEnabled(enabled: Boolean) {
        if (enabled == isStaticEnabled) return
        isStaticEnabled = enabled

        if (enabled) {
            if (playerStatic?.isPlaying == false) {
                playerStatic?.play()
            }
            if (staticCrackleJob?.isActive != true) {
                startStaticCrackleEffect()
            }
            serviceScope.launch {
                fadeStaticVolume(from = playerStatic?.volume ?: 0f, to = staticBaseVolume, durationMs = 500L)
            }
        } else {
            staticCrackleJob?.cancel()
            serviceScope.launch {
                fadeStaticVolume(from = playerStatic?.volume ?: staticBaseVolume, to = 0f, durationMs = 500L)
                playerStatic?.pause()
            }
        }
    }

    private suspend fun fadeStaticVolume(from: Float, to: Float, durationMs: Long) {
        val steps = 20
        val stepDelay = (durationMs / steps).coerceAtLeast(1L)
        for (i in 0..steps) {
            if (!currentCoroutineContext().isActive) return
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
                .add(SessionCommand("SET_STATIC_ENABLED", Bundle.EMPTY))
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
                "SET_STATIC_ENABLED" -> {
                    setStaticEnabled(args.getBoolean("ENABLED", true))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun triggerTuningTransition() {
        isTuningTransition = true
        isDjFollowingAd = false
        duckingForPlayer = null
        duckedMusicPlayer = null

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
                        val isSpecialIntroOrMid = mediaId.contains("_intro") || mediaId.contains("_mid_")
                        val isSpecialOutro = mediaId.contains("_outro")

                        if (isSpecialIntroOrMid) {
                            continue
                        } else if (isSpecialOutro) {
                            val outroFadeMs = Random.nextLong(2000L, 3000L)
                            if (timeLeft in 1..outroFadeMs && timeLeft > outroFadeMs - 500) {
                                startBackgroundFadeOut(player, outroFadeMs)
                                playNextTrack()
                            }
                        } else if (nextIsDj) {
                            val djDuration = nextTrack.durationMs ?: 10000L
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

                    // DJ TOCANDO
                    else if (mediaId.contains("_dj_")) {
                        val nextIsSpecialMusic = nextTrack?.isSpecialTrack == true

                        if (nextIsSpecialMusic) {
                            if (isDjFollowingAd) {
                                triggerAdToSpecialBlend(timeLeft)
                            }
                        } else if (nextIsMusic) {
                            if (timeLeft <= djToMusicOverlapMs) {
                                duckingForPlayer = player
                                playNextTrack(duckToHalfVolume = true)
                            }
                        } else {
                            if (timeLeft in 1..500) {
                                playNextTrack()
                            }
                        }
                    }

                    // VINHETA (JINGLE) TOCANDO
                    else if (mediaId.contains("_jingle")) {
                        if (timeLeft in 1..500) {
                            playNextTrack()
                        }
                    }

                    // RADIO HELLO OU NEWS BLOCK (Deixa o listener de fim de faixa tratar a transição quando terminar)
                    else if (mediaId.contains("_newsblock_")) {
                        // Intentionally left empty — let ExoPlayer play all segment MediaItems sequentially
                    }

                    else if (mediaId.contains("_radiohello_") || mediaId.contains("radio_hello_")) {
                        if (timeLeft in 1..300) {
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

    private fun handleAdEnded() {
        val nextTrack = playbackManager?.peekNextTrack()
        val nextWasDj = nextTrack?.filePath?.contains("/dj_talks/") == true

        playNextTrack()

        if (nextWasDj) {
            val djDeck = activePlayer
            val upcomingAfterDj = playbackManager?.peekNextTrack()
            val upcomingIsSpecial = upcomingAfterDj?.isSpecialTrack == true

            if (!upcomingIsSpecial) {
                duckingForPlayer = djDeck
                playNextTrack(duckToHalfVolume = true)
            } else {
                isDjFollowingAd = true
            }
        }
    }

    fun playNextTrack(isManualSkip: Boolean = false, specialFadeInOverrideMs: Long? = null, duckToHalfVolume: Boolean = false) {
        val nextTrack = playbackManager?.getNextTrack() ?: return
        val station = playbackManager?.station

        val artworkData = getStationArtwork(station)

        val extras = Bundle().apply {
            putString("icon_path", station?.iconPath)
        }

        val nextPlayer = if (activePlayer == playerA) playerB else playerA

        if (nextPlayer == playerA) fadeOutJobA?.cancel() else fadeOutJobB?.cancel()

        if (isManualSkip) {
            fadeInJobA?.cancel()
            fadeInJobB?.cancel()
            activePlayer?.stop()
            activePlayer?.clearMediaItems()
            duckingForPlayer = null
            duckedMusicPlayer = null
        }

        if (nextTrack.isNewsBlock) {
            val chosenIntro = nextTrack.newsIntroOptions?.takeIf { it.isNotEmpty() }?.random()
            val chosenOutro = nextTrack.newsOutroOptions?.takeIf { it.isNotEmpty() }?.random()
            val newsPool = (nextTrack.newsItemOptions ?: emptyList()).shuffled()
            val transitionPool = nextTrack.newsTransitionOptions ?: emptyList()
            val endingPool = nextTrack.newsEndingOptions ?: emptyList()

            val newsCount = if (newsPool.isEmpty()) 0 else Random.nextInt(1, 4).coerceAtMost(newsPool.size)
            val chosenNews = newsPool.take(newsCount)

            val middleSegments = mutableListOf<String>()
            chosenNews.forEachIndexed { index, newsPath ->
                middleSegments.add(newsPath)
                val isLast = index == chosenNews.lastIndex
                if (!isLast && transitionPool.isNotEmpty()) {
                    middleSegments.add(transitionPool.random())
                }
            }

            if (endingPool.isNotEmpty()) {
                middleSegments.add(endingPool.random())
            }

            val segmentPaths = mutableListOf<String>()
            if (chosenIntro != null) segmentPaths.add(chosenIntro)
            segmentPaths.addAll(middleSegments)
            if (chosenOutro != null) segmentPaths.add(chosenOutro)

            if (segmentPaths.isEmpty() && nextTrack.filePath.isNotEmpty()) {
                segmentPaths.add(nextTrack.filePath)
            }

            val mediaItems = segmentPaths.mapIndexed { index, path ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(nextTrack.title)
                    .setArtist(station?.name)
                    .setSubtitle(station?.frequency)
                    .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .setExtras(extras)
                    .build()

                MediaItem.Builder()
                    .setUri(assetUri(path))
                    .setMediaId("${nextTrack.id}_newsblock_seg_$index")
                    .setMediaMetadata(metadata)
                    .build()
            }

            nextPlayer?.setMediaItems(mediaItems)
            nextPlayer?.prepare()
        } else if (nextTrack.isSpecialTrack) {
            val chosenIntro = nextTrack.introOptions?.takeIf { it.isNotEmpty() }?.random()
            val chosenOutro = nextTrack.outroOptions?.takeIf { it.isNotEmpty() }?.random()

            val segmentPaths = mutableListOf<String>()
            if (chosenIntro != null) segmentPaths.add(chosenIntro)
            segmentPaths.addAll(nextTrack.midSegments ?: emptyList())
            if (chosenOutro != null) segmentPaths.add(chosenOutro)

            if (segmentPaths.isEmpty() && !nextTrack.filePath.isNullOrEmpty()) {
                segmentPaths.add(nextTrack.filePath)
            }

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

        val isNoFadeType = nextTrack.isNewsBlock || nextTrack.filePath.contains("/radio_hello/") || nextTrack.id.contains("radiohello")

        if (isNoFadeType) {
            fadeInJobA?.cancel()
            fadeInJobB?.cancel()
            nextPlayer?.volume = newsAndHelloVolume
        } else if (isJingle) {
            nextPlayer?.volume = 0.0f
            startFadeIn(nextPlayer, 1500L)
        } else if (isMusic || isFirstTrackOfStation) {
            nextPlayer?.volume = 0.0f
            val musicFadeInMs = specialFadeInOverrideMs
                ?: (if (nextTrack.isSpecialTrack) 1000L else 4000L)

            if (duckToHalfVolume && !nextTrack.isSpecialTrack) {
                duckedMusicPlayer = nextPlayer
                startFadeIn(nextPlayer, musicFadeInMs, targetVolume = 0.5f)
            } else {
                startFadeIn(nextPlayer, musicFadeInMs)
            }
        } else {
            fadeInJobA?.cancel()
            fadeInJobB?.cancel()
            nextPlayer?.volume = 1.0f
        }

        nextPlayer?.play()

        activePlayer = nextPlayer
        mediaSession?.player = getForwardingPlayer(activePlayer!!)
    }

    private fun startFadeIn(
        targetPlayer: ExoPlayer?,
        durationMs: Long = 3000L,
        targetVolume: Float = 1.0f,
        fromVolume: Float = 0.0f
    ) {
        val isDeckA = targetPlayer == playerA
        if (isDeckA) fadeInJobA?.cancel() else fadeInJobB?.cancel()

        val job = serviceScope.launch {
            val steps = 30
            val delayTime = (durationMs / steps).coerceAtLeast(1L)
            for (i in 0..steps) {
                if (!isActive) break
                val fraction = i.toFloat() / steps
                targetPlayer?.volume = fromVolume + (targetVolume - fromVolume) * fraction
                delay(delayTime)
            }
            targetPlayer?.volume = targetVolume
        }

        if (isDeckA) fadeInJobA = job else fadeInJobB = job
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

    private fun triggerAdToSpecialBlend(timeLeft: Long) {
        if (timeLeft in 1..specialBlendAfterAdMs && timeLeft > specialBlendAfterAdMs - 500) {
            isDjFollowingAd = false
            playNextTrack(specialFadeInOverrideMs = specialBlendAfterAdMs)
        }
    }

    private fun startBackgroundFadeOut(backgroundPlayer: ExoPlayer?, durationMs: Long) {
        val job = serviceScope.launch {
            val steps = 50
            val delayTime = (durationMs / steps).coerceAtLeast(1L)
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

    override fun onTaskRemoved(rootIntent: Intent?) {
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