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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.palette.graphics.Palette
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextStyle
import com.example.radioplayer.R
import androidx.compose.foundation.border

val pricedownFont = FontFamily(Font(R.font.pricedown))

@Composable
fun GtaCanvasSkipNextIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 48.dp,
    fillColor: Color = Color(0xFFFFD700),
    outlineColor: Color = Color.Black,
    outlineStrokeWidth: androidx.compose.ui.unit.Dp = 1.5.dp
) {
    val totalSize = iconSize + outlineStrokeWidth * 2

    val density = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics.density
    val strokeWidthPx = outlineStrokeWidth.value * density

    Box(
        modifier = modifier.size(totalSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val w = size.width
            val h = size.height

            // 📐 Traçado desenhado à mão fora de composables! Sem erros.
            val path = androidx.compose.ui.graphics.Path().apply {
                // Triângulo (Play)
                moveTo(w * 0.15f, h * 0.15f)
                lineTo(w * 0.70f, h * 0.50f)
                lineTo(w * 0.15f, h * 0.85f)
                close()

                // Barra (Rec)
                addRect(
                    androidx.compose.ui.geometry.Rect(
                        left = w * 0.70f,
                        top = h * 0.15f,
                        right = w * 0.85f,
                        bottom = h * 0.85f
                    )
                )
            }

            // 1. Desenha a Borda Preta
            drawPath(
                path = path,
                color = outlineColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidthPx,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            // 2. Desenha o Miolo Amarelo
            drawPath(
                path = path,
                color = fillColor,
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        }

        // Acessibilidade silenciosa
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.SkipNext,
            contentDescription = contentDescription,
            tint = Color.Transparent,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun androidx.compose.ui.graphics.vector.VectorPainter.toPath(): androidx.compose.ui.graphics.Path? {
    // Como o ExoPlayer ou standard vectors do Android não dão acesso direto ao Path vetorial
    // no Compose de forma simples sem bibliotecas terceiras, desenhamos o ícone no "SkipNext"
    // manualmente no Canvas para garantir o alinhamento perfeito.
    return null // Retornamos nulo e vamos fazer o desenho manual abaixo.
}

@Composable
fun GtaText(
    text: String,
    fillColor: Color,
    outlineColor: Color = Color.Black,
    fontSize: androidx.compose.ui.unit.TextUnit,
    strokeWidth: Float = 12f
) {

    val baseStyle = LocalTextStyle.current.copy(
        fontSize = fontSize,
        fontFamily = pricedownFont,
        textAlign = TextAlign.Center
    )

    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = outlineColor,
            style = baseStyle.copy(
                drawStyle = Stroke(
                    miter = 10f,
                    width = strokeWidth,
                    join = StrokeJoin.Round
                )
            )
        )

        Text(
            text = text,
            color = fillColor,
            style = baseStyle
        )
    }
}

@Composable
fun PlayerScreen(viewModel: RadioViewModel) {

    val isPlaying by viewModel.isPlaying.collectAsState()
    val trackTitle by viewModel.currentTrackTitle.collectAsState()
    val stationName by viewModel.stationName.collectAsState()
    val iconPath by viewModel.iconPath.collectAsState()
    val availableStations by viewModel.availableStations.collectAsState()
    var showStationsDialog by remember { mutableStateOf(false) }
    val frequency by viewModel.frequency.collectAsState()
    var dominantColor by remember { mutableStateOf(Color(0xFF121212)) }

    val context = LocalContext.current

    val bitmap = remember(iconPath) {
        iconPath?.let { path ->
            try {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            val androidBitmap = bitmap.asAndroidBitmap()

            Palette.from(androidBitmap).generate { palette ->
                val extractedColor = palette?.darkMutedSwatch?.rgb
                    ?: palette?.dominantSwatch?.rgb

                if (extractedColor != null) {
                    dominantColor = Color(extractedColor).copy(alpha = 0.65f)
                } else {
                    dominantColor = Color(0xFF121212)
                }
            }
        } else {
            dominantColor = Color(0xFF121212)
        }
    }

    val animatedBackgroundColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 1000),
        label = "BgColorAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBackgroundColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Logo da $stationName",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(250.dp)
                    .clickable { showStationsDialog = true }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .background(Color.DarkGray)
                    .clickable { showStationsDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        GtaText(
            text = stationName,
            fillColor = Color.White,
            fontSize = 46.sp,
            strokeWidth = 14f
        )

        Spacer(modifier = Modifier.height(8.dp))

        GtaText(
            text = trackTitle,
            fillColor = Color(0xFFFFD700),
            fontSize = 28.sp,
            strokeWidth = 10f
        )

        Spacer(modifier = Modifier.height(8.dp))

        GtaText(
            text = frequency,
            fillColor = Color(0xFFFFB300),
            fontSize = 22.sp,
            strokeWidth = 8f
        )

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // BOTÃO DE PLAY / PAUSE
            Button(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier
                    .size(80.dp)
                    // ✨ REDUZIDO: Borda de 4.dp (Padrão era 6.dp)
                    .border(width = 4.dp, color = Color.Black, shape = CircleShape),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)), // Amarelo GTA
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Tocar",
                    tint = Color.Black,
                    modifier = Modifier.size(46.dp)
                )
            }

            // BOTÃO DE AVANÇAR (SKIP NEXT)
            IconButton(
                onClick = { viewModel.skipNext() },
                modifier = Modifier.size(64.dp)
            ) {
                // ✨ USANDO O NOVO ÍCONE DE CANVAS PERFEITAMENTE ALINHADO
                GtaCanvasSkipNextIcon(
                    contentDescription = "Avançar",
                    iconSize = 48.dp,
                    fillColor = Color(0xFFFFD700),
                    // ✨ Borda fina e precisa
                    outlineStrokeWidth = 8.dp
                )
            }
        }

    }

    if (showStationsDialog) {

        Dialog(onDismissRequest = { showStationsDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Sintonizar Rádio",
                        color = Color.Yellow,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(availableStations) { station ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.switchStation(station.id)
                                        showStationsDialog = false // Fecha o pop-up
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = station.name,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

    }

}