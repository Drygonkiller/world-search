package com.drygonkiller.wordsearch.ads

import android.app.Activity
import android.os.Build
import com.drygonkiller.wordsearch.R
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

class AdMobAds(private val activity: Activity) {
    private val interstitialId = activity.getString(R.string.admob_interstitial_id)
    private val rewardedId = activity.getString(R.string.admob_rewarded_id)
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutablePrivacyRequired = MutableStateFlow(false)
    val privacyRequired = mutablePrivacyRequired.asStateFlow()
    private val mutableConsentVersion = MutableStateFlow(0)
    val consentVersion = mutableConsentVersion.asStateFlow()

    private var canPresentForm = false
    private var pendingForm = false
    private var formOpen = false
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
        if (disposed || !canPresentForm || formOpen || !privacyRequired.value || !canShow()) return
        formOpen = true
        clearAds()
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            formOpen = false
            updatePermissions()
        }
    }

    private fun showPendingForm() {
        if (!pendingForm || !canPresentForm || formOpen || !canShow()) return
        pendingForm = false
        formOpen = true
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
            formOpen = false
            updatePermissions()
        }
    }

    private fun updatePermissions() {
        if (disposed) return
        mutablePrivacyRequired.value =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (formOpen || !consent.canRequestAds()) {
            clearAds()
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
        val consentVersionAtLoad = consentVersion.value
        if (interstitial == null && !interstitialLoading) {
            interstitialLoading = true
            InterstitialAd.load(activity, interstitialId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (disposed || consentVersionAtLoad != consentVersion.value) return
                    interstitialLoading = false
                    if (ready.value) interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (consentVersionAtLoad == consentVersion.value) interstitialLoading = false
                }
            })
        }
        if (rewarded == null && !rewardedLoading) {
            rewardedLoading = true
            RewardedAd.load(activity, rewardedId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (disposed || consentVersionAtLoad != consentVersion.value) return
                    rewardedLoading = false
                    if (ready.value) rewarded = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (consentVersionAtLoad == consentVersion.value) rewardedLoading = false
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
        clearAds()
    }

    private fun clearAds() {
        mutableReady.value = false
        mutableConsentVersion.value += 1
        interstitial = null
        rewarded = null
        interstitialLoading = false
        rewardedLoading = false
    }

    private fun canShow(): Boolean = !disposed && !activity.isFinishing && !activity.isDestroyed &&
        (Build.VERSION.SDK_INT < 29 || activity.hasWindowFocus() || canPresentForm)

}
