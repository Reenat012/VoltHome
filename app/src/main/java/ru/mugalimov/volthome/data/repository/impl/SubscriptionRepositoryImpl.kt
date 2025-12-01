package ru.mugalimov.volthome.data.repository.impl

import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.data.remote.api.BillingApi
import ru.mugalimov.volthome.data.remote.api.RustoreConfirmRequest
import ru.mugalimov.volthome.data.repository.SubscriptionRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.UserPlan

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val billingApi: BillingApi,
    private val userPlanRepository: UserPlanRepository
) : SubscriptionRepository {

    override suspend fun syncStatus(): Result<UserPlan> =
        runCatching {
            val dto = billingApi.getStatus()

            val plan = UserPlan(
                plan = dto.plan.ifBlank { "free" },
                planUntilEpochSeconds = dto.periodEndEpochSeconds
            )

            userPlanRepository.setPlan(plan)
            plan
        }

    override suspend fun confirmRustorePurchase(
        productId: String,
        orderId: String,
        purchaseToken: String
    ): Result<UserPlan> =
        runCatching {
            val resp = billingApi.confirmRustorePurchase(
                RustoreConfirmRequest(
                    productId = productId,
                    orderId = orderId,
                    purchaseToken = purchaseToken
                )
            )

            val plan = UserPlan(
                plan = resp.plan.ifBlank { "free" },
                planUntilEpochSeconds = resp.periodEndEpochSeconds
            )

            userPlanRepository.setPlan(plan)
            plan
        }
}