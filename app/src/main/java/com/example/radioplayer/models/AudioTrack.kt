package com.example.radioplayer.models

data class AudioTrack(
    val id: String,
    val title: String,
    val filePath: String,
    val type: AudioType
)