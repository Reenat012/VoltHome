package ru.mugalimov.volthome.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class BillingStatusDto(
    val plan: String,                  // "free" | "pro"
    val status: String,                // "NONE" | "ACTIVE" | "EXPIRED" | ...
    val productId: String?,            // product_id из subscriptions или null
    val periodEndEpochSeconds: Long?   // unix time в секундах или null
)

data class RustoreConfirmRequest(
    val productId: String,
    val orderId: String,
    val purchaseToken: String
)

data class RustoreConfirmResponse(
    val ok: Boolean,
    val plan: String,                  // "pro"
    val status: String,                // "ACTIVE" и т.п.
    val periodEndEpochSeconds: Long?
)

interface BillingApi {

    @GET("v1/billing/status")
    suspend fun getStatus(): BillingStatusDto

    @POST("v1/billing/rustore/confirm")
    suspend fun confirmRustorePurchase(
        @Body body: RustoreConfirmRequest
    ): RustoreConfirmResponse
}