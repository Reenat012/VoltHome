package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import java.util.UUID
import javax.inject.Inject
import ru.mugalimov.volthome.core.validation.DeviceRequestsValidator
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.create.CreatedRoomResult
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest

class CreateRoomWithDevicesUseCase @Inject constructor(
    private val repo: RoomRepository
) {
    suspend operator fun invoke(req: RoomCreateRequest): CreatedRoomResult {
        // ✅ Доменная защита: единая точка проверки мощностей
        DeviceRequestsValidator.validateDevicesPowerOrThrow(req.devices)

        // ✅ Commit 1/2: opId корреляции для room-create (один на один вызов)
        val opId = UUID.randomUUID().toString()

        // ✅ Временный лог оставляем: удобно видеть opId ещё до входа в repo,
        // но основной gate теперь закрывает лог в RoomRepositoryImpl.
        Log.i(
            "CREATE_ROOM_UC",
            "opId=$opId name='${req.name}' roomType=${req.roomType} devices=${req.devices.size}"
        )

        // ✅ Commit 2: теперь компилируется — есть overload в repo
        return repo.addRoomWithDevices(req = req, opId = opId)
    }
}