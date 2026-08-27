package ru.mugalimov.volthome.data.repository.impl

import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.data.repository.SubscriptionRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.ProProduct
import ru.mugalimov.volthome.domain.model.PurchaseKind
import ru.mugalimov.volthome.domain.model.UserPlan
import ru.mugalimov.volthome.domain.model.VOLTHOME_PRO_MONTHLY_PRODUCT_ID

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val userPlanRepository: UserPlanRepository
) : SubscriptionRepository {

    override fun getProProducts(): List<ProProduct> = listOf(
        ProProduct(
            productId = VOLTHOME_PRO_MONTHLY_PRODUCT_ID,
            kind = PurchaseKind.SUBSCRIPTION
        )
    )

    override suspend fun activatePro(periodEndEpochSeconds: Long?): UserPlan {
        val plan = UserPlan(plan = "pro", planUntilEpochSeconds = periodEndEpochSeconds)
        userPlanRepository.setPlan(plan)
        return plan
    }

    override suspend fun deactivatePro(): UserPlan {
        userPlanRepository.setPlan(UserPlan.FREE)
        return UserPlan.FREE
    }
}
