package ru.mugalimov.volthome

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.data.repository.PanelEquipmentRepository
import ru.mugalimov.volthome.domain.model.catalog.ApparatusCatalog
import ru.mugalimov.volthome.domain.model.catalog.ApparatusProduct
import ru.mugalimov.volthome.domain.model.catalog.ProductPrice
import ru.mugalimov.volthome.domain.model.catalog.SelectedApparatusSnapshot
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceKind
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec
import ru.mugalimov.volthome.domain.model.provider.ApparatusCatalogProvider
import ru.mugalimov.volthome.domain.use_case.SelectApparatusProductUseCase

class SelectApparatusProductUseCaseTest {
    private val spec = ProtectionDeviceSpec(ProtectionDeviceKind.MCB, 1, 16, "C", 6_000)
    private val product = ApparatusProduct(
        productId = "brand-series-c16",
        manufacturer = "Brand",
        series = "Series",
        model = "C16",
        article = "SKU-16",
        function = ProtectionDeviceKind.MCB,
        spec = spec,
        moduleUnits = 1,
        price = ProductPrice(MoneyRange(60_000, 70_000, 80_000), "test", "2026-08-07")
    )
    private val catalog = ApparatusCatalog(1, "catalog-v1", "2026-08-07", products = listOf(product))

    @Test
    fun `selection is frozen from catalog product`() = runBlocking {
        val repository = FakeRepository()
        val useCase = SelectApparatusProductUseCase(repository, provider())

        val result = useCase(
            SelectApparatusProductUseCase.Params("project", "slot", spec, product.productId)
        )

        assertTrue(result.saved)
        assertEquals("catalog-v1", repository.saved?.catalogVersion)
        assertEquals("SKU-16", repository.saved?.article)
        assertEquals(spec, repository.saved?.productSpec)
    }

    @Test
    fun `unknown product cannot be persisted`() = runBlocking {
        val repository = FakeRepository()
        val useCase = SelectApparatusProductUseCase(repository, provider())

        val result = useCase(
            SelectApparatusProductUseCase.Params("project", "slot", spec, "missing")
        )

        assertFalse(result.saved)
        assertEquals(SelectApparatusProductUseCase.SelectionFailure.PRODUCT_NOT_FOUND, result.failure)
        assertNull(repository.saved)
    }

    private fun provider() = object : ApparatusCatalogProvider {
        override fun get(): ApparatusCatalog = catalog
    }

    private class FakeRepository : PanelEquipmentRepository {
        var saved: SelectedApparatusSnapshot? = null

        override fun observeSelections(projectId: String): Flow<Map<String, SelectedApparatusSnapshot>> =
            flowOf(saved?.let { mapOf(it.slotId to it) }.orEmpty())

        override suspend fun getSelections(projectId: String) =
            saved?.let { mapOf(it.slotId to it) }.orEmpty()

        override suspend fun saveSelection(projectId: String, snapshot: SelectedApparatusSnapshot) {
            saved = snapshot
        }

        override suspend fun setUserPrice(projectId: String, slotId: String, priceKopecks: Long?) = Unit
        override suspend fun removeSelection(projectId: String, slotId: String) = Unit
        override suspend fun clearProject(projectId: String) = Unit
    }
}
