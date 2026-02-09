package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState

/**
 * Каскад удаления группы из draft.
 *
 * Правила:
 * - Группа удаляется.
 * - Все её устройства переводятся в unassignedDeviceIds (если они не были переназначены).
 * - "Хвосты" ссылок чистятся автоматически, потому что source of truth:
 *   - group.deviceIds
 *   - unassignedDeviceIds
 *
 * ВАЖНО:
 * - Мы НЕ меняем nextGroupNumber (он монотонный счётчик).
 * - Мы НЕ пересобираем группы.
 */
class DeleteGroupCascadeUseCase @Inject constructor() {

    data class Params(
        val state: ProjectEditState,
        val groupId: Long
    )

    fun execute(p: Params): ProjectEditState {
        val group = p.state.groups.firstOrNull { it.groupId == p.groupId } ?: return p.state

        val movedToUnassigned = group.deviceIds.toSet()

        val newGroups = p.state.groups.filterNot { it.groupId == p.groupId }

        // Устройства могли быть уже в unassigned — просто объединяем множества.
        val newUnassigned = p.state.unassignedDeviceIds + movedToUnassigned

        return p.state.copy(
            groups = newGroups,
            unassignedDeviceIds = newUnassigned
        )
    }
}