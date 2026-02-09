package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction

class MoveDeviceUseCase @Inject constructor() {
    fun action(deviceId: Long, fromGroupId: Long, toGroupId: Long): ManualEditAction =
        ManualEditAction.MoveDevice(
            deviceId = deviceId,
            fromGroupId = fromGroupId,
            toGroupId = toGroupId
        )
}