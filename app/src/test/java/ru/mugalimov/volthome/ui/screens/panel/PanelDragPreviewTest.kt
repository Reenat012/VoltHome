package ru.mugalimov.volthome.ui.screens.panel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.mugalimov.volthome.domain.model.panel.ModuleType
import ru.mugalimov.volthome.domain.model.panel.PanelAssembly
import ru.mugalimov.volthome.domain.model.panel.PanelModule
import ru.mugalimov.volthome.domain.model.panel.PanelRail
import ru.mugalimov.volthome.domain.model.panel.PanelVisualization
import ru.mugalimov.volthome.domain.use_case.PanelLayoutEngine

class PanelDragPreviewTest {
    @Test
    fun `placement animation ignores non finite layout coordinates`() {
        assertNull(panelPlacementDelta(Offset.Zero, Offset(Float.NaN, Float.NEGATIVE_INFINITY)))
        assertNull(panelPlacementDelta(Offset(Float.POSITIVE_INFINITY, 0f), Offset.Zero))
    }

    @Test
    fun `placement animation uses finite position delta`() {
        assertEquals(
            Offset(20f, -15f),
            panelPlacementDelta(Offset(120f, 85f), Offset(100f, 100f))
        )
    }

    @Test
    fun `interrupted placement continues from current visual position`() {
        assertEquals(
            Offset(-60f, 120f),
            panelInterruptedPlacementDelta(
                previousTarget = Offset(100f, 200f),
                currentOffset = Offset(40f, -20f),
                newTarget = Offset(200f, 60f)
            )
        )
    }

    @Test
    fun `first measured placement does not animate from screen origin`() {
        assertNull(
            panelInterruptedPlacementDelta(
                previousTarget = null,
                currentOffset = Offset.Zero,
                newTarget = Offset(200f, 60f)
            )
        )
    }

    @Test
    fun `preview insertion uses one global order across rails`() {
        val panel = panelOf("a", "b", "c", "d")

        val order = previewPanelOrder(
            panel = panel,
            draggedId = "calculated:d",
            target = PanelLayoutEngine.DropTarget(railIndex = 0, itemIndex = 1)
        )

        assertEquals(
            listOf("calculated:a", "calculated:d", "calculated:b", "calculated:c"),
            order
        )
    }

    @Test
    fun `global preview index maps back to original drop target`() {
        val panel = panelOf("a", "b", "c", "d")

        val target = dropTargetForGlobalIndex(
            panel = panel,
            draggedId = "calculated:d",
            requestedIndex = 1
        )

        assertEquals(PanelLayoutEngine.DropTarget(0, 1), target)
    }

    @Test
    fun `global preview index does not count dragged item before target rail`() {
        val panel = panelOf("a", "b", "c", "d")

        val target = dropTargetForGlobalIndex(
            panel = panel,
            draggedId = "calculated:a",
            requestedIndex = 2
        )

        assertEquals(PanelLayoutEngine.DropTarget(0, 2), target)
    }

    @Test
    fun `drop target uses stable module geometry instead of animated item bounds`() {
        val panel = panelOf("a", "b", "c", "d")

        val target = resolvePanelDropTarget(
            pointerRoot = Offset(160f, 50f),
            draggedId = "calculated:d",
            panel = panel,
            previewRails = panel.rails,
            railGeometry = mapOf(
                0 to PanelRailDragGeometry(
                    viewportBounds = Rect(0f, 0f, 300f, 100f),
                    contentBounds = Rect(0f, 0f, 300f, 100f)
                ),
                1 to PanelRailDragGeometry(
                    viewportBounds = Rect(0f, 120f, 300f, 220f),
                    contentBounds = Rect(0f, 120f, 300f, 220f)
                )
            )
        )

        assertEquals(PanelLayoutEngine.DropTarget(0, 2), target)
    }

    private fun panelOf(vararg ids: String): PanelVisualization {
        val assemblies = ids.map(::assembly)
        return PanelVisualization(
            rails = listOf(
                PanelRail(1, 3, assemblies.take(3)),
                PanelRail(2, 3, assemblies.drop(3))
            ),
            groupsCount = ids.size
        )
    }

    private fun assembly(id: String) = PanelAssembly(
        id = id,
        title = id,
        group = null,
        modules = listOf(
            PanelModule(
                id = id,
                inventorySlotId = id,
                type = ModuleType.BREAKER,
                designation = "QF",
                label = id,
                nominalCurrent = 16,
                breakerCurve = "C",
                leakageCurrent = null,
                poles = 1,
                moduleUnits = 1,
                phase = null,
                priceSpec = null
            )
        )
    )
}
