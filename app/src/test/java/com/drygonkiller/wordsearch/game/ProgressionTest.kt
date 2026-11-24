package com.drygonkiller.wordsearch.game

import com.drygonkiller.wordsearch.data.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    @Test
    fun exactlyFiftyCoinsBuysAWordAndLeavesZeroCoins() {
        assertEquals(50, HINT_COST_COINS)
        assertEquals(HintPurchase(0, "HORSE"), spendHintCoins(50, "HORSE"))
    }

    @Test
    fun fortyNineCoinsCannotRevealAWordOrChargeCoins() {
        assertEquals(HintPurchase(49), spendHintCoins(49, "HORSE"))
    }

    @Test
    fun anotherPurchaseWorksAfterEarningCoins() {
        val first = spendHintCoins(50, "HORSE")
        val afterEarningMore = first.remainingCoins + 60
        assertEquals(HintPurchase(10, "OTTER"), spendHintCoins(afterEarningMore, "OTTER"))
    }

    @Test
    fun aMissingHintNeverChargesCoins() {
        assertEquals(HintPurchase(50), spendHintCoins(50, null))
    }

    @Test
    fun onlyNewCompletionMultiplesOfTenAwardTheChest() {
        assertEquals(0, chestBonus(9, newlyCompleted = true))
        assertEquals(50, chestBonus(10, newlyCompleted = true))
        assertEquals(0, chestBonus(11, newlyCompleted = true))
        assertEquals(50, chestBonus(20, newlyCompleted = true))
    }

    @Test
    fun aDailyThatWasAlreadyCompletedCannotAwardAnotherChest() {
        assertEquals(0, chestBonus(10, newlyCompleted = false))
        assertEquals(0, chestBonus(20, newlyCompleted = false))
    }

    @Test
    fun firstMasteryStarNeedsThreeAtAnyDifficulty() {
        assertEquals(0, masteryStars(2, 0, 0, 0))
        assertEquals(1, masteryStars(3, 0, 0, 0))
        assertEquals(1, masteryStars(1, 0, 1, 1))
    }

    @Test
    fun secondMasteryStarNeedsThreeAtMediumOrAbove() {
        assertEquals(1, masteryStars(10, 2, 0, 0))
        assertEquals(2, masteryStars(0, 3, 0, 0))
        assertEquals(2, masteryStars(0, 1, 1, 1))
    }

    @Test
    fun thirdMasteryStarCombinesHardAndExpert() {
        assertEquals(2, masteryStars(0, 1, 1, 1))
        assertEquals(3, masteryStars(0, 0, 3, 0))
        assertEquals(3, masteryStars(0, 0, 2, 1))
        assertEquals(3, masteryStars(0, 0, 0, 3))
    }

    @Test
    fun wordHunterUnlocksAtFiveHundredWords() {
        val badge = Achievement.WORD_HUNTER
        assertFalse(badge in achievements(PlayerProfile(wordsFound = 499), emptyMap(), emptyMap(), 0))
        assertTrue(badge in achievements(PlayerProfile(wordsFound = 500), emptyMap(), emptyMap(), 0))
    }

    @Test
    fun streakBadgesUseTheBestStreakSoTheyStayUnlocked() {
        val before = achievements(PlayerProfile(currentStreak = 6, bestStreak = 6), emptyMap(), emptyMap(), 0)
        val seven = achievements(PlayerProfile(currentStreak = 1, bestStreak = 7), emptyMap(), emptyMap(), 0)
        val twentyNine = achievements(PlayerProfile(bestStreak = 29), emptyMap(), emptyMap(), 0)
        val thirty = achievements(PlayerProfile(bestStreak = 30), emptyMap(), emptyMap(), 0)

        assertFalse(Achievement.ON_FIRE in before)
        assertTrue(Achievement.ON_FIRE in seven)
        assertFalse(Achievement.UNSTOPPABLE in twentyNine)
        assertTrue(Achievement.UNSTOPPABLE in thirty)
    }

    @Test
    fun explorerNeedsACompletionInAllTwelveCategories() {
        val badge = Achievement.EXPLORER
        val eleven = Category.entries.take(11).associateWith { 1 }
        val twelve = Category.entries.associateWith { 1 }
        assertFalse(badge in achievements(PlayerProfile(), eleven, emptyMap(), 0))
        assertFalse(badge in achievements(PlayerProfile(), twelve + (Category.ANIMALS to 0), emptyMap(), 0))
        assertTrue(badge in achievements(PlayerProfile(), twelve, emptyMap(), 0))
    }

    @Test
    fun completionDailyAndExpertBadgesUnlockAtTheirThresholds() {
        assertTrue(achievements(PlayerProfile(), emptyMap(), emptyMap(), 0).isEmpty())
        val before = achievements(PlayerProfile(completedCount = 99), emptyMap(), mapOf(Difficulty.HARD to 1), 9)
        assertEquals(setOf(Achievement.FIRST_FIND), before)
        val at = achievements(PlayerProfile(completedCount = 100), emptyMap(), mapOf(Difficulty.EXPERT to 1), 10)
        assertEquals(
            setOf(Achievement.FIRST_FIND, Achievement.PUZZLE_PRO, Achievement.SHARPSHOOTER, Achievement.DAILY_DEVOTEE),
            at,
        )
    }

    @Test
    fun reviewMilestoneRequiresExactlyTenAndANewRegularCompletion() {
        assertFalse(isReviewMilestone(9, newlyCompleted = true, daily = false))
        assertTrue(isReviewMilestone(10, newlyCompleted = true, daily = false))
        assertFalse(isReviewMilestone(11, newlyCompleted = true, daily = false))
        assertFalse(isReviewMilestone(10, newlyCompleted = true, daily = true))
        assertFalse(isReviewMilestone(10, newlyCompleted = false, daily = false))
    }

    @Test
    fun lightAndDarkThemesHaveDistinctFourColorTracks() {
        for (dark in listOf(false, true)) {
            val palettes = BoardTheme.entries.map { boardPalette(it, dark) }
            assertEquals(3, palettes.map { it.tracks }.toSet().size)
            assertEquals(3, palettes.map { it.hintColor }.toSet().size)
            assertEquals(3, palettes.map { it.heroColor }.toSet().size)
            assertTrue(palettes.all { it.tracks.size == 4 && it.tracks.toSet().size == 4 })
        }
    }
}
