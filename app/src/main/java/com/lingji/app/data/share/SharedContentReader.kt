package com.lingji.app.data.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/** 信息岛：系统分享——单个分享附件 */
data class SharedAttachment(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    /** 可直接交给视觉模型的图片（图片本身或 PDF 渲染页），base64 data URI */
    val imageDataUris: List<String> = emptyList(),
    /** 文本类文件解析出的内容（已截断） */
    val text: String? = null,
    /** 无法读取或解析时的说明 */
    val note: String? = null
)

/** 信息岛：系统分享——一次系统分享的全部内容 */
data class SharedPayload(
    val text: String = "",
    val attachments: List<SharedAttachment> = emptyList(),
    /** 超出上限未处理的附件数量 */
    val skippedAttachments: Int = 0
)

/**
 * 信息岛：系统分享
 *
 * 将 ACTION_SEND / ACTION_SEND_MULTIPLE 分享进来的内容统一转换成 Agent 可处理的形态：
 * 文本原样保留，图片压缩成 base64，PDF 渲染成页面图片，文本类文件解码出正文，
 * 其余二进制文件只保留元信息。读取需要尽快完成——URI 读取权限只随本次 Intent 授予。
 */
@Singleton
class SharedContentReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun read(intent: Intent): SharedPayload? = withContext(Dispatchers.IO) {
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return@withContext null

        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val uris = intent.streamUris().distinct()
        val attachments = uris.take(MAX_ATTACHMENTS).mapNotNull { uri ->
            runCatching { readAttachment(uri) }
                .onFailure { Log.w(TAG, "读取分享附件失败: $uri", it) }
                .getOrNull()
        }
        if (text.isEmpty() && attachments.isEmpty()) return@withContext null
        SharedPayload(
            text = text,
            attachments = attachments,
            skippedAttachments = (uris.size - MAX_ATTACHMENTS).coerceAtLeast(0)
        )
    }

    private fun readAttachment(uri: Uri): SharedAttachment? {
        val resolver = context.contentResolver
        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "分享内容"
        // 部分来源只会给出 application/octet-stream，按文件名后缀再猜一次类型
        val mimeType = resolver.getType(uri)
            ?.takeUnless { it == OCTET_STREAM }
            ?: guessMimeType(name)
            ?: OCTET_STREAM
        val size = querySize(uri)
        return when {
            mimeType.startsWith("image/") -> {
                val image = encodeUriToPngDataUri(uri)
                SharedAttachment(
                    name = name,
                    mimeType = mimeType,
                    sizeBytes = size,
                    imageDataUris = listOfNotNull(image),
                    note = if (image == null) "图片读取失败" else null
                )
            }
            mimeType == PDF_MIME -> readPdf(uri, name, size)
            else -> {
                // 只对文本类文件尝试解码，避免把 docx / zip 等二进制解成乱码
                val text = if (isTextLike(mimeType, name)) readTextFile(uri) else null
                SharedAttachment(
                    name = name,
                    mimeType = mimeType,
                    sizeBytes = size,
                    text = text,
                    note = if (text == null) "该类型暂不支持解析内容" else null
                )
            }
        }
    }

    /** PDF 渲染成页面图片，交给支持视觉的模型阅读。 */
    private fun readPdf(uri: Uri, name: String, size: Long): SharedAttachment {
        return runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@runCatching SharedAttachment(name, PDF_MIME, size, note = "文件读取失败")
            descriptor.use { pfd ->
                val renderer = PdfRenderer(pfd)
                try {
                    val pages = buildList {
                        for (index in 0 until min(renderer.pageCount, MAX_PDF_PAGES)) {
                            val page = renderer.openPage(index)
                            try {
                                val bitmap = renderPdfPage(page)
                                try {
                                    encodeBitmapToPngDataUri(bitmap)?.let { add(it) }
                                } finally {
                                    bitmap.recycle()
                                }
                            } finally {
                                page.close()
                            }
                        }
                    }
                    SharedAttachment(
                        name = name,
                        mimeType = PDF_MIME,
                        sizeBytes = size,
                        imageDataUris = pages,
                        note = when {
                            pages.isEmpty() -> "PDF 渲染失败"
                            renderer.pageCount > MAX_PDF_PAGES -> "仅转换前 $MAX_PDF_PAGES 页"
                            else -> null
                        }
                    )
                } finally {
                    renderer.close()
                }
            }
        }.getOrElse {
            Log.w(TAG, "读取 PDF 失败: $uri", it)
            SharedAttachment(name, PDF_MIME, size, note = "PDF 读取失败")
        }
    }

    private fun renderPdfPage(page: PdfRenderer.Page): Bitmap {
        val scale = min(
            PDF_RENDER_WIDTH / page.width.toFloat(),
            PDF_RENDER_MAX_HEIGHT / page.height.toFloat()
        )
        val width = max(1, (page.width * scale).toInt())
        val height = max(1, (page.height * scale).toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    private fun encodeUriToPngDataUri(uri: Uri): String? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)?.let { bitmap ->
                    val scaled = scaleBitmap(bitmap, IMAGE_MAX_DIMENSION)
                    try {
                        encodeBitmapToPngDataUri(scaled)
                    } finally {
                        if (scaled !== bitmap) scaled.recycle()
                        bitmap.recycle()
                    }
                }
            }
        }.getOrNull()
    }

    private fun encodeBitmapToPngDataUri(bitmap: Bitmap): String? {
        return runCatching {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    /** 文本类文件读成正文；含大量控制字符的二进制内容返回 null。 */
    private fun readTextFile(uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readAtMost(MAX_TEXT_BYTES) }
            ?: return null
        if (bytes.isEmpty()) return ""
        if (looksBinary(bytes)) return null
        val decoded = decodeText(bytes) ?: return null
        return if (decoded.length > MAX_TEXT_CHARS) {
            decoded.take(MAX_TEXT_CHARS) + "\n[内容过长已截断]"
        } else {
            decoded
        }
    }

    private fun decodeText(bytes: ByteArray): String? {
        // 先按 UTF-8 严格解码，失败再尝试 GBK（大量中文 txt 仍是 GBK 编码）
        for (charset in DECODE_CHARSETS) {
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            runCatching { return decoder.decode(ByteBuffer.wrap(bytes)).toString() }
        }
        return null
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val sample = bytes.take(min(bytes.size, 8192))
        if (sample.isEmpty()) return false
        val suspicious = sample.count { byte ->
            val value = byte.toInt() and 0xFF
            value < 0x09 || value in 0x0E..0x1F || value == 0x7F
        }
        return suspicious * 100 > sample.size * 2
    }

    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < limit) {
            val read = read(buffer, 0, min(buffer.size, limit - total))
            if (read <= 0) break
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / sampleSize >= IMAGE_MAX_DIMENSION || halfHeight / sampleSize >= IMAGE_MAX_DIMENSION) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = max(1, (maxDimension / ratio).toInt())
        } else {
            newHeight = maxDimension
            newWidth = max(1, (maxDimension * ratio).toInt())
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun isTextLike(mimeType: String, name: String): Boolean {
        if (mimeType.startsWith("text/")) return true
        if (mimeType in TEXT_APPLICATION_MIMES) return true
        return name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS
    }

    private fun guessMimeType(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "heic", "heif" -> "image/heic"
        "pdf" -> PDF_MIME
        "txt" -> "text/plain"
        "md", "markdown" -> "text/markdown"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html"
        else -> null
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        }
    }.getOrNull() ?: 0L

    @Suppress("DEPRECATION")
    private fun Intent.streamUris(): List<Uri> = when (action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else getParcelableExtra(Intent.EXTRA_STREAM)
        )
        Intent.ACTION_SEND_MULTIPLE -> if (Build.VERSION.SDK_INT >= 33) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            val list: ArrayList<Uri>? = getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            list.orEmpty()
        }
        else -> emptyList()
    }

    private companion object {
        private const val TAG = "SharedContent"
        private const val PDF_MIME = "application/pdf"
        private const val OCTET_STREAM = "application/octet-stream"
        private const val MAX_ATTACHMENTS = 9
        private const val MAX_PDF_PAGES = 3
        private const val PDF_RENDER_WIDTH = 1440f
        private const val PDF_RENDER_MAX_HEIGHT = 2400f
        private const val IMAGE_MAX_DIMENSION = 960
        private const val MAX_TEXT_BYTES = 512 * 1024
        private const val MAX_TEXT_CHARS = 20_000
        private val DECODE_CHARSETS = listOf(Charsets.UTF_8, Charset.forName("GBK"))
        private val TEXT_APPLICATION_MIMES = setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/yaml",
            "application/x-yaml"
        )
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "csv", "json", "xml", "html", "htm",
            "log", "yaml", "yml", "ini", "conf", "srt", "vtt",
            "kt", "java", "py", "js", "ts", "css"
        )
    }
}
