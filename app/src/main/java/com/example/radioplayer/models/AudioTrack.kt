package com.example.radioplayer.models

data class AudioTrack(
    val id: String,
    val title: String,
    val filePath: String,
    val type: AudioType,
    val durationMs: Long? = null,
    // --- Faixas especiais estilo GTA (pasta com intro/mid/outro) ---
    val introOptions: List<String>? = null,
    val midSegments: List<String>? = null,
    val outroOptions: List<String>? = null,
    // --- Bloco de notícias estilo Fallout (pasta news/) ---
    val newsIntroOptions: List<String>? = null,
    val newsItemOptions: List<String>? = null,
    val newsTransitionOptions: List<String>? = null,
    val newsEndingOptions: List<String>? = null, // pool combinado de "ending" + "sponsor"
    val newsOutroOptions: List<String>? = null
) {
    val isSpecialTrack: Boolean
        get() = introOptions != null && outroOptions != null

    val isNewsBlock: Boolean
        get() = newsIntroOptions != null && newsOutroOptions != null
}