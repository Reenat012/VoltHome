package ru.mugalimov.volthome.domain.model.provider

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageTypeAdapter
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectObjectType
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectTemplate
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectTemplateDevice
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectTemplateRoom

/** Шаблоны описывают состав объекта, а характеристики устройств читаются из единого JSON-каталога. */
@Singleton
class ProjectTemplateCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private data class CanonicalRoom(
        val name: String,
        val roomType: RoomType
    )

    private val canonicalRoomTypes: Map<String, RoomType> by lazy {
        val type = object : TypeToken<List<CanonicalRoom>>() {}.type
        context.assets.open("default_rooms.json").use { input ->
            input.bufferedReader().use { reader ->
                GsonBuilder().create()
                    .fromJson<List<CanonicalRoom>>(reader, type)
                    .orEmpty()
                    .associate { it.name to it.roomType }
            }
        }
    }

    suspend fun devices(): List<DefaultDevice> = withContext(Dispatchers.IO) {
        val gson = GsonBuilder()
            .registerTypeAdapter(Voltage::class.java, VoltageTypeAdapter())
            .create()
        val type = object : TypeToken<List<DefaultDevice>>() {}.type
        context.assets.open("default_devices.json").use { input ->
            input.bufferedReader().use { gson.fromJson<List<DefaultDevice>>(it, type).orEmpty() }
        }
    }

    suspend fun templates(): List<ProjectTemplate> = withContext(Dispatchers.IO) {
        buildTemplates()
    }

    private fun buildTemplates(): List<ProjectTemplate> = listOf(
        ProjectTemplate(
            id = "apartment_v1",
            version = 2,
            objectType = ProjectObjectType.APARTMENT,
            title = "Квартира",
            subtitle = "Стартовый набор помещений и бытовых нагрузок",
            recommendedPhaseMode = PhaseMode.SINGLE,
            rooms = listOf(
                room("hall", "Прихожая", "Свет и бытовые розетки", RoomType.STANDARD, 12 to 2, 22 to 1),
                room("kitchen", "Кухня", "Техника, розетки и освещение", RoomType.KITCHEN, 13 to 1, 22 to 1, 1 to 1, 2 to 1, 3 to 1),
                room("bathroom", "Ванная", "Влажная зона с дифференциальной защитой", RoomType.BATHROOM, 12 to 2, 4 to 1, 30 to 1),
                room("living", "Гостиная", "Освещение и мультимедиа", RoomType.STANDARD, 13 to 1, 22 to 1, 5 to 1, 20 to 1),
                room("bedroom", "Спальня", "Освещение и розетки", RoomType.STANDARD, 13 to 1, 22 to 1, defaultCount = 1),
                room("balcony", "Балкон", "Дополнительная зона", RoomType.STANDARD, 12 to 1, 22 to 1, optional = true, defaultCount = 0)
            )
        ),
        ProjectTemplate(
            id = "house_v1",
            version = 1,
            objectType = ProjectObjectType.HOUSE,
            title = "Дом",
            subtitle = "Жилые, технические и наружные зоны",
            recommendedPhaseMode = PhaseMode.THREE,
            rooms = listOf(
                room("hall", "Холл", "Общее освещение и розетки", RoomType.STANDARD, 13 to 1, 22 to 2),
                room("kitchen_living", "Кухня-гостиная", "Основная бытовая нагрузка", RoomType.KITCHEN, 13 to 2, 22 to 3, 1 to 1, 2 to 1, 3 to 1, 28 to 1, 29 to 1),
                room("bathroom", "Ванная", "Влажная зона", RoomType.BATHROOM, 12 to 2, 4 to 1, 31 to 1),
                room("bedroom", "Спальня", "Типовая жилая комната", RoomType.STANDARD, 13 to 1, 22 to 2, defaultCount = 2),
                room("boiler", "Котельная", "Инженерное оборудование", RoomType.STANDARD, 12 to 1, 30 to 1, 34 to 1),
                room("garage", "Гараж", "Силовые и рабочие розетки", RoomType.STANDARD, 24 to 2, 22 to 2, 33 to 1, optional = true),
                room("outdoor", "Улица", "Наружное освещение и розетки", RoomType.OUTDOOR, 24 to 3, 22 to 1, optional = true)
            )
        ),
        ProjectTemplate(
            id = "garage_v1",
            version = 1,
            objectType = ProjectObjectType.GARAGE_WORKSHOP,
            title = "Гараж / мастерская",
            subtitle = "Рабочие розетки, свет и силовое оборудование",
            recommendedPhaseMode = PhaseMode.THREE,
            rooms = listOf(
                room("garage", "Гараж", "Основная рабочая зона", RoomType.STANDARD, 24 to 3, 22 to 3, 32 to 1, 33 to 1),
                room("workshop", "Мастерская", "Инструмент и оборудование", RoomType.STANDARD, 13 to 2, 22 to 3, 32 to 1),
                room("outdoor", "Улица", "Наружные потребители", RoomType.OUTDOOR, 24 to 2, 22 to 1, optional = true)
            )
        ),
        ProjectTemplate(
            id = "commercial_v1",
            version = 1,
            objectType = ProjectObjectType.COMMERCIAL,
            title = "Небольшой объект",
            subtitle = "Офис, магазин или сервисное помещение",
            recommendedPhaseMode = PhaseMode.THREE,
            rooms = listOf(
                room("main", "Основное помещение", "Освещение и общие розетки", RoomType.STANDARD, 23 to 8, 22 to 4),
                room("workplaces", "Рабочая зона", "Компьютеры и оргтехника", RoomType.STANDARD, 7 to 4, 8 to 2, 25 to 1),
                room("utility", "Подсобное помещение", "Служебная нагрузка", RoomType.STANDARD, 12 to 2, 22 to 1),
                room("restroom", "Санузел", "Влажная зона", RoomType.BATHROOM, 12 to 2, 30 to 1),
                room("climate", "Климат", "Кондиционирование", RoomType.STANDARD, 9 to 2, optional = true)
            )
        ),
        ProjectTemplate(
            id = "custom_v1",
            version = 1,
            objectType = ProjectObjectType.CUSTOM,
            title = "Пустой проект",
            subtitle = "Начать без шаблона и добавить помещения вручную",
            recommendedPhaseMode = PhaseMode.THREE,
            rooms = emptyList()
        )
    )

    private fun room(
        key: String,
        title: String,
        description: String,
        roomType: RoomType,
        vararg devices: Pair<Int, Int>,
        defaultCount: Int = 1,
        optional: Boolean = false
    ) = ProjectTemplateRoom(
        key = key,
        title = title,
        description = description,
        roomType = canonicalRoomTypes[title] ?: roomType,
        defaultCount = defaultCount,
        optional = optional,
        devices = devices.map { (id, count) -> ProjectTemplateDevice(id.toLong(), count) }
    )
}
