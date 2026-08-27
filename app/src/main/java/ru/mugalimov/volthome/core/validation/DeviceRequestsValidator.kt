package ru.mugalimov.volthome.core.validation

import ru.mugalimov.volthome.core.validation.PowerValidator
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest

/**
 * Единая доменная проверка мощности устройств при создании/добавлении.
 * Правила:
 *  - ratedPowerW обязателен (не null)
 *  - диапазон: PowerValidator (технический диапазон хранения поля)
 *
 * Возможность автоматического подбора линии проверяется отдельно по току:
 * высокая мощность сама по себе больше не является ошибкой ввода.
 * Ошибки — человекочитаемые, включают название устройства.
 */
object DeviceRequestsValidator {

    /**
     * Валидирует одну заявку. Бросает IllegalArgumentException при нарушении инвариантов.
     */
    fun validateDevicePowerOrThrow(req: DeviceCreateRequest) {
        val power = requireNotNull(req.ratedPowerW) { "«${req.title}»: укажите мощность (Вт)" }
        PowerValidator.errorMessage(power)?.let { msg ->
            throw IllegalArgumentException("«${req.title}»: $msg")
        }
    }

    /**
     * Валидирует коллекцию заявок. Бросает IllegalArgumentException на первом нарушении.
     */
    fun validateDevicesPowerOrThrow(devices: Iterable<DeviceCreateRequest>) {
        devices.forEach { validateDevicePowerOrThrow(it) }
    }
}
