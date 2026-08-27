package ru.mugalimov.volthome

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceKind
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec
import ru.mugalimov.volthome.domain.model.provider.JsonApparatusCatalogProvider
import ru.mugalimov.volthome.domain.use_case.FindCompatibleApparatusUseCase

@RunWith(AndroidJUnit4::class)
class ApparatusCatalogAssetTest {

    private val provider = JsonApparatusCatalogProvider(ApplicationProvider.getApplicationContext())

    @Test
    fun catalog_isValidAndContainsReleaseAssortment() {
        val catalog = provider.get()

        assertEquals("2026.08-structural-editor-1", catalog.catalogVersion)
        assertTrue(catalog.products.size >= 35)
        assertTrue(catalog.products.map { it.article }.distinct().size == catalog.products.size)
        assertTrue(catalog.auxiliaryProducts.size >= 7)
        assertTrue(catalog.auxiliaryProducts.map { it.article }.distinct().size ==
            catalog.auxiliaryProducts.size)
    }

    @Test
    fun commonC16Circuit_hasThreeCompatibleManufacturers() {
        val candidates = FindCompatibleApparatusUseCase(provider)(
            ProtectionDeviceSpec(
                kind = ProtectionDeviceKind.MCB,
                poles = 1,
                ratedCurrentA = 16,
                breakerCurve = "C"
            )
        )

        assertEquals(
            setOf("ABB", "IEK", "Schneider Electric"),
            candidates.map { it.product.manufacturer }.toSet()
        )
    }
}
