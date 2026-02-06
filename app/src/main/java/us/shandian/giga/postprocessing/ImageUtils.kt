package us.shandian.giga.postprocessing

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.Base64
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
        var quality = 100
        var scale = 1.0f
        var width = original.width
        var height = original.height
        var compressedSize: Int

        do {
            var bitmap = original.copy(original.config ?: Bitmap.Config.ARGB_8888, false)
            if (scale < 1.0f) {
                bitmap = bitmap.scale(width = width, height = height)
            }
            do {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                compressedSize = Base64.getEncoder().encodeToString(outputStream.toByteArray()).length
                quality -= 5 // Decrease quality by 5% for the next iteration
            } while (compressedSize > maxSizeBytes && quality > 70)
            if (compressedSize <= maxSizeBytes) {
                return CompressedImage(bitmap, quality, width, height)
            }
            if (scale > 0.5f) {
                scale -= 0.1f
            } else {
                scale *= 0.9f
            }
            width = (original.width * scale).toInt()
            height = (original.height * scale).toInt()
            quality = 100 // Reset quality for the next size reduction
        } while (width > 50 && height > 50) // Prevent too much downscaling
        return null
    }
}
