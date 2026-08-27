package ru.mugalimov.volthome.core.analytics

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AnalyticsRuntimeEntryPoint {
    fun analyticsTracker(): AnalyticsTracker
    fun purchaseAnalyticsContext(): PurchaseAnalyticsContext
}
