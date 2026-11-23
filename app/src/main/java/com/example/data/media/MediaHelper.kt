package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PreparedMedia(
    val mediaType: String,
    val mediaUrl: String,
    val thumbnailUrl: String
)

object MediaHelper {

    suspend fun prepareImageDataUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 720,
        maxChars: Int = 220_000
    ): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to read selected image")
        val original = inputStream.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("Unsupported image format")
        encodeBitmapToDataUri(original, maxDimension, maxChars)
    }

    private fun encodeBitmapToDataUri(
        source: Bitmap,
        maxDimension: Int,
        maxChars: Int
    ): String {
        val scale = minOf(
            1f,
            maxDimension.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1)
        )
        val targetW = (source.width * scale).toInt().coerceAtLeast(1)
        val targetH = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(source, targetW, targetH, true) else source

        var quality = 78
        var encoded = ""
        while (quality >= 25) {
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            encoded = "data:image/jpeg;base64,$b64"
            if (encoded.length <= maxChars) break
            quality -= 12
        }
        if (scaled !== source) scaled.recycle()
        return encoded
    }

    suspend fun preparePickedMedia(context: Context, uri: Uri): PreparedMedia =
        withContext(Dispatchers.IO) {
            val mimeType = context.contentResolver.getType(uri).orEmpty()
            if (mimeType.startsWith("video/")) {
                val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
                val targetFile = File(mediaDir, "vid_${UUID.randomUUID()}.mp4")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Unable to read selected video")

                val thumbUri = extractVideoThumbnailDataUri(targetFile.absolutePath)
                PreparedMedia(
                    mediaType = "video",
                    mediaUrl = Uri.fromFile(targetFile).toString(),
                    thumbnailUrl = thumbUri
                )
            } else {
                val imageDataUri = prepareImageDataUri(context, uri, maxDimension = 768, maxChars = 240_000)
                PreparedMedia(
                    mediaType = "image",
                    mediaUrl = imageDataUri,
                    thumbnailUrl = imageDataUri
                )
            }
        }

    private fun extractVideoThumbnailDataUri(filePath: String): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val frame = retriever.getFrameAtTime(0)
            if (frame != null) {
                val uri = encodeBitmapToDataUri(frame, maxDimension = 480, maxChars = 120_000)
                frame.recycle()
                uri
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun decodeDataUriBytes(dataUri: String): ByteArray? {
        if (!dataUri.startsWith("data:image")) return null
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex <= 0) return null
        return try {
            Base64.decode(dataUri.substring(commaIndex + 1), Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }
}

class VoiceMessageController(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L
    private var player: MediaPlayer? = null

    fun startRecording(): Boolean {
        return try {
            val dir = File(context.filesDir, "voice").apply { mkdirs() }
            val file = File(dir, "voice_${UUID.randomUUID()}.m4a")
            currentFile = file
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(22050)
            mr.setAudioEncodingBitRate(32000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            startTimeMs = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            cleanupRecorder()
            false
        }
    }

    fun stopRecording(): Pair<String, Long>? {
        val mr = recorder ?: return null
        val file = currentFile ?: return null
        val duration = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(500L)
        return try {
            mr.stop()
            mr.release()
            recorder = null
            val bytes = file.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val uri = "data:audio/mp4;base64,$b64"
            if (uri.length <= 320_000) {
                uri to duration
            } else {
                Uri.fromFile(file).toString() to duration
            }
        } catch (_: Exception) {
            cleanupRecorder()
            null
        }
    }

    fun cancelRecording() {
        cleanupRecorder()
        currentFile?.delete()
        currentFile = null
    }

    private fun cleanupRecorder() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }

    fun playAudio(audioSource: String, onComplete: () -> Unit) {
        stopPlayback()
        try {
            val playablePath = if (audioSource.startsWith("data:audio")) {
                val comma = audioSource.indexOf(',')
                if (comma <= 0) {
                    onComplete()
                    return
                }
                val bytes = Base64.decode(audioSource.substring(comma + 1), Base64.DEFAULT)
                val temp = File(context.cacheDir, "play_${audioSource.hashCode()}.m4a")
                temp.writeBytes(bytes)
                temp.absolutePath
            } else {
                audioSource
            }
            player = MediaPlayer().apply {
                if (playablePath.startsWith("file://") || playablePath.startsWith("content://")) {
                    setDataSource(context, Uri.parse(playablePath))
                } else {
                    setDataSource(playablePath)
                }
                setOnCompletionListener {
                    stopPlayback()
                    onComplete()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    onComplete()
                    true
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            stopPlayback()
            onComplete()
        }
    }

    fun stopPlayback() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }
}
