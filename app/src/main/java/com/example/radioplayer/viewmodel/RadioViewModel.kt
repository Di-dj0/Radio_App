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

class RadioViewModel : ViewModel() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentTrackTitle = MutableStateFlow("Rádio Desligada")
    val currentTrackTitle = _currentTrackTitle.asStateFlow()

    private val _stationName = MutableStateFlow("Sintonizando...")
    val stationName = _stationName.asStateFlow()

    private val _iconPath = MutableStateFlow<String?>(null)
    val iconPath = _iconPath.asStateFlow()

    fun initController(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, RadioMediaService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture?.addListener({
            setupPlayerListener()
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        val player = controller ?: return

        _isPlaying.value = player.isPlaying
        _currentTrackTitle.value = player.mediaMetadata.title?.toString() ?: "Sintonizando..."

        _stationName.value = player.mediaMetadata.artist?.toString() ?: "Sintonizando..."
        _iconPath.value = player.mediaMetadata.artworkUri?.toString()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
                _currentTrackTitle.value = metadata.title?.toString() ?: "Sintonizando"

                _stationName.value = metadata.artist?.toString() ?: "Rádio"
                _iconPath.value = metadata.artworkUri?.toString()
            }
        })
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() {
        val player = controller ?: return
        if (player.duration > 0) {
            player.seekTo(player.duration)
        }
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

}