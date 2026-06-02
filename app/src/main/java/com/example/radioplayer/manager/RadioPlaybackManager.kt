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

        // Tuning effect: Pula aleatoriamente de 2 a 5 passos na fila inicial
        val stepsToSkip = (2..5).random()
        repeat(stepsToSkip) {
            if (playbackQueue.isNotEmpty()) {
                playbackQueue.removeAt(0)
            }
        }

        // Caso o pulo tenha limpado a fila inteira, gera o bloco seguinte imediatamente
        if (playbackQueue.isEmpty()) {
            prepareNextSegment()
        }
    }

    fun getNextTrack(): AudioTrack {
        if (playbackQueue.isEmpty()) {
            prepareNextSegment()
        }
        return playbackQueue.removeAt(0)
    }

    private fun prepareNextSegment() {
        // [DJ 1] -> [Jingle] -> [DJ 2] -> (30%)[Ad + DJ 3] -> [Música]
        // 1. DJ de encerramento (50% de chance de aparecer no final da música)
        if (station.djTalks.isNotEmpty() && Random.nextFloat() < 0.5f) {
            playbackQueue.add(unplayedDjTalks.removeAt(0))
            if (unplayedDjTalks.isEmpty()) {
                unplayedDjTalks = station.djTalks.shuffled().toMutableList()
            }
        }

        // 2. Vinheta / Jingle da rádio
        if (station.jingles.isNotEmpty()) {
            playbackQueue.add(unplayedJingles.removeAt(0))
            if (unplayedJingles.isEmpty()) {
                unplayedJingles = station.jingles.shuffled().toMutableList()
            }
        }

        // 3. DJ de introdução (chama o próximo bloco)
        if (station.djTalks.isNotEmpty()) {
            playbackQueue.add(unplayedDjTalks.removeAt(0))
            if (unplayedDjTalks.isEmpty()) {
                unplayedDjTalks = station.djTalks.shuffled().toMutableList()
            }
        }

        // 4. Bloco de Comercial + DJ de Transição (30% de chance)
        if (station.ads.isNotEmpty() && Random.nextFloat() < 0.3f) {
            // Comercial
            playbackQueue.add(unplayedAds.removeAt(0))
            if (unplayedAds.isEmpty()) {
                unplayedAds = station.ads.shuffled().toMutableList()
            }

            // DJ que fala em cima da música pós-comercial
            if (station.djTalks.isNotEmpty()) {
                playbackQueue.add(unplayedDjTalks.removeAt(0))
                if (unplayedDjTalks.isEmpty()) {
                    unplayedDjTalks = station.djTalks.shuffled().toMutableList()
                }
            }
        }

        // 5. A nova música que vai rodar
        if (unplayedMusic.isEmpty()) {
            unplayedMusic = station.musicTracks.shuffled().toMutableList()
        }
        if (unplayedMusic.isNotEmpty()) {
            playbackQueue.add(unplayedMusic.removeAt(0))
        }

    }

    fun peekNextTrack(): AudioTrack {
        if (playbackQueue.isEmpty()) {
            prepareNextSegment()
        }
        // Retorna o primeiro item sem dar um .removeAt(0)
        return playbackQueue.first()
    }

}