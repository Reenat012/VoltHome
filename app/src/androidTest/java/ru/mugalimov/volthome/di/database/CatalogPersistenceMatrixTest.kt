package ru.mugalimov.volthome.di.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.data.local.entity.ProjectEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.domain.model.CalculationAlgorithm
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.VoltageTypeAdapter
import ru.mugalimov.volthome.domain.policy.compatibility.CircuitCompatibilityPolicy
import ru.mugalimov.volthome.domain.policy.protection.RcdDeviceInput
import ru.mugalimov.volthome.domain.policy.protection.RcdSelectionPolicy
import ru.mugalimov.volthome.domain.policy.protection.RcdSpecFactory

/**
 * Сквозная релизная матрица:
 * каталог × помещение × 1/3-фазный проект × AUTO/MANUAL × закрытие/повторное
 * открытие Room.
 *
 * Недопустимая комбинация (3-фазный потребитель в однофазном проекте)
 * проверяется policy и не попадает в БД. Все допустимые решения сохраняются,
 * база закрывается и затем читается новым экземпляром AppDatabase.
 */
@RunWith(AndroidJUnit4::class)
class CatalogPersistenceMatrixTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "catalog-persistence-matrix.db"
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun everySupportedCombinationSurvivesDatabaseRestart() = runBlocking {
        val catalog = readCatalog()
        val projectId = "matrix-project"
        database.projectDao().insert(
            ProjectEntity(
                id = projectId,
                name = "Матрица",
                note = null,
                version = 1,
                updated_at = "2026-07-23T00:00:00.000Z",
                is_deleted = false
            )
        )

        val roomIds = RoomType.entries.associateWith { roomType ->
            database.roomDao().addRoom(
                RoomEntity(
                    name = "Помещение ${roomType.name}",
                    createdAt = Date(roomType.ordinal.toLong()),
                    roomType = roomType,
                    projectId = projectId
                )
            )
        }

        data class Expected(
            val phaseMode: PhaseMode,
            val roomType: RoomType,
            val source: CalculationSource,
            val device: DefaultDevice
        )

        val expectedByGroupId = linkedMapOf<Long, Expected>()
        val rcdPolicy = RcdSelectionPolicy()
        var groupNumber = 1

        catalog.forEach { template ->
            RoomType.entries.forEach { roomType ->
                PhaseMode.entries.forEach { phaseMode ->
                    CalculationSource.entries
                        .filter { it != CalculationSource.LEGACY }
                        .forEach sourceLoop@ { source ->
                            val roomId = requireNotNull(roomIds[roomType])
                            val phase = if (template.voltage.type == VoltageType.AC_3PHASE) {
                                Phase.THREE_PHASE
                            } else {
                                Phase.A
                            }
                            val compatibilityDevice =
                                CircuitCompatibilityPolicy.DeviceInput(
                                    id = template.id,
                                    roomId = roomId,
                                    deviceType = template.deviceType,
                                    voltageType = template.voltage.type,
                                    requiresDedicatedCircuit =
                                        template.requiresDedicatedCircuit,
                                    requiresSocketConnection =
                                        template.requiresSocketConnection
                                )
                            val decision = CircuitCompatibilityPolicy.evaluateGroup(
                                CircuitCompatibilityPolicy.GroupInput(
                                    phaseMode = phaseMode,
                                    phase = phase,
                                    roomId = roomId,
                                    devices = listOf(compatibilityDevice)
                                )
                            )

                            if (phaseMode == PhaseMode.SINGLE &&
                                template.voltage.type == VoltageType.AC_3PHASE
                            ) {
                                assertFalse(decision.allowed)
                                return@sourceLoop
                            }
                            assertTrue(
                                "${template.name}/$roomType/$phaseMode/$source",
                                decision.allowed
                            )

                            val deviceId = database.deviceDao().insert(
                                DeviceEntity(
                                    name = template.name,
                                    power = template.power,
                                    voltage = template.voltage,
                                    demandRatio = template.demandRatio,
                                    createdAt = Date(groupNumber.toLong()),
                                    roomId = roomId,
                                    deviceType = template.deviceType,
                                    powerFactor = template.powerFactor,
                                    hasMotor = template.hasMotor,
                                    requiresDedicatedCircuit =
                                        template.requiresDedicatedCircuit,
                                    requiresSocketConnection =
                                        template.requiresSocketConnection,
                                    projectId = projectId
                                )
                            )

                            val rcdDecision = rcdPolicy.select(
                                roomType = roomType,
                                devices = listOf(
                                    RcdDeviceInput(
                                        deviceType = template.deviceType,
                                        requiresSocketConnection =
                                            template.requiresSocketConnection
                                    )
                                )
                            )
                            val rcdSpec = RcdSpecFactory.createGroupRecommendation(
                                required = rcdDecision.required,
                                leakageCurrentMa = rcdDecision.leakageCurrentMa,
                                breakerRatingA = 16,
                                phase = phase,
                                source = source
                            )
                            val groupId = database.groupDao().addGroup(
                                CircuitGroupEntity(
                                    groupNumber = groupNumber++,
                                    roomId = roomId,
                                    roomName = "Помещение ${roomType.name}",
                                    groupType = template.deviceType.name,
                                    nominalCurrent =
                                        template.power / template.voltage.value.toDouble(),
                                    circuitBreaker = 16,
                                    cableSection = 2.5,
                                    breakerType = if (template.hasMotor) "C" else "B",
                                    rcdRequired = rcdDecision.required,
                                    rcdCurrent = rcdDecision.leakageCurrentMa ?: 30,
                                    rcdReasonCodes =
                                        rcdDecision.reasons.joinToString(",") { it.name },
                                    rcdNominalCurrent = rcdSpec?.ratedCurrentA,
                                    rcdType = rcdSpec?.type?.name,
                                    rcdPoles = rcdSpec?.poles,
                                    rcdSelectivity =
                                        rcdSpec?.selectivity?.name ?: "NONE",
                                    rcdKind = rcdSpec?.kind?.name,
                                    rcdSource =
                                        rcdSpec?.source?.name
                                            ?: CalculationSource.LEGACY.name,
                                    manualDeviationCodes =
                                        if (source == CalculationSource.MANUAL) {
                                            "MATRIX_MANUAL_DECISION"
                                        } else {
                                            ""
                                        },
                                    calculationSource = source.name,
                                    algorithmVersion = CalculationAlgorithm.VERSION,
                                    phase = phase.name,
                                    projectId = projectId
                                )
                            )
                            database.groupDeviceJoinDao().insertJoin(
                                GroupDeviceJoin(groupId = groupId, deviceId = deviceId)
                            )
                            expectedByGroupId[groupId] =
                                Expected(phaseMode, roomType, source, template)
                        }
                }
            }
        }

        val expectedCount = expectedByGroupId.size
        assertTrue(expectedCount > catalog.size)
        database.close()

        database = openDatabase()
        val restoredGroups =
            database.groupDao().getAllGroupsByProject(projectId).associateBy { it.groupId }
        val restoredDevices =
            database.deviceDao().getAllDevicesByProject(projectId).associateBy { it.deviceId }
        val restoredJoins = database.groupDeviceJoinDao()
            .getJoinsForGroupIds(restoredGroups.keys.toList())
            .associateBy { it.groupId }

        assertEquals(expectedCount, restoredGroups.size)
        assertEquals(expectedCount, restoredDevices.size)
        assertEquals(expectedCount, restoredJoins.size)

        expectedByGroupId.forEach { (groupId, expected) ->
            val group = requireNotNull(restoredGroups[groupId])
            val join = requireNotNull(restoredJoins[groupId])
            val device = requireNotNull(restoredDevices[join.deviceId])
            val roomId = requireNotNull(roomIds[expected.roomType])

            assertEquals(expected.source.name, group.calculationSource)
            assertEquals(
                expected.source == CalculationSource.MANUAL,
                group.manualDeviationCodes.isNotBlank()
            )
            assertEquals(expected.device.requiresSocketConnection, device.requiresSocketConnection)
            assertEquals(expected.device.requiresDedicatedCircuit, device.requiresDedicatedCircuit)
            assertEquals(expected.device.voltage.type, device.voltage.type)

            val restoredDecision = CircuitCompatibilityPolicy.evaluateGroup(
                CircuitCompatibilityPolicy.GroupInput(
                    phaseMode = expected.phaseMode,
                    phase = Phase.valueOf(group.phase),
                    roomId = roomId,
                    devices = listOf(
                        CircuitCompatibilityPolicy.DeviceInput(
                            id = device.deviceId,
                            roomId = requireNotNull(device.roomId),
                            deviceType = device.deviceType,
                            voltageType = device.voltage.type,
                            requiresDedicatedCircuit = device.requiresDedicatedCircuit,
                            requiresSocketConnection = device.requiresSocketConnection
                        )
                    )
                )
            )
            assertTrue("restored group $groupId", restoredDecision.allowed)
            if (group.rcdRequired) {
                assertTrue((group.rcdNominalCurrent ?: 0) >= group.circuitBreaker)
                assertEquals(if (group.phase == Phase.THREE_PHASE.name) 4 else 2, group.rcdPoles)
            }
        }
    }

    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()

    private fun readCatalog(): List<DefaultDevice> {
        val gson = GsonBuilder()
            .registerTypeAdapter(Voltage::class.java, VoltageTypeAdapter())
            .create()
        val type = object : TypeToken<List<DefaultDevice>>() {}.type
        return context.assets.open("default_devices.json").bufferedReader().use { reader ->
            gson.fromJson<List<DefaultDevice>>(reader, type).orEmpty()
        }
    }
}
