package com.example.radioplayer.manager

import android.content.Context
import android.content.res.AssetManager
import com.example.radioplayer.models.AudioTrack
import com.example.radioplayer.models.AudioType
import com.example.radioplayer.models.RadioStation
import java.io.IOException
import org.json.JSONObject
import android.media.MediaMetadataRetriever
import com.example.radioplayer.models.GameColors
import androidx.core.graphics.toColorInt

object RadioStationFactory {

    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "aac")

    fun createFromAssets(context: Context, gameFolder: String, stationFolder: String): RadioStation? {
        val assetManager = context.assets
        val basePath = "$gameFolder/$stationFolder"
        val sharedAdsPath = "$gameFolder/general/ads"
        return try {
            val musicItems = assetManager.list("$basePath/music") ?: emptyArray()
            val djFiles = assetManager.list("$basePath/dj_talks") ?: emptyArray()
            val jingleFiles = assetManager.list("$basePath/jingles") ?: emptyArray()
            val adFiles = assetManager.list(sharedAdsPath) ?: emptyArray()

            val idPrefix = "${gameFolder.replace(" ", "_")}_$stationFolder"
            val adIdPrefix = "${gameFolder.replace(" ", "_")}_general"

            val musicTracks = musicItems.mapNotNull { itemName ->
                val extension = itemName.substringAfterLast(".", "").lowercase()
                if (extension in AUDIO_EXTENSIONS) {
                    // Arquivo de música normal
                    AudioTrack(
                        id = "${idPrefix}_music_$itemName",
                        title = itemName.substringBeforeLast("."),
                        filePath = "$basePath/music/$itemName",
                        type = AudioType.MUSIC
                    )
                } else {
                    // Sem extensão de áudio -> é uma pasta = música especial (intro/mid/outro)
                    buildSpecialTrack(assetManager, idPrefix, basePath, itemName)
                }
            }

            val djTalks = djFiles.map { fileName ->
                val filePath = "$basePath/dj_talks/$fileName"
                AudioTrack(
                    id = "${idPrefix}_dj_$fileName",
                    title = "Locução",
                    filePath = filePath,
                    type = AudioType.DJ_TALK,
                    durationMs = computeDurationMs(assetManager, filePath)
                )
            }

            val jingles = jingleFiles.map { fileName ->
                AudioTrack(
                    id = "${idPrefix}_jingle_$fileName",
                    title = "Vinheta",
                    filePath = "$basePath/jingles/$fileName",
                    type = AudioType.JINGLE
                )
            }

            val ads = adFiles.map { fileName ->
                AudioTrack(
                    id = "${adIdPrefix}_ad_$fileName",
                    title = "Comercial",
                    filePath = "$sharedAdsPath/$fileName",
                    type = AudioType.AD
                )
            }

            val newsTemplate = buildNewsTemplate(assetManager, idPrefix, basePath)
            val radioHellos = buildRadioHellos(assetManager, idPrefix, basePath)

            // default names and frequency
            var prettyName = stationFolder
            var stationFrequency = "89.9 FM"

            try {
                val jsonStream = assetManager.open("$basePath/station_info.json")
                val jsonString = jsonStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)

                if (jsonObject.has("name")) {
                    prettyName = jsonObject.getString("name")
                }
                if (jsonObject.has("frequency")) {
                    stationFrequency = jsonObject.getString("frequency")
                }
            } catch (e: Exception) {
                println("Aviso: station_info.json não encontrado em $basePath. Usando valores padrão.")
                prettyName = stationFolder.split("_").joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
            }

            return RadioStation(
                id = stationFolder,
                gameFolder = gameFolder,
                name = prettyName,
                frequency = stationFrequency,
                iconPath = "$basePath/logo.png",
                musicTracks = musicTracks,
                djTalks = djTalks,
                jingles = jingles,
                ads = ads,
                newsTemplate = newsTemplate,
                radioHellos = radioHellos
            )
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }


    private fun buildSpecialTrack(
        assetManager: AssetManager,
        idPrefix: String,
        basePath: String,
        folderName: String
    ): AudioTrack? {
        val folderPath = "$basePath/music/$folderName"
        val files = assetManager.list(folderPath) ?: return null
        if (files.isEmpty()) return null

        // 1. Explicitly filter for intro, mid, and outro audio files
        val introFiles = files.filter { fileName ->
            val lower = fileName.lowercase()
            lower.contains("intro") && lower.substringAfterLast(".", "") in AUDIO_EXTENSIONS
        }.sorted()

        val midFiles = files.filter { fileName ->
            val lower = fileName.lowercase()
            lower.contains("mid") && lower.substringAfterLast(".", "") in AUDIO_EXTENSIONS
        }.sorted()

        val outroFiles = files.filter { fileName ->
            val lower = fileName.lowercase()
            lower.contains("outro") && lower.substringAfterLast(".", "") in AUDIO_EXTENSIONS
        }.sorted()

        // 2. Strict GTA Track Validation: MUST have at least 1 intro, 1 mid, AND 1 outro
        if (introFiles.isEmpty() || midFiles.isEmpty() || outroFiles.isEmpty()) {
            println("Aviso: pasta de música especial '$folderName' precisa ter arquivos intro_X, mid_X e outro_X. Ignorando track incompleta.")
            return null
        }

        val trackId = "${idPrefix}_music_${folderName.replace(" ", "_")}"

        // 3. Map clean full asset paths relative to the asset root
        return AudioTrack(
            id = trackId,
            title = folderName,
            filePath = "$folderPath/${midFiles.first()}", // Fallback/reference path
            type = AudioType.MUSIC,
            introOptions = introFiles.map { "$folderPath/$it" },
            midSegments = midFiles.map { "$folderPath/$it" },
            outroOptions = outroFiles.map { "$folderPath/$it" }
        )
    }

    fun getAllAvailableStations(context: Context, gameFolder: String): List<RadioStation> {
        val assetManager = context.assets
        val stations = mutableListOf<RadioStation>()
        try {
            val items = assetManager.list(gameFolder) ?: emptyArray()
            for (item in items) {
                if (item == "general") continue
                val subItems = assetManager.list("$gameFolder/$item") ?: emptyArray()
                if (subItems.contains("logo.png")) {
                    createFromAssets(context, gameFolder, item)?.let { stations.add(it) }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return stations
    }

    fun getAvailableGames(context: Context): List<String> {
        val assetManager = context.assets
        val rootItems = try {
            assetManager.list("") ?: emptyArray()
        } catch (e: IOException) {
            emptyArray()
        }
        return rootItems.filter { item -> isGameFolder(context, item) }.sorted()
    }

    private fun isGameFolder(context: Context, folderName: String): Boolean {
        val assetManager = context.assets
        return try {
            val subItems = assetManager.list(folderName) ?: return false
            if (!subItems.contains("general")) return false

            subItems.any { sub ->
                val subSubItems = assetManager.list("$folderName/$sub") ?: emptyArray()
                subSubItems.contains("logo.png")
            }
        } catch (e: IOException) {
            false
        }
    }

    private fun computeDurationMs(assetManager: AssetManager, filePath: String): Long? {
        return try {
            val retriever = MediaMetadataRetriever()
            val afd = assetManager.openFd(filePath)
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val timeString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            afd.close()
            timeString?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun buildNewsTemplate(assetManager: AssetManager, idPrefix: String, basePath: String): AudioTrack? {
        val newsPath = "$basePath/news"
        val files = assetManager.list(newsPath) ?: emptyArray()
        if (files.isEmpty()) return null

        val introFiles = files.filter { it.contains("intro", ignoreCase = true) }.sorted()
        val outroFiles = files.filter { it.contains("outro", ignoreCase = true) }.sorted()
        val transitionFiles = files.filter { it.contains("transition", ignoreCase = true) }.sorted()
        val sponsorFiles = files.filter { it.contains("sponsor", ignoreCase = true) }.sorted()
        val endingFiles = files.filter { it.contains("ending", ignoreCase = true) }.sorted()

        val newsItemFiles = files.filter { fileName ->
            val lower = fileName.lowercase()
            lower.contains("news") || (!lower.contains("intro") &&
                    !lower.contains("outro") &&
                    !lower.contains("transition") &&
                    !lower.contains("sponsor") &&
                    !lower.contains("ending") &&
                    lower.substringAfterLast(".", "") in AUDIO_EXTENSIONS)
        }.sorted()

        if (introFiles.isEmpty() || outroFiles.isEmpty() || newsItemFiles.isEmpty()) return null

        return AudioTrack(
            id = "${idPrefix}_newsblock",
            title = "Notícias",
            filePath = "$newsPath/${newsItemFiles.first()}",
            type = AudioType.NEWS_BLOCK,
            newsIntroOptions = introFiles.map { "$newsPath/$it" },
            newsItemOptions = newsItemFiles.map { "$newsPath/$it" },
            newsTransitionOptions = transitionFiles.map { "$newsPath/$it" },
            newsEndingOptions = (endingFiles + sponsorFiles).map { "$newsPath/$it" },
            newsOutroOptions = outroFiles.map { "$newsPath/$it" }
        )
    }

    private fun buildRadioHellos(assetManager: AssetManager, idPrefix: String, basePath: String): List<AudioTrack> {
        val helloPath = "$basePath/radio_hello"
        val files = assetManager.list(helloPath) ?: emptyArray()
        return files.map { fileName ->
            AudioTrack(
                id = "${idPrefix}_radiohello_$fileName",
                title = "Identificação da Rádio",
                filePath = "$helloPath/$fileName",
                type = AudioType.RADIO_HELLO
            )
        }
    }

    fun getGameFontAssetPath(context: Context, gameFolder: String): String? {
        val assetManager = context.assets
        return try {
            val configPath = "$gameFolder/general/config.json"
            val jsonString = assetManager.open(configPath).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            val fontFileName = if (jsonObject.has("fontFamily")) {
                jsonObject.getString("fontFamily")
            } else {
                null
            } ?: return null

            val fontPath = "$gameFolder/general/fonts/$fontFileName"

            val fontsDirFiles = assetManager.list("$gameFolder/general/fonts") ?: emptyArray()
            if (fontFileName !in fontsDirFiles) {
                println("Aviso: config.json de '$gameFolder' referencia '$fontFileName', mas o arquivo não foi encontrado em general/fonts/. Usando fonte padrão.")
                return null
            }

            fontPath
        } catch (e: Exception) {
            // Sem config.json, JSON malformado, ou qualquer outro problema -> fallback
            null
        }
    }

    fun getGameColors(context: Context, gameFolder: String): GameColors {
        val assetManager = context.assets
        return try {
            val configPath = "$gameFolder/general/config.json"
            val jsonString = assetManager.open(configPath).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            val textColor = jsonObject.optString("textColor", GameColors.DEFAULT_TEXT_COLOR)
                .takeIf { isValidHexColor(it) } ?: GameColors.DEFAULT_TEXT_COLOR

            val accentColor = jsonObject.optString("accentColor", GameColors.DEFAULT_ACCENT_COLOR)
                .takeIf { isValidHexColor(it) } ?: GameColors.DEFAULT_ACCENT_COLOR

            val selectorStyle = jsonObject.optString("stationSelectorStyle", GameColors.DEFAULT_SELECTOR_STYLE)
                .takeIf { it == "radial" || it == "list" } ?: GameColors.DEFAULT_SELECTOR_STYLE

            GameColors(textColorHex = textColor, accentColorHex = accentColor, stationSelectorStyle = selectorStyle)
        } catch (e: Exception) {
            GameColors()
        }
    }

    private fun isValidHexColor(value: String): Boolean {
        return try {
            value.toColorInt()
            true
        } catch (e: Exception) {
            false
        }
    }
}