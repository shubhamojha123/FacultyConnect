package com.facultyconnect.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object ImageUtils {
    /** Validates that [uri] is an image, scales it down and returns JPEG bytes (always well under 5 MB). */
    fun compress(ctx: Context, uri: Uri, maxDim: Int = 512): ByteArray {
        val type = ctx.contentResolver.getType(uri)
        require(type != null && type.startsWith("image/")) { "Please choose an image file" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("Could not read that image")

        val scale = min(1f, maxDim.toFloat() / max(bmp.width, bmp.height))
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true
            )
        } else bmp
        return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }
}
