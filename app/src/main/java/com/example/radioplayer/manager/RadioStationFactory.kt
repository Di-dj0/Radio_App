package com.example.radioplayer.manager

import android.content.Context
import com.example.radioplayer.models.AudioTrack
import com.example.radioplayer.models.AudioType
import com.example.radioplayer.models.RadioStation
import java.io.IOException
import org.json.JSONObject
import java.io.InputStreamReader

object RadioStationFactory {

    fun createFromAssets(context: Context, folderName: String): RadioStation? {
        val assetManager = context.assets
        return try {
            val musicFiles = assetManager.list("$folderName/music") ?: emptyArray()
            val djFiles = assetManager.list("$folderName/dj_talks") ?: emptyArray()
            val jingleFiles = assetManager.list("$folderName/jingles") ?: emptyArray()
            val adFiles = assetManager.list("$folderName/ads") ?: emptyArray()

            val musicTracks = musicFiles.map { fileName ->
                AudioTrack(
                    id = "${folderName}_music_$fileName",
                    title = fileName.substringBeforeLast("."), // removes the extension
                    filePath = "$folderName/music/$fileName",
                    type = AudioType.MUSIC
                )
            }

            val djTalks = djFiles.map { fileName ->
                AudioTrack(
                    id = "${folderName}_dj_$fileName",
                    title = "Locução",
                    filePath = "$folderName/dj_talks/$fileName",
                    type = AudioType.DJ_TALK
                )
            }

            val jingles = jingleFiles.map { fileName ->
                AudioTrack(
                    id = "${folderName}_jingle_$fileName",
                    title = "Vinheta",
                    filePath = "$folderName/jingles/$fileName",
                    type = AudioType.JINGLE
                )
            }

            val ads = adFiles.map { fileName ->
                AudioTrack(
                    id = "${folderName}_ad_$fileName",
                    title = "Comercial",
                    filePath = "$folderName/ads/$fileName",
                    type = AudioType.AD
                )
            }

            // default names and frequency
            var prettyName = folderName
            var stationFrequency = "89.9 FM"

            try {
                val jsonStream = assetManager.open("$folderName/station_info.json")
                val jsonString = jsonStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)

                if (jsonObject.has("name")) {
                    prettyName = jsonObject.getString("name")
                }
                if (jsonObject.has("frequency")) {
                    stationFrequency = jsonObject.getString("frequency")
                }
            } catch (e: Exception) {
                println("Aviso: station_info.json não encontrado em $folderName. Usando valores padrão.")
                prettyName = folderName.split("_").joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
            }

            return RadioStation(
                id = folderName,
                name = prettyName,
                frequency = stationFrequency,
                iconPath = "$folderName/logo.png",
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

    fun getAllAvailableStations(context: Context): List<RadioStation> {
        val assetManager = context.assets
        val stations = mutableListOf<RadioStation>()
        try {
            val rootItems = assetManager.list("") ?: emptyArray()

            for (item in rootItems) {
                val subItems = assetManager.list(item) ?: emptyArray()
                if (subItems.contains("logo.png")) {
                    createFromAssets(context, item)?.let { stations.add(it) }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return stations
    }

}