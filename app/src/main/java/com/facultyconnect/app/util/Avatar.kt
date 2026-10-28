package com.facultyconnect.app.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import kotlin.math.abs
import kotlin.math.min

/** Circle with the person's initials; used when there is no profile photo. */
class InitialsDrawable(name: String) : Drawable() {
    private val palette = intArrayOf(
        0xFF0B2447.toInt(), 0xFF0A7C86.toInt(), 0xFF1B7F45.toInt(),
        0xFF4A5FC1.toInt(), 0xFFA85D00.toInt(), 0xFF6A3D9A.toInt()
    )
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette[abs(name.hashCode()) % palette.size] }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = min(b.width(), b.height()) / 2f
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, bg)
        tp.textSize = r * 0.8f
        val y = b.exactCenterY() - (tp.descent() + tp.ascent()) / 2
        canvas.drawText(initials, b.exactCenterX(), y, tp)
    }

    override fun setAlpha(alpha: Int) { bg.alpha = alpha; tp.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { bg.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

object Avatar {
    fun bind(view: ImageView, name: String, url: String?) {
        val placeholder = InitialsDrawable(name)
        if (url.isNullOrBlank()) {
            Glide.with(view).clear(view)
            view.setImageDrawable(placeholder)
        } else {
            Glide.with(view).load(url).placeholder(placeholder).error(placeholder).circleCrop().into(view)
        }
    }
}
