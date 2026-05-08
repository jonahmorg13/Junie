package com.juni.app.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Pull JPEG bytes out of a CameraX ImageProxy, applying any rotation reported
 * by the camera so the image is upright when handed to a vision API.
 */
fun ImageProxy.toUprightJpeg(maxLongSide: Int = 1568, quality: Int = 85): ByteArray {
    val buffer = planes[0].buffer
    val raw = ByteArray(buffer.remaining())
    buffer.get(raw)
    val rotation = imageInfo.rotationDegrees
    return raw.resizeAndRotate(rotation, maxLongSide, quality)
}

/**
 * Decode → rotate → downscale to keep us comfortably under provider image-size
 * limits (Claude recommends 1568px on the long side; OpenAI/Gemini are similar).
 */
fun ByteArray.resizeAndRotate(rotationDegrees: Int = 0, maxLongSide: Int = 1568, quality: Int = 85): ByteArray {
    val src = BitmapFactory.decodeByteArray(this, 0, size) ?: return this
    val scale = maxLongSide.toFloat() / max(src.width, src.height)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
    } else {
        src
    }
    val rotated = if (rotationDegrees % 360 != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
    } else {
        scaled
    }
    val out = ByteArrayOutputStream()
    rotated.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}
