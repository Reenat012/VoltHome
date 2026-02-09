package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft

/**
 * Автораспределение устройств из unassigned.
 *
 * В этом коммите:
 * - добавляем "апгрейд": после автораспределения пересчитываем линию затронутых групп.
 *
 * ВАЖНО:
 * - сам алгоритм авто-раскидывания по группам может быть уже в репозитории/селекторе.
 * - здесь мы держим интерфейс "в домене": получил state -> вернул новый state.
 */
class AutoAssignUnassignedDevicesUseCase @Inject constructor(
    private val recalculateGroupLineUseCase: RecalculateGroupLineUseCase,
) {

    data class Params(
        val state: ProjectEditState,
        val mode: PhaseMode,
    )

    fun execute(p: Params): ProjectEditState {
        // Здесь должен быть реальный алгоритм автораспределения.
        // Пока оставляем "как было" (без изменения состава), но пересчёт линии — готов.
        //
        // Если у тебя авто-распределение живёт в manualRepo как action, то этот use case
        // можно переиспользовать уже ПОСЛЕ применения action: прогнать пересчёт линии по всем группам.
        val state = p.state

        // Пересчитываем линии по всем группам (без пересборки).
        val devicesById = state.devices.associateBy { it.deviceId }

        val newGroups: List<ManualGroupDraft> = state.groups.map { g ->
            val devs = g.deviceIds.mapNotNull { id -> devicesById[id] }
            recalculateGroupLineUseCase.execute(
                RecalculateGroupLineUseCase.Params(group = g, devicesInGroup = devs)
            )
        }

        return state.copy(groups = newGroups)
    }
}