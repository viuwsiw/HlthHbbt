package com.tshk.hlthhbbty.ui.messenger

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tshk.hlthhbbty.data.db.ChatMessageEntity
import com.tshk.hlthhbbty.messenger.MAX_VOICE_MS
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.GlassDialog
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.glass.glass
import com.tshk.hlthhbbty.ui.theme.Liquid
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

// ---------- запись голосовых ----------

/** Запись голосового: AAC 48 кГц, 256 кбит/с (максимальное качество, ~10 МБ за 5 минут). */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var file: File? = null
        private set
    var startedAt = 0L
        private set

    fun start(): Boolean = runCatching {
        val f = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(48_000)
        r.setAudioEncodingBitRate(256_000)
        r.setAudioChannels(1)
        r.setMaxDuration(MAX_VOICE_MS.toInt())
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = f
        startedAt = System.currentTimeMillis()
        true
    }.getOrElse { release(); false }

    /** Остановить; возвращает файл и длительность (null — слишком коротко или ошибка). */
    fun stop(): Pair<File, Long>? {
        val dur = System.currentTimeMillis() - startedAt
        val ok = runCatching { recorder?.stop() }.isSuccess
        release()
        val f = file ?: return null
        if (!ok || dur < 700) { f.delete(); return null }
        return f to dur.coerceAtMost(MAX_VOICE_MS)
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        release()
        file?.delete()
    }

    private fun release() {
        runCatching { recorder?.release() }
        recorder = null
    }
}

// ---------- голосовое сообщение ----------

@Composable
fun VoiceBubble(m: ChatMessageEntity, meta: JSONObject, loadFile: suspend () -> File?, onPlayed: () -> Unit) {
    val p = Liquid.palette
    val color = if (m.outgoing) p.onAccent else p.textPrimary
    val duration = meta.optLong("duration")
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var position by remember { mutableLongStateOf(0L) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var loading by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(file) {
        if (file == null) return@LaunchedEffect
        val mp = MediaPlayer().apply {
            setDataSource(file!!.absolutePath)
            prepare()
            setOnCompletionListener { playing = false; progress = 0f; position = 0 }
        }
        player = mp
        mp.start()
        playing = true
        onPlayed()
    }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { pl -> position = pl.currentPosition.toLong(); progress = pl.currentPosition.toFloat() / pl.duration.coerceAtLeast(1) }
            delay(100)
        }
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    Row(Modifier.padding(bottom = 4.dp).width(230.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)).clickable {
                val pl = player
                when {
                    pl == null && !loading -> { loading = true }
                    pl != null && playing -> { pl.pause(); playing = false }
                    pl != null -> { pl.start(); playing = true }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            if (loading && player == null) {
                LaunchedEffect(Unit) { file = loadFile(); loading = false }
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = color, strokeWidth = 2.dp)
            } else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = color)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                color = color, trackColor = color.copy(alpha = 0.2f),
            )
            VSpace(4.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (playing || position > 0) formatMs(position) else formatMs(duration), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                if (m.listened) {
                    Icon(Icons.Rounded.Headphones, null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(if (m.outgoing) "прослушано" else "", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
                } else if (!m.outgoing) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(p.accent))
                }
            }
        }
    }
}

// ---------- встроенный просмотр фото и видео ----------

@Composable
fun MediaViewer(file: File, mime: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (mime.startsWith("video/")) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            val mc = android.widget.MediaController(ctx)
                            mc.setAnchorView(this)
                            setMediaController(mc)
                            setVideoPath(file.absolutePath)
                            setOnPreparedListener { start() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                    onRelease = { it.stopPlayback() },
                )
            } else {
                var scale by remember { mutableFloatStateOf(1f) }
                var ox by remember { mutableFloatStateOf(0f) }
                var oy by remember { mutableFloatStateOf(0f) }
                coil.compose.AsyncImage(
                    file, null,
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                if (scale > 1f) { ox += pan.x; oy += pan.y } else { ox = 0f; oy = 0f }
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy),
                    contentScale = ContentScale.Fit,
                )
            }
            Box(
                Modifier.statusBarsPadding().padding(16.dp).size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Закрыть", tint = Color.White) }
        }
    }
}

// ---------- предпросмотр чата без отметки «прочитано» ----------

@Composable
fun ChatPreviewDialog(
    title: String,
    messages: List<ChatMessageEntity>,
    pinned: Boolean,
    canDeleteForAll: Boolean,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onDelete: (forAll: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = Liquid.palette
    var confirm by remember { mutableStateOf<Boolean?>(null) }
    GlassDialog(onDismiss = onDismiss, title = title, confirmText = "Открыть чат", onConfirm = onOpen, dismissText = "Закрыть") {
        Text("Предпросмотр — собеседник не увидит, что вы прочитали.", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
        VSpace(8.dp)
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).glass(RoundedCornerShape(16.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val last = messages.takeLast(12)
            if (last.isEmpty()) Text("Сообщений нет", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            last.forEach { m ->
                val meta = m.media?.let { runCatching { JSONObject(it) }.getOrNull() }
                val text = when {
                    meta?.optBoolean("voice") == true -> "🎤 Голосовое · " + formatMs(meta.optLong("duration"))
                    meta != null -> "📎 " + (m.text.ifBlank { meta.optString("name") })
                    else -> m.text
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.outgoing) Arrangement.End else Arrangement.Start) {
                    Text(
                        (if (!m.outgoing && m.peer.startsWith("g:")) "@${m.sender}: " else "") + text,
                        style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        color = if (m.outgoing) p.onAccent else p.textPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (m.outgoing) p.accent else p.textSecondary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        VSpace(10.dp)
        GlassButton(if (pinned) "Открепить" else "Закрепить", onPin, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary)
        VSpace(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Удалить у себя", { confirm = false }, Modifier.weight(1f), style = GlassButtonStyle.Secondary)
            if (canDeleteForAll) GlassButton("У всех", { confirm = true }, Modifier.weight(1f), style = GlassButtonStyle.Danger)
        }
    }
    confirm?.let { all ->
        GlassDialog(
            onDismiss = { confirm = null },
            title = if (all) "Удалить у всех?" else "Удалить у себя?",
            confirmText = "Удалить", confirmStyle = GlassButtonStyle.Danger,
            onConfirm = { confirm = null; onDelete(all) },
        ) {
            Text(
                if (all) "Переписка исчезнет у вас и у собеседника, сообщения удалятся с сервера." else "Переписка исчезнет только у вас. У собеседника она останется.",
                style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
            )
        }
    }
}

// ---------- новая группа ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewGroupDialog(busy: Boolean, error: String?, onCreate: (String, List<String>) -> Unit, onDismiss: () -> Unit) {
    val p = Liquid.palette
    var name by remember { mutableStateOf("") }
    var member by remember { mutableStateOf("") }
    val members = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    GlassDialog(
        onDismiss = onDismiss, title = "Новая группа", confirmText = if (busy) "Создаём…" else "Создать",
        confirmEnabled = !busy && name.isNotBlank() && members.isNotEmpty(),
        onConfirm = { onCreate(name, members.toList()) },
    ) {
        GlassTextField(name, { name = it.take(40) }, "Название")
        VSpace(10.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(member, { member = it.trim().removePrefix("@").lowercase() }, "Логин участника", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            GlassButton("+", {
                if (member.length >= 3 && member !in members && members.size < 4) { members.add(member); member = "" }
            }, enabled = member.length >= 3 && members.size < 4)
        }
        VSpace(8.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            members.forEach { u -> GlassChip("@$u ✕", onClick = { members.remove(u) }) }
        }
        Text("До 5 участников вместе с вами. Сообщения шифруются для каждого участника отдельно.",
            style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
        if (error != null) Text(error, style = MaterialTheme.typography.labelMedium, color = p.danger)
    }
}

// ---------- медиа / ссылки / голосовые из переписки ----------

private val urlRegex = Regex("""https?://[^\s]+""")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedContent(messages: List<ChatMessageEntity>, loadFile: suspend (String) -> File?) {
    val p = Liquid.palette
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var viewer by remember { mutableStateOf<Pair<File, String>?>(null) }
    val metas = remember(messages) {
        messages.mapNotNull { m -> m.media?.let { runCatching { m to JSONObject(it) }.getOrNull() } }
    }
    val media = metas.filter { (_, j) -> j.optString("mime").let { it.startsWith("image/") || it.startsWith("video/") } }
    val voices = metas.filter { (_, j) -> j.optBoolean("voice") }
    val links = remember(messages) { messages.flatMap { m -> urlRegex.findAll(m.text).map { m to it.value }.toList() } }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp)) {
        Text("Вложения", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
        VSpace(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Медиа · ${media.size}", selected = tab == 0, onClick = { tab = 0 })
            GlassChip("Ссылки · ${links.size}", selected = tab == 1, onClick = { tab = 1 })
            GlassChip("Голосовые · ${voices.size}", selected = tab == 2, onClick = { tab = 2 })
        }
        VSpace(12.dp)
        when (tab) {
            0 -> if (media.isEmpty()) Empty("Фото и видео пока нет") else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                media.reversed().forEach { (_, j) ->
                    val file by produceState<File?>(null, j.toString()) { value = loadFile(j.toString()) }
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(p.textSecondary.copy(alpha = 0.15f))
                            .clickable { file?.let { viewer = it to j.optString("mime") } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (j.optString("mime").startsWith("image/") && file != null) coil.compose.AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.PlayArrow, null, tint = p.textPrimary)
                    }
                }
            }
            1 -> if (links.isEmpty()) Empty("Ссылок пока нет") else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                links.reversed().forEach { (m, url) ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }.padding(8.dp)) {
                        Text(url, color = p.accent, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(chatTime(m.createdAt), color = p.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            else -> if (voices.isEmpty()) Empty("Голосовых пока нет") else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                voices.reversed().forEach { (m, j) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (m.outgoing) "Вы" else "@${m.sender.ifBlank { m.peer }}", color = p.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(70.dp), maxLines = 1)
                        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(p.textSecondary.copy(alpha = 0.1f)).padding(8.dp)) {
                            VoiceBubble(m.copy(outgoing = false), j, { loadFile(j.toString()) }, {})
                        }
                    }
                }
            }
        }
    }
    viewer?.let { (f, mime) -> MediaViewer(f, mime) { viewer = null } }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Liquid.palette.textSecondary, modifier = Modifier.padding(8.dp))
}

@Suppress("unused")
private val keepAspect = Modifier.aspectRatio(1f)
