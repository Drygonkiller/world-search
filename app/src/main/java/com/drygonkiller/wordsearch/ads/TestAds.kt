package com.drygonkiller.wordsearch.ads

import android.app.Activity
import android.os.Build
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TestAds(private val activity: Activity) {
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutablePrivacyRequired = MutableStateFlow(false)
    val privacyRequired = mutablePrivacyRequired.asStateFlow()

    private var canPresentForm = false
    private var pendingForm = false
    private var initializing = false
    private var initialized = false
    private var disposed = false
    private var interstitialLoading = false
    private var rewardedLoading = false
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null

    fun start() {
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                if (!disposed) {
                    pendingForm = true
                    showPendingForm()
                }
            },
            { updatePermissions() },
        )
    }

    fun setConsentPresentationAllowed(allowed: Boolean) {
        canPresentForm = allowed
        showPendingForm()
    }

    fun showPrivacyOptions() {
        if (disposed || !canPresentForm) return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { updatePermissions() }
    }

    private fun showPendingForm() {
        if (!pendingForm || !canPresentForm || !canShow()) return
        pendingForm = false
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { updatePermissions() }
    }

    private fun updatePermissions() {
        if (disposed) return
        mutablePrivacyRequired.value =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (!consent.canRequestAds()) {
            mutableReady.value = false
            interstitial = null
            rewarded = null
            return
        }
        if (initialized) {
            mutableReady.value = true
            preload()
        } else if (!initializing) {
            initializing = true
            MobileAds.initialize(activity.applicationContext) {
                activity.runOnUiThread {
                    if (!disposed) {
                        initialized = true
                        updatePermissions()
                    }
                }
            }
        }
    }

    fun preload() {
        if (!ready.value || disposed) return
        if (interstitial == null && !interstitialLoading) {
            interstitialLoading = true
            InterstitialAd.load(activity, INTERSTITIAL_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    if (!disposed && ready.value) interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                }
            })
        }
        if (rewarded == null && !rewardedLoading) {
            rewardedLoading = true
            RewardedAd.load(activity, REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    if (!disposed && ready.value) rewarded = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                }
            })
        }
    }

    fun showInterstitial(onShown: () -> Unit) {
        val ad = interstitial
        if (!canShow() || !ready.value || ad == null) {
            preload()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = onShown()
            override fun onAdDismissedFullScreenContent() = preload()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = preload()
        }
        ad.show(activity)
    }

    fun showRewarded(onEarned: () -> Unit, onClosed: (unavailable: Boolean) -> Unit) {
        val ad = rewarded
        if (!canShow() || !ready.value || ad == null) {
            preload()
            onClosed(true)
            return
        }
        rewarded = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onClosed(false)
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onClosed(true)
                preload()
            }
        }
        ad.show(activity) { onEarned() }
    }

    fun dispose() {
        disposed = true
        interstitial = null
        rewarded = null
    }

    private fun canShow(): Boolean = !disposed && !activity.isFinishing && !activity.isDestroyed &&
        (Build.VERSION.SDK_INT < 29 || activity.hasWindowFocus() || canPresentForm)

    companion object {
        const val BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
    }
}
