package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.data.repository.PanelEquipmentRepository
import ru.mugalimov.volthome.domain.model.catalog.ApparatusCompatibility
import ru.mugalimov.volthome.domain.model.catalog.ApparatusCompatibilityPolicy
import ru.mugalimov.volthome.domain.model.catalog.CompatibilityDecision
import ru.mugalimov.volthome.domain.model.catalog.SelectedApparatusSnapshot
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec
import ru.mugalimov.volthome.domain.model.provider.ApparatusCatalogProvider
import javax.inject.Inject

class SelectApparatusProductUseCase @Inject constructor(
    private val repository: PanelEquipmentRepository,
    private val catalogProvider: ApparatusCatalogProvider
) {
    data class Params(
        val projectId: String,
        val slotId: String,
        val requiredSpec: ProtectionDeviceSpec,
        val productId: String,
        val allowIncompatible: Boolean = false,
        val selectedAtEpochMs: Long = System.currentTimeMillis()
    )

    data class Result(
        val saved: Boolean,
        val decision: CompatibilityDecision,
        val snapshot: SelectedApparatusSnapshot?,
        val failure: SelectionFailure? = null
    )

    enum class SelectionFailure { PRODUCT_NOT_FOUND, INCOMPATIBLE }

    suspend operator fun invoke(params: Params): Result {
        val catalog = catalogProvider.get()
        val product = catalog.products.firstOrNull { it.productId == params.productId }
            ?: return Result(
                saved = false,
                decision = CompatibilityDecision(
                    result = ApparatusCompatibility.INCOMPATIBLE,
                    reasonCodes = emptyList()
                ),
                snapshot = null,
                failure = SelectionFailure.PRODUCT_NOT_FOUND
            )
        val decision = ApparatusCompatibilityPolicy.evaluate(params.requiredSpec, product)
        if (decision.result == ApparatusCompatibility.INCOMPATIBLE && !params.allowIncompatible) {
            return Result(
                saved = false,
                decision = decision,
                snapshot = null,
                failure = SelectionFailure.INCOMPATIBLE
            )
        }

        val snapshot = SelectedApparatusSnapshot(
            slotId = params.slotId,
            requiredSpec = params.requiredSpec,
            productId = product.productId,
            productSpec = product.spec,
            productStatus = product.status,
            manufacturer = product.manufacturer,
            series = product.series,
            model = product.model,
            article = product.article,
            catalogVersion = catalog.catalogVersion,
            moduleUnits = product.moduleUnits,
            catalogPrice = product.price.range,
            priceSource = product.price.sourceLabel,
            priceUpdatedAt = product.price.updatedAt,
            compatibility = decision.result,
            compatibilityReasonCodes = decision.reasonCodes,
            selectedAtEpochMs = params.selectedAtEpochMs
        )
        repository.saveSelection(params.projectId, snapshot)
        return Result(saved = true, decision = decision, snapshot = snapshot)
    }
}
