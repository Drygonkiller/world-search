package com.drygonkiller.wordsearch.game

import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

enum class Difficulty(val label: String, val gridSize: Int, val wordCount: Int) {
    EASY("Easy", 8, 6),
    MEDIUM("Medium", 10, 9),
    HARD("Hard", 12, 12),
}

enum class Category(val label: String, val asset: String, val symbol: String) {
    ANIMALS("Animals", "animals", "paw"),
    FOOD("Food & Drink", "food", "food"),
    TRAVEL("Travel", "travel", "travel"),
    SPORTS("Sports", "sports", "sports"),
    NATURE("Nature", "nature", "leaf"),
    COUNTRIES("Countries", "countries", "globe"),
    JOBS("Jobs", "jobs", "jobs"),
    HOUSE("House", "house", "home"),
    SCHOOL("School", "school", "book"),
    MOVIES("Movies", "movies", "film"),
    SCIENCE("Science", "science", "science"),
    HOLIDAYS("Holidays", "holidays", "star"),
}

data class Cell(val row: Int, val column: Int)

data class WordPath(val start: Cell, val end: Cell) {
    fun cells(): List<Cell> {
        val rows = end.row - start.row
        val columns = end.column - start.column
        require(rows == 0 || columns == 0 || abs(rows) == abs(columns))
        val rowStep = rows.compareTo(0)
        val columnStep = columns.compareTo(0)
        return List(maxOf(abs(rows), abs(columns)) + 1) { i ->
            Cell(start.row + i * rowStep, start.column + i * columnStep)
        }
    }
}

data class PlacedWord(val word: String, val path: WordPath)

data class Puzzle(val grid: List<String>, val words: List<PlacedWord>) {
    val size: Int get() = grid.size

    fun wordAt(path: WordPath): String = path.cells().map { grid[it.row][it.column] }.joinToString("")

    fun match(path: WordPath, found: Set<String>): String? {
        val selection = wordAt(path)
        // A generated path gets priority when two target words are reverses of each other.
        val placed = words.firstOrNull { it.path == path || it.path == WordPath(path.end, path.start) }
        if (placed != null && placed.word !in found) return placed.word
        return words.firstOrNull {
            it.word !in found && (it.word == selection || it.word.reversed() == selection)
        }?.word
    }
}

data class PuzzleSpec(
    val category: Category,
    val difficulty: Difficulty,
    val seed: Long,
    val dailyDate: LocalDate? = null,
)

fun dailySpec(date: LocalDate): PuzzleSpec = PuzzleSpec(
    category = Category.entries[(date.dayOfYear - 1) % Category.entries.size],
    difficulty = Difficulty.MEDIUM,
    seed = date.toEpochDay(),
    dailyDate = date,
)

fun makePuzzle(spec: PuzzleSpec, categoryWords: List<String>): Puzzle {
    val difficulty = spec.difficulty
    val words = categoryWords.distinct().filter { it.length <= difficulty.gridSize }
    require(words.size >= difficulty.wordCount) { "Insufficient words for " + spec.category.label }
    val selected = words.shuffled(Random(spec.seed)).take(difficulty.wordCount)
    return WordSearchGenerator.generate(difficulty.gridSize, selected, spec.seed)
}

fun pickHint(puzzle: Puzzle, found: Set<String>, random: Random = Random.Default): PlacedWord? =
    puzzle.words.filterNot { it.word in found }.randomOrNull(random)

data class Streak(val current: Int = 0, val best: Int = 0, val lastDay: LocalDate? = null)

fun updateStreak(previous: Streak, date: LocalDate): Streak {
    val lastDay = previous.lastDay
    if (lastDay != null && date <= lastDay) return previous
    val current = if (lastDay?.plusDays(1) == date) previous.current + 1 else 1
    return Streak(current, maxOf(previous.best, current), date)
}

private const val INTERSTITIAL_INTERVAL_MILLIS = 180_000L

fun shouldShowInterstitial(completedCount: Int, lastShownMillis: Long?, nowMillis: Long): Boolean =
    completedCount >= 3 && completedCount % 3 == 0 &&
        (lastShownMillis == null ||
            (nowMillis >= lastShownMillis && nowMillis - lastShownMillis >= INTERSTITIAL_INTERVAL_MILLIS))
