package com.facultyconnect.app.ui.common

import com.facultyconnect.app.R
import com.facultyconnect.app.databinding.ViewStateBinding
import com.facultyconnect.app.util.show

/** Wraps the shared loading / empty / error overlay (layout view_state). */
class StateView(private val b: ViewStateBinding) {

    fun loading() {
        b.root.show(true)
        b.stateProgress.show(true)
        b.stateIcon.show(false); b.stateTitle.show(false); b.stateMessage.show(false); b.stateRetry.show(false)
    }

    fun message(iconRes: Int, title: String, message: String? = null, onRetry: (() -> Unit)? = null) {
        b.root.show(true)
        b.stateProgress.show(false)
        b.stateIcon.show(true); b.stateIcon.setImageResource(iconRes)
        b.stateTitle.show(true); b.stateTitle.text = title
        b.stateMessage.show(!message.isNullOrBlank()); b.stateMessage.text = message
        b.stateRetry.show(onRetry != null)
        b.stateRetry.setOnClickListener { onRetry?.invoke() }
    }

    fun empty(title: String, message: String? = null) = message(R.drawable.ic_search, title, message)

    fun error(message: String, onRetry: () -> Unit) = message(R.drawable.ic_cloud_off, "Something went wrong", message, onRetry)

    fun hide() = b.root.show(false)
}
