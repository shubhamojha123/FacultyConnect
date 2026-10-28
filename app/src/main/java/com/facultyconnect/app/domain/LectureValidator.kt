package com.facultyconnect.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class LectureField { SUBJECT, CLASS, ROOM, REASON, DATE, START, END }

data class LectureInput(
    val subject: String,
    val className: String,
    val room: String,
    val reason: String,
    val date: LocalDate?,
    val start: LocalTime?,
    val end: LocalTime?
)

data class LectureValidation(val errors: Map<LectureField, String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Pure Kotlin (no Android types) so it can be unit-tested on the JVM. */
object LectureValidator {
    const val MAX_SUBJECT = 100
    const val MAX_CLASS = 60
    const val MAX_ROOM = 40
    const val MAX_REASON = 500

    fun validate(input: LectureInput, now: LocalDateTime): LectureValidation {
        val e = linkedMapOf<LectureField, String>()
        val subject = input.subject.trim()
        val cls = input.className.trim()
        val room = input.room.trim()
        val reason = input.reason.trim()

        if (subject.isEmpty()) e[LectureField.SUBJECT] = "Subject is required"
        else if (subject.length > MAX_SUBJECT) e[LectureField.SUBJECT] = "Maximum $MAX_SUBJECT characters"

        if (cls.isEmpty()) e[LectureField.CLASS] = "Class / division is required"
        else if (cls.length > MAX_CLASS) e[LectureField.CLASS] = "Maximum $MAX_CLASS characters"

        if (room.isEmpty()) e[LectureField.ROOM] = "Classroom or location is required"
        else if (room.length > MAX_ROOM) e[LectureField.ROOM] = "Maximum $MAX_ROOM characters"

        if (reason.isEmpty()) e[LectureField.REASON] = "Please add a reason or details"
        else if (reason.length > MAX_REASON) e[LectureField.REASON] = "Maximum $MAX_REASON characters"

        if (input.date == null) e[LectureField.DATE] = "Choose a date"
        else if (input.date.isBefore(now.toLocalDate())) e[LectureField.DATE] = "Date cannot be in the past"

        if (input.start == null) e[LectureField.START] = "Choose a start time"
        if (input.end == null) e[LectureField.END] = "Choose an end time"

        if (input.start != null && input.end != null && !input.end.isAfter(input.start)) {
            e[LectureField.END] = "End time must be after start time"
        }
        // Same-day lecture that has already finished.
        if (input.date != null && input.end != null && !e.containsKey(LectureField.DATE) &&
            !e.containsKey(LectureField.END) && LocalDateTime.of(input.date, input.end).isBefore(now)
        ) {
            e[LectureField.END] = "This lecture has already ended"
        }
        return LectureValidation(e)
    }
}
