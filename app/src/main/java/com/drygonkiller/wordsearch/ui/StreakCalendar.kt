package com.drygonkiller.wordsearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drygonkiller.wordsearch.game.CalendarDay
import com.drygonkiller.wordsearch.game.buildStreakCalendar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)
private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun StreakCalendar(today: LocalDate, dailyDates: Set<String>, modifier: Modifier = Modifier) {
    val days = remember(today, dailyDates) { buildStreakCalendar(today, dailyDates) }
    val palette = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Streak calendar", style = MaterialTheme.typography.titleMedium)
            Text(today.format(monthFormatter), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                weekdays.forEach { weekday ->
                    Text(
                        weekday,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            days.chunked(weekdays.size).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        if (day == null) Spacer(Modifier.weight(1f))
                        else DayCell(day, Modifier.weight(1f))
                    }
                    repeat(weekdays.size - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val outline = if (day.isToday) Modifier.border(2.dp, palette.primary, shape) else Modifier
    val background = if (day.completed) palette.primaryContainer else Color.Transparent

    Column(
        modifier.height(46.dp).background(background, shape).then(outline).clearAndSetSemantics {
            contentDescription = buildString {
                append(day.date.format(dayFormatter))
                if (day.isToday) append(", today")
                if (day.completed) append(", Daily Challenge completed")
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (day.completed) palette.onPrimaryContainer else palette.onSurface,
        )
        if (day.completed) {
            Glyph("check", color = palette.onPrimaryContainer, dimension = 12.dp)
        }
    }
}
