package ru.mugalimov.volthome

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectObjectType
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.provider.ProjectTemplateCatalog

@RunWith(AndroidJUnit4::class)
class ProjectTemplateCatalogTest {
    private val catalog = ProjectTemplateCatalog(ApplicationProvider.getApplicationContext())

    @Test
    fun templatesUseOnlyCanonicalDeviceCatalogEntries() = runBlocking {
        val deviceIds = catalog.devices().map { it.id }.toSet()
        val templates = catalog.templates()
        val referencedIds = templates
            .flatMap { template -> template.rooms }
            .flatMap { room -> room.devices }
            .map { it.deviceId }
            .toSet()

        assertTrue("Шаблон ссылается на неизвестное устройство", deviceIds.containsAll(referencedIds))
        assertTrue(templates.flatMap { it.rooms }.all { room -> room.devices.all { it.count > 0 } })
        assertEquals(1, templates.count { it.objectType == ProjectObjectType.CUSTOM })
        assertTrue(templates.single { it.objectType == ProjectObjectType.CUSTOM }.rooms.isEmpty())
    }

    @Test
    fun canonicalSpecialZonesAreNotDowngradedByTemplates() = runBlocking {
        val rooms = catalog.templates().flatMap { it.rooms }

        assertTrue(
            "Балкон должен сохранять наружный тип зоны",
            rooms.filter { it.title == "Балкон" }.all { it.roomType == RoomType.OUTDOOR }
        )
        assertTrue(
            "Гараж должен сохранять наружный тип зоны",
            rooms.filter { it.title == "Гараж" }.all { it.roomType == RoomType.OUTDOOR }
        )
        assertTrue(
            "Ванные должны сохранять тип влажного помещения",
            rooms.filter { it.title == "Ванная" }.all { it.roomType == RoomType.BATHROOM }
        )
    }

    @Test
    fun apartmentTemplateIsAStartingPointRatherThanAnOverfilledProject() = runBlocking {
        val apartment = catalog.templates().single { it.objectType == ProjectObjectType.APARTMENT }
        val devices = catalog.devices().associateBy { it.id }
        val installedPowerW = apartment.rooms.sumOf { room ->
            room.defaultCount * room.devices.sumOf { device ->
                (devices[device.deviceId]?.power ?: 0) * device.count
            }
        }

        assertTrue("В квартире должен быть хотя бы один стартовый потребитель", installedPowerW > 0)
        assertTrue("Стартовый шаблон квартиры не должен быть заведомо перегружен", installedPowerW < 20_000)
    }
}
