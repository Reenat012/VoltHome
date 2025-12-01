package ru.mugalimov.volthome.data.repository

import ru.mugalimov.volthome.domain.model.UserPlan

/**
 * Репозиторий подписки RuStore.
 * Не хранит состояние сам — только ходит в BillingApi
 * и обновляет UserPlanRepository.
 */
interface SubscriptionRepository {

    /**
     * Синхронизировать статус подписки с сервера.
     * GET /v1/billing/status
     */
    suspend fun syncStatus(): Result<UserPlan>

    /**
     * Подтвердить покупку RuStore:
     * POST /v1/billing/rustore/confirm
     */
    suspend fun confirmRustorePurchase(
        productId: String,
        orderId: String,
        purchaseToken: String
    ): Result<UserPlan>
}