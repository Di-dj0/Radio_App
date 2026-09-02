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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import com.example.radioplayer.R
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.core.graphics.toColorInt
import com.example.radioplayer.models.RadioStation

val defaultGtaFont = FontFamily(Font(R.font.pricedown))

private val fontCache = mutableMapOf<String, FontFamily>()

private fun parseHexColorOrDefault(hex: String, default: Color): Color {
    return try {
        Color(hex.toColorInt())
    } catch (e: Exception) {
        default
    }
}
@Composable
fun rememberGameFont(context: android.content.Context, fontAssetPath: String?): FontFamily {
    var resolvedFont by remember(fontAssetPath) { mutableStateOf(defaultGtaFont) }

    LaunchedEffect(fontAssetPath) {
        if (fontAssetPath == null) {
            resolvedFont = defaultGtaFont
            return@LaunchedEffect
        }

        fontCache[fontAssetPath]?.let {
            resolvedFont = it
            return@LaunchedEffect
        }

        val loaded = withContext(Dispatchers.IO) {
            try {
                val tempFile = File.createTempFile("gamefont", fontAssetPath.substringAfterLast("."), context.cacheDir)
                context.assets.open(fontAssetPath).use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                FontFamily(Font(tempFile))
            } catch (e: Exception) {
                null
            }
        }

        resolvedFont = loaded ?: defaultGtaFont
        if (loaded != null) fontCache[fontAssetPath] = loaded
    }

    return resolvedFont
}

@Composable
fun StickerImage(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    borderWidth: androidx.compose.ui.unit.Dp = 6.dp,
    borderColor: Color = Color.Black,
    steps: Int = 16
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        for (i in 0 until steps) {
            val angle = 2.0 * Math.PI * i / steps
            val offsetX = borderWidth * cos(angle).toFloat()
            val offsetY = borderWidth * sin(angle).toFloat()

            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(borderColor, BlendMode.SrcIn),
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = offsetX, y = offsetY)
            )
        }

        // Logo original por cima, sem nenhum tingimento
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize()
        )
    }
}

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
fun RadialStationSelector(
    stations: List<RadioStation>,
    currentStationName: String,
    accentColor: Color,
    onStationSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.80f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        ) {
            val n = stations.size
            if (n == 0) return@BoxWithConstraints

            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }
            val centerX = screenWidthPx / 2f
            val centerY = screenHeightPx / 2f

            val safePaddingPx = with(density) { 20.dp.toPx() }
            val minItemDp = 40f
            val maxItemDp = 64f
            val spacingDp = 10f

            // Raio máximo que o CÍRCULO INTEIRO (com todos os ícones) consegue
            // ocupar sem sair da tela.
            val maxRadiusPx = (minOf(screenWidthPx, screenHeightPx) / 2f) - safePaddingPx

            // A partir desse raio máximo, calcula o maior tamanho de ícone que
            // ainda cabe sem sobrepor o vizinho (corda do círculo entre dois
            // pontos adjacentes = 2 * raio * sin(π/N)). Quanto mais estações,
            // menor o ícone — automaticamente.
            val angleStep = 2.0 * Math.PI / n
            val chordAtMaxRadiusPx = if (n > 1) (2f * maxRadiusPx * sin(angleStep / 2.0)).toFloat() else maxRadiusPx
            val chordAtMaxRadiusDp = with(density) { chordAtMaxRadiusPx.toDp().value }
            val itemSizeDp = (chordAtMaxRadiusDp - spacingDp).coerceIn(minItemDp, maxItemDp)
            val itemSizePx = with(density) { itemSizeDp.dp.toPx() }

            // Raio real usado pro posicionamento: o mesmo raio máximo, só que
            // recuado em meio ícone, pra nenhum círculo estourar a borda da tela.
            val radiusPx = maxRadiusPx - itemSizePx / 2f

            stations.forEachIndexed { index, station ->
                val angle = -Math.PI / 2.0 + angleStep * index // começa no topo, sentido horário
                val itemCenterX = centerX + radiusPx * cos(angle).toFloat()
                val itemCenterY = centerY + radiusPx * sin(angle).toFloat()

                val stationBitmap = remember(station.iconPath) {
                    try {
                        context.assets.open(station.iconPath).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }

                val isSelected = station.name == currentStationName

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (itemCenterX - itemSizePx / 2f).toDp() },
                            y = with(density) { (itemCenterY - itemSizePx / 2f).toDp() }
                        )
                        .size(itemSizeDp.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A))
                        .border(
                            width = if (isSelected) 3.dp else 1.5.dp,
                            color = if (isSelected) accentColor else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .clickable {
                            onStationSelected(station.id)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (stationBitmap != null) {
                        Image(
                            bitmap = stationBitmap,
                            contentDescription = station.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = station.name.take(2).uppercase(),
                            color = Color.White,
                            fontSize = (itemSizeDp / 4).sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GtaText(
    text: String,
    fillColor: Color,
    outlineColor: Color = Color.Black,
    fontSize: androidx.compose.ui.unit.TextUnit,
    strokeWidth: Float = 12f,
    fontFamily: FontFamily = defaultGtaFont
) {

    val baseStyle = LocalTextStyle.current.copy(
        fontSize = fontSize,
        fontFamily = fontFamily,
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
    val availableGames by viewModel.availableGames.collectAsState()
    val selectedGame by viewModel.selectedGame.collectAsState()
    var showStationsDialog by remember { mutableStateOf(false) }
    var showGameDropdown by remember { mutableStateOf(false) }
    val frequency by viewModel.frequency.collectAsState()
    val isStaticEnabled by viewModel.isStaticEnabled.collectAsState()
    val gameFontAssetPath by viewModel.gameFontAssetPath.collectAsState()
    val gameColors by viewModel.gameColors.collectAsState()
    var dominantColor by remember { mutableStateOf(Color(0xFF121212)) }

    val context = LocalContext.current
    val gameFont = rememberGameFont(context, gameFontAssetPath)

    val mainTextColor = remember(gameColors.textColorHex) { parseHexColorOrDefault(gameColors.textColorHex, Color.White) }
    val accentColor = remember(gameColors.accentColorHex) { parseHexColorOrDefault(gameColors.accentColorHex, Color(0xFFFFD700)) }

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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (availableGames.size > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                OutlinedButton(onClick = { showGameDropdown = true }) {
                    Text(text = selectedGame)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.ArrowDropDown,
                        contentDescription = "Selecionar jogo"
                    )
                }

                DropdownMenu(
                    expanded = showGameDropdown,
                    onDismissRequest = { showGameDropdown = false }
                ) {
                    availableGames.forEach { game ->
                        DropdownMenuItem(
                            text = { Text(game) },
                            onClick = {
                                viewModel.switchGame(game)
                                showGameDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            if (bitmap != null) {
                StickerImage(
                    bitmap = bitmap,
                    contentDescription = "Logo da $stationName",
                    borderWidth = 6.dp,
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
                fillColor = mainTextColor,
                fontSize = 42.sp,
                strokeWidth = 14f,
                fontFamily = gameFont
            )

            Spacer(modifier = Modifier.height(8.dp))

            GtaText(
                text = trackTitle,
                fillColor = accentColor,
                fontSize = 28.sp,
                strokeWidth = 10f,
                fontFamily = gameFont
            )

            Spacer(modifier = Modifier.height(5.dp))

            GtaText(
                text = frequency,
                fillColor = accentColor,
                fontSize = 22.sp,
                strokeWidth = 8f,
                fontFamily = gameFont
            )

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // BOTÃO DE RUÍDO ESTÁTICO (LIGA/DESLIGA)
                Button(
                    onClick = { viewModel.toggleStatic() },
                    modifier = Modifier
                        .size(80.dp)
                        .border(width = 4.dp, color = Color.Black, shape = CircleShape),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.GraphicEq,
                        contentDescription = if (isStaticEnabled) "Desligar ruído estático" else "Ligar ruído estático",
                        tint = if (isStaticEnabled) Color.Black else Color.Gray,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // BOTÃO DE PLAY / PAUSE
                Button(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(80.dp)
                        .border(width = 4.dp, color = Color.Black, shape = CircleShape),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
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
                    GtaCanvasSkipNextIcon(
                        contentDescription = "Avançar",
                        iconSize = 48.dp,
                        fillColor = accentColor,
                        outlineStrokeWidth = 8.dp
                    )
                }
            }

            val staticVolume by viewModel.staticVolume.collectAsState()

            // SLIDER IMPLEMENTATION
            if (isStaticEnabled) {
                Spacer(modifier = Modifier.height(32.dp))

                Slider(
                    value = staticVolume * 100f, // Convert 0.0-1.0 to 0-100 range for the UI
                    onValueChange = { newValue ->
                        viewModel.setStaticVolume(newValue / 100f) // Convert back to 0.0-1.0 float
                    },
                    valueRange = 0f..100f,
                    steps = 9, // Snap in steps of 10
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = accentColor.copy(alpha = 0.3f),
                        activeTickColor = Color.Black.copy(alpha = 0.5f),
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth(0.75f)
                )
            }

        }
    }

    if (showStationsDialog) {
        if (gameColors.stationSelectorStyle == "list") {
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
                                            showStationsDialog = false
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
        } else {
            RadialStationSelector(
                stations = availableStations,
                currentStationName = stationName,
                accentColor = accentColor,
                onStationSelected = { stationId -> viewModel.switchStation(stationId) },
                onDismiss = { showStationsDialog = false }
            )
        }
    }

}