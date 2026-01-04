package ru.mugalimov.volthome.data.repository

import ru.mugalimov.volthome.domain.model.UserPlan
import ru.mugalimov.volthome.domain.model.ProProduct

/**
 * Репозиторий подписки RuStore.
 * Не хранит состояние сам — только ходит в BillingApi
 * и обновляет UserPlanRepository.
 */
interface SubscriptionRepository {

    /**
     * Список PRO-продуктов, которые приложение умеет продавать.
     * Сейчас — минимум (один SKU), но оставляем как List для будущих планов (месяц/год).
     */
    fun getProProducts(): List<ProProduct>

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