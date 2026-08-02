package com.example.radioplayer.models

data class AudioTrack(
    val id: String,
    val title: String,
    val filePath: String,
    val type: AudioType,
    // Presentes apenas em faixas especiais (pasta com intro/mid/outro).
    // Se introOptions/outroOptions forem null, é uma faixa normal de arquivo único.
    val introOptions: List<String>? = null,
    val midSegments: List<String>? = null,
    val outroOptions: List<String>? = null
) {
    val isSpecialTrack: Boolean
        get() = introOptions != null && outroOptions != null
}