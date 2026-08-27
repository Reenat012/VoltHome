package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.catalog.ApparatusCompatibility
import ru.mugalimov.volthome.domain.model.catalog.ApparatusCompatibilityPolicy
import ru.mugalimov.volthome.domain.model.catalog.ApparatusProduct
import ru.mugalimov.volthome.domain.model.catalog.CompatibilityDecision
import ru.mugalimov.volthome.domain.model.catalog.ProductStatus
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec
import ru.mugalimov.volthome.domain.model.provider.ApparatusCatalogProvider
import javax.inject.Inject

data class ApparatusCandidate(
    val product: ApparatusProduct,
    val decision: CompatibilityDecision
)

/** Единая точка выдачи моделей для будущего окна выбора аппарата. */
class FindCompatibleApparatusUseCase @Inject constructor(
    private val catalogProvider: ApparatusCatalogProvider
) {
    operator fun invoke(
        requiredSpec: ProtectionDeviceSpec,
        includeIncompatible: Boolean = false
    ): List<ApparatusCandidate> = catalogProvider.get().products
        .asSequence()
        .filter { it.status == ProductStatus.ACTIVE }
        .map { product ->
            ApparatusCandidate(
                product = product,
                decision = ApparatusCompatibilityPolicy.evaluate(requiredSpec, product)
            )
        }
        .filter { includeIncompatible || it.decision.result != ApparatusCompatibility.INCOMPATIBLE }
        .sortedWith(
            compareBy<ApparatusCandidate> { it.decision.result.ordinal }
                .thenBy { it.product.manufacturer.lowercase() }
                .thenBy { it.product.series.lowercase() }
                .thenBy { it.product.model.lowercase() }
        )
        .toList()
}
