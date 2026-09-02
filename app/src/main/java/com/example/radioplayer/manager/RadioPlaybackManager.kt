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

        // Safely perform tuning skip on track items
        val stepsToSkip = Random.nextInt(1, 5)
        repeat(stepsToSkip) {
            if (playbackQueue.isNotEmpty()) {
                playbackQueue.removeAt(0)
            }
        }

        if (playbackQueue.isEmpty()) {
            prepareNextSegment()
        }
    }

    fun getNextTrack(): AudioTrack {
        if (playbackQueue.isEmpty()) {
            prepareNextSegment()
        }
        check(playbackQueue.isNotEmpty()) {
            "Station '${station.name}' has no audio tracks to play."
        }
        return playbackQueue.removeAt(0)
    }

    private fun prepareNextSegment() {
        val djClosingChance = Random.nextFloat() * 0.20f + 0.40f
        val adChance = Random.nextFloat() * 0.15f + 0.225f
        val stationHasJingles = station.jingles.isNotEmpty()

        // 1. DJ Closing
        if (stationHasJingles && station.djTalks.isNotEmpty() && Random.nextFloat() < djClosingChance) {
            drawDjTalk(requireShort = true)?.let { playbackQueue.add(it) }
        }

        // 2. Jingle
        if (stationHasJingles) {
            playbackQueue.add(unplayedJingles.removeAt(0))
            if (unplayedJingles.isEmpty()) {
                unplayedJingles = station.jingles.shuffled().toMutableList()
            }
        }

        // 3. DJ Intro
        if (station.djTalks.isNotEmpty()) {
            drawDjTalk(requireShort = true)?.let { playbackQueue.add(it) }
        }

        // 4. Ad Block
        if (station.ads.isNotEmpty() && Random.nextFloat() < adChance) {
            playbackQueue.add(unplayedAds.removeAt(0))
            if (unplayedAds.isEmpty()) {
                unplayedAds = station.ads.shuffled().toMutableList()
            }

            if (station.djTalks.isNotEmpty()) {
                drawDjTalk(requireShort = true)?.let { playbackQueue.add(it) }
            }
        }

        // 5. Main Song / Track
        if (unplayedMusic.isEmpty()) {
            unplayedMusic = station.musicTracks.shuffled().toMutableList()
        }
        if (unplayedMusic.isNotEmpty()) {
            playbackQueue.add(unplayedMusic.removeAt(0))
        }

        // 6. News Block (Fallout)
        station.newsTemplate?.let { newsTemplate ->
            val newsChance = Random.nextFloat() * 0.25f + 0.50f
            if (Random.nextFloat() < newsChance) {
                if (station.radioHellos.isNotEmpty()) {
                    playbackQueue.add(station.radioHellos.random())
                }
                playbackQueue.add(newsTemplate)
            }
        }
    }

    fun peekNextTrack(): AudioTrack {
        if (playbackQueue.isEmpty()) {
            prepareNextSegment()
        }
        check(playbackQueue.isNotEmpty()) {
            "Station '${station.name}' has no audio tracks to play."
        }
        return playbackQueue.first()
    }

    private val maxFadeDjDurationMs = 9000L

    private fun drawDjTalk(requireShort: Boolean): AudioTrack? {
        if (station.djTalks.isEmpty()) return null

        fun isShort(track: AudioTrack) = (track.durationMs ?: 0L) <= maxFadeDjDurationMs

        val pool = if (requireShort) unplayedDjTalks.filter { isShort(it) } else unplayedDjTalks

        val chosen = pool.randomOrNull() ?: run {
            if (requireShort) {
                station.djTalks.filter { isShort(it) }.randomOrNull()
            } else {
                unplayedDjTalks.randomOrNull()
            }
        } ?: return null

        unplayedDjTalks.remove(chosen)
        if (unplayedDjTalks.isEmpty()) {
            unplayedDjTalks = station.djTalks.shuffled().toMutableList()
        }
        return chosen
    }
}