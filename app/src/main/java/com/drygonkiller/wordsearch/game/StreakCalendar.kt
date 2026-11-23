package com.drygonkiller.wordsearch.game

import java.time.LocalDate
import java.time.YearMonth

data class CalendarDay(val date: LocalDate, val completed: Boolean, val isToday: Boolean)

/** Monday-first calendar cells for today's month. Null cells represent the leading blank days. */
fun buildStreakCalendar(today: LocalDate, completedDailyDates: Set<String>): List<CalendarDay?> {
    val month = YearMonth.from(today)
    val leadingBlankCount = month.atDay(1).dayOfWeek.value - 1

    return buildList {
        repeat(leadingBlankCount) { add(null) }
        for (dayOfMonth in 1..month.lengthOfMonth()) {
            val date = month.atDay(dayOfMonth)
            add(CalendarDay(date, date.toString() in completedDailyDates, date == today))
        }
    }
}
