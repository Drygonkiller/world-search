package com.drygonkiller.wordsearch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drygonkiller.wordsearch.data.PuzzleSession
import com.drygonkiller.wordsearch.game.Cell
import com.drygonkiller.wordsearch.game.Puzzle
import com.drygonkiller.wordsearch.game.WordPath
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PuzzleScreen(
    board: Puzzle,
    session: PuzzleSession,
    elapsedMillis: Long,
    hideTimer: Boolean,
    inputEnabled: Boolean,
    busy: Boolean,
    rewardPending: Boolean,
    onSelect: (WordPath) -> Unit,
    onPause: () -> Unit,
    onFreeHint: () -> Unit,
    onRewardedHint: () -> Unit,
    onRetrySave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val daily = session.spec.dailyDate != null
    val solved = session.found.size == board.words.size
    val palette = MaterialTheme.colorScheme
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    if (daily) "Daily Challenge" else session.spec.category.label,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (daily) session.spec.category.label + " · Medium" else session.spec.difficulty.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
            }
            if (!hideTimer) {
                Row(
                    Modifier.padding(end = 8.dp).semantics { contentDescription = "Elapsed time" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Glyph("clock", dimension = 18.dp, color = palette.onSurfaceVariant)
                    Text(formatTime(elapsedMillis), style = MaterialTheme.typography.labelLarge)
                }
            }
            NavigationControl("pause", "Pause", onPause, enabled = inputEnabled && !solved)
        }

        val hintStart = board.words.firstOrNull { it.word == session.hint }?.path?.start
        val foundTracks = session.found.values.toList()
        LetterGrid(board, foundTracks, hintStart, inputEnabled && !solved, onSelect)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Find the words", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                session.found.size.toString() + " / " + board.words.size,
                style = MaterialTheme.typography.labelLarge,
                color = palette.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val orderedWords = session.found.keys.toList()
            board.words.forEach { word ->
                val foundIndex = orderedWords.indexOf(word.word)
                val isFound = foundIndex >= 0
                val color = if (isFound) trackColor(foundIndex, palette).copy(alpha = 0.28f) else palette.surface
                Surface(color = color, shape = RoundedCornerShape(9.dp)) {
                    Text(
                        word.word,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            .semantics { if (isFound) contentDescription = word.word + ", found" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isFound) FontWeight.Normal else FontWeight.SemiBold,
                        textDecoration = if (isFound) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
        when {
            solved && busy -> LinearProgressIndicator(Modifier.fillMaxWidth())
            solved -> OutlinedButton(onRetrySave, Modifier.fillMaxWidth()) { Text("Retry save") }
            session.freeHintUsed -> OutlinedButton(
                onRewardedHint, Modifier.fillMaxWidth(), enabled = inputEnabled && !rewardPending,
            ) {
                Glyph("play", dimension = 17.dp, color = LocalContentColor.current)
                Spacer(Modifier.width(5.dp))
                Text(if (rewardPending) "Opening ad…" else "Watch ad for a hint")
            }
            else -> OutlinedButton(onFreeHint, Modifier.fillMaxWidth(), enabled = inputEnabled) {
                Glyph("hint", dimension = 17.dp, color = LocalContentColor.current)
                Spacer(Modifier.width(6.dp))
                Text("Use free hint")
            }
        }
    }
}

@Composable
private fun LetterGrid(
    board: Puzzle,
    foundTracks: List<WordPath>,
    hint: Cell?,
    inputEnabled: Boolean,
    onSelect: (WordPath) -> Unit,
) {
    val palette = MaterialTheme.colorScheme
    val select by rememberUpdatedState(onSelect)
    var selection by remember(board) { mutableStateOf<WordPath?>(null) }
    BoxWithConstraints(
        modifier = Modifier.widthIn(max = 490.dp).fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(19.dp)).background(palette.surface)
            .semantics { contentDescription = board.size.toString() + " by " + board.size + " letter grid" }
            .pointerInput(board, inputEnabled) {
                if (!inputEnabled) return@pointerInput
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val cellSize = size.width.toFloat() / board.size
                        val start = cellAt(down.position, cellSize, board.size)
                        var end = start
                        selection = WordPath(start, start)
                        down.consume()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            if (change.position != change.previousPosition) {
                                end = snappedCell(change.position, start, cellSize, board.size)
                                selection = WordPath(start, end)
                            }
                            change.consume()
                            if (!change.pressed) {
                                if (end != start) select(WordPath(start, end))
                                break
                            }
                        }
                        selection = null
                    }
                } finally {
                    selection = null
                }
            },
    ) {
        val font = (maxWidth.value / board.size * 0.54f).coerceAtMost(28f).sp
        Canvas(Modifier.matchParentSize()) {
            val unit = size.width / board.size
            fun center(cell: Cell) = Offset((cell.column + 0.5f) * unit, (cell.row + 0.5f) * unit)
            fun line(path: WordPath, color: Color) {
                if (path.start == path.end) {
                    drawCircle(color, unit * 0.34f, center(path.start))
                } else {
                    drawLine(color, center(path.start), center(path.end), unit * 0.68f, StrokeCap.Round)
                }
            }

            for (index in 1 until board.size) {
                val x = unit * index
                drawLine(palette.outlineVariant.copy(alpha = 0.48f), Offset(x, 0f), Offset(x, size.height))
                drawLine(palette.outlineVariant.copy(alpha = 0.48f), Offset(0f, x), Offset(size.width, x))
            }
            foundTracks.forEachIndexed { index, path -> line(path, trackColor(index, palette).copy(alpha = 0.43f)) }
            hint?.let { drawCircle(palette.tertiaryContainer, unit * 0.40f, center(it)) }
            selection?.let { line(it, palette.primary.copy(alpha = 0.34f)) }
        }
        Column(Modifier.fillMaxSize()) {
            board.grid.forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { letter ->
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text(letter.toString(), fontSize = font, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun cellAt(position: Offset, cellSize: Float, size: Int): Cell = Cell(
    (position.y / cellSize).toInt().coerceIn(0, size - 1),
    (position.x / cellSize).toInt().coerceIn(0, size - 1),
)

private val directions = listOf(
    Cell(0, 1), Cell(1, 1), Cell(1, 0), Cell(1, -1),
    Cell(0, -1), Cell(-1, -1), Cell(-1, 0), Cell(-1, 1),
)

private fun snappedCell(position: Offset, start: Cell, cellSize: Float, size: Int): Cell {
    if (cellAt(position, cellSize, size) == start) return start
    val x = position.x / cellSize - start.column - 0.5f
    val y = position.y / cellSize - start.row - 0.5f
    val angle = atan2(y, x)
    val direction = directions[(angle / (PI / 4)).roundToInt().mod(directions.size)]
    val row = direction.row
    val column = direction.column
    val projected = ((x * column + y * row) / (row * row + column * column)).roundToInt().coerceAtLeast(0)
    val rowLimit = if (row == 0) size else if (row > 0) size - 1 - start.row else start.row
    val columnLimit = if (column == 0) size else if (column > 0) size - 1 - start.column else start.column
    val steps = minOf(projected, rowLimit, columnLimit)
    return Cell(start.row + steps * row, start.column + steps * column)
}

private fun trackColor(index: Int, palette: ColorScheme): Color = when (index % 4) {
    0 -> palette.primary
    1 -> Color(0xFFB98B43)
    2 -> Color(0xFF6A91B1)
    else -> Color(0xFFB486AD)
}
