package ru.mugalimov.volthome.domain.model.provider

import ru.mugalimov.volthome.domain.model.pricing.ProtectionPriceCatalog

interface ProtectionPriceCatalogProvider {
    fun get(): ProtectionPriceCatalog
}
