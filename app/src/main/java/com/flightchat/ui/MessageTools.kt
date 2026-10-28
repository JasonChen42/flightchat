package com.flightchat.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flightchat.database.ChatMessageDao
import com.flightchat.model.ChatMessage
import com.flightchat.network.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SearchMessagesDialog(dao: ChatMessageDao, onDismiss: () -> Unit, onSelect: (ChatMessage) -> Unit) {
    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(query) { delay(250); debounced = query.trim() }
    val results by remember(debounced) { dao.search(debounced) }.collectAsState(initial = emptyList())
    AlertDialog(onDismissRequest = onDismiss, title = { Text("搜索聊天记录") }, text = {
        Column {
            TextField(value = query, onValueChange = { query = it }, placeholder = { Text("输入关键词") }, singleLine = true)
            Text("搜索本机历史文字和图片说明，最多显示 200 条结果", style = MaterialTheme.typography.labelSmall)
            if (debounced.isNotBlank()) {
                if (results.isEmpty()) Text("没有找到相关消息", Modifier.padding(16.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(results, key = { it.id }) { message ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(message) }.padding(vertical = 10.dp)) {
                            Text("${message.nickname} · ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(message.timestamp))}",
                                style = MaterialTheme.typography.labelSmall)
                            val content = if (message.type == "IMAGE") "[图片] ${message.content}" else message.content
                            val highlighted = remember(content, debounced) {
                                val builder = AnnotatedString.Builder(content)
                                var start = content.indexOf(debounced, ignoreCase = true)
                                while (start >= 0) {
                                    builder.addStyle(SpanStyle(background = Color(0xFFFFE082)), start, start + debounced.length)
                                    start = content.indexOf(debounced, start + debounced.length, ignoreCase = true)
                                }
                                builder.toAnnotatedString()
                            }
                            Text(highlighted, maxLines = 3)
                        }
                        Divider()
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
fun StoredImage(id: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val revision by ImageStore.revision.collectAsState()
    val bitmap by produceState<android.graphics.Bitmap?>(null, id, revision) {
        value = withContext(Dispatchers.IO) {
            if (!ImageStore.exists(context, id)) null else {
                ImageStore.decode(ImageStore.file(context, id).path)
            }
        }
    }
    val progress by ImageStore.progress.collectAsState()
    Column(modifier) {
        bitmap?.let { Image(it.asImageBitmap(), "聊天图片", Modifier.fillMaxWidth()) }
            ?: Text("图片等待下载，连接后自动重试", Modifier.padding(12.dp))
        progress[id]?.let { Text("传输中 $it%", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
fun SendImageDialog(onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Uri?>(null) }
    var original by remember { mutableStateOf(false) }
    var imageId by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        selected = it
        imageId = null
    }
    LaunchedEffect(selected, original) {
        imageId = null
        error = null
        val uri = selected ?: return@LaunchedEffect
        processing = true
        try { imageId = ImageStore.import(context, uri, original) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "读取图片失败" }
        finally { processing = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("发送图片") }, text = {
        Column {
            TextButton(onClick = { picker.launch("image/*") }) { Text(if (selected == null) "选择图片" else "重新选择") }
            imageId?.let { StoredImage(it, Modifier.fillMaxWidth().heightIn(max = 230.dp)) }
            if (processing) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = original, onCheckedChange = { original = it; imageId = null })
                Text("发送原图（最大 20 MB）")
            }
            TextField(caption, onValueChange = { caption = it }, placeholder = { Text("添加说明（可选）") })
        }
    }, confirmButton = {
        TextButton(enabled = imageId != null && !processing, onClick = { imageId?.let { onSend(it, caption) } }) { Text("发送") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun ImageViewer(id: String, onDismiss: () -> Unit) {
    var zoom by remember { mutableStateOf(1f) }
    var x by remember { mutableStateOf(0f) }
    var y by remember { mutableStateOf(0f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box {
                StoredImage(id, Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTransformGestures { _, pan, scale, _ ->
                        zoom = (zoom * scale).coerceIn(1f, 5f)
                        x = if (zoom == 1f) 0f else (x + pan.x).coerceIn(-size.width * zoom / 2, size.width * zoom / 2)
                        y = if (zoom == 1f) 0f else (y + pan.y).coerceIn(-size.height * zoom / 2, size.height * zoom / 2)
                    }
                }.graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = x, translationY = y))
                TextButton(onClick = onDismiss) { Text("关闭", color = Color.White) }
            }
        }
    }
}
