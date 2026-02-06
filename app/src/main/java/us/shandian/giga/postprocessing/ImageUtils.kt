package us.shandian.giga.postprocessing

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.schabi.newpipe.ktx.scale
import org.schabi.newpipe.util.image.PreferredImageQuality

object ImageUtils {
    fun getImageTypeFromUrl(url: String): String {
        val extension = url.substringAfterLast('.', "")
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            else -> "application/octet-stream" // Default binary type
        }
    }

    data class CompressedImage(
        val bitmap: Bitmap,
        val quality: Int,
        val width: Int,
        val height: Int
    )

    fun compressToSize(original: Bitmap, maxSizeBytes: Int): CompressedImage? {
        // Strategy:
        // 1. Compress once and measure binary size; compute base64 size via formula:
        //    base64Len = 4 * ceil(binaryLen / 3)
        //    See https://de.wikipedia.org/wiki/Base64#Platzbedarf (en wiki doesn't have formula)
        // 2. If too big, try an adaptive quality reduction proportional to the ratio:
        //    newQuality ≈ quality * (maxSize / measuredBase64Size)
        // 3. If quality hits minimum and still too big,
        //    compute scale factor ≈ sqrt(maxSize / measuredBase64Size)
        //    to reduce width/height (area scales ~ scale^2).
        //    Repeat until fits or min dimension reached.
        val MIN_DIMENSION = 50
        val MIN_QUALITY = 70
        var quality = 100
        var scale = 1.0f
        var width = original.width
        var height = original.height

        while (width > MIN_DIMENSION && height > MIN_DIMENSION) { // loop for scaling down
            // Prepare bitmap at current dimensions
            val bitmap = if (scale < 1.0f) {
                original.scale(width = width, height = height)
            } else {
                // use a copy to ensure compress works on mutable config if needed
                original.copy(original.config ?: Bitmap.Config.ARGB_8888, false)
            }

            while (true) { // loop for iterative quality adjustments for this size
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                val binarySize = outputStream.size() // actual compressed bytes
                // base64 size formula: 4 * ceil(binarySize / 3)
                // is the same as ((binarySize + 2) / 3) * 4 which is more efficient
                val base64Size = ((binarySize + 2) / 3) * 4

                if (base64Size <= maxSizeBytes) {
                    return CompressedImage(bitmap, quality, width, height)
                }

                // Try to compute an adaptive new quality based on ratio assuming linear scaling
                val ratio = maxSizeBytes.toDouble() / base64Size.toDouble()
                val computedQuality = max(
                    MIN_QUALITY,
                    min(quality - 5, (quality * ratio).toInt())
                )

                if (computedQuality >= quality) {
                    // If quality cannot be effectively reduced further, break to scale image down
                    break
                }
                quality = computedQuality
            }

            // If quality reductions were insufficient, reduce scale using sqrt of ratio
            // Re-compress once to get a size for scaling decision (conservative)
            val probeStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, probeStream)
            val probeBinary = probeStream.size()
            val probeBase64 = ((probeBinary + 2) / 3) * 4

            // avoid division by zero, should not happen but just in case
            if (probeBase64 == 0) return null

            // Desired overall scale factor: sqrt(maxSize / observedSize)
            val desiredRatio = maxSizeBytes.toDouble() / probeBase64.toDouble()
            val scaleFactor = sqrt(desiredRatio).coerceAtMost(0.95)

            // Update scale and dimensions, reset quality to allow better quality at smaller size
            scale *= scaleFactor.toFloat()
            width = (original.width * scale).toInt()
            height = (original.height * scale).toInt()
            quality = 100
        }

        return null
    }
}
