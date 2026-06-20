package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.panel.ModuleType
import ru.mugalimov.volthome.domain.model.panel.PanelGroup
import ru.mugalimov.volthome.domain.model.panel.PanelModule
import ru.mugalimov.volthome.domain.model.panel.PanelVisualization
import javax.inject.Inject

/**
 * Собирает модель визуализации щита из уже рассчитанной экспликации.
 *
 * Важно:
 * - не пересчитывает группы;
 * - не подбирает автоматы;
 * - не подбирает кабели;
 * - не меняет расчётный движок;
 * - не создаёт PDF/PNG;
 * - не хранит координаты, размеры и DIN-позиции.
 */
class GeneratePanelVisualizationUseCase @Inject constructor() {

    operator fun invoke(
        incomer: IncomerSpec?,
        groups: List<CircuitGroup>
    ): PanelVisualization {
        return PanelVisualization(
            incomer = incomer?.toPanelIncomerModule(),
            modules = groups.map { group ->
                group.toPanelModule()
            }
        )
    }

    /**
     * Маппит вводной аппарат в модуль визуализации.
     *
     * Используем только уже готовые данные IncomerSpec.
     * Никакого повторного подбора вводного автомата здесь нет.
     */
    private fun IncomerSpec.toPanelIncomerModule(): PanelModule {
        return PanelModule(
            type = ModuleType.INCOMER,
            label = "Вводной автомат",
            nominalCurrent = mcbRating,
            breakerCurve = mcbCurve,
            leakageCurrent = rcdSensitivityMa,
            group = null
        )
    }

    /**
     * Маппит рассчитанную группу экспликации в модуль визуализации.
     *
     * Важно:
     * текущая модель CircuitGroup хранит rcdRequired/rcdCurrent,
     * но не хранит отдельный признак "это дифавтомат".
     * Поэтому группа с rcdRequired отображается как УЗО.
     */
    private fun CircuitGroup.toPanelModule(): PanelModule {
        val moduleType = if (rcdRequired) {
            ModuleType.RCD
        } else {
            ModuleType.BREAKER
        }

        return PanelModule(
            type = moduleType,
            label = moduleType.toPanelLabel(),
            nominalCurrent = circuitBreaker,
            breakerCurve = if (moduleType == ModuleType.BREAKER) {
                breakerType
            } else {
                null
            },
            leakageCurrent = if (rcdRequired) {
                rcdCurrent
            } else {
                null
            },
            group = PanelGroup(
                name = buildGroupName(),
                cableSection = cableSection
            )
        )
    }

    /**
     * Формирует человекочитаемое имя группы
     * из уже существующих данных экспликации.
     */
    private fun CircuitGroup.buildGroupName(): String {
        return "$roomName · ${groupType.name}"
    }

    /**
     * Возвращает подпись аппарата для UI.
     */
    private fun ModuleType.toPanelLabel(): String {
        return when (this) {
            ModuleType.INCOMER -> "Вводной автомат"
            ModuleType.BREAKER -> "Автомат"
            ModuleType.RCD -> "УЗО"
            ModuleType.RCBO -> "Дифавтомат"
        }
    }
}