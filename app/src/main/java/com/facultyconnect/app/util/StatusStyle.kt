package com.facultyconnect.app.util

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.facultyconnect.app.R
import com.facultyconnect.app.model.PostStatus
import com.facultyconnect.app.model.PostType

/** Status and type are always shown as WORDS plus colour, never colour alone. */
object StatusStyle {
    fun bindStatus(tv: TextView, status: PostStatus) {
        val color = when (status) {
            PostStatus.AVAILABLE -> R.color.status_available
            PostStatus.ASSIGNED -> R.color.status_assigned
            PostStatus.CLOSED -> R.color.status_closed
        }
        tv.text = status.label.uppercase()
        ViewCompat.setBackgroundTintList(tv, ColorStateList.valueOf(ContextCompat.getColor(tv.context, color)))
    }

    fun bindType(tv: TextView, type: PostType) {
        val color = if (type == PostType.REPLACEMENT) R.color.type_replacement else R.color.type_extra
        tv.text = type.label.uppercase()
        ViewCompat.setBackgroundTintList(tv, ColorStateList.valueOf(ContextCompat.getColor(tv.context, color)))
    }

    fun bindPill(tv: TextView, text: String, colorRes: Int) {
        tv.text = text.uppercase()
        ViewCompat.setBackgroundTintList(tv, ColorStateList.valueOf(ContextCompat.getColor(tv.context, colorRes)))
    }
}
