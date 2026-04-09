package ru.mugalimov.volthome.domain.model

/**
 * Доменная группа расчётной системы.
 *
 * Важно:
 * - nominalCurrent = canonical current группы;
 * - это же значение используется:
 *   1) как persisted расчётный ток группы,
 *   2) как weight for phase balancing,
 *   3) как источник для decision log распределения фаз.
 *
 * Поля whyBreakerSelected / whyCableSelected пока живут только в домене.
 * В БД и sync их не пишем.
 */
data class CircuitGroup(
    val groupId: Long = 0,
    val groupNumber: Int,
    val roomName: String,
    val roomId: Long,
    val groupType: DeviceType,
    val devices: List<Device>,

    // Расчётные параметры
    val nominalCurrent: Double,
    val installedPowerW: Int,
    val circuitBreaker: Int,
    val cableSection: Double,
    val breakerType: String,

    // Объяснение выбора линии единым policy-слоем
    val whyBreakerSelected: LineSelectionReason? = null,
    val whyCableSelected: CableSelectionReason? = null,

    // Параметры безопасности
    val rcdRequired: Boolean,
    val rcdCurrent: Int = 30,

    // Фазы
    val phase: Phase = Phase.A
) {
    /**
     * Явный алиас для мест, где группе нужен вес в балансировке.
     * Это не отдельное поле, а то же самое значение canonical current.
     */
    val balancingWeightA: Double
        get() = nominalCurrent
}