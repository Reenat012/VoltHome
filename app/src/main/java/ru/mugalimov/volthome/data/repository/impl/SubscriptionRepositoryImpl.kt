package ru.mugalimov.volthome.data.repository.impl

import android.util.Log
import ru.mugalimov.volthome.data.billing.BillingRecoveryCoordinator
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.data.remote.api.BillingApi
import ru.mugalimov.volthome.data.remote.api.RustoreConfirmRequest
import ru.mugalimov.volthome.data.repository.SubscriptionRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.ProProduct
import ru.mugalimov.volthome.domain.model.UserPlan
import ru.mugalimov.volthome.domain.model.PurchaseKind
import ru.mugalimov.volthome.domain.model.VOLTHOME_PRO_MONTHLY_PRODUCT_ID

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val billingApi: BillingApi,
    private val userPlanRepository: UserPlanRepository
) : SubscriptionRepository {

    companion object {
        private const val TAG = "SubscriptionRepo"
    }

    override fun getProProducts(): List<ProProduct> {
        return listOf(
            ProProduct(
                productId = VOLTHOME_PRO_MONTHLY_PRODUCT_ID,
                kind = PurchaseKind.SUBSCRIPTION
            )
        )
    }

    override suspend fun syncStatus(
        flowId: String?,
        source: String
    ): Result<UserPlan> {
        val safeFlowId = flowId ?: "status-no-flow"

        return runCatching {
            logBegin(
                operation = "repo.syncStatus",
                flowId = safeFlowId,
                extra = "route=GET /v1/billing/status, source=$source"
            )

            val dto = billingApi.getStatus()

            val plan = UserPlan(
                plan = dto.plan.ifBlank { "free" },
                planUntilEpochSeconds = dto.periodEndEpochSeconds
            )

            userPlanRepository.setPlan(plan)

            logEnd(
                operation = "repo.syncStatus",
                flowId = safeFlowId,
                outcome = "SYNC_STATUS_OK",
                extra = buildString {
                    append("source=").append(source)
                    append(", plan=").append(plan.plan)
                    append(", status=").append(dto.status)
                    append(", productId=").append(maskValue(dto.productId))
                    append(", planUntil=").append(plan.planUntilEpochSeconds)
                }
            )

            plan
        }.onFailure {
            logError(
                operation = "repo.syncStatus",
                flowId = safeFlowId,
                outcome = "SYNC_STATUS_FAILED",
                throwable = it,
                extra = "source=$source"
            )
        }
    }

    override suspend fun confirmRustorePurchase(
        productId: String,
        orderId: String,
        purchaseToken: String,
        flowId: String?,
        source: String
    ): Result<UserPlan> = runCatching {
        logBegin(
            operation = "repo.confirmRustorePurchase",
            flowId = flowId,
            extra = buildString {
                append("route=POST /v1/billing/rustore/confirm")
                append(", source=").append(source)
                append(", productId=").append(maskValue(productId))
                append(", orderId=").append(maskValue(orderId))
                append(", purchaseToken=").append(maskValue(purchaseToken))
            }
        )

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

        logEnd(
            operation = "repo.confirmRustorePurchase",
            flowId = flowId,
            outcome = "CONFIRM_OK",
            extra = buildString {
                append("source=").append(source)
                append(", ok=").append(resp.ok)
                append(", plan=").append(plan.plan)
                append(", status=").append(resp.status)
                append(", planUntil=").append(plan.planUntilEpochSeconds)
            }
        )

        plan
    }.onFailure {
        logError(
            operation = "repo.confirmRustorePurchase",
            flowId = flowId,
            outcome = "CONFIRM_FAILED",
            throwable = it,
            extra = buildString {
                append("source=").append(source)
                append(", productId=").append(maskValue(productId))
                append(", orderId=").append(maskValue(orderId))
                append(", purchaseToken=").append(maskValue(purchaseToken))
            }
        )
    }

    /**
     * Маскирование чувствительных значений для логов.
     */
    private fun maskValue(value: String?): String {
        if (value.isNullOrBlank()) return "null"
        return when {
            value.length <= 4 -> "***$value"
            value.length <= 8 -> "${value.take(1)}***${value.takeLast(2)}"
            else -> "${value.take(3)}***${value.takeLast(4)}"
        }
    }

    /**
     * Лог начала шага.
     */
    private fun logBegin(
        operation: String,
        flowId: String?,
        extra: String? = null
    ) {
        val message = buildString {
            append("BEGIN")
            append(" op=").append(operation)
            append(" flowId=").append(flowId ?: "null")
            if (!extra.isNullOrBlank()) append(" ").append(extra)
        }
        Log.d(TAG, message)
    }

    /**
     * Лог завершения шага.
     */
    private fun logEnd(
        operation: String,
        flowId: String?,
        outcome: String,
        extra: String? = null
    ) {
        val message = buildString {
            append("END")
            append(" op=").append(operation)
            append(" flowId=").append(flowId ?: "null")
            append(" outcome=").append(outcome)
            if (!extra.isNullOrBlank()) append(" ").append(extra)
        }
        Log.d(TAG, message)
    }

    /**
     * Error-лог.
     */
    private fun logError(
        operation: String,
        flowId: String?,
        outcome: String,
        throwable: Throwable,
        extra: String? = null
    ) {
        val message = buildString {
            append("END")
            append(" op=").append(operation)
            append(" flowId=").append(flowId ?: "null")
            append(" outcome=").append(outcome)
            if (!extra.isNullOrBlank()) append(" ").append(extra)
            append(" errorClass=").append(throwable.javaClass.simpleName)
            append(" errorMessage=").append(throwable.message)
        }
        Log.e(TAG, message, throwable)
    }
}