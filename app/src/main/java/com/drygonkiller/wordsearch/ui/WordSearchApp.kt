package com.drygonkiller.wordsearch.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drygonkiller.wordsearch.GameViewModel
import com.drygonkiller.wordsearch.MainActivity
import com.drygonkiller.wordsearch.R
import com.drygonkiller.wordsearch.Screen
import com.drygonkiller.wordsearch.Tab
import com.drygonkiller.wordsearch.ads.AdMobAds
import com.drygonkiller.wordsearch.data.Setting
import com.drygonkiller.wordsearch.game.boardPalette
import com.drygonkiller.wordsearch.game.isReviewMilestone
import com.drygonkiller.wordsearch.game.shouldShowInterstitial
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun WordSearchApp(activity: MainActivity, model: GameViewModel, ads: AdMobAds) {
    val state by model.ui.collectAsStateWithLifecycle()
    val profile by model.profile.collectAsStateWithLifecycle()
    val counts by model.completionCounts.collectAsStateWithLifecycle()
    val dailyDates by model.dailyDates.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val adsReady by ads.ready.collectAsStateWithLifecycle()
    val privacyRequired by ads.privacyRequired.collectAsStateWithLifecycle()
    val adConsentVersion by ads.consentVersion.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val dark = settings[Setting.DARK_MODE]
    val boardColors = remember(settings.boardTheme, dark) { boardPalette(settings.boardTheme, dark) }
    val view = LocalView.current

    SideEffect {
        val insets = WindowCompat.getInsetsController(activity.window, view)
        insets.isAppearanceLightStatusBars = !dark
        insets.isAppearanceLightNavigationBars = !dark
        activity.allowConsentOnMainScreen(state.screen == Screen.MAIN)
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
        model.clearMessage(message)
    }
    val finishSession = state.session?.takeIf { state.screen == Screen.FINISH }
    LaunchedEffect(finishSession?.id) {
        val session = finishSession ?: return@LaunchedEffect
        val completion = state.completion ?: return@LaunchedEffect
        // Give the finish composable a frame before an eligible full-screen ad can cover it.
        withFrameNanos { }
        withFrameNanos { }
        val finishShownAtMillis = System.currentTimeMillis()
        if (completion.newlyCompleted && model.takeWinSoundOpportunity(session.id)) {
            activity.playWinJingle()
        }
        if (model.ui.value.screen == Screen.FINISH && model.ui.value.session?.id == session.id &&
            completion.newlyCompleted && model.takeInterstitialOpportunity(session.id) &&
            shouldShowInterstitial(
                completion.profile.completedCount,
                completion.profile.lastInterstitialMillis,
                finishShownAtMillis,
            )
        ) {
            ads.showInterstitial { model.recordInterstitial(System.currentTimeMillis()) }
        }
        if (isReviewMilestone(completion.profile.completedCount, completion.newlyCompleted, session.spec.dailyDate != null)) {
            activity.requestReviewAfterFinish(session.id)
        }
    }

    BackHandler(enabled = state.screen != Screen.MAIN || state.tab != Tab.HOME) { model.back() }

    WordSearchTheme(dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (state.screen == Screen.MAIN) {
                    Column {
                        if (adsReady && (state.tab == Tab.HOME || state.tab == Tab.CATEGORIES)) {
                            key(state.tab, adConsentVersion) { Banner(activity) }
                        }
                        BottomTabs(state.tab, model::selectTab)
                    }
                }
            },
        ) { insets ->
            Box(Modifier.padding(insets).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val content = Modifier.widthIn(max = 650.dp).fillMaxSize()
                when (state.screen) {
                    Screen.MAIN -> when (state.tab) {
                        Tab.HOME -> HomeScreen(
                            profile, state.today, state.today.toString() in dailyDates,
                            state.session != null, state.busy, Color(boardColors.heroColor),
                            model::homePlay, model::playDaily, content,
                        )
                        Tab.CATEGORIES -> CategoriesScreen(counts, state.busy, model::pickCategory, content)
                        Tab.STATS -> StatsScreen(profile, state.today, dailyDates, counts, content)
                        Tab.SETTINGS -> SettingsScreen(
                            settings, privacyRequired, model::setSetting, model::setBoardTheme,
                            ads::showPrivacyOptions, content,
                        )
                    }
                    Screen.DIFFICULTY -> state.category?.let {
                        DifficultyScreen(it, state.busy, model::back, model::pickDifficulty, content)
                    }
                    Screen.PUZZLE -> {
                        val session = state.session
                        val board = state.board
                        if (session != null && board != null) {
                            val elapsed by model.elapsed.collectAsStateWithLifecycle()
                            PuzzleScreen(
                                board = board,
                                session = session,
                                elapsedMillis = elapsed,
                                hideTimer = settings[Setting.HIDE_TIMER],
                                inputEnabled = !state.busy && !state.paused && !state.rewardPending,
                                busy = state.busy,
                                rewardPending = state.rewardPending,
                                coins = profile.coins,
                                boardColors = boardColors,
                                onSelect = { if (model.selectWord(it)) activity.wordFound() },
                                onPause = model::pause,
                                onFreeHint = { if (model.useFreeHint()) activity.hintRevealed() },
                                onCoinHint = { model.buyHint(activity::hintRevealed) },
                                onRewardedHint = {
                                    model.beginReward()?.let { id ->
                                        var revealed = false
                                        ads.showRewarded(
                                            onEarned = { revealed = model.rewardEarned(id) },
                                            onClosed = { unavailable ->
                                                model.rewardClosed(id, unavailable)
                                                if (revealed) activity.hintRevealed()
                                            },
                                        )
                                    }
                                },
                                onRetrySave = model::completePuzzle,
                                modifier = content,
                            )
                        }
                    }
                    Screen.FINISH -> {
                        val session = state.session
                        val result = state.completion
                        if (session != null && result != null) {
                            FinishScreen(session, result, state.busy, model::nextPuzzle, model::finishHome, content)
                        }
                    }
                }
            }
        }

        if (state.paused) {
            AlertDialog(
                onDismissRequest = model::resume,
                icon = { Glyph("pause") },
                title = { Text("Puzzle paused") },
                text = {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(model::resume, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Resume") }
                        OutlinedButton(model::restart, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Restart") }
                        TextButton(model::quit, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Quit") }
                    }
                },
                confirmButton = {},
            )
        }
        if (state.replacement != null) {
            AlertDialog(
                onDismissRequest = model::cancelReplacement,
                title = { Text("Start a new puzzle?") },
                text = { Text("This will replace the puzzle you have in progress.") },
                confirmButton = { TextButton(model::confirmReplacement) { Text("Start new") } },
                dismissButton = { TextButton(model::cancelReplacement) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun BottomTabs(current: Tab, select: (Tab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            val glyph = when (tab) {
                Tab.HOME -> "home"
                Tab.CATEGORIES -> "grid"
                Tab.STATS -> "stats"
                Tab.SETTINGS -> "settings"
            }
            NavigationBarItem(
                selected = selected,
                onClick = { select(tab) },
                icon = {
                    Glyph(glyph, color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                },
                label = { Text(tab.label) },
            )
        }
    }
}

@Composable
private fun Banner(activity: MainActivity) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = maxWidth.value.toInt().coerceAtMost(650)
        if (width < 320) return@BoxWithConstraints
        val size = remember(width) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width) }
        var loaded by remember(width) { mutableStateOf(false) }
        key(width) {
            AndroidView(
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(size)
                        adUnitId = context.getString(R.string.admob_banner_id)
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        adListener = object : com.google.android.gms.ads.AdListener() {
                            override fun onAdLoaded() { loaded = true }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                modifier = Modifier.width(width.dp).height(if (loaded) size.height.dp else 0.dp),
                onRelease = { it.destroy() },
            )
        }
    }
}
