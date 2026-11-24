package com.drygonkiller.wordsearch

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.drygonkiller.wordsearch.ads.AdMobAds
import com.drygonkiller.wordsearch.data.Setting
import com.drygonkiller.wordsearch.ui.WordSearchApp
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val model: GameViewModel by viewModels()
    private lateinit var ads: AdMobAds
    private var tone: ToneGenerator? = null
    private var hintTone: ToneGenerator? = null
    private var inForeground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ads = AdMobAds(this)
        setContent { WordSearchApp(this, model, ads) }
        ads.start()
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
        model.foregroundChanged(true)
        allowConsentOnMainScreen(model.ui.value.screen == Screen.MAIN)
    }

    override fun onPause() {
        inForeground = false
        tone?.stopTone()
        hintTone?.stopTone()
        model.foregroundChanged(false)
        ads.setConsentPresentationAllowed(false)
        super.onPause()
    }

    fun allowConsentOnMainScreen(isMainScreen: Boolean) {
        ads.setConsentPresentationAllowed(inForeground && isMainScreen && model.ui.value.screen == Screen.MAIN)
    }

    fun wordFound() {
        val settings = model.settings.value
        if (settings[Setting.SOUND]) {
            playGameTone(ToneGenerator.TONE_PROP_ACK, 100)
        }
        if (settings[Setting.VIBRATION]) {
            val feedback = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.CLOCK_TICK
            window.decorView.performHapticFeedback(feedback)
        }
    }

    fun hintRevealed() {
        if (isDestroyed || !model.settings.value[Setting.SOUND]) return
        runCatching {
            val player = hintTone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 18).also { hintTone = it }
            player.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
        }
    }

    suspend fun requestReviewAfterFinish(puzzleId: String) {
        fun stillOnFinish(): Boolean {
            val state = model.ui.value
            return inForeground && !isFinishing && !isDestroyed &&
                state.screen == Screen.FINISH && state.session?.id == puzzleId
        }

        if (!stillOnFinish() || !model.claimReviewRequest() || !stillOnFinish()) return
        runCatching {
            val manager = ReviewManagerFactory.create(this)
            manager.requestReviewFlow().addOnSuccessListener(this) { reviewInfo ->
                if (stillOnFinish()) runCatching { manager.launchReviewFlow(this, reviewInfo) }
            }
        }
    }

    suspend fun playWinJingle() {
        if (!inForeground || !model.settings.value[Setting.SOUND]) return
        // Leave room for the last found-word tone before playing the three-note jingle.
        delay(100)
        val notes = listOf(ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_5, ToneGenerator.TONE_DTMF_9)
        for (note in notes) {
            if (!inForeground || !model.settings.value[Setting.SOUND]) return
            playGameTone(note, 95)
            delay(115)
        }
    }

    private fun playGameTone(note: Int, durationMillis: Int) {
        runCatching {
            val player = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 40).also { tone = it }
            player.startTone(note, durationMillis)
        }
    }

    override fun onDestroy() {
        ads.dispose()
        tone?.release()
        hintTone?.release()
        super.onDestroy()
    }
}
