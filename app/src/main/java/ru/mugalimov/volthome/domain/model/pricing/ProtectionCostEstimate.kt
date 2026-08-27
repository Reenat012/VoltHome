package ru.mugalimov.volthome.domain.model.pricing

import ru.mugalimov.volthome.domain.model.catalog.EquipmentEstimateScope
import ru.mugalimov.volthome.domain.model.incomer.RcdSelectivity
import ru.mugalimov.volthome.domain.model.incomer.RcdType

enum class ProtectionDeviceKind { MCB, RCD, RCBO }

data class ProtectionDeviceSpec(
    val kind: ProtectionDeviceKind,
    val poles: Int,
    val ratedCurrentA: Int,
    val breakerCurve: String? = null,
    val breakingCapacityA: Int? = null,
    val leakageCurrentMa: Int? = null,
    val rcdType: RcdType? = null,
    val selectivity: RcdSelectivity = RcdSelectivity.NONE
)

data class ProtectionDeviceInventoryItem(
    val id: String,
    val spec: ProtectionDeviceSpec
)

data class MoneyRange(
    val minKopecks: Long,
    val typicalKopecks: Long,
    val maxKopecks: Long
) {
    operator fun times(quantity: Int) = MoneyRange(
        minKopecks = minKopecks * quantity,
        typicalKopecks = typicalKopecks * quantity,
        maxKopecks = maxKopecks * quantity
    )
}

data class ProtectionEstimateLine(
    val spec: ProtectionDeviceSpec,
    val title: String,
    val quantity: Int,
    val unitPrice: MoneyRange,
    val totalPrice: MoneyRange,
    val slotIds: List<String> = emptyList(),
    val productId: String? = null,
    val article: String? = null,
    val priceSource: String? = null,
    val isUserPrice: Boolean = false
)

data class ProtectionCostEstimate(
    val lines: List<ProtectionEstimateLine>,
    val total: MoneyRange,
    val pricedDeviceCount: Int,
    val totalDeviceCount: Int,
    val catalogUpdatedAt: String,
    val exclusions: List<String>,
    val scope: EquipmentEstimateScope = EquipmentEstimateScope.PROTECTION_AND_CONTROL_DEVICES,
    val selectedProductCount: Int = 0,
    val staleSelectionSlotIds: List<String> = emptyList(),
    val concreteCatalogVersions: Set<String> = emptySet(),
    val auxiliaryLines: List<AuxiliaryEstimateLine> = emptyList()
) {
    val coveragePercent: Int
        get() = if (totalDeviceCount == 0) 0 else pricedDeviceCount * 100 / totalDeviceCount

    val isConcreteSelectionComplete: Boolean
        get() = totalDeviceCount > 0 && selectedProductCount == totalDeviceCount &&
            staleSelectionSlotIds.isEmpty()
}

data class AuxiliaryEstimateLine(
    val customModuleId: String,
    val designation: String,
    val title: String,
    val functionLabel: String,
    val price: MoneyRange,
    val isUserPrice: Boolean,
    val connectionDefined: Boolean
)

/** Единая смета всех аппаратов, видимых в проектном щите. */
data class PanelEquipmentEstimate(
    val protection: ProtectionCostEstimate,
    val auxiliaryLines: List<AuxiliaryEstimateLine>,
    val total: MoneyRange
) {
    val totalDeviceCount: Int
        get() = protection.totalDeviceCount + auxiliaryLines.size

    val undefinedConnectionCount: Int
        get() = auxiliaryLines.count { !it.connectionDefined }

    /** Совместимое представление для существующих карточек сметы экрана «Линии». */
    val unifiedProtectionView: ProtectionCostEstimate
        get() = protection.copy(
            total = total,
            pricedDeviceCount = protection.pricedDeviceCount + auxiliaryLines.size,
            totalDeviceCount = totalDeviceCount,
            selectedProductCount = protection.selectedProductCount + auxiliaryLines.size,
            auxiliaryLines = auxiliaryLines
        )
}

data class ProtectionPriceCatalog(
    val updatedAt: String,
    val entries: List<ProtectionPriceEntry>
)

data class ProtectionPriceEntry(
    val kind: ProtectionDeviceKind,
    val poles: Int,
    val minRatedCurrentA: Int,
    val maxRatedCurrentA: Int,
    val rcdType: RcdType? = null,
    val selective: Boolean? = null,
    val price: MoneyRange
)
