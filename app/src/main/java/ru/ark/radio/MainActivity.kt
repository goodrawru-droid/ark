package ru.ark.radio

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

private val BG = Color(0xFF101114)
private val PANEL = Color(0xFF1B1D22)
private val ACCENT = Color(0xFF9C7BFF)
private val MUTED = Color(0xFF9A9DA7)

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, ArkPlaybackService::class.java))).buildAsync()
        controllerFuture?.addListener({ try { controller = controllerFuture?.get() } catch (_: Exception) {} }, mainExecutor)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BG, surface = PANEL, primary = ACCENT)) {
                RadioScreen(controller)
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@Composable
private fun RadioScreen(controller: MediaController?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ark_radio", android.content.Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Все") }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var selected by remember { mutableStateOf(StationCatalog.all.first()) }
    var playing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Выберите станцию") }
    var showEqualizer by remember { mutableStateOf(false) }

    LaunchedEffect(favorites) { prefs.edit().putStringSet("favorites", favorites.toSet()).apply() }

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> status = "Подключение к потоку…"
                    Player.STATE_READY -> status = if (controller.isPlaying) "В эфире" else "Готово к воспроизведению"
                    Player.STATE_ENDED -> { status = "Эфир завершён"; playing = false }
                    Player.STATE_IDLE -> if (!controller.isPlaying) status = "Остановлено"
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) status = "В эфире"
                else if (controller.playbackState == Player.STATE_READY) status = "Пауза"
            }
            override fun onPlayerError(error: PlaybackException) {
                playing = false
                status = "Не удалось подключиться. Проверьте интернет или поток станции."
                Toast.makeText(context, "Не удалось воспроизвести ${selected.name}", Toast.LENGTH_LONG).show()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val title = mediaItem?.mediaMetadata?.title?.toString() ?: return
                StationCatalog.all.firstOrNull { it.name == title }?.let { selected = it }
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    fun play(station: Station) {
        selected = station
        val c = controller
        if (c == null) { status = "Плеер подключается. Подождите и попробуйте снова."; return }
        try {
            c.setMediaItem(MediaItem.Builder().setUri(station.url).setMediaMetadata(
                MediaMetadata.Builder().setTitle(station.name).setArtist("ARK Radio • Zaycev.FM").build()
            ).build())
            c.prepare()
            c.play()
            playing = true
            status = "Подключение к потоку…"
        } catch (_: Exception) {
            playing = false
            status = "Не удалось запустить станцию. Попробуйте ещё раз."
        }
    }

    val categories = listOf("Все", "Избранное") + StationCatalog.all.map { it.category }.distinct()
    val list = StationCatalog.all.filter { station ->
        (category == "Все" || (category == "Избранное" && station.id in favorites) || station.category == category) &&
            (station.name.contains(query, true) || station.category.contains(query, true))
    }

    Column(Modifier.fillMaxSize().background(BG).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ARK RADIO", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Zaycev.FM • Интернет-радио", color = MUTED, fontSize = 12.sp)
            }
            OutlinedButton(onClick = { showEqualizer = true }) { Text("Эквалайзер") }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск станции") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories) { cat -> FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) }) }
        }
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = PANEL), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("СЕЙЧАС В ЭФИРЕ", color = ACCENT, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(selected.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(status, color = MUTED, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        val c = controller
                        if (c == null) status = "Плеер подключается. Попробуйте снова через секунду."
                        else if (c.isPlaying) c.pause()
                        else if (c.mediaItemCount > 0 && c.playbackState != Player.STATE_IDLE) c.play()
                        else play(selected)
                    }) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(5.dp)); Text(if (playing) "Пауза" else "Слушать")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { controller?.stop(); playing = false; status = "Остановлено" }) { Text("Стоп") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("${list.size} станций", color = MUTED, fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list, key = { it.id }) { station ->
                Row(Modifier.fillMaxWidth().background(PANEL, RoundedCornerShape(15.dp)).clickable { play(station) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(station.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(station.category, color = MUTED, fontSize = 12.sp)
                    }
                    IconButton(onClick = { favorites = if (station.id in favorites) favorites - station.id else favorites + station.id }) {
                        Icon(if (station.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Избранное", tint = if (station.id in favorites) ACCENT else MUTED)
                    }
                }
            }
        }
    }

    if (showEqualizer) EqualizerDialog(onDismiss = { showEqualizer = false })
}

@Composable
private fun EqualizerDialog(onDismiss: () -> Unit) {
    val bandCount = ArkPlaybackService.bandCount().coerceAtMost(5)
    val range = ArkPlaybackService.bandRange()
    val values = remember(bandCount) { mutableStateListOf<Int>().apply { repeat(bandCount) { add(ArkPlaybackService.level(it)) } } }
    val presets = remember { ArkPlaybackService.presetNames() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Эквалайзер") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (ArkPlaybackService.audioSessionId <= 0 || bandCount == 0) {
                    Text("Запусти воспроизведение станции, затем открой эквалайзер ещё раз. Некоторые телефоны не поддерживают системный аудиоэффект.")
                } else {
                    Text("Настройка звука", color = MUTED, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    values.forEachIndexed { index, value ->
                        val freq = ArkPlaybackService.centerFrequencyHz(index)
                        val label = when {
                            freq < 100 -> "${freq} Гц"
                            freq >= 1000 -> "${freq / 1000.0} кГц"
                            else -> "$freq Гц"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, modifier = Modifier.width(62.dp), fontSize = 12.sp)
                            Slider(value = value / 100f, onValueChange = { db ->
                                val level = (db * 100).toInt().coerceIn(range.first, range.second)
                                values[index] = level
                                ArkPlaybackService.setLevel(index, level)
                            }, valueRange = range.first / 100f..range.second / 100f, modifier = Modifier.weight(1f))
                            Text("${if (value > 0) "+" else ""}${"%.1f".format(value / 100f)}", modifier = Modifier.width(44.dp), fontSize = 11.sp)
                        }
                    }
                    if (presets.isNotEmpty()) {
                        Text("Пресеты", fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(presets.indices.toList()) { index ->
                                AssistChip(onClick = {
                                    ArkPlaybackService.applyPreset(index)
                                    for (i in values.indices) values[i] = ArkPlaybackService.level(i)
                                }, label = { Text(presets[index]) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        dismissButton = {
            if (bandCount > 0) TextButton(onClick = {
                ArkPlaybackService.resetBands()
                for (i in values.indices) values[i] = ArkPlaybackService.level(i)
            }) { Text("Сбросить") }
        }
    )
}
