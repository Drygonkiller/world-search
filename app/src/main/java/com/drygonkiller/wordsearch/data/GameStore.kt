package com.drygonkiller.wordsearch.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.drygonkiller.wordsearch.game.BoardTheme
import com.drygonkiller.wordsearch.game.Category
import com.drygonkiller.wordsearch.game.Cell
import com.drygonkiller.wordsearch.game.Difficulty
import com.drygonkiller.wordsearch.game.PuzzleSpec
import com.drygonkiller.wordsearch.game.Streak
import com.drygonkiller.wordsearch.game.WordPath
import com.drygonkiller.wordsearch.game.chestBonus
import com.drygonkiller.wordsearch.game.spendHintCoins
import com.drygonkiller.wordsearch.game.updateStreak
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PuzzleSession(
    val id: String,
    val spec: PuzzleSpec,
    val found: Map<String, WordPath> = emptyMap(),
    val freeHintUsed: Boolean = false,
    val hint: String? = null,
    val elapsedMillis: Long = 0,
)

@Entity(tableName = "active_puzzle")
data class ActivePuzzleEntity(
    @PrimaryKey val slot: Int = 1,
    val id: String,
    val category: String,
    val difficulty: String,
    val seed: Long,
    val dailyDate: String?,
    val foundPaths: String,
    val freeHintUsed: Boolean,
    val hint: String?,
    val elapsedMillis: Long,
) {
    fun toSession(): PuzzleSession {
        val found = foundPaths.lineSequence().filter(String::isNotEmpty).associate { line ->
            val (word, row, column, endRow, endColumn) = line.split('|')
            word to WordPath(Cell(row.toInt(), column.toInt()), Cell(endRow.toInt(), endColumn.toInt()))
        }
        return PuzzleSession(
            id = id,
            spec = PuzzleSpec(Category.valueOf(category), Difficulty.valueOf(difficulty), seed, dailyDate?.let(LocalDate::parse)),
            found = found,
            freeHintUsed = freeHintUsed,
            hint = hint,
            elapsedMillis = elapsedMillis,
        )
    }
}

private fun PuzzleSession.toEntity(): ActivePuzzleEntity = ActivePuzzleEntity(
    id = id,
    category = spec.category.name,
    difficulty = spec.difficulty.name,
    seed = spec.seed,
    dailyDate = spec.dailyDate?.toString(),
    foundPaths = found.entries.joinToString("\n") { (word, path) ->
        listOf(word, path.start.row, path.start.column, path.end.row, path.end.column).joinToString("|")
    },
    freeHintUsed = freeHintUsed,
    hint = hint,
    elapsedMillis = elapsedMillis,
)

@Entity(tableName = "completed_puzzle", indices = [Index(value = ["dailyDate"], unique = true)])
data class CompletedPuzzle(
    @PrimaryKey val id: String,
    val dailyDate: String?,
    val category: String,
    val difficulty: String,
    val wordCount: Int,
    val elapsedMillis: Long,
    val coinsAwarded: Int,
    val completedAtMillis: Long,
)

@Entity(tableName = "player_profile")
data class PlayerProfile(
    @PrimaryKey val id: Int = 1,
    val completedCount: Int = 0,
    val wordsFound: Int = 0,
    val coins: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastDailyDate: String? = null,
    val lastInterstitialMillis: Long? = null,
)

data class CategoryDifficultyCount(val category: String, val difficulty: String, val count: Int)

data class Completion(
    val coinsAwarded: Int,
    val profile: PlayerProfile,
    val newlyCompleted: Boolean,
    val chestBonusCoins: Int = 0,
)

@Dao
interface GameDao {
    @Query("SELECT * FROM active_puzzle WHERE slot = 1")
    suspend fun activePuzzle(): ActivePuzzleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActive(puzzle: ActivePuzzleEntity)

    @Query("DELETE FROM active_puzzle WHERE id = :puzzleId")
    suspend fun clearActive(puzzleId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCompletion(puzzle: CompletedPuzzle): Long

    @Query("SELECT category, difficulty, COUNT(*) AS count FROM completed_puzzle GROUP BY category, difficulty")
    fun observeCompletionCounts(): Flow<List<CategoryDifficultyCount>>

    @Query("SELECT dailyDate FROM completed_puzzle WHERE dailyDate IS NOT NULL")
    fun observeDailyDates(): Flow<List<String>>

    @Query("SELECT * FROM player_profile WHERE id = 1")
    suspend fun profile(): PlayerProfile?

    @Query("SELECT * FROM player_profile WHERE id = 1")
    fun observeProfile(): Flow<PlayerProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: PlayerProfile)
}

@Database(entities = [ActivePuzzleEntity::class, CompletedPuzzle::class, PlayerProfile::class], version = 1)
abstract class GameDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile private var instance: GameDatabase? = null

        fun get(context: Context): GameDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GameDatabase::class.java, "word-search.db")
                .build().also { instance = it }
        }
    }
}

class GameStore(private val database: GameDatabase) {
    private val dao = database.gameDao()
    val profile = dao.observeProfile().map { it ?: PlayerProfile() }
    val completionCounts = dao.observeCompletionCounts().map { counts ->
        counts.groupBy(
            keySelector = { Category.valueOf(it.category) },
            valueTransform = { Difficulty.valueOf(it.difficulty) to it.count },
        ).mapValues { (_, countsByDifficulty) -> countsByDifficulty.toMap() }
    }
    val dailyDates = dao.observeDailyDates().map { it.toSet() }

    suspend fun activeSession(): PuzzleSession? = dao.activePuzzle()?.toSession()
    suspend fun save(session: PuzzleSession) = dao.saveActive(session.toEntity())
    suspend fun discard(session: PuzzleSession) = dao.clearActive(session.id)

    suspend fun purchaseHint(sessionId: String, word: String, elapsedMillis: Long): PuzzleSession? =
        database.withTransaction {
            val active = dao.activePuzzle()?.toSession() ?: return@withTransaction null
            if (active.id != sessionId || !active.freeHintUsed || word == active.hint || word in active.found) {
                return@withTransaction null
            }
            val profile = dao.profile() ?: return@withTransaction null
            val purchase = spendHintCoins(profile.coins, word)
            val revealedWord = purchase.revealedWord ?: return@withTransaction null
            val updated = active.copy(hint = revealedWord, elapsedMillis = maxOf(active.elapsedMillis, elapsedMillis))
            dao.saveProfile(profile.copy(coins = purchase.remainingCoins))
            dao.saveActive(updated.toEntity())
            updated
        }

    suspend fun complete(session: PuzzleSession, nowMillis: Long): Completion = database.withTransaction {
        val dailyDate = session.spec.dailyDate
        val reward = if (dailyDate != null) 20 else 10
        val previous = dao.profile() ?: PlayerProfile()
        val newCount = previous.completedCount + 1
        val bonus = chestBonus(newCount, newlyCompleted = true)
        val inserted = dao.addCompletion(
            CompletedPuzzle(
                id = session.id,
                dailyDate = dailyDate?.toString(),
                category = session.spec.category.name,
                difficulty = session.spec.difficulty.name,
                wordCount = session.found.size,
                elapsedMillis = session.elapsedMillis,
                coinsAwarded = reward + bonus,
                completedAtMillis = nowMillis,
            ),
        ) != -1L
        dao.clearActive(session.id)
        if (!inserted) return@withTransaction Completion(0, previous, false)

        val streak = Streak(previous.currentStreak, previous.bestStreak, previous.lastDailyDate?.let(LocalDate::parse))
        val updated = dailyDate?.let { updateStreak(streak, it) } ?: streak
        val profile = previous.copy(
            completedCount = newCount,
            wordsFound = previous.wordsFound + session.found.size,
            coins = previous.coins + reward + bonus,
            currentStreak = updated.current,
            bestStreak = updated.best,
            lastDailyDate = updated.lastDay?.toString(),
        )
        dao.saveProfile(profile)
        Completion(reward, profile, true, bonus)
    }

    suspend fun recordInterstitial(nowMillis: Long) = database.withTransaction {
        val profile = dao.profile() ?: PlayerProfile()
        val last = profile.lastInterstitialMillis
        if (last == null || nowMillis >= last) dao.saveProfile(profile.copy(lastInterstitialMillis = nowMillis))
    }
}

enum class Setting(val label: String, val description: String, val default: Boolean) {
    SOUND("Sound", "Play a tone when you find a word", true),
    VIBRATION("Vibration", "Feel a tap when you find a word", true),
    DARK_MODE("Dark mode", "Use the dark color theme", false),
    HIDE_TIMER("Hide timer", "Keep elapsed time off the puzzle screen", false),
}

data class GameSettings(
    val values: Map<Setting, Boolean> = emptyMap(),
    val boardTheme: BoardTheme = BoardTheme.FOREST,
) {
    operator fun get(setting: Setting): Boolean = values[setting] ?: setting.default
}

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsStore(context: Context) {
    private val store = context.settingsDataStore
    private val keys = Setting.entries.associateWith { booleanPreferencesKey(it.name.lowercase()) }
    private val boardThemeKey = stringPreferencesKey("board_theme")
    private val reviewRequestedKey = booleanPreferencesKey("review_requested")

    val settings = store.data.map { preferences ->
        GameSettings(
            values = Setting.entries.associateWith { preferences[keys.getValue(it)] ?: it.default },
            boardTheme = BoardTheme.entries.firstOrNull { it.name == preferences[boardThemeKey] }
                ?: BoardTheme.FOREST,
        )
    }

    suspend fun set(setting: Setting, enabled: Boolean) {
        store.edit { it[keys.getValue(setting)] = enabled }
    }

    suspend fun setBoardTheme(theme: BoardTheme) {
        store.edit { it[boardThemeKey] = theme.name }
    }

    suspend fun claimReviewRequest(): Boolean {
        var claimed = false
        store.edit { preferences ->
            if (preferences[reviewRequestedKey] != true) {
                preferences[reviewRequestedKey] = true
                claimed = true
            }
        }
        return claimed
    }
}
