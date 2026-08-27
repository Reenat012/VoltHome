package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.catalog.ApparatusCompatibility
import ru.mugalimov.volthome.domain.model.catalog.ApparatusCompatibilityPolicy
import ru.mugalimov.volthome.domain.model.catalog.ApparatusProduct
import ru.mugalimov.volthome.domain.model.catalog.ProductPrice
import ru.mugalimov.volthome.domain.model.catalog.SelectedApparatusSnapshot
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.incomer.RcdSelectivity
import ru.mugalimov.volthome.domain.model.incomer.RcdType
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.model.pricing.ProtectionCostEstimate
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceKind
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec
import ru.mugalimov.volthome.domain.model.pricing.ProtectionEstimateLine
import ru.mugalimov.volthome.domain.model.pricing.ProtectionPriceEntry
import ru.mugalimov.volthome.domain.model.provider.ProtectionPriceCatalogProvider
import javax.inject.Inject

/**
 * Формирует ориентировочную стоимость только рассчитанных аппаратов защиты.
 * Кабель, корпус, шины, клеммы, работа и доставка намеренно не учитываются.
 */
class EstimateProtectionCostUseCase @Inject constructor(
    private val catalogProvider: ProtectionPriceCatalogProvider
) {
    operator fun invoke(
        incomer: IncomerSpec,
        groups: List<CircuitGroup>,
        selections: Map<String, SelectedApparatusSnapshot> = emptyMap()
    ): ProtectionCostEstimate {
        val inventory = ProtectionDeviceInventory.build(incomer, groups)
        val specs = inventory.map { it.spec }
        val catalog = catalogProvider.get()
        val lines = mutableListOf<ProtectionEstimateLine>()
        var pricedCount = 0
        var selectedProductCount = 0
        val staleSelectionSlotIds = mutableListOf<String>()
        val genericInventory = mutableListOf<ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceInventoryItem>()

        inventory.forEach { item ->
            val selection = selections[item.id]
            if (selection == null) {
                genericInventory += item
                return@forEach
            }

            val decision = ApparatusCompatibilityPolicy.evaluate(
                required = item.spec,
                product = selection.asProduct()
            )
            if (decision.result == ApparatusCompatibility.INCOMPATIBLE) {
                staleSelectionSlotIds += item.id
                genericInventory += item
                return@forEach
            }

            val price = selection.effectivePrice
            pricedCount += 1
            selectedProductCount += 1
            lines += ProtectionEstimateLine(
                spec = item.spec,
                title = selection.displayName.ifBlank { item.spec.displayTitle() },
                quantity = 1,
                unitPrice = price,
                totalPrice = price,
                slotIds = listOf(item.id),
                productId = selection.productId,
                article = selection.article,
                priceSource = if (selection.userPriceKopecks != null) {
                    "Цена пользователя"
                } else {
                    selection.priceSource
                },
                isUserPrice = selection.userPriceKopecks != null
            )
        }

        genericInventory.groupBy { it.spec }.forEach { (spec, items) ->
            val quantity = items.size
            val entry = bestMatch(spec, catalog.entries) ?: return@forEach
            pricedCount += quantity
            lines += ProtectionEstimateLine(
                spec = spec,
                title = spec.displayTitle(),
                quantity = quantity,
                unitPrice = entry.price,
                totalPrice = entry.price * quantity,
                slotIds = items.map { it.id },
                priceSource = "Усреднённый ценовой каталог"
            )
        }

        val sorted = lines.sortedWith(
            compareBy<ProtectionEstimateLine> { it.spec.kind.ordinal }
                .thenBy { it.spec.poles }
                .thenBy { it.spec.ratedCurrentA }
        )
        val total = sorted.fold(MoneyRange(0, 0, 0)) { acc, line ->
            MoneyRange(
                minKopecks = acc.minKopecks + line.totalPrice.minKopecks,
                typicalKopecks = acc.typicalKopecks + line.totalPrice.typicalKopecks,
                maxKopecks = acc.maxKopecks + line.totalPrice.maxKopecks
            )
        }

        return ProtectionCostEstimate(
            lines = sorted,
            total = total,
            pricedDeviceCount = pricedCount,
            totalDeviceCount = specs.size,
            catalogUpdatedAt = catalog.updatedAt,
            exclusions = listOf(
                "кабель и кабеленесущие системы",
                "корпус щита, шины, клеммы и аксессуары",
                "монтаж, доставка и региональные коэффициенты"
            ),
            selectedProductCount = selectedProductCount,
            staleSelectionSlotIds = staleSelectionSlotIds.sorted(),
            concreteCatalogVersions = selections.values
                .filter { it.slotId in inventory.map { item -> item.id }.toSet() }
                .map { it.catalogVersion }
                .toSet()
        )
    }

    private fun SelectedApparatusSnapshot.asProduct() = ApparatusProduct(
        productId = productId,
        manufacturer = manufacturer,
        series = series,
        model = model,
        article = article,
        function = productSpec.kind,
        spec = productSpec,
        moduleUnits = moduleUnits,
        status = productStatus,
        price = ProductPrice(catalogPrice, priceSource, priceUpdatedAt)
    )

    private fun bestMatch(
        spec: ProtectionDeviceSpec,
        entries: List<ProtectionPriceEntry>
    ): ProtectionPriceEntry? = entries
        .asSequence()
        .filter { entry ->
            entry.kind == spec.kind &&
                entry.poles == spec.poles &&
                spec.ratedCurrentA in entry.minRatedCurrentA..entry.maxRatedCurrentA &&
                (entry.rcdType == null || entry.rcdType == spec.rcdType) &&
                (entry.selective == null || entry.selective == (spec.selectivity == RcdSelectivity.S))
        }
        .maxByOrNull { entry ->
            (if (entry.rcdType != null) 2 else 0) + (if (entry.selective != null) 1 else 0)
        }

    private fun ProtectionDeviceSpec.displayTitle(): String {
        val poleLabel = when (poles) {
            1 -> "1P"
            2 -> "2P"
            3 -> "3P"
            4 -> "4P"
            else -> "${poles}P"
        }
        return when (kind) {
            ProtectionDeviceKind.MCB -> "Автомат $poleLabel ${breakerCurve.orEmpty()}$ratedCurrentA"
            ProtectionDeviceKind.RCD -> "УЗО $poleLabel $ratedCurrentA А / ${leakageCurrentMa ?: 30} мА, тип ${rcdType ?: RcdType.A}"
            ProtectionDeviceKind.RCBO -> "Дифавтомат $poleLabel ${breakerCurve.orEmpty()}$ratedCurrentA / ${leakageCurrentMa ?: 30} мА"
        }
    }
}
