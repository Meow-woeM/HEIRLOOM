package com.heirloom.app.di

import com.heirloom.app.monetization.AdMobRewardedAds
import com.heirloom.app.monetization.PlayBillingSupporterStore
import com.heirloom.app.monetization.RewardedAds
import com.heirloom.app.monetization.SupporterStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MonetizationModule {

    @Binds
    @Singleton
    abstract fun bindSupporterStore(impl: PlayBillingSupporterStore): SupporterStore

    @Binds
    @Singleton
    abstract fun bindRewardedAds(impl: AdMobRewardedAds): RewardedAds
}
