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

class MainActivity : ComponentActivity() {
    private val model: GameViewModel by viewModels()
    private lateinit var ads: AdMobAds
    private var tone: ToneGenerator? = null
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
            runCatching {
                val player = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 40).also { tone = it }
                player.startTone(ToneGenerator.TONE_PROP_ACK, 100)
            }
        }
        if (settings[Setting.VIBRATION]) {
            val feedback = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.CLOCK_TICK
            window.decorView.performHapticFeedback(feedback)
        }
    }

    override fun onDestroy() {
        ads.dispose()
        tone?.release()
        super.onDestroy()
    }
}
