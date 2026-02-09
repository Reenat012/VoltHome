package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction

class MoveDeviceToNewGroupUseCase @Inject constructor() {
    fun action(deviceId: Long): ManualEditAction =
        ManualEditAction.CreateNewGroupAndMove(deviceId = deviceId)
}