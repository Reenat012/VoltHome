package ru.mugalimov.volthome.domain.model

import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest

/**
 * Каноническое описание демонстрационного проекта.
 *
 * Служебная метка хранится в уже существующем поле note, поэтому для демо не
 * нужна миграция базы. При повторном открытии проект пересоздаётся из этого
 * шаблона и не учитывается в лимите пользовательских проектов.
 */
object DemoProjectSpec {
    const val PROJECT_NAME: String = "Демо · квартира"
    const val NOTE_MARKER: String = "__volthome_demo_project_v1__"

    fun isDemo(note: String?): Boolean = note == NOTE_MARKER

    val rooms: List<RoomCreateRequest>
        get() = listOf(
            RoomCreateRequest(
                name = "Гостиная",
                roomType = RoomType.STANDARD,
                devices = listOf(
                    DeviceCreateRequest(
                        title = "Светодиодная лампа",
                        type = DeviceType.LIGHTING,
                        count = 4,
                        ratedPowerW = 12
                    ),
                    DeviceCreateRequest(
                        title = "Розетка бытовая",
                        type = DeviceType.SOCKET,
                        count = 3,
                        ratedPowerW = 1000
                    ),
                    DeviceCreateRequest(
                        title = "Телевизор",
                        type = DeviceType.OTHER,
                        ratedPowerW = 150,
                        demandRatio = 0.8,
                        requiresSocketConnection = true
                    )
                )
            ),
            RoomCreateRequest(
                name = "Кухня",
                roomType = RoomType.KITCHEN,
                devices = listOf(
                    DeviceCreateRequest(
                        title = "Розетка бытовая",
                        type = DeviceType.SOCKET,
                        count = 3,
                        ratedPowerW = 1200
                    ),
                    DeviceCreateRequest(
                        title = "Холодильник",
                        type = DeviceType.OTHER,
                        ratedPowerW = 300,
                        powerFactor = 0.85,
                        demandRatio = 1.0,
                        hasMotor = true,
                        requiresSocketConnection = true
                    ),
                    DeviceCreateRequest(
                        title = "Микроволновая печь",
                        type = DeviceType.OTHER,
                        ratedPowerW = 1400,
                        demandRatio = 0.8,
                        requiresSocketConnection = true
                    ),
                    DeviceCreateRequest(
                        title = "Духовой шкаф",
                        type = DeviceType.OVEN,
                        ratedPowerW = 3000
                    )
                )
            ),
            RoomCreateRequest(
                name = "Ванная",
                roomType = RoomType.BATHROOM,
                devices = listOf(
                    DeviceCreateRequest(
                        title = "Освещение",
                        type = DeviceType.LIGHTING,
                        count = 2,
                        ratedPowerW = 12
                    ),
                    DeviceCreateRequest(
                        title = "Стиральная машина",
                        type = DeviceType.WASHING_MACHINE,
                        ratedPowerW = 2200
                    ),
                    DeviceCreateRequest(
                        title = "Тёплый пол электрический",
                        type = DeviceType.HEAVY_DUTY,
                        ratedPowerW = 1500,
                        hasMotor = false,
                        requiresDedicatedCircuit = true,
                        requiresSocketConnection = false
                    )
                )
            )
        )
}
