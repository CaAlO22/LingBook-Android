package com.lingji.app.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.core.graphics.get
import androidx.core.graphics.set
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A pure-CPU Gaussian blur implementation that does not rely on platform blur
 * APIs (RenderEffect, Haze, RenderScript, etc.).
 *
 * For performance it works on a down-scaled copy of the input, applies a
 * separable Gaussian convolution, then scales the result back up. This is
 * intended as a fallback for devices whose GPU/drivers disable hardware blur.
 */
object GaussianBlur {

    /**
     * Blurs the given [Bitmap] and returns a new blurred [Bitmap] of the same
     * size.
     *
     * @param source The source bitmap.
     * @param radius The blur radius in pixels (sigma is derived from it).
     * @param downscaleFactor The factor by which the image is down-scaled before
     *   blurring. Smaller values are much faster but lower quality. Values in
     *   the 0.2f–0.33f range usually give a good trade-off.
     */
    fun blur(source: Bitmap, radius: Float, downscaleFactor: Float = 0.25f): Bitmap {
        require(radius > 0f) { "radius must be > 0" }
        require(downscaleFactor > 0f && downscaleFactor <= 1f) { "downscaleFactor must be in (0, 1]" }

        // GraphicsLayer.toImageBitmap() may return a HARDWARE bitmap which cannot
        // be read pixel-by-pixel. Copy it to a software bitmap first.
        val softwareSource = ensureSoftware(source)

        val scaledWidth = (softwareSource.width * downscaleFactor).toInt().coerceAtLeast(1)
        val scaledHeight = (softwareSource.height * downscaleFactor).toInt().coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(softwareSource, scaledWidth, scaledHeight, true)
        val blurred = blurExact(scaled, radius * downscaleFactor)
        scaled.recycle()

        if (softwareSource !== source) softwareSource.recycle()

        return Bitmap.createScaledBitmap(blurred, source.width, source.height, true).also {
            blurred.recycle()
        }
    }

    /**
     * Crops [source] to [cropRect] (in source coordinates), blurs the crop, and
     * returns a [Bitmap] of size [cropRect.width] x [cropRect.height].
     */
    fun blurCrop(
        source: Bitmap,
        cropRect: Rect,
        radius: Float,
        downscaleFactor: Float = 0.25f
    ): Bitmap {
        val left = cropRect.left.toInt().coerceIn(0, source.width)
        val top = cropRect.top.toInt().coerceIn(0, source.height)
        val right = cropRect.right.toInt().coerceIn(0, source.width)
        val bottom = cropRect.bottom.toInt().coerceIn(0, source.height)

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(source, left, top, width, height)
        return blur(cropped, radius, downscaleFactor).also {
            cropped.recycle()
        }
    }

    private fun ensureSoftware(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("Failed to copy hardware bitmap")
        } else {
            bitmap
        }
    }

    private fun blurExact(source: Bitmap, radius: Float): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val sigma = radius / 2.4f
        val kernelSize = (radius * 2).toInt() + 1
        val kernel = createKernel(kernelSize, sigma)
        val half = kernelSize / 2

        // Horizontal pass
        val horizontal = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                var a = 0f
                var weightSum = 0f

                for (k in -half..half) {
                    val sampleX = (x + k).coerceIn(0, width - 1)
                    val weight = kernel[k + half]
                    val pixel = source[sampleX, y]

                    a += weight * ((pixel ushr 24) and 0xFF)
                    r += weight * ((pixel ushr 16) and 0xFF)
                    g += weight * ((pixel ushr 8) and 0xFF)
                    b += weight * (pixel and 0xFF)
                    weightSum += weight
                }

                val ia = (a / weightSum).toInt().coerceIn(0, 255)
                val ir = (r / weightSum).toInt().coerceIn(0, 255)
                val ig = (g / weightSum).toInt().coerceIn(0, 255)
                val ib = (b / weightSum).toInt().coerceIn(0, 255)
                horizontal[y * width + x] = (ia shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }

        // Vertical pass
        val resultPixels = IntArray(width * height)
        for (x in 0 until width) {
            for (y in 0 until height) {
                var r = 0f
                var g = 0f
                var b = 0f
                var a = 0f
                var weightSum = 0f

                for (k in -half..half) {
                    val sampleY = (y + k).coerceIn(0, height - 1)
                    val weight = kernel[k + half]
                    val pixel = horizontal[sampleY * width + x]

                    a += weight * ((pixel ushr 24) and 0xFF)
                    r += weight * ((pixel ushr 16) and 0xFF)
                    g += weight * ((pixel ushr 8) and 0xFF)
                    b += weight * (pixel and 0xFF)
                    weightSum += weight
                }

                val ia = (a / weightSum).toInt().coerceIn(0, 255)
                val ir = (r / weightSum).toInt().coerceIn(0, 255)
                val ig = (g / weightSum).toInt().coerceIn(0, 255)
                val ib = (b / weightSum).toInt().coerceIn(0, 255)
                resultPixels[y * width + x] = (ia shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }

        output.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun createKernel(size: Int, sigma: Float): FloatArray {
        val kernel = FloatArray(size)
        val mean = size / 2
        var sum = 0f
        for (i in 0 until size) {
            val value = ((1.0 / (sqrt(2.0 * Math.PI) * sigma.toDouble())) *
                    exp(-((i - mean).toDouble().pow(2.0) / (2.0 * sigma * sigma)))).toFloat()
            kernel[i] = value
            sum += value
        }
        // Normalize
        for (i in kernel.indices) {
            kernel[i] /= sum
        }
        return kernel
    }
}
