package com.heirloom.app.monetization

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Rewarded ads only — no banners, no interstitials. */
@Singleton
class AdMobRewardedAds @Inject constructor(
    @ApplicationContext private val context: Context,
) : RewardedAds {

    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var loaded: RewardedAd? = null

    init {
        MobileAds.initialize(context) { load() }
    }

    private fun load() {
        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loaded = ad
                    _ready.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = null
                    _ready.value = false
                    // Offline is the normal state for this game; try again on next show().
                }
            },
        )
    }

    override fun show(activity: Activity, onReward: () -> Unit) {
        val ad = loaded ?: run {
            load()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loaded = null
                _ready.value = false
                load()
            }
        }
        ad.show(activity) { _ -> onReward() }
    }

    companion object {
        // Google's public TEST rewarded unit. TODO(release): replace with the real unit ID.
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }
}
