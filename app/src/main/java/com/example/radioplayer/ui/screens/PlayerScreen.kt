package com.example.radioplayer.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import com.example.radioplayer.viewmodel.RadioViewModel


@Composable
fun PlayerScreen(viewModel: RadioViewModel) {

    val isPlaying by viewModel.isPlaying.collectAsState()
    val trackTitle by viewModel.currentTrackTitle.collectAsState()
    val stationName by viewModel.stationName.collectAsState()
    val iconPath by viewModel.iconPath.collectAsState()

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        val bitmap = remember(iconPath) {
            iconPath?.let { path ->
                try {
                    context.assets.open(path).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Logo da $stationName",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(250.dp)
            )
        } else {
            // Se o logo não carregar, mostra uma bola cinza de placeholder
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .background(Color.DarkGray)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = stationName,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = trackTitle,
            color = Color.Yellow,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "107.7 FM",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Button(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Tocar",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = { viewModel.skipNext() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Avançar",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

        }


    }

}