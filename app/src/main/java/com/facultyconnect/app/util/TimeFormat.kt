package com.facultyconnect.app.util

import android.text.format.DateUtils
import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One place for all date/time formatting so every screen shows the same thing. */
object TimeFormat {
    private val zone get() = ZoneId.systemDefault()
    private val dateFmt get() = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.getDefault())
    private val timeFmt get() = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
    private val shortDateFmt get() = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
    private val dateTimeFmt get() = DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault())

    private fun Timestamp.zoned() = toDate().toInstant().atZone(zone)

    fun date(ts: Timestamp?): String = ts?.let { dateFmt.format(it.zoned()) } ?: "-"
    fun time(ts: Timestamp?): String = ts?.let { timeFmt.format(it.zoned()) } ?: "-"
    fun range(start: Timestamp?, end: Timestamp?): String = "${time(start)} - ${time(end)}"
    fun localDate(ts: Timestamp?): LocalDate? = ts?.zoned()?.toLocalDate()

    fun relative(ts: Timestamp?): String =
        if (ts == null) "just now"
        else DateUtils.getRelativeTimeSpanString(ts.toDate().time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    fun chatTime(ts: Timestamp?): String {
        if (ts == null) return "sending..."
        val z = ts.zoned()
        return if (z.toLocalDate() == LocalDate.now()) timeFmt.format(z) else dateTimeFmt.format(z)
    }

    fun listTime(ts: Timestamp?): String {
        if (ts == null) return "now"
        val z = ts.zoned()
        val today = LocalDate.now()
        return when (z.toLocalDate()) {
            today -> timeFmt.format(z)
            today.minusDays(1) -> "Yesterday"
            else -> shortDateFmt.format(z)
        }
    }
}
