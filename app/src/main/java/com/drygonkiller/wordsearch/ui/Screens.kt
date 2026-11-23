package com.drygonkiller.wordsearch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drygonkiller.wordsearch.data.Completion
import com.drygonkiller.wordsearch.data.GameSettings
import com.drygonkiller.wordsearch.data.PlayerProfile
import com.drygonkiller.wordsearch.data.PuzzleSession
import com.drygonkiller.wordsearch.data.Setting
import com.drygonkiller.wordsearch.game.Category
import com.drygonkiller.wordsearch.game.Difficulty
import com.drygonkiller.wordsearch.game.dailySpec
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val cardShape = RoundedCornerShape(24.dp)
private val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

@Composable
fun HomeScreen(
    profile: PlayerProfile,
    today: LocalDate,
    dailyDone: Boolean,
    hasActivePuzzle: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
    onDaily: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Word Search", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            CoinBadge(profile.coins)
        }
        val panel = Color(0xFF204D3D)
        val ink = Color(0xFFF5FAF3)
        Box(Modifier.fillMaxWidth().clip(cardShape).background(panel)) {
            HeroPattern(Modifier.matchParentSize())
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (hasActivePuzzle) "In progress" else "Quick play",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ink,
                )
                Text(
                    if (hasActivePuzzle) "Pick up where you left off" else "Easy · 8 × 8 · 6 words",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD2E5D7),
                )
                Button(
                    onClick = onPlay,
                    enabled = !busy,
                    modifier = Modifier.padding(top = 7.dp).heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD8EDDF), contentColor = panel),
                ) {
                    Glyph("play", color = LocalContentColor.current, dimension = 19.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(if (hasActivePuzzle) "Resume puzzle" else "Play")
                }
            }
        }

        Card(
            onClick = onDaily,
            enabled = !dailyDone && !busy,
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                disabledContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (dailyDone) "check" else "calendar", warm = true)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Daily Challenge", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (dailyDone) "Completed today" else today.format(dateFormat) + " · " + dailySpec(today).category.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!dailyDone) Glyph("next", dimension = 20.dp, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(days(profile.currentStreak) + " streak", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (dailyDone) "See you tomorrow" else "+20 coins",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPattern(modifier: Modifier) {
    Canvas(modifier) {
        val spacing = size.minDimension * 0.19f
        val x = size.width - spacing * 3.3f
        for (row in 0..5) {
            for (column in 0..4) {
                val center = Offset(x + spacing * column, spacing * row)
                drawCircle(Color(0xFF83AA93).copy(alpha = 0.30f), spacing * 0.09f, center)
            }
        }
        drawLine(
            Color(0xFFBEDDCA).copy(alpha = 0.29f),
            Offset(x + spacing, spacing), Offset(x + spacing * 3, spacing * 3),
            strokeWidth = spacing * 0.35f, cap = StrokeCap.Round,
        )
        drawLine(
            Color(0xFFDDC28A).copy(alpha = 0.38f),
            Offset(x + spacing, spacing * 4), Offset(x + spacing * 3, spacing * 4),
            strokeWidth = spacing * 0.30f, cap = StrokeCap.Round,
        )
    }
}

@Composable
fun CategoriesScreen(
    counts: Map<String, Int>,
    busy: Boolean,
    onCategory: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        Text("Categories", style = MaterialTheme.typography.displaySmall)
        Category.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { category ->
                    Card(
                        onClick = { onCategory(category) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = cardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconBadge(category.symbol, warm = category.ordinal % 3 == 1)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(category.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    (counts[category.name] ?: 0).toString() + " completed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DifficultyScreen(
    category: Category,
    busy: Boolean,
    onBack: () -> Unit,
    select: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavigationControl("back", "Back", onBack)
            Spacer(Modifier.width(9.dp))
            Text(category.label, style = MaterialTheme.typography.displaySmall)
        }
        Text("Choose difficulty", style = MaterialTheme.typography.titleMedium)
        Difficulty.entries.forEach { difficulty ->
            Card(
                onClick = { select(difficulty) }, enabled = !busy, shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge("grid", warm = difficulty == Difficulty.HARD)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(difficulty.label, style = MaterialTheme.typography.titleMedium)
                        Text(boardDetails(difficulty), style = MaterialTheme.typography.bodyMedium)
                    }
                    Glyph("next", dimension = 21.dp)
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
fun StatsScreen(
    profile: PlayerProfile,
    today: LocalDate,
    dailyDates: Set<String>,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        Text("Stats", style = MaterialTheme.typography.displaySmall)
        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(Modifier.fillMaxWidth().padding(21.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Coins", style = MaterialTheme.typography.titleMedium)
                    Text(profile.coins.toString(), style = MaterialTheme.typography.headlineMedium)
                }
                IconBadge("coin", warm = true)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Puzzles completed", profile.completedCount.toString(), Modifier.weight(1f))
            Metric("Words found", profile.wordsFound.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Current streak", days(profile.currentStreak), Modifier.weight(1f))
            Metric("Best streak", days(profile.bestStreak), Modifier.weight(1f))
        }
        StreakCalendar(today, dailyDates)
    }
}

@Composable
fun SettingsScreen(
    settings: GameSettings,
    privacyRequired: Boolean,
    onToggle: (Setting, Boolean) -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        Text("Settings", style = MaterialTheme.typography.displaySmall)
        Card(shape = cardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Setting.entries.forEachIndexed { index, setting ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(settings[setting], role = Role.Switch, onValueChange = { onToggle(setting, it) })
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(setting.label, style = MaterialTheme.typography.titleMedium)
                        Text(setting.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = settings[setting], onCheckedChange = null)
                }
            }
        }
        if (privacyRequired) OutlinedButton(onPrivacy, Modifier.fillMaxWidth()) { Text("Ad privacy options") }
    }
}

@Composable
fun FinishScreen(
    session: PuzzleSession,
    result: Completion,
    busy: Boolean,
    onNext: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { IconBadge("check", warm = true) }
        Text("Puzzle complete", style = MaterialTheme.typography.displaySmall)
        Text(
            if (session.spec.dailyDate != null) "Daily Challenge · " + session.spec.category.label
            else session.spec.category.label + " · " + session.spec.difficulty.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Time", formatTime(session.elapsedMillis), Modifier.weight(1f))
            Metric("Coins earned", "+" + result.coinsAwarded, Modifier.weight(1f))
        }
        Button(onNext, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !busy) { Text("Next Puzzle") }
        OutlinedButton(onHome, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("Home") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = cardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CoinBadge(coins: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            Modifier.semantics { contentDescription = coins.toString() + " coins" }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Glyph("coin", dimension = 19.dp, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(coins.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IconBadge(glyph: String, warm: Boolean = false) {
    val palette = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = if (warm) palette.tertiaryContainer else palette.primaryContainer) {
        Box(Modifier.size(43.dp), contentAlignment = Alignment.Center) {
            Glyph(glyph, dimension = 22.dp, color = if (warm) palette.onTertiaryContainer else palette.onPrimaryContainer)
        }
    }
}

@Composable
fun NavigationControl(glyph: String, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        Glyph(glyph, dimension = 22.dp, color = LocalContentColor.current)
    }
}

@Composable
private fun ScreenColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
        content = content,
    )
}

private fun days(count: Int): String = count.toString() + if (count == 1) " day" else " days"

private fun boardDetails(difficulty: Difficulty): String {
    val size = difficulty.gridSize.toString()
    return "$size × $size · " + difficulty.wordCount + " words"
}

fun formatTime(elapsedMillis: Long): String {
    val seconds = elapsedMillis / 1_000
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
