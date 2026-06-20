package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.singleline.SingleLineBus
import ru.mugalimov.volthome.domain.model.singleline.SingleLineBusType
import ru.mugalimov.volthome.domain.model.singleline.SingleLineDeviceSummary
import ru.mugalimov.volthome.domain.model.singleline.SingleLineDiagram
import ru.mugalimov.volthome.domain.model.singleline.SingleLineGroupBlock
import ru.mugalimov.volthome.domain.model.singleline.SingleLineInputBlock
import ru.mugalimov.volthome.domain.model.singleline.SingleLinePhaseSection
import ru.mugalimov.volthome.domain.model.singleline.SingleLineProtectionBlock
import ru.mugalimov.volthome.domain.model.singleline.SingleLineProtectionType
import javax.inject.Inject

/**
 * Генерирует доменную модель однолинейной схемы
 * ИСКЛЮЧИТЕЛЬНО из уже готовой экспликации.
 *
 * Важно:
 * - use-case ничего не рассчитывает;
 * - не изменяет группы;
 * - не вызывает расчётный движок;
 * - не распределяет фазы;
 * - не выбирает автоматы/кабели.
 */
class GenerateSingleLineDiagramUseCase @Inject constructor() {

    operator fun invoke(
        projectName: String,
        phaseMode: PhaseMode,
        groups: List<CircuitGroup>,
        incomer: IncomerSpec?,
        totalInstalledPowerWatts: Double?,
        totalCalculatedPowerWatts: Double?,
        totalCurrentAmps: Double?,
        generatedAtMillis: Long = System.currentTimeMillis()
    ): SingleLineDiagram {

        // Визуальная N-шина
        val neutralBus = SingleLineBus(
            type = SingleLineBusType.NEUTRAL,
            label = "N"
        )

        // Визуальная PE-шина
        val protectiveEarthBus = SingleLineBus(
            type = SingleLineBusType.PROTECTIVE_EARTH,
            label = "PE"
        )

        // Вводной блок схемы
        val inputBlock = SingleLineInputBlock(
            title = when (phaseMode) {
                PhaseMode.SINGLE -> "Ввод 1ф"
                PhaseMode.THREE -> "Ввод 3ф"
            },
            incomerLabel = incomer?.let {
                "${it.mcbCurve}${it.mcbRating}"
            },
            incomerNominalCurrentLabel = incomer?.mcbRating?.toString(),
            totalInstalledPowerWatts = totalInstalledPowerWatts,
            totalCalculatedPowerWatts = totalCalculatedPowerWatts,
            totalCurrentAmps = totalCurrentAmps
        )

        // Блок вводного автомата.
        // Создаётся только если данные реально есть.
        val protectionBlocks = buildProtectionBlocks(incomer)

        // Группируем существующие группы по фазам.
        // Важно:
        // здесь НЕТ перераспределения фаз.
        val phaseSections = groups
            .groupBy { it.phase }
            .map { (phase, phaseGroups) ->

                val mappedGroups = phaseGroups.map { group ->
                    mapGroup(group)
                }

                SingleLinePhaseSection(
                    phase = phase,
                    phaseBus = SingleLineBus(
                        type = SingleLineBusType.PHASE,
                        label = phase.toSingleLineBusLabel(),
                        phase = phase
                    ),
                    totalCurrentAmps = phaseGroups.sumOf { it.nominalCurrent },
                    totalInstalledPowerWatts = phaseGroups.sumOf {
                        it.installedPowerW.toDouble()
                    },
                    totalCalculatedPowerWatts = null,
                    groups = mappedGroups
                )
            }
            .sortedBy { section ->
                when (section.phase) {
                    Phase.A -> 0
                    Phase.B -> 1
                    Phase.C -> 2
                    Phase.THREE_PHASE -> 3
                }
            }

        return SingleLineDiagram(
            projectName = projectName,
            phaseMode = phaseMode,
            input = inputBlock,
            protectionBlocks = protectionBlocks,
            phaseSections = phaseSections,
            neutralBus = neutralBus,
            protectiveEarthBus = protectiveEarthBus,
            generatedAtMillis = generatedAtMillis
        )
    }

    /**
     * Маппинг группы экспликации в блок схемы.
     *
     * Здесь запрещены:
     * - расчёты;
     * - подбор автомата;
     * - подбор кабеля;
     * - изменение токов;
     * - изменение фаз.
     */
    private fun mapGroup(
        group: CircuitGroup
    ): SingleLineGroupBlock {

        val warnings = buildList {

            // Кабель отсутствует
            if (group.cableSection <= 0.0) {
                add("Кабель не указан")
            }

            // Ток отсутствует
            if (group.nominalCurrent <= 0.0) {
                add("Расчётный ток отсутствует")
            }
        }

        return SingleLineGroupBlock(
            groupId = group.groupId,
            groupNumber = group.groupNumber,
            groupName = group.groupType.name,
            phase = group.phase,
            roomNames = listOf(group.roomName),
            devices = group.devices.map(::mapDevice),
            installedPowerWatts = group.installedPowerW.toDouble(),
            calculatedPowerWatts = null,
            calculatedCurrentAmps = group.nominalCurrent,
            breakerLabel = buildBreakerLabel(group),
            cableLabel = buildCableLabel(group),
            rcdLabel = buildRcdLabel(group),
            leakageCurrentMilliAmps = if (group.rcdRequired) {
                group.rcdCurrent
            } else {
                null
            },
            warnings = warnings
        )
    }

    /**
     * Компактное описание устройства для PDF-схемы.
     */
    private fun mapDevice(
        device: Device
    ): SingleLineDeviceSummary {

        return SingleLineDeviceSummary(
            deviceId = device.id,
            name = device.name,
            roomName = null,
            type = device.deviceType,
            powerWatts = device.power.toDouble(),
            calculatedPowerWatts = null,
            calculatedCurrentAmps = device.current
        )
    }

    /**
     * Создаёт блок вводной защиты,
     * только если данные реально существуют.
     */
    private fun buildProtectionBlocks(
        incomer: IncomerSpec?
    ): List<SingleLineProtectionBlock> {

        if (incomer == null) {
            return emptyList()
        }

        return listOf(
            SingleLineProtectionBlock(
                id = "input_breaker",
                title = "Вводной автомат",
                type = SingleLineProtectionType.INPUT_BREAKER,
                phase = null,
                nominalCurrentAmps = incomer.mcbRating.toDouble(),
                leakageCurrentMilliAmps = incomer.rcdSensitivityMa,
                description = buildString {
                    append("${incomer.mcbCurve}${incomer.mcbRating}")

                    incomer.rcdSensitivityMa?.let {
                        append(" / ${it}мА")
                    }
                }
            )
        )
    }

    /**
     * Формирует label автомата
     * исключительно из уже готовых данных группы.
     */
    private fun buildBreakerLabel(
        group: CircuitGroup
    ): String {

        return "${group.breakerType}${group.circuitBreaker}"
    }

    /**
     * Формирует label кабеля
     * исключительно из уже готовых данных группы.
     */
    private fun buildCableLabel(
        group: CircuitGroup
    ): String {

        return if (group.cableSection > 0.0) {
            "3×${group.cableSection}"
        } else {
            "Кабель не указан"
        }
    }

    /**
     * Формирует label УЗО
     * только если УЗО реально есть в группе.
     */
    private fun buildRcdLabel(
        group: CircuitGroup
    ): String? {

        if (!group.rcdRequired) {
            return null
        }

        return "УЗО ${group.rcdCurrent}мА"
    }
}

/**
 * Подпись фазной шины для однолинейной схемы.
 *
 * THREE_PHASE не показываем как "Фаза THREE_PHASE",
 * потому что это выглядит как четвёртая фаза.
 */
private fun Phase.toSingleLineBusLabel(): String {
    return when (this) {
        Phase.A -> "Фазная шина A"
        Phase.B -> "Фазная шина B"
        Phase.C -> "Фазная шина C"
        Phase.THREE_PHASE -> "Трёхфазная линия 3P"
    }
}
