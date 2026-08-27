package ru.mugalimov.volthome.domain.use_case

import kotlin.math.max
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryApparatusKind
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryApparatusProduct
import ru.mugalimov.volthome.domain.model.catalog.SelectedAuxiliaryApparatusSnapshot
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryElectricalConnection
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryConnectionPoint
import ru.mugalimov.volthome.domain.model.panel.ModuleType
import ru.mugalimov.volthome.domain.model.panel.PanelAssembly
import ru.mugalimov.volthome.domain.model.panel.PanelCustomModuleSnapshot
import ru.mugalimov.volthome.domain.model.panel.PanelEnclosureConfig
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutRailSnapshot
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutSnapshot
import ru.mugalimov.volthome.domain.model.panel.PanelModule
import ru.mugalimov.volthome.domain.model.panel.PanelModuleSource
import ru.mugalimov.volthome.domain.model.panel.PanelMoveDirection
import ru.mugalimov.volthome.domain.model.panel.PanelRail
import ru.mugalimov.volthome.domain.model.panel.PanelVisualization
import javax.inject.Inject

/**
 * Чистая геометрия DIN-компоновки.
 *
 * Начиная со schema v2 движок оперирует отдельными физическими аппаратами. Электрически
 * связанные УЗО и автомат сохраняют общую [ru.mugalimov.volthome.domain.model.panel.PanelGroup],
 * но могут занимать и менять самостоятельные позиции на DIN-рейке.
 */
class PanelLayoutEngine @Inject constructor() {

    data class Reconciled(
        val panel: PanelVisualization,
        val snapshot: PanelLayoutSnapshot,
        val calculatedStructureChanged: Boolean
    )

    data class Mutation(
        val snapshot: PanelLayoutSnapshot,
        val message: String? = null,
        val changed: Boolean
    )

    data class DropTarget(
        val railIndex: Int,
        val itemIndex: Int
    )

    fun reconcile(
        calculated: PanelVisualization,
        saved: PanelLayoutSnapshot?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Reconciled {
        val calculatedItems = calculated.individualAssemblies()
            .associateBy(PanelAssembly::layoutBlockId)
        val customItems = saved?.customModules.orEmpty()
            .associate { it.blockId to it.toAssembly() }
        val allItems = calculatedItems + customItems
        val largestItem = allItems.values.maxOfOrNull(PanelAssembly::moduleUnits) ?: 1
        val defaultCapacity = calculated.rails.firstOrNull()?.capacityModuleUnits
            ?: PanelLayoutSnapshot.DEFAULT_RAIL_CAPACITY
        val requestedConfig = saved?.enclosure ?: PanelEnclosureConfig(
            modulesPerRail = max(defaultCapacity, largestItem)
                .coerceIn(
                    PanelEnclosureConfig.MIN_MODULES_PER_RAIL,
                    PanelEnclosureConfig.MAX_MODULES_PER_RAIL
                ),
            railCount = calculated.rails.size.coerceIn(
                PanelEnclosureConfig.MIN_RAIL_COUNT,
                PanelEnclosureConfig.MAX_RAIL_COUNT
            )
        )
        val config = requestedConfig.copy(
            modulesPerRail = max(requestedConfig.modulesPerRail, largestItem)
        )

        val defaultOrder = buildList {
            calculated.individualAssemblies().forEach { add(it.layoutBlockId) }
            saved?.customModules.orEmpty().forEach { add(it.blockId) }
        }
        val savedOrder = saved?.rails.orEmpty()
            .flatMap(PanelLayoutRailSnapshot::itemIds)
            .filter { it in allItems }
            .distinct()
        val order = savedOrder + defaultOrder.filter { it in allItems && it !in savedOrder }
        val widths = allItems.mapValues { it.value.moduleUnits }
        val packed = if (saved == null) {
            pack(order, widths, config, allowRailGrowth = true)
        } else {
            reconcilePlacement(saved, order, widths, config)
        }
        val rails = packed.rails.mapIndexed { index, ids ->
            PanelRail(
                number = index + 1,
                capacityModuleUnits = packed.enclosure.modulesPerRail,
                assemblies = ids.map(allItems::getValue)
            )
        }
        val fingerprint = fingerprint(calculatedItems.values)
        val normalizedSnapshot = PanelLayoutSnapshot(
            enclosure = packed.enclosure,
            rails = packed.rails.mapIndexed { index, ids ->
                PanelLayoutRailSnapshot(
                    id = saved?.rails?.getOrNull(index)?.id ?: "rail-${index + 1}",
                    itemIds = ids
                )
            },
            customModules = saved?.customModules.orEmpty(),
            baseFingerprint = fingerprint,
            updatedAtEpochMs = saved?.updatedAtEpochMs ?: nowEpochMs
        )
        return Reconciled(
            panel = PanelVisualization(rails = rails, groupsCount = calculated.groupsCount),
            snapshot = normalizedSnapshot,
            calculatedStructureChanged = saved != null && saved.baseFingerprint != fingerprint
        )
    }

    /**
     * Базовая операция будущего drag-and-drop: извлекает аппарат, вставляет в выбранную
     * позицию и каскадно сдвигает следующие аппараты вправо и на следующую рейку.
     */
    fun moveTo(
        snapshot: PanelLayoutSnapshot,
        itemId: String,
        target: DropTarget,
        itemWidths: Map<String, Int>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Mutation {
        val currentOrder = snapshot.rails.flatMap(PanelLayoutRailSnapshot::itemIds)
        val sourceIndex = currentOrder.indexOf(itemId)
        if (sourceIndex < 0) return Mutation(snapshot, "Аппарат не найден в компоновке", false)
        if (target.railIndex !in 0 until snapshot.enclosure.railCount) {
            return Mutation(snapshot, "Выбранная DIN-рейка не существует", false)
        }
        val railsWithoutSource = snapshot.rails.map { rail -> rail.itemIds - itemId }
        val targetRail = railsWithoutSource.getOrNull(target.railIndex).orEmpty()
        val boundedItemIndex = target.itemIndex.coerceIn(0, targetRail.size)
        var insertionIndex = railsWithoutSource
            .take(target.railIndex)
            .sumOf(List<String>::size) + boundedItemIndex
        val reordered = currentOrder.toMutableList().apply { removeAt(sourceIndex) }
        insertionIndex = insertionIndex.coerceIn(0, reordered.size)
        reordered.add(insertionIndex, itemId)
        if (reordered == currentOrder) return Mutation(snapshot, changed = false)

        val packed = pack(reordered, itemWidths, snapshot.enclosure, allowRailGrowth = true)
        return Mutation(
            snapshot = snapshot.withPacked(packed, nowEpochMs),
            changed = true
        )
    }

    /** Совместимая операция для кнопок редактора первого поколения. */
    fun move(
        snapshot: PanelLayoutSnapshot,
        blockId: String,
        direction: PanelMoveDirection,
        blockWidths: Map<String, Int>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Mutation {
        val rails = snapshot.rails.map { it.itemIds.toMutableList() }.toMutableList()
        val sourceRail = rails.indexOfFirst { blockId in it }
        if (sourceRail < 0) return Mutation(snapshot, "Аппарат не найден в компоновке", false)
        val sourceIndex = rails[sourceRail].indexOf(blockId)
        val target = when (direction) {
            PanelMoveDirection.LEFT -> {
                if (sourceIndex == 0) return Mutation(snapshot, "Аппарат уже первый на рейке", false)
                DropTarget(sourceRail, sourceIndex - 1)
            }
            PanelMoveDirection.RIGHT -> {
                if (sourceIndex == rails[sourceRail].lastIndex) {
                    return Mutation(snapshot, "Аппарат уже последний на рейке", false)
                }
                DropTarget(sourceRail, sourceIndex + 1)
            }
            PanelMoveDirection.UP -> {
                if (sourceRail == 0) return Mutation(snapshot, "Это верхняя DIN-рейка", false)
                return moveBetweenRails(
                    snapshot,
                    blockId,
                    sourceRail,
                    sourceRail - 1,
                    blockWidths,
                    nowEpochMs
                )
            }
            PanelMoveDirection.DOWN -> {
                val base = if (sourceRail == snapshot.enclosure.railCount - 1) {
                    growEnclosure(snapshot)
                        ?: return Mutation(snapshot, "Достигнуто максимальное число DIN-реек", false)
                } else snapshot
                return moveBetweenRails(
                    base,
                    blockId,
                    sourceRail,
                    sourceRail + 1,
                    blockWidths,
                    nowEpochMs
                )
            }
        }
        return moveTo(snapshot, blockId, target, blockWidths, nowEpochMs)
    }

    private fun moveBetweenRails(
        snapshot: PanelLayoutSnapshot,
        itemId: String,
        sourceRail: Int,
        targetRail: Int,
        itemWidths: Map<String, Int>,
        nowEpochMs: Long
    ): Mutation {
        val width = itemWidths[itemId]
            ?: return Mutation(snapshot, "Не удалось определить ширину аппарата", false)
        val rails = snapshot.rails.map { it.itemIds.toMutableList() }.toMutableList()
        val targetOccupied = rails[targetRail].sumOf { itemWidths[it] ?: 1 }
        if (targetOccupied + width > snapshot.enclosure.modulesPerRail) {
            return Mutation(snapshot, "На выбранной DIN-рейке недостаточно места", false)
        }
        rails[sourceRail].remove(itemId)
        rails[targetRail].add(itemId)
        return Mutation(
            snapshot = snapshot.copy(
                rails = rails.mapIndexed { index, ids ->
                    PanelLayoutRailSnapshot(
                        id = snapshot.rails[index].id,
                        itemIds = ids
                    )
                },
                updatedAtEpochMs = nowEpochMs
            ),
            changed = true
        )
    }

    /**
     * Меняет геометрию корпуса и полностью перепаковывает аппараты. Метод уже поддерживает
     * произвольное пользовательское число модулей, UI выбора появится на следующем этапе.
     */
    fun resizeEnclosure(
        snapshot: PanelLayoutSnapshot,
        modulesPerRail: Int,
        railCount: Int,
        itemWidths: Map<String, Int>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Mutation {
        if (modulesPerRail !in PanelEnclosureConfig.MIN_MODULES_PER_RAIL..
            PanelEnclosureConfig.MAX_MODULES_PER_RAIL
        ) {
            return Mutation(snapshot, "Допустимо от 4 до 72 модулей в ряду", false)
        }
        if (railCount !in PanelEnclosureConfig.MIN_RAIL_COUNT..PanelEnclosureConfig.MAX_RAIL_COUNT) {
            return Mutation(snapshot, "Допустимо от 1 до 12 DIN-реек", false)
        }
        val largest = itemWidths.values.maxOrNull() ?: 1
        if (largest > modulesPerRail) {
            return Mutation(snapshot, "Один из аппаратов шире выбранного ряда", false)
        }
        val requested = PanelEnclosureConfig(modulesPerRail, railCount)
        if (snapshot.enclosure == requested) {
            return Mutation(snapshot, "Этот размер уже выбран", false)
        }
        val order = snapshot.rails.flatMap(PanelLayoutRailSnapshot::itemIds)
        val requiredRails = requiredRailCount(order, itemWidths, modulesPerRail)
        if (requiredRails > railCount) {
            return Mutation(
                snapshot,
                "Для текущих аппаратов требуется минимум $requiredRails DIN-реек",
                false
            )
        }
        val packed = pack(order, itemWidths, requested, allowRailGrowth = false)
        return Mutation(
            snapshot = snapshot.withPacked(packed, nowEpochMs),
            message = "Размер щита обновлён",
            changed = snapshot.enclosure != requested
        )
    }

    fun addCustom(
        snapshot: PanelLayoutSnapshot,
        product: AuxiliaryApparatusProduct,
        customModuleId: String,
        designation: String,
        catalogVersion: String,
        blockWidths: Map<String, Int>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Mutation {
        if (snapshot.customModules.any { it.id == customModuleId }) {
            return Mutation(snapshot, "Аппарат с таким идентификатором уже существует", false)
        }
        if (product.moduleUnits > snapshot.enclosure.modulesPerRail) {
            return Mutation(snapshot, "Аппарат шире выбранного ряда", false)
        }
        val selected = SelectedAuxiliaryApparatusSnapshot(
            customModuleId = customModuleId,
            productId = product.productId,
            manufacturer = product.manufacturer,
            series = product.series,
            model = product.model,
            article = product.article,
            function = product.function,
            moduleUnits = product.moduleUnits,
            ratedCurrentA = product.ratedCurrentA,
            poles = product.poles,
            summary = product.summary,
            characteristics = product.characteristics,
            catalogVersion = catalogVersion,
            catalogPrice = product.price.range,
            priceSource = product.price.sourceLabel,
            priceUpdatedAt = product.price.updatedAt,
            addedAtEpochMs = nowEpochMs
        )
        val custom = PanelCustomModuleSnapshot(
            id = customModuleId,
            designation = designation,
            apparatus = selected
        )
        val widths = blockWidths + (custom.blockId to product.moduleUnits)
        val order = snapshot.rails.flatMap(PanelLayoutRailSnapshot::itemIds) + custom.blockId
        val packed = pack(order, widths, snapshot.enclosure, allowRailGrowth = true)
        return Mutation(
            snapshot = snapshot.copy(
                enclosure = packed.enclosure,
                rails = packed.toRailSnapshots(snapshot.rails),
                customModules = snapshot.customModules + custom,
                updatedAtEpochMs = nowEpochMs
            ),
            message = "${product.displayName} добавлен в компоновку",
            changed = true
        )
    }

    fun removeCustom(
        snapshot: PanelLayoutSnapshot,
        blockId: String,
        itemWidths: Map<String, Int> = emptyMap(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): Mutation {
        val custom = snapshot.customModules.firstOrNull { it.blockId == blockId }
            ?: return Mutation(snapshot, "Рассчитанный аппарат нельзя удалить", false)
        val order = snapshot.rails.flatMap(PanelLayoutRailSnapshot::itemIds) - blockId
        val widths = itemWidths + snapshot.customModules
            .filterNot { it.id == custom.id }
            .associate { it.blockId to it.apparatus.moduleUnits }
        val packed = pack(order, widths, snapshot.enclosure, allowRailGrowth = false)
        return Mutation(
            snapshot = snapshot.copy(
                rails = packed.toRailSnapshots(snapshot.rails),
                customModules = snapshot.customModules - custom,
                updatedAtEpochMs = nowEpochMs
            ),
            message = "Аппарат удалён из компоновки",
            changed = true
        )
    }

    fun updateCustomApparatus(
        snapshot: PanelLayoutSnapshot,
        customModuleId: String,
        userManufacturer: String?,
        userModel: String?,
        userPriceKopecks: Long?,
        connection: AuxiliaryElectricalConnection? = null,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Mutation {
        require(userPriceKopecks == null ||
            userPriceKopecks in 0..SelectedAuxiliaryApparatusSnapshot.MAX_USER_PRICE_KOPECKS
        ) { "Цена аппарата выходит за допустимый диапазон" }
        val normalizedManufacturer = userManufacturer
            ?.normalizeProjectLabel()
            ?.takeIf(String::isNotBlank)
            ?.also {
                require(it.length <= SelectedAuxiliaryApparatusSnapshot.MAX_USER_MANUFACTURER_LENGTH) {
                    "Название производителя слишком длинное"
                }
            }
        val normalizedModel = userModel
            ?.normalizeProjectLabel()
            ?.takeIf(String::isNotBlank)
            ?.also {
                require(it.length <= SelectedAuxiliaryApparatusSnapshot.MAX_USER_MODEL_LENGTH) {
                    "Название модели слишком длинное"
                }
            }
        var found = false
        var changed = false
        val updated = snapshot.customModules.map { custom ->
            if (custom.id != customModuleId) return@map custom
            found = true
            val apparatus = custom.apparatus.copy(
                userManufacturer = normalizedManufacturer,
                userModel = normalizedModel,
                userPriceKopecks = userPriceKopecks,
                connection = connection ?: custom.apparatus.connection
            )
            changed = changed || apparatus != custom.apparatus
            custom.copy(apparatus = apparatus)
        }
        return when {
            !found -> Mutation(snapshot, "Аппарат не найден", false)
            !changed -> Mutation(snapshot, "Данные аппарата не изменились", false)
            else -> Mutation(
                snapshot.copy(customModules = updated, updatedAtEpochMs = nowEpochMs),
                message = "Модель и цена аппарата сохранены",
                changed = true
            )
        }
    }

    fun updateCustomConnection(
        snapshot: PanelLayoutSnapshot,
        customModuleId: String,
        connection: AuxiliaryElectricalConnection,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Mutation {
        require(
            connection.point != AuxiliaryConnectionPoint.GROUP || connection.groupId != null
        ) { "Для подключения к линии выберите группу" }
        require(
            connection.point == AuxiliaryConnectionPoint.GROUP || connection.groupId == null
        ) { "Группа допустима только для подключения к линии" }

        var found = false
        var changed = false
        val updated = snapshot.customModules.map { custom ->
            if (custom.id != customModuleId) return@map custom
            found = true
            val apparatus = custom.apparatus.copy(connection = connection)
            changed = changed || apparatus != custom.apparatus
            custom.copy(apparatus = apparatus)
        }
        return when {
            !found -> Mutation(snapshot, "Аппарат не найден", false)
            !changed -> Mutation(snapshot, "Точка подключения не изменилась", false)
            else -> Mutation(
                snapshot.copy(customModules = updated, updatedAtEpochMs = nowEpochMs),
                message = "Точка подключения сохранена",
                changed = true
            )
        }
    }

    private data class PackedLayout(
        val enclosure: PanelEnclosureConfig,
        val rails: List<List<String>>
    ) {
        fun toRailSnapshots(previous: List<PanelLayoutRailSnapshot>): List<PanelLayoutRailSnapshot> =
            rails.mapIndexed { index, ids ->
                PanelLayoutRailSnapshot(
                    id = previous.getOrNull(index)?.id ?: "rail-${index + 1}",
                    itemIds = ids
                )
            }
    }

    private fun pack(
        order: List<String>,
        widths: Map<String, Int>,
        requested: PanelEnclosureConfig,
        allowRailGrowth: Boolean
    ): PackedLayout {
        val distinctOrder = order.distinct()
        distinctOrder.forEach { itemId ->
            val width = widths[itemId] ?: 1
            require(width in 1..requested.modulesPerRail) {
                "Аппарат $itemId шире выбранной DIN-рейки"
            }
        }
        val required = requiredRailCount(distinctOrder, widths, requested.modulesPerRail)
        val actualRailCount = if (allowRailGrowth) max(requested.railCount, required) else requested.railCount
        require(actualRailCount <= PanelEnclosureConfig.MAX_RAIL_COUNT) {
            "Для компоновки требуется больше ${PanelEnclosureConfig.MAX_RAIL_COUNT} DIN-реек"
        }
        val rails = MutableList(actualRailCount) { mutableListOf<String>() }
        var railIndex = 0
        var occupied = 0
        distinctOrder.forEach { itemId ->
            val width = widths[itemId] ?: 1
            if (occupied + width > requested.modulesPerRail) {
                railIndex++
                occupied = 0
            }
            rails[railIndex] += itemId
            occupied += width
        }
        return PackedLayout(
            enclosure = requested.copy(railCount = actualRailCount),
            rails = rails.map { it.toList() }
        )
    }

    /**
     * При обычном пересчёте сохраняет выбранные пользователем рейки. Новые и вытесненные
     * аппараты добавляет в первое доступное место; полная перепаковка выполняется только
     * явной операцией вставки или изменения корпуса.
     */
    private fun reconcilePlacement(
        saved: PanelLayoutSnapshot,
        order: List<String>,
        widths: Map<String, Int>,
        requested: PanelEnclosureConfig
    ): PackedLayout {
        val rails = MutableList(requested.railCount) { mutableListOf<String>() }
        val placed = linkedSetOf<String>()
        val pending = mutableListOf<String>()
        saved.rails.forEachIndexed { railIndex, rail ->
            rail.itemIds.forEach { itemId ->
                if (itemId !in order || !placed.add(itemId)) return@forEach
                val width = widths[itemId] ?: 1
                val occupied = rails.getOrNull(railIndex)?.sumOf { widths[it] ?: 1 }
                if (railIndex in rails.indices && occupied != null &&
                    occupied + width <= requested.modulesPerRail
                ) {
                    rails[railIndex] += itemId
                } else {
                    pending += itemId
                }
            }
        }
        pending += order.filter { it !in placed }
        pending.forEach { itemId ->
            val width = widths[itemId] ?: 1
            var target = rails.indexOfFirst { rail ->
                rail.sumOf { widths[it] ?: 1 } + width <= requested.modulesPerRail
            }
            if (target < 0) {
                require(rails.size < PanelEnclosureConfig.MAX_RAIL_COUNT) {
                    "Для компоновки требуется больше ${PanelEnclosureConfig.MAX_RAIL_COUNT} DIN-реек"
                }
                rails += mutableListOf<String>()
                target = rails.lastIndex
            }
            rails[target] += itemId
        }
        return PackedLayout(
            enclosure = requested.copy(railCount = rails.size),
            rails = rails.map { it.toList() }
        )
    }

    private fun requiredRailCount(
        order: List<String>,
        widths: Map<String, Int>,
        modulesPerRail: Int
    ): Int {
        var rails = 1
        var occupied = 0
        order.distinct().forEach { itemId ->
            val width = widths[itemId] ?: 1
            if (occupied + width > modulesPerRail) {
                rails++
                occupied = 0
            }
            occupied += width
        }
        return rails
    }

    private fun growEnclosure(snapshot: PanelLayoutSnapshot): PanelLayoutSnapshot? {
        if (snapshot.enclosure.railCount >= PanelEnclosureConfig.MAX_RAIL_COUNT) return null
        val grown = snapshot.enclosure.copy(railCount = snapshot.enclosure.railCount + 1)
        return snapshot.copy(
            enclosure = grown,
            rails = snapshot.rails + PanelLayoutRailSnapshot(
                id = "rail-${grown.railCount}",
                itemIds = emptyList()
            )
        )
    }

    private fun PanelLayoutSnapshot.withPacked(
        packed: PackedLayout,
        nowEpochMs: Long
    ): PanelLayoutSnapshot = copy(
        enclosure = packed.enclosure,
        rails = packed.toRailSnapshots(rails),
        updatedAtEpochMs = nowEpochMs
    )

    private fun PanelVisualization.individualAssemblies(): List<PanelAssembly> = rails
        .flatMap(PanelRail::assemblies)
        .flatMap { assembly ->
            assembly.modules.map { module ->
                PanelAssembly(
                    id = "${assembly.id}:${module.inventorySlotId}",
                    title = assembly.title,
                    modules = listOf(module),
                    group = assembly.group
                )
            }
        }

    private fun PanelCustomModuleSnapshot.toAssembly(): PanelAssembly {
        val product = apparatus
        return PanelAssembly(
            id = "custom-$id",
            title = product.function.displayTitle(),
            group = null,
            modules = listOf(
                PanelModule(
                    id = id,
                    inventorySlotId = id,
                    type = product.function.toModuleType(),
                    designation = designation,
                    label = product.displayName,
                    nominalCurrent = product.ratedCurrentA,
                    breakerCurve = null,
                    leakageCurrent = null,
                    poles = product.poles ?: 1,
                    moduleUnits = product.moduleUnits,
                    phase = null,
                    priceSpec = null,
                    source = PanelModuleSource.USER_ADDED,
                    auxiliarySnapshot = product
                )
            )
        )
    }

    private fun AuxiliaryApparatusKind.toModuleType(): ModuleType = when (this) {
        AuxiliaryApparatusKind.VOLTAGE_RELAY -> ModuleType.VOLTAGE_RELAY
        AuxiliaryApparatusKind.PHASE_CONTROL_RELAY -> ModuleType.PHASE_CONTROL_RELAY
        AuxiliaryApparatusKind.CURRENT_RELAY -> ModuleType.CURRENT_RELAY
        AuxiliaryApparatusKind.MODULAR_CONTACTOR -> ModuleType.MODULAR_CONTACTOR
        AuxiliaryApparatusKind.SURGE_PROTECTION_DEVICE -> ModuleType.SURGE_PROTECTION_DEVICE
    }

    private fun AuxiliaryApparatusKind.displayTitle(): String = when (this) {
        AuxiliaryApparatusKind.VOLTAGE_RELAY -> "Реле напряжения"
        AuxiliaryApparatusKind.PHASE_CONTROL_RELAY -> "Контроль фаз"
        AuxiliaryApparatusKind.CURRENT_RELAY -> "Реле тока"
        AuxiliaryApparatusKind.MODULAR_CONTACTOR -> "Контактор"
        AuxiliaryApparatusKind.SURGE_PROTECTION_DEVICE -> "УЗИП"
    }

    private fun String.normalizeProjectLabel(): String =
        trim().replace(Regex("\\s+"), " ")

    private fun fingerprint(assemblies: Collection<PanelAssembly>): String = assemblies
        .sortedBy(PanelAssembly::layoutBlockId)
        .joinToString("|") { "${it.layoutBlockId}:${it.moduleUnits}" }
}
