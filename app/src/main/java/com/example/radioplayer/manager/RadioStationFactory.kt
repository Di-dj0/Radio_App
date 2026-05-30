package com.example.radioplayer.manager

import android.content.Context
import com.example.radioplayer.models.AudioTrack
import com.example.radioplayer.models.AudioType
import com.example.radioplayer.models.RadioStation
import java.io.IOException

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

            RadioStation(
                id = folderName,
                name = folderName,
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

}