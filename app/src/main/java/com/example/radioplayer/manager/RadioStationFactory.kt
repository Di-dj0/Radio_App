package com.example.radioplayer.manager

import android.content.Context
import android.content.res.AssetManager
import com.example.radioplayer.models.AudioTrack
import com.example.radioplayer.models.AudioType
import com.example.radioplayer.models.RadioStation
import java.io.IOException
import org.json.JSONObject

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
                AudioTrack(
                    id = "${idPrefix}_dj_$fileName",
                    title = "Locução",
                    filePath = "$basePath/dj_talks/$fileName",
                    type = AudioType.DJ_TALK
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
                ads = ads
            )
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Constrói uma faixa "especial": uma pasta dentro de /music contendo
     * uma ou mais introduções, um ou mais trechos centrais (mid) e um ou mais
     * finais, todos já com as transições de DJ embutidas no áudio.
     * O nome da pasta deve seguir o formato "{Nome da Música} - {Artista}".
     */
    private fun buildSpecialTrack(
        assetManager: AssetManager,
        idPrefix: String,
        basePath: String,
        folderName: String
    ): AudioTrack? {
        val folderPath = "$basePath/music/$folderName"
        val files = assetManager.list(folderPath) ?: return null
        if (files.isEmpty()) return null

        val introFiles = files.filter { it.contains("intro", ignoreCase = true) }
        val outroFiles = files.filter { it.contains("outro", ignoreCase = true) }
        val midFiles = files.filter {
            !it.contains("intro", ignoreCase = true) && !it.contains("outro", ignoreCase = true)
        }.sorted()

        if (introFiles.isEmpty() || outroFiles.isEmpty() || midFiles.isEmpty()) {
            println("Aviso: pasta de música especial '$folderName' incompleta (precisa de ao menos 1 intro, 1 mid e 1 outro). Ignorando.")
            return null
        }

        val trackId = "${idPrefix}_music_${folderName.replace(" ", "_")}"

        return AudioTrack(
            id = trackId,
            title = folderName, // já vem no formato "Nome da Música - Artista"
            filePath = "$folderPath/${midFiles.first()}", // referência/fallback, não usado na composição
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
}