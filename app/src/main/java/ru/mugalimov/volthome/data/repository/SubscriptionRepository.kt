package ru.mugalimov.volthome.data.repository

import ru.mugalimov.volthome.domain.model.ProProduct
import ru.mugalimov.volthome.domain.model.UserPlan

/** Локальное entitlement-хранилище поверх постоянного UserPlanRepository. */
interface SubscriptionRepository {
    fun getProProducts(): List<ProProduct>
    suspend fun activatePro(periodEndEpochSeconds: Long? = null): UserPlan
    suspend fun deactivatePro(): UserPlan
}
