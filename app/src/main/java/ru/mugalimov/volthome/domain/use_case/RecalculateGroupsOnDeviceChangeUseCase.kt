package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.dao.GroupNominalCurrentUpdate
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.PhaseMode
import javax.inject.Inject

/**
 * AUTO-пересчёт групп ТОЛЬКО по "входам":
 * - DEVICE_CHANGED (изменились устройства, влияющие на группировку/токи)
 * - JOIN_CHANGED (изменилось membership group_device_join в рамках текущего проекта)
 * - MODE_CHANGED (смена 1/3 фазы)
 *
 * КРИТИЧНО:
 * - Этот usecase НЕ делает никаких STRUCTURE writes.
 * - Он только считает и отдаёт результат наружу (callback).
 * - STRUCTURE-сохранение (replaceAll) — ТОЛЬКО explicit writer (SaveAutoCalculatedGroupsToLocalDbUseCase).
 *
 * Коммит 4:
 * - Допускаем whitelist-запись derived-полей (например nominal_current),
 *   потому что это НЕ структурные поля (не трогаем phase/order/groupNumber/...).
 */
class RecalculateGroupsOnDeviceChangeUseCase @Inject constructor(
    private val activeProjectDs: ActiveProjectDataStore,
    private val roomDao: RoomDao,
    private val deviceDao: DeviceDao,
    private val groupDao: GroupDao,
    private val joinDao: GroupDeviceJoinDao,
    private val calculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: ru.mugalimov.volthome.data.repository.PreferencesRepository,
    private val updateDerivedGroupFieldsUseCase: UpdateDerivedGroupFieldsUseCase // ✅ Коммит 4
) {

    private data class TriggerSnapshot(
        val projectId: String,
        val devHash: String,
        val joinHash: String,
        val mode: PhaseMode,
        val devCount: Int,
        val joinCount: Int,
        val devSample: String,
        val joinSample: String
    )

    private data class PrevCurr(
        val prev: TriggerSnapshot?,
        val curr: TriggerSnapshot
    )

    /**
     * Запуск реактивного пересчёта.
     *
     * @param onCalculated callback, куда отдаём результат расчёта.
     *        ВАЖНО: callback не должен писать STRUCTURE из реактивного контура.
     *        (Сохранение делается отдельным explicit сценарием.)
     */
    @OptIn(FlowPreview::class)
    fun launch(
        scope: CoroutineScope,
        onCalculated: suspend (projectId: String, mode: PhaseMode, result: GroupingResult) -> Unit
    ): Job {
        return activeProjectDs.activeProjectId
            .map { it.orEmpty() }
            .filter { it.isNotBlank() }
            // КРИТИЧНО: смена проекта должна отменять старые подписки (иначе cross-project run)
            .flatMapLatest { projectId ->

                // 1) Rooms boundary по проекту -> набор roomIds
                val roomIdsFlow = roomDao.observeAllRoomsByProject(projectId)
                    .map { rooms -> rooms.map { it.id }.toSet() }
                    .distinctUntilChanged()

                // 2) GroupIds boundary по проекту -> набор groupIds
                // Параметрические обновления групп (nominal_current и т.п.) НЕ меняют ID -> триггер не сработает.
                val groupIdsFlow = groupDao.observeAllGroupsByProject(projectId)
                    .map { groups -> groups.map { it.groupId }.toSet() }
                    .distinctUntilChanged()

                // 3) Devices, отфильтрованные по roomIds проекта (срезает cross-project)
                val devicesInProjectFlow = combine(
                    deviceDao.observeAllDevices(),
                    roomIdsFlow
                ) { devices, roomIds ->
                    devices.filter { d -> d.roomId in roomIds }
                }

                // 4) Joins, отфильтрованные по groupIds проекта (join-таблица без projectId)
                val joinsInProjectFlow = combine(
                    joinDao.observeJoins(),
                    groupIdsFlow
                ) { joins, groupIds ->
                    joins.filter { j -> j.groupId in groupIds }
                }

                // 5) Собираем snapshot "входов"
                combine(
                    devicesInProjectFlow,
                    joinsInProjectFlow,
                    preferencesRepository.phaseMode
                ) { devices, joins, mode ->

                    // DEVICE sig: берём только поля, влияющие на расчёт.
                    val devSig: List<String> = devices
                        .map { d ->
                            listOf(
                                d.deviceId.toString(),
                                d.roomId.toString(),
                                d.power.toString(),
                                d.deviceType.name,
                                d.powerFactor.toString(),
                                d.demandRatio.toString(),
                                d.voltage.value.toString(),
                                d.voltage.type.name,
                                d.hasMotor.toString(),
                                d.requiresDedicatedCircuit.toString(),
                                d.requiresSocketConnection.toString()
                            ).joinToString(separator = ",")
                        }
                        .sorted()

                    // JOIN sig: membership как пары (groupId, deviceId)
                    val joinPairs: List<Pair<Long, Long>> = joins
                        .map { it.groupId to it.deviceId }
                        .sortedWith(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })

                    val devHash = sha256Short(devSig.joinToString("|"))
                    val joinHash = sha256Short(joinPairs.joinToString("|") { "${it.first}:${it.second}" })

                    TriggerSnapshot(
                        projectId = projectId,
                        devHash = devHash,
                        joinHash = joinHash,
                        mode = mode,
                        devCount = devices.size,
                        joinCount = joins.size,
                        devSample = devSig.take(5).joinToString(" | "),
                        joinSample = joinPairs.take(8).joinToString(" | ") { "${it.first}:${it.second}" }
                    )
                }
                    // distinctUntilChanged по "входам": device hash + join hash + mode
                    .distinctUntilChanged { a, b ->
                        a.projectId == b.projectId &&
                                a.devHash == b.devHash &&
                                a.joinHash == b.joinHash &&
                                a.mode == b.mode
                    }
                    .debounce(200)
                    // Чтобы логировать REASON, сравниваем prev/curr
                    .runningFold<TriggerSnapshot, PrevCurr?>(null) { acc, curr ->
                        PrevCurr(prev = acc?.curr, curr = curr)
                    }
                    .drop(1)
                    .map { it!! }
                    .onEach { (prev, curr) ->
                        val reason = when {
                            prev == null -> "INIT"
                            prev.mode != curr.mode -> "MODE_CHANGED"
                            prev.devHash != curr.devHash && prev.joinHash != curr.joinHash -> "DEVICE_CHANGED+JOIN_CHANGED"
                            prev.devHash != curr.devHash -> "DEVICE_CHANGED"
                            prev.joinHash != curr.joinHash -> "JOIN_CHANGED"
                            else -> "UNKNOWN"
                        }

                        Log.w(
                            "AUTO_RECALC_TRIGGER",
                            "reason=$reason pid=${curr.projectId} mode=${curr.mode.name} " +
                                    "devs=${curr.devCount} joins=${curr.joinCount} " +
                                    "devHash=${curr.devHash} joinHash=${curr.joinHash} " +
                                    "devSample=[${curr.devSample}] joinSample=[${curr.joinSample}]"
                        )

                        // ВАЖНО: тут ТОЛЬКО расчёт. Никаких STRUCTURE-сохранений.
                        val result = withContext(Dispatchers.IO) {
                            // ВАЖНО: считаем строго в curr.projectId, а не “какой сейчас активный”
                            calculatorFactory.create(curr.projectId).calculateGroups(curr.mode)
                        }

                        // ✅ Коммит 4: whitelist derived write (пример: nominal_current)
                        // ВАЖНО: здесь НЕ трогаем phase/order/groupNumber/roomId/groupType/...
                        // Только derived-поля, которые безопасно пересчитывать реактивно.
                        applyDerivedFieldsWhitelisted(
                            projectId = curr.projectId,
                            result = result
                        )

                        // Отдаём наружу — пусть VM решает, что делать со STRUCTURE.
                        onCalculated(curr.projectId, curr.mode, result)
                    }
            }
            .launchIn(scope)
    }

    /**
     * Коммит 4.
     * Здесь намеренно нет "универсального" апдейта — только whitelist.
     *
     * Реализация для твоей модели:
     * GroupingResult.Success(system: ElectricalSystem)
     * ElectricalSystem.groups: List<CircuitGroup>
     * CircuitGroup.nominalCurrent: Double
     */
    private suspend fun applyDerivedFieldsWhitelisted(
        projectId: String,
        result: GroupingResult
    ) {
        val updates: List<GroupNominalCurrentUpdate> = when (result) {
            is GroupingResult.Success -> {
                // ⚠️ ТОЛЬКО derived-поле nominal_current.
                // groupId берём из доменной модели (у тебя он есть).
                result.system.groups.map { g ->
                    GroupNominalCurrentUpdate(
                        groupId = g.groupId,
                        nominalCurrent = g.nominalCurrent
                    )
                }
            }

            is GroupingResult.Error -> {
                // На ошибке расчёта ничего не пишем в БД.
                emptyList()
            }
        }

        // Не делаем пустых writes
        if (updates.isEmpty()) return

        // Пишем whitelist-апдейты строго в границах проекта (projectId в DAO/usecase обязателен)
        updateDerivedGroupFieldsUseCase.updateNominalCurrentsOnly(
            projectId = projectId,
            updates = updates
        )
    }

    /**
     * Короткий sha256 (16 hex символов) для логов.
     * Достаточно для детекта "что-то изменилось".
     */
    private fun sha256Short(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}