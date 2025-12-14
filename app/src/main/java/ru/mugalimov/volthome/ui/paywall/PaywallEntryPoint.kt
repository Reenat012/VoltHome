package ru.mugalimov.volthome.ui.paywall

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PaywallEntryPoint {
    fun paywallBus(): PaywallBus
}