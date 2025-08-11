package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import javax.inject.Inject

class GetPhaseLoadUiUseCase @Inject constructor(
    private val repository: ExplicationRepository
) {
    operator fun invoke(): Flow<List<PhaseLoadItem>> {
        return repository.observeGroupsWithDevices() // Flow<List<CircuitGroupWithDevices>>
            // 1) Лог «сырых» связок из репо (сколько rels пришло)
            .onEach { Log.d("LOADS_UC", "rels=${it.size} first=${it.firstOrNull()?.group?.groupNumber}") }
            // 2) Маппим в UI-модель
            .map { list -> list.toPhaseLoadItems() }
            // 3) Лог уже ПОСЛЕ маппинга: ток и кол-во групп в фазе A
            .onEach { ui ->
                val a = ui.firstOrNull { it.phase == Phase.A }
                Log.d(
                    "LOADS_UC",
                    "A.totalCurrent=${a?.totalCurrent} A.groups=${a?.groups?.size}"
                )
            }
    }
}

/* ===================== mapping ===================== */

private fun List<CircuitGroupWithDevices>.toPhaseLoadItems(): List<PhaseLoadItem> {
    return Phase.values().map { phase ->
        val groupsOfPhase = this.filter { rel ->
            rel.group.phase.equals(phase.name, ignoreCase = true)
        }

        val groupItems = groupsOfPhase.map { rel ->
            var totalPowerW = 0.0
            var totalCurrentA = 0.0

            val deviceNames = rel.devices.map { d ->
                val pW = d.power.toDouble()                     // Int -> Double
                val uV = d.voltage.toNominalVoltage()           // Voltage -> Double (V)
                val vType = d.voltage.toVoltageType()           // Voltage -> VoltageType
                val pf: Double? = d.powerFactor                 // уже Double
                val demand: Double = d.demandRatio              // уже Double

                val iA = CurrentCalculator.calculateNominalCurrent(
                    power = pW,
                    voltage = uV,
                    powerFactor = pf,
                    demandRatio = demand,
                    voltageType = vType
                )

                totalPowerW += pW
                totalCurrentA += iA
                d.name
            }

            PhaseGroupItem(
                groupNumber = rel.group.groupNumber,
                roomName = rel.group.roomName,
                devices = deviceNames,
                totalPower = totalPowerW,
                totalCurrent = totalCurrentA
            )
        }

        PhaseLoadItem(
            phase = phase,
            totalPower = groupItems.sumOf { it.totalPower },
            totalCurrent = groupItems.sumOf { it.totalCurrent },
            groups = groupItems
        )
    }
}

/* ===================== converters ===================== */

private fun Voltage.toVoltageType(): VoltageType = this.type

private fun Voltage.toNominalVoltage(): Double {
    val v = this.value.toDouble()
    // safety‑net: если в БД 0 — подставим типовой 230 В
    return if (v > 0.0) v else 230.0
}