package ru.mugalimov.volthome.ui.utilities

import android.content.Context
import androidx.annotation.StringRes
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.domain.model.DeviceType

/**
 * UI-only слой отображения DeviceType.
 *
 * ВАЖНО:
 * - НЕ использовать enum.name в UI
 * - НЕ хранить строки в коде
 * - НЕ трогать домен / sync / DB
 */
@StringRes
fun DeviceType.labelRes(): Int =
    when (this) {
        DeviceType.LIGHTING -> R.string.device_type_lighting
        DeviceType.SOCKET -> R.string.device_type_socket
        DeviceType.HEAVY_DUTY -> R.string.device_type_heavy_duty

        DeviceType.AIR_CONDITIONER -> R.string.device_type_air_conditioner
        DeviceType.ELECTRIC_STOVE -> R.string.device_type_electric_stove
        DeviceType.OVEN -> R.string.device_type_oven
        DeviceType.WASHING_MACHINE -> R.string.device_type_washing_machine
        DeviceType.DISHWASHER -> R.string.device_type_dishwasher
        DeviceType.WATER_HEATER -> R.string.device_type_water_heater

        DeviceType.OTHER -> R.string.device_type_other
    }

/**
 * Удобный хелпер для Composable / ViewModel,
 * когда нужен сразу String.
 */
fun DeviceType.label(context: Context): String =
    context.getString(labelRes())