package com.drygonkiller.wordsearch.game

import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {
    @Test
    fun bundledListsHaveAtLeast150DistinctValidWords() {
        assertEquals(12, Category.entries.size)
        Category.entries.forEach { category ->
            val words = wordsFor(category)
            assertTrue(category.label, words.size >= 150)
            assertEquals(category.label, words.size, words.toSet().size)
            assertTrue(category.label, words.all { it.matches(Regex("[A-Z]{3,10}")) })
        }
    }

    @Test
    fun everyDifficultyFillsTheGridAndPlacesEveryWord() {
        val directionsSeen = mutableSetOf<Pair<Int, Int>>()
        for (category in Category.entries) {
            val wordList = wordsFor(category)
            for (difficulty in Difficulty.entries) {
                for (seed in 0L..4L) {
                    val puzzle = makePuzzle(PuzzleSpec(category, difficulty, seed), wordList)
                    assertEquals(difficulty.gridSize, puzzle.size)
                    assertEquals(difficulty.wordCount, puzzle.words.size)
                    assertEquals(difficulty.wordCount, puzzle.words.distinctBy { it.word }.size)
                    assertTrue(puzzle.grid.all { row ->
                        row.length == difficulty.gridSize && row.all { it in 'A'..'Z' }
                    })
                    puzzle.words.forEach { placed ->
                        assertEquals(placed.word, puzzle.wordAt(placed.path))
                        val backward = WordPath(placed.path.end, placed.path.start)
                        assertEquals(placed.word.reversed(), puzzle.wordAt(backward))
                        val row = (placed.path.end.row - placed.path.start.row).compareTo(0)
                        val column = (placed.path.end.column - placed.path.start.column).compareTo(0)
                        directionsSeen += row to column
                    }
                }
            }
        }
        assertEquals(8, directionsSeen.size)
    }

    @Test
    fun dailyPuzzleMatchesDateAndRotatesCategory() {
        val date = LocalDate.of(2026, 11, 19)
        val today = dailySpec(date)
        val sameDate = dailySpec(date)
        val tomorrow = dailySpec(date.plusDays(1))
        assertEquals(today, sameDate)
        assertEquals(Difficulty.MEDIUM, today.difficulty)
        assertEquals(Category.entries[(date.dayOfYear - 1) % 12], today.category)
        assertNotEquals(today.category, tomorrow.category)
        val puzzle = makePuzzle(today, wordsFor(today.category))
        val repeated = makePuzzle(sameDate, wordsFor(sameDate.category))
        val next = makePuzzle(tomorrow, wordsFor(tomorrow.category))
        assertEquals(puzzle.grid, repeated.grid)
        assertEquals(puzzle.words, repeated.words)
        assertNotEquals(puzzle.grid, next.grid)
        assertNotEquals(puzzle.words, next.words)
    }

    @Test
    fun hintOnlyPicksWordsThatRemain() {
        val puzzle = makePuzzle(PuzzleSpec(Category.ANIMALS, Difficulty.EASY, 91), wordsFor(Category.ANIMALS))
        val words = puzzle.words.map { it.word }.toSet()
        val unfound = puzzle.words.takeLast(2).map { it.word }.toSet()
        val found = words - unfound
        repeat(64) { assertTrue(pickHint(puzzle, found, Random(it))?.word in unfound) }
        assertNull(pickHint(puzzle, words, Random(0)))
    }

    @Test
    fun puzzleMatchesEitherDragDirectionAndIgnoresFoundWords() {
        val puzzle = WordSearchGenerator.generate(8, listOf("HORSE"), 17)
        val path = puzzle.words.single().path
        assertEquals("HORSE", puzzle.match(path, emptySet()))
        assertEquals("HORSE", puzzle.match(WordPath(path.end, path.start), emptySet()))
        assertNull(puzzle.match(path, setOf("HORSE")))
        assertNull(puzzle.match(WordPath(path.start, path.start), emptySet()))
    }

    @Test
    fun interstitialRequiresEveryThirdCompletionAndThreeMinuteCooldown() {
        val last = 1_000L
        assertFalse(shouldShowInterstitial(0, null, 900_000))
        assertFalse(shouldShowInterstitial(1, null, 900_000))
        assertFalse(shouldShowInterstitial(2, null, 900_000))
        assertTrue(shouldShowInterstitial(3, null, 5_000))
        assertTrue(shouldShowInterstitial(3, last, last + 180_000))
        assertFalse(shouldShowInterstitial(3, last, last + 179_999))
        assertFalse(shouldShowInterstitial(4, last, last + 180_000))
        assertFalse(shouldShowInterstitial(5, last, last + 180_000))
        assertTrue(shouldShowInterstitial(6, last, last + 180_000))
        assertFalse(shouldShowInterstitial(6, last, last - 1))
    }

    @Test
    fun streakCountsConsecutiveDaysOnceAndPreservesBest() {
        val date = LocalDate.of(2026, 12, 31)
        val first = updateStreak(Streak(), date)
        assertEquals(Streak(1, 1, date), first)
        assertEquals(first, updateStreak(first, date))
        val nextDate = date.plusDays(1)
        val next = updateStreak(first, nextDate)
        assertEquals(Streak(2, 2, nextDate), next)
        assertEquals(next, updateStreak(next, nextDate))
        val missed = nextDate.plusDays(2)
        assertEquals(Streak(1, 2, missed), updateStreak(next, missed))
        assertEquals(next, updateStreak(next, date))
    }

    private fun wordsFor(category: Category): List<String> =
        checkNotNull(javaClass.getResourceAsStream("/words/" + category.asset + ".txt"))
            .bufferedReader().use { it.readLines() }
}
