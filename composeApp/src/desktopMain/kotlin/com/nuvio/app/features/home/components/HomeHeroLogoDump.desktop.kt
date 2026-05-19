package com.nuvio.app.features.home.components

import com.nuvio.app.core.ui.upgradeTmdbImageQuality
import com.nuvio.app.desktop.DesktopRuntimeLog
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DumpConnectTimeoutMs = 15_000
private const val DumpReadTimeoutMs = 30_000
private const val DumpEnabledEnvironmentKey = "NUVIO_DUMP_HERO_LOGOS"
private const val DumpEnabledPropertyKey = "nuvio.dumpHeroLogos"
private val DumpTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault())

internal actual suspend fun dumpHomeHeroLogos(items: List<HomeHeroLogoDumpItem>) {
    if (!isHeroLogoDumpEnabled()) return

    if (items.isEmpty()) {
        DesktopRuntimeLog.info("homeHeroLogoDump skipped: no hero logos")
        return
    }

    withContext(Dispatchers.IO) {
        val outputDir = heroLogoDumpDirectory()
        Files.createDirectories(outputDir)
        DesktopRuntimeLog.info("homeHeroLogoDump start count=${items.size} dir=$outputDir")

        val manifest = StringBuilder()
        items.forEach { item ->
            val effectiveUrl = item.heroLogoUrl.upgradeTmdbImageQuality()
            val result = runCatching {
                dumpHeroLogo(
                    outputDir = outputDir,
                    item = item,
                    effectiveUrl = effectiveUrl,
                )
            }.getOrElse { error ->
                DesktopRuntimeLog.error(
                    "homeHeroLogoDump failed index=${item.index} id=${item.id} url=$effectiveUrl",
                    error,
                )
                DumpedHeroLogo(
                    item = item,
                    effectiveUrl = effectiveUrl,
                    statusCode = null,
                    contentType = null,
                    bytes = null,
                    width = null,
                    height = null,
                    file = null,
                    error = error.message ?: error::class.simpleName ?: "unknown error",
                )
            }

            manifest.appendLine(result.toJsonLine(outputDir))
            DesktopRuntimeLog.info(
                "homeHeroLogoDump item index=${item.index} id=${item.id} status=${result.statusCode} " +
                    "bytes=${result.bytes} dimensions=${result.width}x${result.height} file=${result.file}",
            )
        }

        val manifestPath = outputDir.resolve("manifest.jsonl")
        Files.writeString(manifestPath, manifest.toString(), StandardCharsets.UTF_8)
        Files.writeString(
            outputDir.resolve("README.txt"),
            "Hero logo dump created at ${Instant.now()}\n" +
                "Runtime log: ${DesktopRuntimeLog.path()}\n" +
                "Each manifest.jsonl row contains the source URL, effective URL, response metadata, and dumped file path.\n",
            StandardCharsets.UTF_8,
        )
        DesktopRuntimeLog.info("homeHeroLogoDump done dir=$outputDir manifest=$manifestPath")
    }
}

private fun isHeroLogoDumpEnabled(): Boolean =
    System.getProperty(DumpEnabledPropertyKey).isTruthy() ||
        System.getenv(DumpEnabledEnvironmentKey).isTruthy()

private fun String?.isTruthy(): Boolean =
    equals("true", ignoreCase = true) ||
        equals("1") ||
        equals("yes", ignoreCase = true) ||
        equals("on", ignoreCase = true)

private fun dumpHeroLogo(
    outputDir: Path,
    item: HomeHeroLogoDumpItem,
    effectiveUrl: String,
): DumpedHeroLogo {
    val connection = (URI(effectiveUrl).toURL().openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = DumpConnectTimeoutMs
        readTimeout = DumpReadTimeoutMs
        setRequestProperty("User-Agent", "NuvioDesktop hero-logo-dump")
    }

    return try {
        val statusCode = connection.responseCode
        val contentType = connection.contentType
        val stream = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        val bytes = stream.use { it.readBytes() }
        val dimensions = imageDimensions(bytes)
        val extension = fileExtension(contentType, effectiveUrl)
        val file = outputDir.resolve("${item.index.toString().padStart(2, '0')}-${safeFilePart(item.type)}-${safeFilePart(item.id)}-${safeFilePart(item.name)}$extension")
        Files.write(file, bytes)

        DumpedHeroLogo(
            item = item,
            effectiveUrl = effectiveUrl,
            statusCode = statusCode,
            contentType = contentType,
            bytes = bytes.size,
            width = dimensions?.first,
            height = dimensions?.second,
            file = file,
            error = null,
        )
    } finally {
        connection.disconnect()
    }
}

private fun heroLogoDumpDirectory(): Path =
    DesktopRuntimeLog.path()
        .parent
        .parent
        .resolve("debug")
        .resolve("hero-logos")
        .resolve(DumpTimestampFormatter.format(Instant.now()))

private fun imageDimensions(bytes: ByteArray): Pair<Int, Int>? =
    runCatching {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes))?.use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return@use null
            val reader = readers.next()
            try {
                reader.input = input
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

private fun fileExtension(contentType: String?, url: String): String {
    val normalizedContentType = contentType?.substringBefore(';')?.trim()?.lowercase()
    return when (normalizedContentType) {
        "image/png" -> ".png"
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "image/svg+xml" -> ".svg"
        else -> url.substringBefore('?')
            .substringAfterLast('/', "")
            .substringAfterLast('.', "")
            .takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
            ?.let { ".$it" }
            ?: ".img"
    }
}

private fun safeFilePart(value: String): String =
    value.trim()
        .ifBlank { "unknown" }
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .take(80)
        .ifBlank { "unknown" }

private data class DumpedHeroLogo(
    val item: HomeHeroLogoDumpItem,
    val effectiveUrl: String,
    val statusCode: Int?,
    val contentType: String?,
    val bytes: Int?,
    val width: Int?,
    val height: Int?,
    val file: Path?,
    val error: String?,
)

private fun DumpedHeroLogo.toJsonLine(outputDir: Path): String =
    buildString {
        append('{')
        appendJson("index", item.index)
        append(',')
        appendJson("id", item.id)
        append(',')
        appendJson("type", item.type)
        append(',')
        appendJson("name", item.name)
        append(',')
        appendJson("originalLogoUrl", item.originalLogoUrl)
        append(',')
        appendJson("heroLogoUrl", item.heroLogoUrl)
        append(',')
        appendJson("effectiveUrl", effectiveUrl)
        append(',')
        appendJson("statusCode", statusCode)
        append(',')
        appendJson("contentType", contentType)
        append(',')
        appendJson("bytes", bytes)
        append(',')
        appendJson("width", width)
        append(',')
        appendJson("height", height)
        append(',')
        appendJson("file", file?.toAbsolutePath()?.toString())
        append(',')
        appendJson("relativeFile", file?.let { outputDir.relativize(it).toString() })
        append(',')
        appendJson("error", error)
        append('}')
    }

private fun StringBuilder.appendJson(name: String, value: String?) {
    append('"').append(name).append('"').append(':')
    if (value == null) {
        append("null")
    } else {
        append('"').append(value.jsonEscaped()).append('"')
    }
}

private fun StringBuilder.appendJson(name: String, value: Int?) {
    append('"').append(name).append('"').append(':')
    append(value?.toString() ?: "null")
}

private fun String.jsonEscaped(): String = buildString(length + 8) {
    this@jsonEscaped.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
}
