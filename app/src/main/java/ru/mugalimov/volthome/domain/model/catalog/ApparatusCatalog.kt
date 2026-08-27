package ru.mugalimov.volthome.domain.model.catalog

import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceKind
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec
import ru.mugalimov.volthome.domain.model.Phase

/**
 * Версионируемый офлайн-каталог конкретных аппаратов.
 *
 * Каталог хранит товарные предложения отдельно от расчётной модели. Расчёт
 * определяет требуемую функцию и параметры, а пользователь позднее выбирает
 * подходящую модель производителя для конкретного [slotId].
 */
data class ApparatusCatalog(
    val schemaVersion: Int,
    val catalogVersion: String,
    val publishedAt: String,
    val currency: String = "RUB",
    val products: List<ApparatusProduct>,
    val auxiliaryProducts: List<AuxiliaryApparatusProduct> = emptyList()
)

data class ApparatusProduct(
    val productId: String,
    val manufacturer: String,
    val series: String,
    val model: String,
    val article: String,
    val function: ProtectionDeviceKind,
    val spec: ProtectionDeviceSpec,
    val moduleUnits: Int,
    val status: ProductStatus = ProductStatus.ACTIVE,
    val price: ProductPrice
) {
    val displayName: String
        get() = listOf(manufacturer, series, model)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
}

enum class ProductStatus { ACTIVE, ARCHIVED }

data class ProductPrice(
    val range: MoneyRange,
    val sourceLabel: String,
    val updatedAt: String
)

/**
 * Дополнительный модуль щита, который пользователь добавляет в компоновку
 * вручную. Он участвует в раскладке и смете, но не меняет рассчитанные линии.
 */
data class AuxiliaryApparatusProduct(
    val productId: String,
    val manufacturer: String,
    val series: String,
    val model: String,
    val article: String,
    val function: AuxiliaryApparatusKind,
    val moduleUnits: Int,
    val ratedCurrentA: Int? = null,
    val poles: Int? = null,
    val summary: String,
    val characteristics: List<String> = emptyList(),
    val status: ProductStatus = ProductStatus.ACTIVE,
    val price: ProductPrice
) {
    val displayName: String
        get() = listOf(manufacturer, series, model)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
}

enum class AuxiliaryApparatusKind {
    VOLTAGE_RELAY,
    PHASE_CONTROL_RELAY,
    CURRENT_RELAY,
    MODULAR_CONTACTOR,
    SURGE_PROTECTION_DEVICE
}

/** Замороженная товарная позиция внутри проекта. */
data class SelectedAuxiliaryApparatusSnapshot(
    val customModuleId: String,
    val productId: String,
    val manufacturer: String,
    val series: String,
    val model: String,
    val article: String,
    val function: AuxiliaryApparatusKind,
    val moduleUnits: Int,
    val ratedCurrentA: Int?,
    val poles: Int?,
    val summary: String,
    val characteristics: List<String>,
    val catalogVersion: String,
    val catalogPrice: MoneyRange,
    val priceSource: String,
    val priceUpdatedAt: String,
    val userPriceKopecks: Long? = null,
    val userManufacturer: String? = null,
    val userModel: String? = null,
    val connection: AuxiliaryElectricalConnection = AuxiliaryElectricalConnection(),
    val addedAtEpochMs: Long
) {
    companion object {
        const val MAX_USER_PRICE_KOPECKS = 9_999_999_999L
        const val MAX_USER_MANUFACTURER_LENGTH = 60
        const val MAX_USER_MODEL_LENGTH = 100
    }

    val displayManufacturer: String
        get() = userManufacturer?.trim()?.takeIf(String::isNotBlank) ?: manufacturer

    val displayModel: String
        get() = userModel?.trim()?.takeIf(String::isNotBlank) ?: model

    val hasUserIdentity: Boolean
        get() = !userManufacturer.isNullOrBlank() || !userModel.isNullOrBlank()

    val displayName: String
        get() = if (hasUserIdentity) {
            listOf(displayManufacturer, displayModel)
        } else {
            listOf(manufacturer, series, model)
        }
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")

    val effectivePrice: MoneyRange
        get() = userPriceKopecks?.coerceAtLeast(0L)?.let { MoneyRange(it, it, it) }
            ?: catalogPrice
}

/**
 * Электрическое место ручного аппарата. Физическая позиция на DIN-рейке хранится
 * отдельно: перестановка корпуса не должна незаметно менять электрическую схему.
 */
data class AuxiliaryElectricalConnection(
    val point: AuxiliaryConnectionPoint = AuxiliaryConnectionPoint.UNASSIGNED,
    val phase: Phase? = null,
    val groupId: Long? = null
) {
    val isDefined: Boolean
        get() = point != AuxiliaryConnectionPoint.UNASSIGNED &&
            (point != AuxiliaryConnectionPoint.GROUP || groupId != null)
}

enum class AuxiliaryConnectionPoint {
    UNASSIGNED,
    PANEL_INPUT,
    AFTER_INCOMER,
    DISTRIBUTION_BUS,
    GROUP
}

/** Граница первой версии сметы: только аппараты защиты и управления. */
enum class EquipmentEstimateScope {
    PROTECTION_AND_CONTROL_DEVICES
}

/**
 * Снимок выбранной модели хранится внутри проекта и не зависит от будущих
 * обновлений каталога. Поэтому старый проект остаётся воспроизводимым, даже
 * если позиция снята с продажи или изменила цену.
 */
data class SelectedApparatusSnapshot(
    val slotId: String,
    val requiredSpec: ProtectionDeviceSpec,
    val productId: String,
    val productSpec: ProtectionDeviceSpec,
    val productStatus: ProductStatus,
    val manufacturer: String,
    val series: String,
    val model: String,
    val article: String,
    val catalogVersion: String,
    val moduleUnits: Int,
    val catalogPrice: MoneyRange,
    val priceSource: String,
    val priceUpdatedAt: String,
    val userPriceKopecks: Long? = null,
    val compatibility: ApparatusCompatibility,
    val compatibilityReasonCodes: List<CompatibilityReasonCode>,
    val selectedAtEpochMs: Long
) {
    val displayName: String
        get() = listOf(manufacturer, series, model)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")

    val effectivePrice: MoneyRange
        get() = userPriceKopecks?.coerceAtLeast(0L)?.let { MoneyRange(it, it, it) }
            ?: catalogPrice
}

enum class ApparatusCompatibility {
    EXACT,
    COMPATIBLE_WITH_DIFFERENCES,
    INCOMPATIBLE
}

enum class CompatibilityReasonCode {
    FUNCTION_MISMATCH,
    POLES_MISMATCH,
    RATED_CURRENT_MISMATCH,
    RATED_CURRENT_BELOW_REQUIRED,
    BREAKER_CURVE_MISMATCH,
    BREAKING_CAPACITY_BELOW_REQUIRED,
    LEAKAGE_CURRENT_MISMATCH,
    RCD_TYPE_MISMATCH,
    SELECTIVITY_MISMATCH,
    PRODUCT_ARCHIVED
}

data class CompatibilityDecision(
    val result: ApparatusCompatibility,
    val reasonCodes: List<CompatibilityReasonCode>
)
