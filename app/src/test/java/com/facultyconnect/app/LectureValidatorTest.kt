package com.facultyconnect.app

import com.facultyconnect.app.domain.LectureField
import com.facultyconnect.app.domain.LectureInput
import com.facultyconnect.app.domain.LectureValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LectureValidatorTest {
    private val now = LocalDateTime.of(2026, 10, 24, 12, 0)
    private val today = now.toLocalDate()

    private fun input(
        subject: String = "Operating Systems", cls: String = "TE-A", room: String = "R-301",
        reason: String = "Attending a conference", date: LocalDate? = today.plusDays(1),
        start: LocalTime? = LocalTime.of(10, 0), end: LocalTime? = LocalTime.of(11, 0)
    ) = LectureInput(subject, cls, room, reason, date, start, end)

    @Test fun validInputPasses() = assertTrue(LectureValidator.validate(input(), now).isValid)

    @Test fun blankRequiredFieldsAreRejected() {
        val r = LectureValidator.validate(input(subject = " ", cls = "", room = "", reason = ""), now)
        assertEquals(
            setOf(LectureField.SUBJECT, LectureField.CLASS, LectureField.ROOM, LectureField.REASON),
            r.errors.keys
        )
    }

    @Test fun missingDateAndTimesAreRejected() {
        val r = LectureValidator.validate(input(date = null, start = null, end = null), now)
        assertTrue(r.errors.containsKey(LectureField.DATE))
        assertTrue(r.errors.containsKey(LectureField.START))
        assertTrue(r.errors.containsKey(LectureField.END))
    }

    @Test fun endMustBeAfterStart() {
        val equal = LectureValidator.validate(input(start = LocalTime.of(10, 0), end = LocalTime.of(10, 0)), now)
        val before = LectureValidator.validate(input(start = LocalTime.of(10, 0), end = LocalTime.of(9, 0)), now)
        assertTrue(equal.errors.containsKey(LectureField.END))
        assertTrue(before.errors.containsKey(LectureField.END))
    }

    @Test fun pastDateIsRejected() {
        val r = LectureValidator.validate(input(date = today.minusDays(1)), now)
        assertEquals("Date cannot be in the past", r.errors[LectureField.DATE])
    }

    @Test fun todayLaterIsAllowedButAlreadyEndedTodayIsRejected() {
        assertTrue(LectureValidator.validate(input(date = today, start = LocalTime.of(14, 0), end = LocalTime.of(15, 0)), now).isValid)
        val ended = LectureValidator.validate(input(date = today, start = LocalTime.of(8, 0), end = LocalTime.of(9, 0)), now)
        assertEquals("This lecture has already ended", ended.errors[LectureField.END])
    }

    @Test fun lengthLimitsAreEnforced() {
        assertFalse(LectureValidator.validate(input(subject = "x".repeat(101)), now).isValid)
        assertFalse(LectureValidator.validate(input(reason = "x".repeat(501)), now).isValid)
        assertTrue(LectureValidator.validate(input(subject = "x".repeat(100), reason = "x".repeat(500)), now).isValid)
    }
}
