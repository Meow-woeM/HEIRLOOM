package com.heirloom.app.monetization

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * Monetization is gated behind these interfaces so the engine and tests never touch
 * the Play Billing / Mobile Ads SDKs, and so store-free builds can stub them out.
 */
interface SupporterStore {
    /** True when "The Family Legacy" one-time purchase is owned. */
    val supporterActive: StateFlow<Boolean>

    /** Localized price ("$4.99") once product details load; null until then. */
    val priceText: StateFlow<String?>

    fun launchPurchase(activity: Activity)

    /** Re-query ownership (app start, after purchase flows). */
    fun refresh()
}

interface RewardedAds {
    /** True when a rewarded ad is loaded and ready to show. */
    val ready: StateFlow<Boolean>

    /**
     * Shows "a traveler lends a hand". [onReward] fires only when the reward is earned
     * (ad watched to completion). Loading the next ad is the implementation's problem.
     */
    fun show(activity: Activity, onReward: () -> Unit)
}
