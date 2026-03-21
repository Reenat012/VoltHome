package ru.mugalimov.volthome.data.repository

import ru.mugalimov.volthome.domain.model.ProProduct
import ru.mugalimov.volthome.domain.model.UserPlan

/**
 * Репозиторий подписки RuStore.
 * Не хранит состояние сам — только ходит в BillingApi
 * и обновляет UserPlanRepository.
 *
 * Commit 1:
 * flowId нужен только для client-side instrumentation.
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
    suspend fun syncStatus(
        flowId: String? = null
    ): Result<UserPlan>

    /**
     * Подтвердить покупку RuStore:
     * POST /v1/billing/rustore/confirm
     */
    suspend fun confirmRustorePurchase(
        productId: String,
        orderId: String,
        purchaseToken: String,
        flowId: String? = null
    ): Result<UserPlan>
}