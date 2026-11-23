package com.drygonkiller.wordsearch.game

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakCalendarTest {
    @Test
    fun februaryHas29DaysInLeapYears() {
        val days = buildStreakCalendar(LocalDate.of(2024, 2, 12), emptySet()).filterNotNull()

        assertEquals(29, days.size)
        assertEquals(LocalDate.of(2024, 2, 29), days.last().date)
    }

    @Test
    fun februaryHas28DaysInCommonYears() {
        val days = buildStreakCalendar(LocalDate.of(2025, 2, 12), emptySet()).filterNotNull()

        assertEquals(28, days.size)
        assertEquals(LocalDate.of(2025, 2, 28), days.last().date)
    }

    @Test
    fun marksOnlyCompletionsInThisMonth() {
        val days = buildStreakCalendar(
            today = LocalDate.of(2024, 2, 12),
            completedDailyDates = setOf("2024-01-31", "2024-02-01", "2024-02-29", "2024-03-01"),
        ).filterNotNull()

        assertEquals(listOf(1, 29), days.filter { it.completed }.map { it.date.dayOfMonth })
        assertTrue(days.first().completed)
        assertFalse(days[1].completed)
    }

    @Test
    fun todayIsStillMarkedWhenItIsAlsoComplete() {
        val today = LocalDate.of(2024, 2, 12)
        val days = buildStreakCalendar(today, setOf(today.toString())).filterNotNull()

        val markedToday = days.single { it.isToday }
        assertEquals(today, markedToday.date)
        assertTrue(markedToday.completed)
    }

    @Test
    fun leadingBlanksAlignTheFirstDayToItsWeekday() {
        val sundayFirst = buildStreakCalendar(LocalDate.of(2026, 2, 12), emptySet())
        val mondayFirst = buildStreakCalendar(LocalDate.of(2026, 6, 12), emptySet())

        assertEquals(6, sundayFirst.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 2, 1), sundayFirst[6]?.date)
        assertEquals(LocalDate.of(2026, 6, 1), mondayFirst.first()?.date)
    }
}
