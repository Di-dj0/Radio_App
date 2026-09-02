package com.example.radioplayer.viewmodel

import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.radioplayer.service.RadioMediaService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Bundle
import androidx.media3.session.SessionCommand
import com.example.radioplayer.models.RadioStation
import com.example.radioplayer.manager.RadioStationFactory
import com.example.radioplayer.models.GameColors
import kotlinx.coroutines.flow.StateFlow

class RadioViewModel : ViewModel() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null
    private var appContext: Context? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentTrackTitle = MutableStateFlow("Rádio Desligada")
    val currentTrackTitle = _currentTrackTitle.asStateFlow()

    private val _stationName = MutableStateFlow("Sintonizando...")
    val stationName = _stationName.asStateFlow()

    private val _iconPath = MutableStateFlow<String?>(null)
    val iconPath = _iconPath.asStateFlow()

    private val _availableStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val availableStations = _availableStations.asStateFlow()

    private val _availableGames = MutableStateFlow<List<String>>(emptyList())
    val availableGames = _availableGames.asStateFlow()

    private val _selectedGame = MutableStateFlow("")
    val selectedGame = _selectedGame.asStateFlow()

    private val _frequency = MutableStateFlow("Sintonizando...")
    val frequency = _frequency.asStateFlow()

    private val _isStaticEnabled = MutableStateFlow(true)
    val isStaticEnabled = _isStaticEnabled.asStateFlow()

    private val _gameFontAssetPath = MutableStateFlow<String?>(null)
    val gameFontAssetPath = _gameFontAssetPath.asStateFlow()

    private val _gameColors = MutableStateFlow(GameColors())
    val gameColors = _gameColors.asStateFlow()

    fun initController(context: Context) {
        appContext = context.applicationContext

        val sessionToken = SessionToken(context, ComponentName(context, RadioMediaService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture?.addListener({
            setupPlayerListener()
        }, MoreExecutors.directExecutor())

        val games = RadioStationFactory.getAvailableGames(context)
        _availableGames.value = games

        val defaultGame = games.firstOrNull() ?: ""
        _selectedGame.value = defaultGame
        _availableStations.value = if (defaultGame.isNotEmpty()) {
            RadioStationFactory.getAllAvailableStations(context, defaultGame)
        } else {
            emptyList()
        }
        _gameFontAssetPath.value = if (defaultGame.isNotEmpty()) {
            RadioStationFactory.getGameFontAssetPath(context, defaultGame)
        } else {
            null
        }
        _gameColors.value = if (defaultGame.isNotEmpty()) {
            RadioStationFactory.getGameColors(context, defaultGame)
        } else {
            GameColors()
        }
    }

    private val _staticVolume = MutableStateFlow(0.5f) // Range: 0.0f to 1.0f
    val staticVolume: StateFlow<Float> = _staticVolume.asStateFlow()

    fun setStaticVolume(volume: Float) {
        _staticVolume.value = volume

        // Send the new volume to the RadioMediaService
        val command = SessionCommand("SET_STATIC_VOLUME", Bundle.EMPTY)
        val args = Bundle().apply { putFloat("VOLUME", volume) }
        controller?.sendCustomCommand(command, args)
    }

    private fun setupPlayerListener() {
        val player = controller ?: return

        _isPlaying.value = player.isPlaying
        _currentTrackTitle.value = player.mediaMetadata.title?.toString() ?: "Sintonizando..."

        _stationName.value = player.mediaMetadata.artist?.toString() ?: "Sintonizando..."
        _iconPath.value = player.mediaMetadata.extras?.getString("icon_path")

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
                _currentTrackTitle.value = metadata.title?.toString() ?: "Sintonizando"

                _stationName.value = metadata.artist?.toString() ?: "Rádio"
                _iconPath.value = metadata.extras?.getString("icon_path")

                _frequency.value = metadata.subtitle?.toString() ?: "100.0 FM"
            }
        })
    }

    fun switchGame(gameFolder: String) {
        if (gameFolder == _selectedGame.value) return

        _selectedGame.value = gameFolder
        appContext?.let { ctx ->
            _availableStations.value = RadioStationFactory.getAllAvailableStations(ctx, gameFolder)
            _gameFontAssetPath.value = RadioStationFactory.getGameFontAssetPath(ctx, gameFolder)
            _gameColors.value = RadioStationFactory.getGameColors(ctx, gameFolder)
        }

        val command = SessionCommand("SWITCH_GAME", Bundle.EMPTY)
        val args = Bundle().apply { putString("GAME_FOLDER", gameFolder) }
        controller?.sendCustomCommand(command, args)
    }

    fun switchStation(stationId: String) {
        val command = SessionCommand("SWITCH_STATION", Bundle.EMPTY)
        val args = Bundle().apply { putString("STATION_ID", stationId) }
        controller?.sendCustomCommand(command, args)
    }

    fun toggleStatic() {
        val newValue = !_isStaticEnabled.value
        _isStaticEnabled.value = newValue

        val command = SessionCommand("SET_STATIC_ENABLED", Bundle.EMPTY)
        val args = Bundle().apply { putBoolean("ENABLED", newValue) }
        controller?.sendCustomCommand(command, args)
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() {
        val command = SessionCommand("SKIP_NEXT", Bundle.EMPTY)
        controller?.sendCustomCommand(command, Bundle.EMPTY)
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

}