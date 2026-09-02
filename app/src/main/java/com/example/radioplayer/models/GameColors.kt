package com.example.radioplayer.models

data class GameColors(
    val textColorHex: String = DEFAULT_TEXT_COLOR,
    val accentColorHex: String = DEFAULT_ACCENT_COLOR,
    val stationSelectorStyle: String = DEFAULT_SELECTOR_STYLE
) {
    companion object {
        const val DEFAULT_TEXT_COLOR = "#FFFFFF"
        const val DEFAULT_ACCENT_COLOR = "#FFD700"
        const val DEFAULT_SELECTOR_STYLE = "radial"
    }
}