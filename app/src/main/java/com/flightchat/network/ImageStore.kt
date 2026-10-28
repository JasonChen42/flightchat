package com.flightchat.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

/** Content-addressed files, independent of local database row IDs and sender URIs. */
object ImageStore {
    const val MAX_BYTES = 20 * 1024 * 1024
    val revision = MutableStateFlow(0L)
    val progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    @Synchronized private fun report(id: String, percent: Int?) {
        progress.value = if (percent == null) progress.value - id else progress.value + (id to percent)
    }
    fun decode(path: String, maxSize: Int = 2048): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSize) sample *= 2
        val source = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val orientation = runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            8 -> matrix.setRotate(-90f)
        }
        if (matrix.isIdentity) return source
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
            if (it !== source) source.recycle()
        }
    }
    fun file(context: Context, id: String): File {
        require(id.matches(Regex("[a-f0-9]{64}")))
        return File(File(context.filesDir, "images").apply { mkdirs() }, id)
    }
    fun exists(context: Context, id: String): Boolean =
        runCatching { file(context, id).isFile }.getOrDefault(false)
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32768)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    suspend fun import(context: Context, uri: Uri, original: Boolean): String = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("photo", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                temp.outputStream().use { output -> copyBounded(input, output, MAX_BYTES.toLong()) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
            if (!original) {
                val bitmap = decode(temp.path)
                    ?: error("无法读取图片")
                try { temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                finally { bitmap.recycle() }
            }
            val id = hash(temp)
            temp.copyTo(file(context, id), overwrite = true)
            revision.value += 1
            id
        } finally { temp.delete() }
    }
    private fun copyBounded(input: InputStream, output: OutputStream, max: Long) {
        var total = 0L
        val buffer = ByteArray(32768)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            require(total <= max) { "图片不能超过 20 MB" }
            output.write(buffer, 0, n)
        }
    }
    fun receive(context: Context, id: String, size: Long, input: DataInputStream) {
        require(size in 1..MAX_BYTES.toLong())
        val destination = file(context, id)
        val temp = File.createTempFile("receive", ".tmp", destination.parentFile)
        try {
            temp.outputStream().use { output ->
                var remaining = size
                val buffer = ByteArray(32768)
                while (remaining > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (n < 0) throw EOFException()
                    output.write(buffer, 0, n)
                    remaining -= n
                    report(id, ((size - remaining) * 100 / size).toInt())
                }
            }
            require(hash(temp) == id) { "图片校验失败" }
            check(temp.renameTo(destination))
            revision.value += 1
        } finally { temp.delete(); report(id, null) }
    }
    fun transfer(context: Context, host: String, port: Int, token: String, id: String, upload: Boolean) {
        report(id, 0)
        try { Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 30000
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            output.writeUTF(token)
            output.writeUTF(if (upload) "PUT" else "GET")
            output.writeUTF(id)
            if (upload) {
                val source = file(context, id)
                output.writeLong(source.length())
                source.inputStream().use { inputFile ->
                    val buffer = ByteArray(32768)
                    var sent = 0L
                    while (true) {
                        val n = inputFile.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        sent += n
                        report(id, (sent * 100 / source.length()).toInt())
                    }
                }
                output.flush()
                check(input.readBoolean()) { "上传失败" }
            } else {
                output.flush()
                receive(context, id, input.readLong(), input)
            }
        } } finally { report(id, null) }
    }
    fun serve(context: Context, socket: Socket, token: String) {
        socket.use {
            socket.soTimeout = 30000
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            require(input.readUTF() == token)
            val action = input.readUTF()
            val id = input.readUTF()
            val image = file(context, id)
            when (action) {
                "PUT" -> { receive(context, id, input.readLong(), input); output.writeBoolean(true) }
                "GET" -> {
                    output.writeLong(if (image.isFile) image.length() else -1)
                    if (image.isFile) image.inputStream().use { it.copyTo(output) }
                }
                else -> error("Unknown image operation")
            }
            output.flush()
        }
    }
}
