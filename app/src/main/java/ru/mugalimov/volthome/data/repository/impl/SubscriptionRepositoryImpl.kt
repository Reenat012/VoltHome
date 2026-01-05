package ru.mugalimov.volthome.data.repository.impl

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.data.remote.api.BillingApi
import ru.mugalimov.volthome.data.remote.api.RustoreConfirmRequest
import ru.mugalimov.volthome.data.repository.SubscriptionRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.ProProduct
import ru.mugalimov.volthome.domain.model.UserPlan

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val billingApi: BillingApi,
    private val userPlanRepository: UserPlanRepository
) : SubscriptionRepository {
    override fun getProProducts(): List<ProProduct> {
        TODO("Not yet implemented")
    }

    companion object {
        private const val TAG = "SubscriptionRepo"
    }

    override suspend fun syncStatus(): Result<UserPlan> =
        runCatching {
            Log.d(TAG, "syncStatus() → calling GET /v1/billing/status")
            val dto = billingApi.getStatus()
            Log.d(
                TAG,
                "syncStatus() → response: plan=${dto.plan}, status=${dto.status}, " +
                        "productId=${dto.productId}, periodEnd=${dto.periodEndEpochSeconds}"
            )

            val plan = UserPlan(
                plan = dto.plan.ifBlank { "free" },
                planUntilEpochSeconds = dto.periodEndEpochSeconds
            )

            userPlanRepository.setPlan(plan)
            Log.d(TAG, "syncStatus() → UserPlanRepository updated: plan=${plan.plan}, until=${plan.planUntilEpochSeconds}")
            plan
        }.onFailure {
            Log.e(TAG, "syncStatus() → failure: ${it.message}", it)
        }

    override suspend fun confirmRustorePurchase(
        productId: String,
        orderId: String,
        purchaseToken: String
    ): Result<UserPlan> =
        runCatching {
            Log.d(
                TAG,
                "confirmRustorePurchase() → calling POST /v1/billing/rustore/confirm " +
                        "with productId=$productId, orderId=$orderId, purchaseToken=$purchaseToken"
            )

            val resp = billingApi.confirmRustorePurchase(
                RustoreConfirmRequest(
                    productId = productId,
                    orderId = orderId,
                    purchaseToken = purchaseToken
                )
            )

            Log.d(
                TAG,
                "confirmRustorePurchase() → response: ok=${resp.ok}, plan=${resp.plan}, " +
                        "status=${resp.status}, periodEnd=${resp.periodEndEpochSeconds}"
            )

            val plan = UserPlan(
                plan = resp.plan.ifBlank { "free" },
                planUntilEpochSeconds = resp.periodEndEpochSeconds
            )

            userPlanRepository.setPlan(plan)
            Log.d(TAG, "confirmRustorePurchase() → UserPlanRepository updated: plan=${plan.plan}, until=${plan.planUntilEpochSeconds}")
            plan
        }.onFailure {
            Log.e(TAG, "confirmRustorePurchase() → failure: ${it.message}", it)
        }
}