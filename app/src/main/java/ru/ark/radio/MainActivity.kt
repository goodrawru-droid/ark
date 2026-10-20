package ru.ark.radio

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, ArkPlaybackService::class.java))).buildAsync()
        controllerFuture?.addListener({ try { controller = controllerFuture?.get() } catch (_: Exception) {} }, mainExecutor)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BG, surface = PANEL, primary = ACCENT)) {
                RadioScreen(controller) { openEqualizer() }
            }
        }
    }
    private fun openEqualizer() {
        val id = ArkPlaybackService.audioSessionId
        if (id <= 0) { Toast.makeText(this, "Сначала запустите станцию", Toast.LENGTH_SHORT).show(); return }
        try {
            val intent = android.content.Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            else Toast.makeText(this, "На устройстве нет панели эквалайзера. Эффект включён, настройка зависит от прошивки.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { Toast.makeText(this, "Панель эквалайзера недоступна на этом устройстве", Toast.LENGTH_LONG).show() }
    }
    override fun onDestroy() { controllerFuture?.let { MediaController.releaseFuture(it) }; super.onDestroy() }
}

@Composable
private fun RadioScreen(controller: MediaController?, onEqualizer: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Все") }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var selected by remember { mutableStateOf(StationCatalog.all.first()) }
    var playing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Выберите станцию") }
    val categories = listOf("Все", "Избранное") + StationCatalog.all.map { it.category }.distinct()
    val list = StationCatalog.all.filter {
        (category == "Все" || it.category == category || (category == "Избранное" && it.id in favorites)) &&
            (it.name.contains(query, true) || it.category.contains(query, true))
    }
    fun play(s: Station) {
        selected = s
        val c = controller
        if (c == null) { status = "Плеер подключается — нажмите ещё раз"; return }
        c.setMediaItem(MediaItem.Builder().setUri(s.url).setMediaMetadata(
            MediaMetadata.Builder().setTitle(s.name).setArtist("ARK Radio • Zaycev.FM").build()).build())
        c.prepare(); c.play(); playing = true; status = "Подключение к потоку…"
    }
    Column(Modifier.fillMaxSize().background(BG).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ARK RADIO", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Zaycev.FM • Интернет-радио", color = MUTED, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onEqualizer) { Text("Эквалайзер") }
        }
        Spacer(Modifier.height(14.dp))
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
                    Button(onClick = { if (playing && controller?.isPlaying == true) { controller?.pause(); playing = false; status = "Пауза" } else play(selected) }) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(5.dp)); Text(if (playing) "Пауза" else "Слушать")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { controller?.stop(); playing = false; status = "Остановлено" }) { Text("Стоп") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("${list.size} записей каталога", color = MUTED, fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(list, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().background(PANEL, RoundedCornerShape(15.dp)).clickable { play(s) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(s.category, color = MUTED, fontSize = 12.sp)
                    }
                    IconButton(onClick = { favorites = if (s.id in favorites) favorites - s.id else favorites + s.id }) {
                        Icon(if (s.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Избранное", tint = if (s.id in favorites) ACCENT else MUTED)
                    }
                }
            }
        }
    }
}
