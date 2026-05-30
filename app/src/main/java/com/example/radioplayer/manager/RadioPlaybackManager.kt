package com.example.radioplayer.manager

import com.example.radioplayer.models.AudioTrack
import com.example.radioplayer.models.RadioStation
import kotlin.random.Random

class RadioPlaybackManager(val station: RadioStation) {

    private var unplayedMusic = station.musicTracks.shuffled().toMutableList()
    private var unplayedJingles = station.jingles.shuffled().toMutableList()
    private var unplayedDjTalks = station.djTalks.shuffled().toMutableList()
    private var unplayedAds = station.ads.shuffled().toMutableList()
    private val playbackQueue = mutableListOf<AudioTrack>()

    init {
        prepareNextSegment()
    }

    fun getNextTrack(): AudioTrack {
        if (playbackQueue.isEmpty()) {
            prepareNextSegment()
        }
        return playbackQueue.removeAt(0)
    }

    private fun prepareNextSegment() {
        // Nova ordem: [Jingle] -> [DJ] -> [Propaganda (30%)] -> [Música]
        if (station.jingles.isNotEmpty()) {
            playbackQueue.add(unplayedJingles.removeAt(0))
            if (unplayedJingles.isEmpty()) {
                unplayedJingles = station.jingles.shuffled().toMutableList()
            }
        }

        if (station.djTalks.isNotEmpty()) {
            playbackQueue.add(unplayedDjTalks.removeAt(0))
            if (unplayedDjTalks.isEmpty()) {
                unplayedDjTalks = station.djTalks.shuffled().toMutableList()
            }
        }

        // ads have a 30% chance of playing
        if (station.ads.isNotEmpty() && Random.nextFloat() < 0.5f) {
            playbackQueue.add(unplayedAds.removeAt(0))
            if (unplayedAds.isEmpty()) {
                unplayedAds = station.ads.shuffled().toMutableList()
            }
        }

        // if all musics have played
        if (unplayedMusic.isEmpty()) {
            unplayedMusic = station.musicTracks.shuffled().toMutableList()
        }

        // add music to queue
        if (unplayedMusic.isNotEmpty()) {
            playbackQueue.add(unplayedMusic.removeAt(0))
        }

    }

}