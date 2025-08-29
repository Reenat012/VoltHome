package ru.mugalimov.volthome

import ru.mugalimov.volthome.domain.use_case.PhaseDistributor
import org.junit.Assert.*
import org.junit.Test
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase

class PhaseDistributorTest {

    /**
     * Хелпер: создает группу с нужным током и номером.
     * ВНИМАНИЕ: подгони типы литералов под свою модель CircuitGroup:
     * - Если cableSection у тебя Double — поставь 2.5
     * - Если rcdCurrent у тебя Double — поставь 0.0
     * Ниже — вариант «всё целое», чтобы не ловить ошибку “float to Int”.
     */
    private fun g(
        number: Int,
        current: Double,
        phase: Phase = Phase.A
    ): CircuitGroup = CircuitGroup(
        groupId = number.toLong(),
        groupNumber = number,
        roomId = 1L,
        roomName = "R$number",
        groupType = DeviceType.SOCKET,
        devices = emptyList(),
        nominalCurrent = current,   // Double — ок
        circuitBreaker = 16,        // Int
        cableSection = 2.5,           // <-- если у тебя Double, замени на 2.5
        breakerType = "C",          // String
        rcdRequired = false,        // Boolean
        rcdCurrent = 0,             // <-- если у тебя Double, замени на 0.0
        phase = phase
    )

    private fun sumsByPhase(groups: List<CircuitGroup>): Triple<Double, Double, Double> {
        val a = groups.filter { it.phase == Phase.A }.sumOf { it.nominalCurrent }
        val b = groups.filter { it.phase == Phase.B }.sumOf { it.nominalCurrent }
        val c = groups.filter { it.phase == Phase.C }.sumOf { it.nominalCurrent }
        return Triple(a, b, c)
    }

    private fun spread(sums: Triple<Double, Double, Double>): Double {
        val (a, b, c) = sums
        return maxOf(a, b, c) - minOf(a, b, c)
    }

    @Test
    fun testEmptyListReturnsEmpty() {
        val res = PhaseDistributor.distributeGroupsBalanced(emptyList())
        assertTrue(res.isEmpty())
    }

    @Test
    fun testNineGroupsOf3ABalanced() {
        val input = (1..9).map { i -> g(i, 3.0) }
        val res = PhaseDistributor.distributeGroupsBalanced(input)

        // номера групп сохраняются
        assertEquals((1..9).toList(), res.map { it.groupNumber })

        val sums = sumsByPhase(res)
        assertTrue("spread=${spread(sums)} sums=$sums", spread(sums) < 1.0)
    }

    @Test
    fun testTwelveGroupsOf2ANotStickingToA() {
        val input = (1..12).map { i -> g(i, 2.0) }
        val res = PhaseDistributor.distributeGroupsBalanced(input)

        val sums = sumsByPhase(res)
        assertTrue("spread=${spread(sums)} sums=$sums", spread(sums) < 2.0)
    }

    @Test
    fun testHeavyGroupsAreSpreadAcrossPhases() {
        val heavy1 = g(1, 20.0)
        val heavy2 = g(2, 20.0)
        val light = (3..18).map { i -> g(i, 1.0) }
        val input = listOf(heavy1, heavy2) + light

        val res = PhaseDistributor.distributeGroupsBalanced(input)

        val phase1 = res.first { it.groupNumber == 1 }.phase
        val phase2 = res.first { it.groupNumber == 2 }.phase
        assertNotEquals(phase1, phase2)

        val sums = sumsByPhase(res)
        assertTrue("spread=${spread(sums)} sums=$sums", spread(sums) < 6.0)
    }
}