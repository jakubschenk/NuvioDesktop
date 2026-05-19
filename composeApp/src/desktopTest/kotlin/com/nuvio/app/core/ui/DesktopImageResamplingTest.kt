package com.nuvio.app.core.ui

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

class DesktopImageResamplingTest {
    @Test
    fun lanczosResizeKeepsTransparentPngPixels() {
        val source = transparentPngBitmap()
        val scaled = assertNotNull(source.nuvioScaleToBitmap(48, 24))

        try {
            repeat(4) {
                System.gc()
                ByteArray(2_000_000)
            }

            val bytes = assertNotNull(
                scaled.readPixels(
                    ImageInfo(48, 24, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
                    48 * 4,
                    0,
                    0,
                ),
            )
            val alphaSum = bytes.asSequence()
                .drop(3)
                .chunked(4)
                .sumOf { it.first().toInt() and 0xFF }

            assertTrue(alphaSum > 0, "Resized transparent PNG should retain visible alpha pixels")
        } finally {
            if (scaled !== source) {
                scaled.close()
            }
            source.close()
        }
    }

    private fun transparentPngBitmap(): Bitmap {
        val buffered = BufferedImage(96, 48, BufferedImage.TYPE_INT_ARGB)
        val graphics = buffered.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color(255, 255, 255, 220)
            graphics.fillRoundRect(8, 8, 80, 32, 12, 12)
            graphics.color = Color(255, 255, 255, 96)
            graphics.fillOval(28, 2, 40, 40)
        } finally {
            graphics.dispose()
        }

        val pngBytes = ByteArrayOutputStream().use { output ->
            ImageIO.write(buffered, "png", output)
            output.toByteArray()
        }
        val image = Image.makeFromEncoded(pngBytes)
        val bitmap = Bitmap()
        try {
            assertTrue(bitmap.allocN32Pixels(image.width, image.height))
            assertTrue(image.readPixels(bitmap))
        } finally {
            image.close()
        }
        return bitmap
    }
}
