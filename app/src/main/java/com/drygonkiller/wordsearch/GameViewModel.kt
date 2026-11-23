package com.drygonkiller.wordsearch

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drygonkiller.wordsearch.data.Completion
import com.drygonkiller.wordsearch.data.GameDatabase
import com.drygonkiller.wordsearch.data.GameSettings
import com.drygonkiller.wordsearch.data.GameStore
import com.drygonkiller.wordsearch.data.PlayerProfile
import com.drygonkiller.wordsearch.data.PuzzleSession
import com.drygonkiller.wordsearch.data.Setting
import com.drygonkiller.wordsearch.data.SettingsStore
import com.drygonkiller.wordsearch.game.Category
import com.drygonkiller.wordsearch.game.Difficulty
import com.drygonkiller.wordsearch.game.Puzzle
import com.drygonkiller.wordsearch.game.PuzzleSpec
import com.drygonkiller.wordsearch.game.WordPath
import com.drygonkiller.wordsearch.game.dailySpec
import com.drygonkiller.wordsearch.game.makePuzzle
import com.drygonkiller.wordsearch.game.pickHint
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class Tab(val label: String) {
    HOME("Home"), CATEGORIES("Categories"), STATS("Stats"), SETTINGS("Settings"),
}

enum class Screen { MAIN, DIFFICULTY, PUZZLE, FINISH }

data class GameUi(
    val screen: Screen = Screen.MAIN,
    val tab: Tab = Tab.HOME,
    val category: Category? = null,
    val session: PuzzleSession? = null,
    val board: Puzzle? = null,
    val completion: Completion? = null,
    val replacement: PuzzleSpec? = null,
    val paused: Boolean = false,
    val rewardPending: Boolean = false,
    val busy: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val message: String? = null,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val store = GameStore(GameDatabase.get(application))
    private val settingsStore = SettingsStore(application.applicationContext)
    private val writes = Mutex()
    private val mutableUi = MutableStateFlow(GameUi())
    val ui = mutableUi.asStateFlow()
    private val mutableElapsed = MutableStateFlow(0L)
    val elapsed = mutableElapsed.asStateFlow()
    val profile = store.profile.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerProfile())
    val categoryCounts = store.categoryCounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val dailyDates = store.dailyDates.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, GameSettings())

    private var foreground = false
    private var clockStarted: Long? = null
    private var clockBaseMillis = 0L
    private var lastCheckpoint = 0L
    private val attemptedInterstitials = mutableSetOf<String>()
    private var celebratedPuzzleId: String? = null

    init {
        viewModelScope.launch {
            try {
                val session = store.activeSession()
                change { copy(session = session) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                notice("Couldn't restore the previous puzzle.")
            } finally {
                change { copy(busy = false) }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(250)
                val today = LocalDate.now()
                if (today != ui.value.today) change { copy(today = today) }
                if (clockStarted == null) continue
                val millis = currentElapsed()
                mutableElapsed.value = millis
                if (millis - lastCheckpoint >= 5_000) {
                    lastCheckpoint = millis
                    ui.value.session?.let { persist(it.copy(elapsedMillis = millis)) }
                }
            }
        }
    }

    fun selectTab(tab: Tab) = change { copy(screen = Screen.MAIN, tab = tab, category = null) }

    fun pickCategory(category: Category) {
        if (!ui.value.busy) change { copy(screen = Screen.DIFFICULTY, category = category) }
    }

    fun homePlay() {
        val state = ui.value
        if (state.busy) return
        if (state.session != null) {
            activate(state.session, recovering = true)
        } else {
            startRandom()
        }
    }

    fun pickDifficulty(difficulty: Difficulty) {
        val category = ui.value.category ?: return
        requestPuzzle(PuzzleSpec(category, difficulty, Random.nextLong()))
    }

    fun playDaily() {
        val today = LocalDate.now()
        change { copy(today = today) }
        if (today.toString() in dailyDates.value) return
        val active = ui.value.session
        if (active?.spec?.dailyDate == today) activate(active, recovering = true)
        else requestPuzzle(dailySpec(today))
    }

    fun confirmReplacement() {
        val spec = ui.value.replacement ?: return
        cancelReplacement()
        activate(PuzzleSession(UUID.randomUUID().toString(), spec))
    }

    fun cancelReplacement() = change { copy(replacement = null) }

    fun nextPuzzle() {
        val previous = ui.value.session?.spec ?: return
        if (previous.dailyDate != null) startRandom()
        else activate(PuzzleSession(UUID.randomUUID().toString(), previous.copy(seed = Random.nextLong())))
    }

    fun finishHome() {
        if (ui.value.busy) return
        change { copy(screen = Screen.MAIN, tab = Tab.HOME, category = null, session = null, board = null, completion = null) }
    }

    fun back() {
        when (ui.value.screen) {
            Screen.MAIN -> if (ui.value.tab != Tab.HOME) selectTab(Tab.HOME)
            Screen.DIFFICULTY -> selectTab(ui.value.tab)
            Screen.PUZZLE -> if (ui.value.paused) resume() else pause()
            Screen.FINISH -> finishHome()
        }
    }

    fun foregroundChanged(isForeground: Boolean) {
        foreground = isForeground
        if (isForeground) {
            change { copy(today = LocalDate.now()) }
            startClock()
        } else {
            freezeClock()
            if (ui.value.screen == Screen.PUZZLE && !ui.value.busy) ui.value.session?.let(::persist)
        }
    }

    fun pause() {
        val state = ui.value
        if (state.screen != Screen.PUZZLE || state.busy || state.rewardPending) return
        freezeClock()
        change { copy(paused = true) }
        ui.value.session?.let(::persist)
    }

    fun resume() {
        change { copy(paused = false) }
        startClock()
    }

    fun restart() {
        val state = ui.value
        val session = state.session ?: return
        if (!state.paused || state.busy) return
        val restarted = session.copy(found = emptyMap(), hint = null, elapsedMillis = 0L)
        mutableElapsed.value = 0
        lastCheckpoint = 0
        change { copy(session = restarted, paused = false) }
        persist(restarted)
        startClock()
    }

    fun quit() {
        val state = ui.value
        val session = state.session ?: return
        if (!state.paused || state.busy) return
        change { copy(busy = true) }
        write(
            action = {
                store.discard(session)
                change { copy(screen = Screen.MAIN, session = null, board = null, paused = false, busy = false) }
            },
            onFailure = {
                change { copy(busy = false) }
                notice("Couldn't discard this puzzle. Try again.")
            },
        )
    }

    fun selectWord(path: WordPath): Boolean {
        val state = ui.value
        val session = state.session ?: return false
        val board = state.board ?: return false
        if (!canPlay(state)) return false
        val word = board.match(path, session.found.keys) ?: return false
        val updated = session.copy(
            found = session.found + (word to path),
            hint = session.hint?.takeUnless { it == word },
            elapsedMillis = currentElapsed(),
        )
        change { copy(session = updated) }
        persist(updated)
        if (updated.found.size == board.words.size) completePuzzle()
        return true
    }

    fun useFreeHint(): Boolean {
        val state = ui.value
        if (!canPlay(state) || state.session?.freeHintUsed != false) return false
        return giveHint(state)
    }

    fun beginReward(): String? {
        val state = ui.value
        val session = state.session ?: return null
        if (!canPlay(state) || !session.freeHintUsed) return null
        freezeClock()
        change { copy(rewardPending = true) }
        return session.id
    }

    fun rewardEarned(sessionId: String): Boolean {
        val state = ui.value
        return state.rewardPending && state.session?.id == sessionId && giveHint(state)
    }

    fun rewardClosed(sessionId: String, unavailable: Boolean) {
        if (ui.value.session?.id != sessionId) return
        change { copy(rewardPending = false) }
        if (unavailable) notice("Ad unavailable. You can keep playing without a hint.")
        startClock()
    }

    fun setSetting(setting: Setting, enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsStore.set(setting, enabled)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                notice("Couldn't save this setting.")
            }
        }
    }

    fun takeInterstitialOpportunity(sessionId: String): Boolean = attemptedInterstitials.add(sessionId)

    fun takeWinSoundOpportunity(puzzleId: String): Boolean {
        if (celebratedPuzzleId == puzzleId) return false
        celebratedPuzzleId = puzzleId
        return true
    }

    fun recordInterstitial(nowMillis: Long) {
        write(action = { store.recordInterstitial(nowMillis) })
    }

    fun notice(message: String) = change { copy(message = message) }

    fun clearMessage(message: String) {
        change { if (this.message == message) copy(message = null) else this }
    }

    fun completePuzzle() {
        val state = ui.value
        val session = state.session ?: return
        val board = state.board ?: return
        if (state.busy || session.found.size != board.words.size) return
        freezeClock()
        val completed = ui.value.session ?: return
        change { copy(busy = true) }
        write(
            action = {
                val completion = store.complete(completed, System.currentTimeMillis())
                change { copy(screen = Screen.FINISH, completion = completion, paused = false, busy = false) }
            },
            onFailure = {
                change { copy(busy = false) }
                notice("Couldn't save the result. Tap Retry save.")
            },
        )
    }

    private fun startRandom() {
        val category = Category.entries.random()
        val spec = PuzzleSpec(category, Difficulty.EASY, Random.nextLong())
        if (ui.value.screen == Screen.FINISH) activate(PuzzleSession(UUID.randomUUID().toString(), spec))
        else requestPuzzle(spec)
    }

    private fun requestPuzzle(spec: PuzzleSpec) {
        val state = ui.value
        if (state.busy) return
        if (state.session != null) change { copy(replacement = spec) }
        else activate(PuzzleSession(UUID.randomUUID().toString(), spec))
    }

    private fun activate(session: PuzzleSession, recovering: Boolean = false) {
        if (ui.value.busy) return
        change { copy(busy = true) }
        viewModelScope.launch {
            try {
                val words = withContext(Dispatchers.IO) {
                    getApplication<Application>().assets.open("words/" + session.spec.category.asset + ".txt")
                        .bufferedReader().use { it.readLines() }
                }
                val board = withContext(Dispatchers.Default) { makePuzzle(session.spec, words) }
                if (!recovering) writes.withLock { store.save(session) }
                mutableElapsed.value = session.elapsedMillis
                lastCheckpoint = session.elapsedMillis
                change {
                    copy(
                        screen = Screen.PUZZLE, board = board, session = session, paused = false,
                        completion = null, rewardPending = false, busy = false,
                    )
                }
                if (session.found.size == board.words.size) completePuzzle() else startClock()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                change { copy(busy = false) }
                notice("Couldn't open this puzzle. Try again.")
            }
        }
    }

    private fun giveHint(state: GameUi): Boolean {
        val session = state.session ?: return false
        val board = state.board ?: return false
        val remaining = if (board.words.count { it.word !in session.found } > 1 && session.hint != null) {
            session.found.keys + session.hint
        } else {
            session.found.keys
        }
        val word = pickHint(board, remaining) ?: return false
        val updated = session.copy(freeHintUsed = true, hint = word.word, elapsedMillis = currentElapsed())
        change { copy(session = updated) }
        persist(updated)
        return session.hint != updated.hint
    }

    private fun canPlay(state: GameUi): Boolean = state.run {
        screen == Screen.PUZZLE && !busy && !paused && !rewardPending &&
            board != null && session != null && session.found.size < board.words.size
    }

    private fun startClock() {
        if (foreground && clockStarted == null && canPlay(ui.value)) {
            clockBaseMillis = ui.value.session?.elapsedMillis ?: 0
            clockStarted = SystemClock.elapsedRealtime()
        }
    }

    private fun freezeClock() {
        val elapsed = currentElapsed()
        clockStarted = null
        mutableElapsed.value = elapsed
        change { copy(session = session?.copy(elapsedMillis = elapsed)) }
    }

    private fun currentElapsed(): Long {
        val started = clockStarted ?: return ui.value.session?.elapsedMillis ?: 0
        return clockBaseMillis + SystemClock.elapsedRealtime() - started
    }

    private fun persist(session: PuzzleSession) = write(action = { store.save(session) })

    private fun write(
        action: suspend () -> Unit,
        onFailure: () -> Unit = { notice("Couldn't save progress. Keep the app open and try again.") },
    ) {
        viewModelScope.launch {
            try {
                writes.withLock { action() }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                onFailure()
            }
        }
    }

    private fun change(update: GameUi.() -> GameUi) = mutableUi.update(update)
}
