package com.example.radioplayer.manager

import android.content.Context
import com.example.radioplayer.models.AudioTrack
import com.example.radioplayer.models.AudioType
import com.example.radioplayer.models.RadioStation
import java.io.IOException
import org.json.JSONObject
import java.io.InputStreamReader

object RadioStationFactory {

    fun createFromAssets(context: Context, gameFolder: String, stationFolder: String): RadioStation? {
        val assetManager = context.assets
        val basePath = "$gameFolder/$stationFolder"
        val sharedAdsPath = "$gameFolder/general/ads"
        return try {
            val musicFiles = assetManager.list("$basePath/music") ?: emptyArray()
            val djFiles = assetManager.list("$basePath/dj_talks") ?: emptyArray()
            val jingleFiles = assetManager.list("$basePath/jingles") ?: emptyArray()
            val adFiles = assetManager.list(sharedAdsPath) ?: emptyArray()

            // Prefixo sanitizado (sem espaços) só para IDs de mídia, o path real usa basePath
            val idPrefix = "${gameFolder.replace(" ", "_")}_$stationFolder"
            // Prefixo dos ads é por jogo (compartilhado entre estações), não por estação
            val adIdPrefix = "${gameFolder.replace(" ", "_")}_general"

            val musicTracks = musicFiles.map { fileName ->
                AudioTrack(
                    id = "${idPrefix}_music_$fileName",
                    title = fileName.substringBeforeLast("."),
                    filePath = "$basePath/music/$fileName",
                    type = AudioType.MUSIC
                )
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

    // Uma pasta na raiz dos assets é considerada um "jogo" se tiver uma pasta
    // "general" (estática/sintonia) E ao menos uma estação (pasta com logo.png)
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