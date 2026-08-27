package ru.mugalimov.volthome.ui.screens.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.mugalimov.volthome.domain.model.catalog.ApparatusCompatibility
import ru.mugalimov.volthome.domain.model.catalog.ApparatusProduct
import ru.mugalimov.volthome.domain.model.catalog.CompatibilityDecision
import ru.mugalimov.volthome.domain.model.catalog.ProductPrice
import ru.mugalimov.volthome.domain.model.panel.ModuleType
import ru.mugalimov.volthome.domain.model.panel.PanelModule
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceKind
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec
import ru.mugalimov.volthome.domain.use_case.ApparatusCandidate

class ApparatusSelectionSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compatibleProduct_isVisibleAndSelectable() {
        var selectedProductId: String? = null
        val spec = ProtectionDeviceSpec(
            kind = ProtectionDeviceKind.MCB,
            poles = 1,
            ratedCurrentA = 16,
            breakerCurve = "C",
            breakingCapacityA = 6_000
        )
        val product = ApparatusProduct(
            productId = "abb-s201-c16",
            manufacturer = "ABB",
            series = "System pro M compact",
            model = "S201-C16",
            article = "2CDS251001R0164",
            function = ProtectionDeviceKind.MCB,
            spec = spec,
            moduleUnits = 1,
            price = ProductPrice(
                range = MoneyRange(65_000, 90_000, 120_000),
                sourceLabel = "test",
                updatedAt = "2026-08-07"
            )
        )

        composeRule.setContent {
            MaterialTheme {
                Surface {
                    ApparatusSelectionSheet(
                        module = PanelModule(
                            id = "module",
                            inventorySlotId = "slot",
                            type = ModuleType.BREAKER,
                            designation = "QF1",
                            label = "Автомат группы",
                            nominalCurrent = 16,
                            breakerCurve = "C",
                            leakageCurrent = null,
                            poles = 1,
                            moduleUnits = 1,
                            phase = null,
                            priceSpec = spec
                        ),
                        currentSelection = null,
                        candidates = listOf(
                            ApparatusCandidate(
                                product,
                                CompatibilityDecision(ApparatusCompatibility.EXACT, emptyList())
                            )
                        ),
                        catalogPublishedAt = "2026-08-07",
                        isSaving = false,
                        onSelectProduct = { selectedProductId = it },
                        onSaveUserPrice = {},
                        onClearSelection = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("ABB").assertIsDisplayed()
        composeRule.onNodeWithText("Артикул 2CDS251001R0164").assertIsDisplayed()
        composeRule.onNodeWithText("Выбрать").performClick()

        composeRule.runOnIdle {
            assertEquals(product.productId, selectedProductId)
        }
    }
}
