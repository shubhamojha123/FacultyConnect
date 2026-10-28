package com.facultyconnect.app.util

import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.util.Locale

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun TextView.str(): String = text?.toString()?.trim().orEmpty()

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

fun Fragment.toast(msg: String) {
    context?.toast(msg)
}

fun String.normalizedEmail(): String = trim().lowercase(Locale.ROOT)

fun parseSubjects(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20)
