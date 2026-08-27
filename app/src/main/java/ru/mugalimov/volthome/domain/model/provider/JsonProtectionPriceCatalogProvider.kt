package ru.mugalimov.volthome.domain.model.provider

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.mugalimov.volthome.domain.model.pricing.ProtectionPriceCatalog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonProtectionPriceCatalogProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : ProtectionPriceCatalogProvider {
    private val catalog by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.assets.open(ASSET_NAME).use { input ->
            input.bufferedReader().use { Gson().fromJson(it, ProtectionPriceCatalog::class.java) }
        }
    }

    override fun get(): ProtectionPriceCatalog = catalog

    private companion object {
        const val ASSET_NAME = "protection_price_catalog.json"
    }
}
