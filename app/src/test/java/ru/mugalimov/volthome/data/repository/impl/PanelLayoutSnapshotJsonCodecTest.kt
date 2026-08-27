package ru.mugalimov.volthome.data.repository.impl

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryApparatusKind
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryConnectionPoint
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryElectricalConnection
import ru.mugalimov.volthome.domain.model.catalog.SelectedAuxiliaryApparatusSnapshot
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.panel.PanelCustomModuleSnapshot
import ru.mugalimov.volthome.domain.model.panel.PanelEnclosureConfig
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutRailSnapshot
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutSnapshot
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange

class PanelLayoutSnapshotJsonCodecTest {
    private val codec = PanelLayoutSnapshotJsonCodec(Gson())

    @Test
    fun `v1 grouped blocks migrate to individual v2 apparatus preserving order`() {
        val legacyJson = """
            {
              "schemaVersion": 1,
              "railCapacityModuleUnits": 18,
              "rails": [
                {
                  "id": "rail-a",
                  "blockIds": [
                    "calculated:incomer-rcd+incomer-breaker",
                    "custom:relay-1",
                    "calculated:group-1-rcd+group-1-breaker"
                  ]
                },
                { "id": "rail-b", "blockIds": [] }
              ],
              "customModules": [],
              "baseFingerprint": "legacy",
              "updatedAtEpochMs": 42
            }
        """.trimIndent()

        val migrated = codec.decode(legacyJson)

        assertNotNull(migrated)
        assertEquals(PanelLayoutSnapshot.CURRENT_SCHEMA_VERSION, migrated?.schemaVersion)
        assertEquals(18, migrated?.enclosure?.modulesPerRail)
        assertEquals(2, migrated?.enclosure?.railCount)
        assertEquals(
            listOf(
                "calculated:incomer-rcd",
                "calculated:incomer-breaker",
                "custom:relay-1",
                "calculated:group-1-rcd",
                "calculated:group-1-breaker"
            ),
            migrated?.rails?.first()?.itemIds
        )
        assertEquals("rail-b", migrated?.rails?.last()?.id)
    }

    @Test
    fun `v2 snapshot written before custom identity fields remains readable`() {
        val oldV2Json = """
            {
              "schemaVersion": 2,
              "enclosure": { "modulesPerRail": 12, "railCount": 1 },
              "rails": [{ "id": "rail-1", "itemIds": ["custom:relay-1"] }],
              "customModules": [{
                "id": "relay-1",
                "designation": "KV1",
                "apparatus": {
                  "customModuleId": "relay-1",
                  "productId": "relay",
                  "manufacturer": "Test",
                  "series": "R",
                  "model": "1",
                  "article": "R1",
                  "function": "VOLTAGE_RELAY",
                  "moduleUnits": 2,
                  "ratedCurrentA": 63,
                  "poles": 1,
                  "summary": "Реле напряжения",
                  "characteristics": [],
                  "catalogVersion": "test",
                  "catalogPrice": {
                    "minKopecks": 100,
                    "typicalKopecks": 200,
                    "maxKopecks": 300
                  },
                  "priceSource": "test",
                  "priceUpdatedAt": "2026-08-10",
                  "userPriceKopecks": null,
                  "addedAtEpochMs": 1
                }
              }],
              "baseFingerprint": "existing",
              "updatedAtEpochMs": 42
            }
        """.trimIndent()

        val decoded = codec.decode(oldV2Json)

        assertNotNull(decoded)
        assertEquals("existing", decoded?.baseFingerprint)
        assertEquals("Test R 1", decoded?.customModules?.single()?.apparatus?.displayName)
        assertNull(decoded?.customModules?.single()?.apparatus?.userManufacturer)
        assertNull(decoded?.customModules?.single()?.apparatus?.userModel)
        assertEquals(false, decoded?.customModules?.single()?.apparatus?.connection?.isDefined)
    }

    @Test
    fun `custom manufacturer model and price survive json round trip`() {
        val snapshot = PanelLayoutSnapshot(
            enclosure = PanelEnclosureConfig(modulesPerRail = 18, railCount = 2),
            rails = listOf(
                PanelLayoutRailSnapshot("rail-1", listOf("custom:relay-1")),
                PanelLayoutRailSnapshot("rail-2", emptyList())
            ),
            customModules = listOf(
                PanelCustomModuleSnapshot(
                    id = "relay-1",
                    designation = "KV1",
                    apparatus = SelectedAuxiliaryApparatusSnapshot(
                        customModuleId = "relay-1",
                        productId = "relay",
                        manufacturer = "Catalog brand",
                        series = "R",
                        model = "Base",
                        article = "R1",
                        function = AuxiliaryApparatusKind.VOLTAGE_RELAY,
                        moduleUnits = 2,
                        ratedCurrentA = 63,
                        poles = 1,
                        summary = "Реле напряжения",
                        characteristics = emptyList(),
                        catalogVersion = "test",
                        catalogPrice = MoneyRange(100, 200, 300),
                        priceSource = "test",
                        priceUpdatedAt = "2026-08-10",
                        userPriceKopecks = 123_450,
                        userManufacturer = "Project brand",
                        userModel = "VH-63",
                        connection = AuxiliaryElectricalConnection(
                            point = AuxiliaryConnectionPoint.GROUP,
                            phase = Phase.B,
                            groupId = 77L
                        ),
                        addedAtEpochMs = 1
                    )
                )
            ),
            baseFingerprint = "round-trip",
            updatedAtEpochMs = 42
        )

        val decoded = codec.decode(codec.encode(snapshot))
        val apparatus = decoded?.customModules?.single()?.apparatus

        assertNotNull(decoded)
        assertEquals("Project brand", apparatus?.userManufacturer)
        assertEquals("VH-63", apparatus?.userModel)
        assertEquals(123_450L, apparatus?.userPriceKopecks)
        assertEquals("Project brand VH-63", apparatus?.displayName)
        assertEquals(AuxiliaryConnectionPoint.GROUP, apparatus?.connection?.point)
        assertEquals(Phase.B, apparatus?.connection?.phase)
        assertEquals(77L, apparatus?.connection?.groupId)
    }

    @Test
    fun `snapshot with duplicate custom apparatus ids is rejected`() {
        val validJson = codec.encode(snapshotWithCustomApparatus())
        val duplicatedJson = validJson.replace(
            "\"customModules\":[",
            "\"customModules\":[${Gson().toJson(snapshotWithCustomApparatus().customModules.single())},"
        )

        assertNull(codec.decode(duplicatedJson))
    }

    private fun snapshotWithCustomApparatus() = PanelLayoutSnapshot(
        enclosure = PanelEnclosureConfig(modulesPerRail = 18, railCount = 1),
        rails = listOf(PanelLayoutRailSnapshot("rail-1", listOf("custom:relay-1"))),
        customModules = listOf(
            PanelCustomModuleSnapshot(
                id = "relay-1",
                designation = "KV1",
                apparatus = SelectedAuxiliaryApparatusSnapshot(
                    customModuleId = "relay-1",
                    productId = "relay",
                    manufacturer = "Test",
                    series = "R",
                    model = "Base",
                    article = "R1",
                    function = AuxiliaryApparatusKind.VOLTAGE_RELAY,
                    moduleUnits = 2,
                    ratedCurrentA = 63,
                    poles = 1,
                    summary = "Реле напряжения",
                    characteristics = emptyList(),
                    catalogVersion = "test",
                    catalogPrice = MoneyRange(100, 200, 300),
                    priceSource = "test",
                    priceUpdatedAt = "2026-08-10",
                    addedAtEpochMs = 1
                )
            )
        ),
        baseFingerprint = "valid",
        updatedAtEpochMs = 1
    )
}
