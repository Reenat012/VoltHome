package ru.mugalimov.volthome.domain.model.provider

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageTypeAdapter

/**
 * Fallback-дефолты берутся из того же JSON, который видит пользователь.
 * Это исключает расхождение между каталогом и repository expand-path.
 *
 * Для запроса без template id используется первый шаблон соответствующего
 * DeviceType. Каталожные запросы всегда передают точные поля самого шаблона.
 */
@Singleton
class JsonDeviceDefaultsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceDefaultsProvider {

    private val defaultsByType: Map<DeviceType, DeviceDefaults> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val gson = GsonBuilder()
            .registerTypeAdapter(Voltage::class.java, VoltageTypeAdapter())
            .create()
        val type = object : TypeToken<List<DefaultDevice>>() {}.type
        val catalog = context.assets.open(ASSET_NAME).use { input ->
            input.bufferedReader().use { reader ->
                gson.fromJson<List<DefaultDevice>>(reader, type).orEmpty()
            }
        }

        val grouped = catalog
            .sortedBy(DefaultDevice::id)
            .groupBy(DefaultDevice::deviceType)
            .mapValues { (_, items) -> items.first().toDefaults() }

        val missing = DeviceType.entries.toSet() - grouped.keys
        check(missing.isEmpty()) {
            "В $ASSET_NAME отсутствуют шаблоны для типов: ${missing.joinToString()}"
        }
        grouped
    }

    override fun get(type: DeviceType): DeviceDefaults =
        defaultsByType.getValue(type)

    private fun DefaultDevice.toDefaults() = DeviceDefaults(
        power = power,
        powerFactor = powerFactor,
        demandRatio = demandRatio,
        voltage = voltage,
        hasMotor = hasMotor,
        requiresDedicatedCircuit = requiresDedicatedCircuit,
        requiresSocketConnection = requiresSocketConnection
    )

    private companion object {
        const val ASSET_NAME = "default_devices.json"
    }
}
