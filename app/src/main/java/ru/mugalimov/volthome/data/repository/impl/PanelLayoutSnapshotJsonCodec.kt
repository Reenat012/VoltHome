package ru.mugalimov.volthome.data.repository.impl

import com.google.gson.Gson
import com.google.gson.JsonParser
import ru.mugalimov.volthome.domain.model.panel.PanelCustomModuleSnapshot
import ru.mugalimov.volthome.domain.model.panel.PanelEnclosureConfig
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutRailSnapshot
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutSnapshot
import ru.mugalimov.volthome.domain.model.catalog.SelectedAuxiliaryApparatusSnapshot
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryElectricalConnection

/** Читает актуальные снимки и без потери пользовательского порядка поднимает schema v1 до v2. */
internal class PanelLayoutSnapshotJsonCodec(
    private val gson: Gson
) {
    fun encode(snapshot: PanelLayoutSnapshot): String = gson.toJson(snapshot)

    fun decode(json: String): PanelLayoutSnapshot? = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        when (root.get("schemaVersion")?.asInt ?: 1) {
            PanelLayoutSnapshot.CURRENT_SCHEMA_VERSION -> {
                val decoded = gson.fromJson(root, PanelLayoutSnapshot::class.java)
                decoded.takeIf(::isValidV2)
            }
            2 -> migrateV2(gson.fromJson(root, PanelLayoutSnapshot::class.java))
            1 -> migrateV1(gson.fromJson(root, LegacyPanelLayoutSnapshotV1::class.java))
            else -> null
        }
    }.getOrNull()

    private fun migrateV1(legacy: LegacyPanelLayoutSnapshotV1): PanelLayoutSnapshot {
        val modulesPerRail = legacy.railCapacityModuleUnits.coerceIn(
            PanelEnclosureConfig.MIN_MODULES_PER_RAIL,
            PanelEnclosureConfig.MAX_MODULES_PER_RAIL
        )
        val requestedRailCount = legacy.rails.size.coerceAtLeast(1)
        val railCount = requestedRailCount.coerceAtMost(PanelEnclosureConfig.MAX_RAIL_COUNT)
        val expandedRails = legacy.rails.map { rail ->
            rail.blockIds.flatMap(::expandLegacyBlockId)
        }
        val normalizedRails = MutableList(railCount) { index ->
            expandedRails.getOrNull(index).orEmpty().toMutableList()
        }
        if (expandedRails.size > railCount) {
            expandedRails.drop(railCount).forEach(normalizedRails.last()::addAll)
        }
        val placed = linkedSetOf<String>()
        return PanelLayoutSnapshot(
            enclosure = PanelEnclosureConfig(
                modulesPerRail = modulesPerRail,
                railCount = railCount
            ),
            rails = normalizedRails.mapIndexed { index, ids ->
                PanelLayoutRailSnapshot(
                    id = legacy.rails.getOrNull(index)?.id ?: "rail-${index + 1}",
                    itemIds = ids.filter(placed::add)
                )
            },
            customModules = normalizeLegacyCustomModules(legacy.customModules),
            baseFingerprint = legacy.baseFingerprint,
            updatedAtEpochMs = legacy.updatedAtEpochMs
        )
    }

    private fun migrateV2(legacy: PanelLayoutSnapshot): PanelLayoutSnapshot {
        val migrated = legacy.copy(
            schemaVersion = PanelLayoutSnapshot.CURRENT_SCHEMA_VERSION,
            customModules = normalizeLegacyCustomModules(legacy.customModules)
        )
        return migrated.takeIf(::isValidV2)
            ?: throw IllegalArgumentException("Invalid panel layout v2 snapshot")
    }

    @Suppress("SENSELESS_COMPARISON")
    private fun normalizeLegacyCustomModules(
        modules: List<PanelCustomModuleSnapshot>
    ): List<PanelCustomModuleSnapshot> = modules.map { custom ->
        val connection = custom.apparatus.connection ?: AuxiliaryElectricalConnection()
        custom.copy(apparatus = custom.apparatus.copy(connection = connection))
    }

    private fun expandLegacyBlockId(blockId: String): List<String> = when {
        blockId.startsWith("custom:") -> listOf(blockId)
        blockId.startsWith("calculated:") -> blockId
            .removePrefix("calculated:")
            .split('+')
            .filter(String::isNotBlank)
            .map { "calculated:$it" }
        else -> emptyList()
    }

    private fun isValidV2(snapshot: PanelLayoutSnapshot): Boolean =
        snapshot.schemaVersion == PanelLayoutSnapshot.CURRENT_SCHEMA_VERSION &&
            snapshot.enclosure.modulesPerRail in
            PanelEnclosureConfig.MIN_MODULES_PER_RAIL..PanelEnclosureConfig.MAX_MODULES_PER_RAIL &&
            snapshot.enclosure.railCount in
            PanelEnclosureConfig.MIN_RAIL_COUNT..PanelEnclosureConfig.MAX_RAIL_COUNT &&
            snapshot.rails.size == snapshot.enclosure.railCount &&
            snapshot.customModules.map(PanelCustomModuleSnapshot::id).let { ids ->
                ids.all(String::isNotBlank) && ids.distinct().size == ids.size
            } &&
            snapshot.customModules.all { custom ->
                val apparatus = custom.apparatus
                apparatus.customModuleId == custom.id &&
                    apparatus.moduleUnits in 1..snapshot.enclosure.modulesPerRail &&
                    apparatus.catalogPrice.minKopecks >= 0 &&
                    apparatus.catalogPrice.typicalKopecks >=
                    apparatus.catalogPrice.minKopecks &&
                    apparatus.catalogPrice.maxKopecks >=
                    apparatus.catalogPrice.typicalKopecks &&
                    (apparatus.userPriceKopecks == null ||
                        apparatus.userPriceKopecks in
                        0..SelectedAuxiliaryApparatusSnapshot.MAX_USER_PRICE_KOPECKS) &&
                    (apparatus.userManufacturer?.length ?: 0) <=
                    SelectedAuxiliaryApparatusSnapshot.MAX_USER_MANUFACTURER_LENGTH &&
                    (apparatus.userModel?.length ?: 0) <=
                    SelectedAuxiliaryApparatusSnapshot.MAX_USER_MODEL_LENGTH &&
                    apparatus.connection != null
            }

    private data class LegacyPanelLayoutSnapshotV1(
        val schemaVersion: Int = 1,
        val railCapacityModuleUnits: Int = PanelLayoutSnapshot.DEFAULT_RAIL_CAPACITY,
        val rails: List<LegacyPanelLayoutRailV1> = emptyList(),
        val customModules: List<PanelCustomModuleSnapshot> = emptyList(),
        val baseFingerprint: String = "",
        val updatedAtEpochMs: Long = 0L
    )

    private data class LegacyPanelLayoutRailV1(
        val id: String = "",
        val blockIds: List<String> = emptyList()
    )
}
