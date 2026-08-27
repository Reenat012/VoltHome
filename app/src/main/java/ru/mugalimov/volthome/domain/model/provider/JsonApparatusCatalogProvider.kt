package ru.mugalimov.volthome.domain.model.provider

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.mugalimov.volthome.domain.model.catalog.ApparatusCatalog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonApparatusCatalogProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : ApparatusCatalogProvider {
    private val catalog by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.assets.open(ASSET_NAME).use { input ->
            input.bufferedReader().use {
                Gson().fromJson(it, ApparatusCatalog::class.java).also(::validate)
            }
        }
    }

    override fun get(): ApparatusCatalog = catalog

    private fun validate(value: ApparatusCatalog) {
        require(value.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported apparatus catalog schema ${value.schemaVersion}"
        }
        require(value.catalogVersion.isNotBlank()) { "Catalog version must not be blank" }
        require(value.currency == "RUB") { "Only RUB catalog is supported" }
        require(value.products.map { it.productId }.distinct().size == value.products.size) {
            "Catalog contains duplicate productId"
        }
        val allProductIds = value.products.map { it.productId } +
            value.auxiliaryProducts.map { it.productId }
        require(allProductIds.distinct().size == allProductIds.size) {
            "Catalog contains duplicate productId across sections"
        }
        value.products.forEach { product ->
            require(product.productId.isNotBlank()) { "productId must not be blank" }
            require(product.manufacturer.isNotBlank()) { "manufacturer must not be blank" }
            require(product.article.isNotBlank()) { "article must not be blank" }
            require(product.function == product.spec.kind) { "Product function/spec mismatch" }
            require(product.moduleUnits > 0) { "moduleUnits must be positive" }
            val price = product.price.range
            require(price.minKopecks >= 0 &&
                price.minKopecks <= price.typicalKopecks &&
                price.typicalKopecks <= price.maxKopecks
            ) { "Invalid price range for ${product.productId}" }
        }
        value.auxiliaryProducts.forEach { product ->
            require(product.productId.isNotBlank()) { "productId must not be blank" }
            require(product.manufacturer.isNotBlank()) { "manufacturer must not be blank" }
            require(product.article.isNotBlank()) { "article must not be blank" }
            require(product.summary.isNotBlank()) { "summary must not be blank" }
            require(product.moduleUnits > 0) { "moduleUnits must be positive" }
            val price = product.price.range
            require(price.minKopecks >= 0 &&
                price.minKopecks <= price.typicalKopecks &&
                price.typicalKopecks <= price.maxKopecks
            ) { "Invalid price range for ${product.productId}" }
        }
    }

    private companion object {
        const val ASSET_NAME = "apparatus_catalog.json"
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}
