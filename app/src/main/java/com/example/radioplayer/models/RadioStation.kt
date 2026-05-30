package com.example.radioplayer.models

data class RadioStation(
    val id: String,
    val name: String,
    val frequency: String,
    val iconPath: String,
    val musicTracks: List<AudioTrack>,
    val djTalks: List<AudioTrack>,
    val jingles: List<AudioTrack>,
    val ads: List<AudioTrack>
)