package com.drygonkiller.wordsearch.game

import com.drygonkiller.wordsearch.data.PlayerProfile

const val HINT_COST_COINS = 50

data class HintPurchase(val remainingCoins: Int, val revealedWord: String? = null)

fun spendHintCoins(balance: Int, hintWord: String?): HintPurchase =
    if (balance >= HINT_COST_COINS && hintWord != null) {
        HintPurchase(balance - HINT_COST_COINS, hintWord)
    } else {
        HintPurchase(balance)
    }

fun chestBonus(newCompletedCount: Int, newlyCompleted: Boolean): Int =
    if (newlyCompleted && newCompletedCount > 0 && newCompletedCount % 10 == 0) 50 else 0

fun masteryStars(easyCount: Int, mediumCount: Int, hardCount: Int, expertCount: Int): Int {
    val hardOrAbove = hardCount + expertCount
    val mediumOrAbove = mediumCount + hardOrAbove
    return when {
        hardOrAbove >= 3 -> 3
        mediumOrAbove >= 3 -> 2
        easyCount + mediumOrAbove >= 3 -> 1
        else -> 0
    }
}

enum class Achievement(val title: String, val target: String) {
    FIRST_FIND("First Find", "Complete 1 puzzle"),
    WORD_HUNTER("Word Hunter", "Find 500 words"),
    PUZZLE_PRO("Puzzle Pro", "Complete 100 puzzles"),
    ON_FIRE("On Fire", "Reach a 7-day streak"),
    UNSTOPPABLE("Unstoppable", "Reach a 30-day streak"),
    DAILY_DEVOTEE("Daily Devotee", "Complete 10 Daily Challenges"),
    EXPLORER("Explorer", "Complete a puzzle in all 12 categories"),
    SHARPSHOOTER("Sharpshooter", "Complete 1 Expert puzzle"),
}

fun achievements(
    profile: PlayerProfile,
    perCategoryCounts: Map<Category, Int>,
    perDifficultyCounts: Map<Difficulty, Int>,
    dailyCount: Int,
): Set<Achievement> = buildSet {
    if (profile.completedCount >= 1) add(Achievement.FIRST_FIND)
    if (profile.wordsFound >= 500) add(Achievement.WORD_HUNTER)
    if (profile.completedCount >= 100) add(Achievement.PUZZLE_PRO)
    if (profile.bestStreak >= 7) add(Achievement.ON_FIRE)
    if (profile.bestStreak >= 30) add(Achievement.UNSTOPPABLE)
    if (dailyCount >= 10) add(Achievement.DAILY_DEVOTEE)
    if (Category.entries.all { perCategoryCounts.getOrDefault(it, 0) > 0 }) add(Achievement.EXPLORER)
    if (perDifficultyCounts.getOrDefault(Difficulty.EXPERT, 0) > 0) add(Achievement.SHARPSHOOTER)
}

fun isReviewMilestone(completedCount: Int, newlyCompleted: Boolean, daily: Boolean): Boolean =
    completedCount == 10 && newlyCompleted && !daily
