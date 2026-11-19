package com.drygonkiller.wordsearch.game

import kotlin.random.Random

object WordSearchGenerator {
    private val directions = listOf(
        Cell(0, 1), Cell(0, -1), Cell(1, 0), Cell(-1, 0),
        Cell(1, 1), Cell(1, -1), Cell(-1, 1), Cell(-1, -1),
    )

    fun generate(size: Int, words: List<String>, seed: Long): Puzzle {
        require(size > 0)
        require(words.distinct().size == words.size) { "Word list must be unique" }
        require(words.all { word -> word.length in 1..size && word.all { it in 'A'..'Z' } }) {
            "Words must be uppercase A–Z and fit the grid"
        }

        val random = Random(seed)
        val longestFirst = words.sortedByDescending(String::length)
        val board = Array(size) { CharArray(size) }
        val placed = mutableMapOf<String, WordPath>()
        val pathsByLength = words.map(String::length).distinct().associateWith { length ->
            buildList {
                for (row in 0 until size) {
                    for (column in 0 until size) {
                        for (direction in directions) {
                            val end = Cell(row + (length - 1) * direction.row, column + (length - 1) * direction.column)
                            if (end.row in 0 until size && end.column in 0 until size) {
                                add(WordPath(Cell(row, column), end).cells())
                            }
                        }
                    }
                }
            }
        }

        fun place(word: String, cells: List<Cell>): List<Cell>? {
            if (cells.withIndex().any { (i, cell) ->
                    val letter = board[cell.row][cell.column]
                    letter != '\u0000' && letter != word[i]
                }
            ) return null
            val changed = cells.filter { board[it.row][it.column] == '\u0000' }
            cells.forEachIndexed { i, cell -> board[cell.row][cell.column] = word[i] }
            placed[word] = WordPath(cells.first(), cells.last())
            return changed
        }

        fun search(index: Int, candidates: Map<Int, List<List<Cell>>>, budget: SearchBudget): Boolean {
            if (index == longestFirst.size) return true
            if (budget.remaining != null) {
                if (budget.remaining == 0) return false
                budget.remaining = budget.remaining?.minus(1)
            }

            val word = longestFirst[index]
            for (cells in candidates.getValue(word.length)) {
                val changed = place(word, cells) ?: continue
                if (search(index + 1, candidates, budget)) return true
                changed.forEach { board[it.row][it.column] = '\u0000' }
                placed.remove(word)
                if (budget.remaining == 0) return false
            }
            return false
        }

        repeat(8) {
            val candidates = pathsByLength.mapValues { (_, paths) -> paths.shuffled(random) }
            if (search(0, candidates, SearchBudget(128))) return finish(board, words, placed, random)
        }

        // Every game difficulty has at most one word per row. This guarantees a layout even
        // when no initial search budget finds one with a mix of diagonals and overlaps.
        if (words.size <= size) {
            words.zip((0 until size).shuffled(random)).forEach { (word, row) ->
                val left = random.nextInt(size - word.length + 1)
                val cells = List(word.length) { Cell(row, left + it) }
                place(word, if (random.nextBoolean()) cells else cells.reversed())
            }
        } else {
            val candidates = pathsByLength.mapValues { (_, paths) -> paths.shuffled(random) }
            require(search(0, candidates, SearchBudget(null))) { "Words cannot fit this grid" }
        }
        return finish(board, words, placed, random)
    }

    private fun finish(
        board: Array<CharArray>,
        words: List<String>,
        placed: Map<String, WordPath>,
        random: Random,
    ): Puzzle {
        val grid = board.map { row ->
            CharArray(row.size) { i -> row[i].takeUnless { it == '\u0000' } ?: ('A' + random.nextInt(26)) }
                .concatToString()
        }
        return Puzzle(grid, words.map { PlacedWord(it, placed.getValue(it)) })
    }

    private data class SearchBudget(var remaining: Int?)
}
