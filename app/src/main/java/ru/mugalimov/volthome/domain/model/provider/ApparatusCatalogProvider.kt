package ru.mugalimov.volthome.domain.model.provider

import ru.mugalimov.volthome.domain.model.catalog.ApparatusCatalog

interface ApparatusCatalogProvider {
    fun get(): ApparatusCatalog
}
