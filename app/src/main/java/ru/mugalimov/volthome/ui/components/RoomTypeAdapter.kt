package ru.mugalimov.volthome.ui.components

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import ru.mugalimov.volthome.domain.model.RoomType

class RoomTypeAdapter : TypeAdapter<RoomType>() {
    override fun write(out: JsonWriter, value: RoomType) {
        out.value(value.name)
    }

    override fun read(reader: JsonReader): RoomType {
        return RoomType.valueOf(reader.nextString())
    }
}