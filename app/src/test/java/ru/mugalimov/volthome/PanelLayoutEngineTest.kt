package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryApparatusKind
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryApparatusProduct
import ru.mugalimov.volthome.domain.model.catalog.ProductPrice
import ru.mugalimov.volthome.domain.model.panel.ModuleType
import ru.mugalimov.volthome.domain.model.panel.PanelAssembly
import ru.mugalimov.volthome.domain.model.panel.PanelEnclosureConfig
import ru.mugalimov.volthome.domain.model.panel.PanelMoveDirection
import ru.mugalimov.volthome.domain.model.panel.PanelModule
import ru.mugalimov.volthome.domain.model.panel.PanelRail
import ru.mugalimov.volthome.domain.model.panel.PanelVisualization
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.use_case.PanelLayoutEngine

class PanelLayoutEngineTest {
    private val engine = PanelLayoutEngine()

    @Test
    fun `saved order survives regeneration of calculated panel`() {
        val base = panel(assembly("a", "slot-a"), assembly("b", "slot-b"))
        val initial = engine.reconcile(base, null, nowEpochMs = 1)
        val widths = initial.panel.widths()
        val moved = engine.move(
            initial.snapshot,
            blockId = "calculated:slot-b",
            direction = PanelMoveDirection.LEFT,
            blockWidths = widths,
            nowEpochMs = 2
        )

        assertTrue(moved.changed)
        val restored = engine.reconcile(base, moved.snapshot, nowEpochMs = 3)
        assertEquals(
            listOf("calculated:slot-b", "calculated:slot-a"),
            restored.snapshot.rails.single().itemIds
        )
    }

    @Test
    fun `custom apparatus remains when calculated structure changes`() {
        val base = panel(assembly("a", "slot-a"))
        val initial = engine.reconcile(base, null, nowEpochMs = 1)
        val added = engine.addCustom(
            snapshot = initial.snapshot,
            product = voltageRelay(),
            customModuleId = "custom-1",
            designation = "KV1",
            catalogVersion = "test",
            blockWidths = initial.panel.widths(),
            nowEpochMs = 2
        )
        val changedBase = panel(assembly("new", "slot-new"))

        val reconciled = engine.reconcile(changedBase, added.snapshot, nowEpochMs = 3)

        assertTrue(reconciled.calculatedStructureChanged)
        assertEquals(1, reconciled.snapshot.customModules.size)
        assertTrue(
            reconciled.snapshot.rails.flatMap { it.itemIds }.contains("custom:custom-1")
        )
        assertTrue(
            reconciled.snapshot.rails.flatMap { it.itemIds }.contains("calculated:slot-new")
        )
        assertFalse(
            reconciled.snapshot.rails.flatMap { it.itemIds }.contains("calculated:slot-a")
        )
    }

    @Test
    fun `custom apparatus identity and price survive reconciliation`() {
        val base = panel(assembly("a", "slot-a"))
        val initial = engine.reconcile(base, null, nowEpochMs = 1)
        val added = engine.addCustom(
            snapshot = initial.snapshot,
            product = voltageRelay(),
            customModuleId = "custom-1",
            designation = "KV1",
            catalogVersion = "test",
            blockWidths = initial.panel.widths(),
            nowEpochMs = 2
        )

        val updated = engine.updateCustomApparatus(
            snapshot = added.snapshot,
            customModuleId = "custom-1",
            userManufacturer = "  Мой   бренд  ",
            userModel = "  VH-63\nPRO  ",
            userPriceKopecks = 12_345L,
            nowEpochMs = 3
        )
        val restored = engine.reconcile(base, updated.snapshot, nowEpochMs = 4)
        val apparatus = restored.snapshot.customModules.single().apparatus
        val module = restored.panel.rails
            .flatMap { it.assemblies }
            .flatMap { it.modules }
            .single { it.isUserAdded }

        assertTrue(updated.changed)
        assertEquals("Мой бренд", apparatus.userManufacturer)
        assertEquals("VH-63 PRO", apparatus.userModel)
        assertEquals("Мой бренд VH-63 PRO", apparatus.displayName)
        assertEquals(12_345L, apparatus.effectivePrice.typicalKopecks)
        assertEquals("Мой бренд VH-63 PRO", module.label)
    }

    @Test
    fun `catalog identity is restored when custom fields are cleared`() {
        val base = panel(assembly("a", "slot-a"))
        val initial = engine.reconcile(base, null, nowEpochMs = 1)
        val added = engine.addCustom(
            snapshot = initial.snapshot,
            product = voltageRelay(),
            customModuleId = "custom-1",
            designation = "KV1",
            catalogVersion = "test",
            blockWidths = initial.panel.widths(),
            nowEpochMs = 2
        )
        val customized = engine.updateCustomApparatus(
            added.snapshot,
            "custom-1",
            "Custom",
            "Model",
            999L,
            nowEpochMs = 3
        )

        val restored = engine.updateCustomApparatus(
            customized.snapshot,
            "custom-1",
            null,
            null,
            null,
            nowEpochMs = 4
        )
        val apparatus = restored.snapshot.customModules.single().apparatus

        assertEquals("Test R 1", apparatus.displayName)
        assertEquals(apparatus.catalogPrice, apparatus.effectivePrice)
        assertFalse(apparatus.hasUserIdentity)
    }

    @Test
    fun `duplicate custom apparatus id is rejected without changing layout`() {
        val base = panel(assembly("a", "slot-a"))
        val initial = engine.reconcile(base, null, nowEpochMs = 1)
        val added = engine.addCustom(
            snapshot = initial.snapshot,
            product = voltageRelay(),
            customModuleId = "custom-1",
            designation = "KV1",
            catalogVersion = "test",
            blockWidths = initial.panel.widths(),
            nowEpochMs = 2
        )

        val duplicate = engine.addCustom(
            snapshot = added.snapshot,
            product = voltageRelay(),
            customModuleId = "custom-1",
            designation = "KV2",
            catalogVersion = "test",
            blockWidths = engine.reconcile(base, added.snapshot).panel.widths(),
            nowEpochMs = 3
        )

        assertFalse(duplicate.changed)
        assertEquals(added.snapshot, duplicate.snapshot)
        assertEquals(1, duplicate.snapshot.customModules.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom apparatus price above supported range is rejected`() {
        val base = panel(assembly("a", "slot-a"))
        val initial = engine.reconcile(base, null, nowEpochMs = 1)
        val added = engine.addCustom(
            snapshot = initial.snapshot,
            product = voltageRelay(),
            customModuleId = "custom-1",
            designation = "KV1",
            catalogVersion = "test",
            blockWidths = initial.panel.widths(),
            nowEpochMs = 2
        )

        engine.updateCustomApparatus(
            snapshot = added.snapshot,
            customModuleId = "custom-1",
            userManufacturer = null,
            userModel = null,
            userPriceKopecks = ru.mugalimov.volthome.domain.model.catalog
                .SelectedAuxiliaryApparatusSnapshot.MAX_USER_PRICE_KOPECKS + 1
        )
    }

    @Test
    fun `moving down creates rail and respects capacity`() {
        val base = panel(assembly("wide", "wide", width = 12))
        val initial = engine.reconcile(base, null, nowEpochMs = 1)

        val moved = engine.move(
            snapshot = initial.snapshot,
            blockId = "calculated:wide",
            direction = PanelMoveDirection.DOWN,
            blockWidths = initial.panel.widths(),
            nowEpochMs = 2
        )

        assertTrue(moved.changed)
        assertEquals(2, moved.snapshot.rails.size)
        assertTrue(moved.snapshot.rails.first().itemIds.isEmpty())
        assertEquals(listOf("calculated:wide"), moved.snapshot.rails.last().itemIds)
    }

    @Test
    fun `explicit rail placement survives reconciliation`() {
        val base = panel(assembly("wide", "wide", width = 12))
        val initial = engine.reconcile(base, null, nowEpochMs = 1)
        val moved = engine.move(
            snapshot = initial.snapshot,
            blockId = "calculated:wide",
            direction = PanelMoveDirection.DOWN,
            blockWidths = initial.panel.widths(),
            nowEpochMs = 2
        )

        val restored = engine.reconcile(base, moved.snapshot, nowEpochMs = 3)

        assertTrue(restored.snapshot.rails.first().itemIds.isEmpty())
        assertEquals(listOf("calculated:wide"), restored.snapshot.rails.last().itemIds)
    }

    @Test
    fun `only user added apparatus can be removed`() {
        val base = panel(assembly("a", "slot-a"))
        val initial = engine.reconcile(base, null, nowEpochMs = 1)
        val calculatedRemoval = engine.removeCustom(
            initial.snapshot,
            "calculated:slot-a",
            nowEpochMs = 2
        )
        assertFalse(calculatedRemoval.changed)

        val added = engine.addCustom(
            snapshot = initial.snapshot,
            product = voltageRelay(),
            customModuleId = "custom-1",
            designation = "KV1",
            catalogVersion = "test",
            blockWidths = initial.panel.widths(),
            nowEpochMs = 2
        )
        val removed = engine.removeCustom(
            added.snapshot,
            "custom:custom-1",
            added.snapshot.rails.flatMap { it.itemIds }.associateWith { 1 },
            nowEpochMs = 3
        )

        assertTrue(removed.changed)
        assertTrue(removed.snapshot.customModules.isEmpty())
        assertFalse(removed.snapshot.rails.flatMap { it.itemIds }.contains("custom:custom-1"))
    }

    @Test
    fun `electrically linked assembly is stored as individual physical apparatus`() {
        val linked = PanelAssembly(
            id = "group-1",
            title = "Группа 1",
            group = null,
            modules = listOf(
                module("rcd", "slot-rcd", width = 2, type = ModuleType.RCD),
                module("breaker", "slot-breaker", width = 1)
            )
        )

        val reconciled = engine.reconcile(panel(linked), null, nowEpochMs = 1)

        assertEquals(
            listOf("calculated:slot-rcd", "calculated:slot-breaker"),
            reconciled.snapshot.rails.single().itemIds
        )
        assertEquals(2, reconciled.panel.rails.single().assemblies.size)
    }

    @Test
    fun `drop insertion cascades neighbours to the next rail`() {
        val base = panel(
            assembly("a", "slot-a", width = 4),
            assembly("b", "slot-b", width = 4),
            assembly("c", "slot-c", width = 4),
            assembly("d", "slot-d", width = 4)
        )
        val initial = engine.reconcile(base, null, nowEpochMs = 1)

        val moved = engine.moveTo(
            snapshot = initial.snapshot,
            itemId = "calculated:slot-d",
            target = PanelLayoutEngine.DropTarget(railIndex = 0, itemIndex = 1),
            itemWidths = initial.panel.widths(),
            nowEpochMs = 2
        )

        assertTrue(moved.changed)
        assertEquals(
            listOf("calculated:slot-a", "calculated:slot-d", "calculated:slot-b"),
            moved.snapshot.rails[0].itemIds
        )
        assertEquals(listOf("calculated:slot-c"), moved.snapshot.rails[1].itemIds)
    }

    @Test
    fun `enclosure accepts arbitrary modules per rail and repacks items`() {
        val base = panel(
            assembly("a", "slot-a", width = 4),
            assembly("b", "slot-b", width = 3),
            assembly("c", "slot-c", width = 4)
        )
        val initial = engine.reconcile(base, null, nowEpochMs = 1)

        val resized = engine.resizeEnclosure(
            snapshot = initial.snapshot,
            modulesPerRail = 7,
            railCount = 2,
            itemWidths = initial.panel.widths(),
            nowEpochMs = 2
        )

        assertTrue(resized.changed)
        assertEquals(PanelEnclosureConfig(modulesPerRail = 7, railCount = 2), resized.snapshot.enclosure)
        assertEquals(2, resized.snapshot.rails.size)
        assertEquals(2, resized.snapshot.rails[0].itemIds.size)
        assertEquals(1, resized.snapshot.rails[1].itemIds.size)
    }

    @Test
    fun `resize rejects enclosure that cannot contain all apparatus`() {
        val base = panel(
            assembly("a", "slot-a", width = 4),
            assembly("b", "slot-b", width = 4),
            assembly("c", "slot-c", width = 4)
        )
        val initial = engine.reconcile(base, null, nowEpochMs = 1)

        val resized = engine.resizeEnclosure(
            snapshot = initial.snapshot,
            modulesPerRail = 7,
            railCount = 1,
            itemWidths = initial.panel.widths(),
            nowEpochMs = 2
        )

        assertFalse(resized.changed)
        assertTrue(resized.message.orEmpty().contains("минимум 3"))
    }

    @Test
    fun `resize leaves draft unchanged when enclosure size is already selected`() {
        val base = panel(assembly("a", "slot-a", width = 2))
        val initial = engine.reconcile(base, null, nowEpochMs = 1)

        val resized = engine.resizeEnclosure(
            snapshot = initial.snapshot,
            modulesPerRail = initial.snapshot.enclosure.modulesPerRail,
            railCount = initial.snapshot.enclosure.railCount,
            itemWidths = initial.panel.widths(),
            nowEpochMs = 2
        )

        assertFalse(resized.changed)
        assertEquals(initial.snapshot, resized.snapshot)
        assertEquals("Этот размер уже выбран", resized.message)
    }

    private fun panel(vararg assemblies: PanelAssembly) = PanelVisualization(
        rails = listOf(
            PanelRail(
                number = 1,
                capacityModuleUnits = 12,
                assemblies = assemblies.toList()
            )
        ),
        groupsCount = assemblies.size
    )

    private fun assembly(id: String, slotId: String, width: Int = 1) = PanelAssembly(
        id = id,
        title = id,
        group = null,
        modules = listOf(module(id, slotId, width))
    )

    private fun module(
        id: String,
        slotId: String,
        width: Int = 1,
        type: ModuleType = ModuleType.BREAKER
    ) = PanelModule(
        id = id,
        inventorySlotId = slotId,
        type = type,
        designation = "QF",
        label = id,
        nominalCurrent = 16,
        breakerCurve = "C",
        leakageCurrent = null,
        poles = width,
        moduleUnits = width,
        phase = null,
        priceSpec = null
    )

    private fun PanelVisualization.widths(): Map<String, Int> = rails
        .flatMap { it.assemblies }
        .associate { it.layoutBlockId to it.moduleUnits }

    private fun voltageRelay() = AuxiliaryApparatusProduct(
        productId = "relay",
        manufacturer = "Test",
        series = "R",
        model = "1",
        article = "R1",
        function = AuxiliaryApparatusKind.VOLTAGE_RELAY,
        moduleUnits = 2,
        ratedCurrentA = 63,
        poles = 1,
        summary = "Реле напряжения",
        price = ProductPrice(
            range = MoneyRange(100, 200, 300),
            sourceLabel = "test",
            updatedAt = "2026-08-10"
        )
    )
}
