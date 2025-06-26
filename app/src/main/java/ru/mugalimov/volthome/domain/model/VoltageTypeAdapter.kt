package ru.mugalimov.volthome.domain.model

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

class VoltageTypeAdapter : TypeAdapter<Voltage>() {
    override fun write(out: JsonWriter, value: Voltage) {
        // При сериализации пишем просто число
        out.value(value.value)
    }

    override fun read(`in`: JsonReader): Voltage {
        val voltageValue = `in`.nextInt()
        // Определяем тип напряжения по значению
        val type = when (voltageValue) {
            220, 230 -> VoltageType.AC_1PHASE
            380, 400 -> VoltageType.AC_3PHASE
            else -> VoltageType.DC
        }
        return Voltage(voltageValue, type)
    }
}