package ru.mugalimov.volthome.ui.screens.explication

import kotlin.math.ceil
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.policy.breaker.BreakerPolicyDefaults
import ru.mugalimov.volthome.domain.policy.cable.CablePolicyDefaults
import ru.mugalimov.volthome.domain.policy.protection.RcdSelectionReason
import ru.mugalimov.volthome.domain.use_case.CircuitLoadCalculator
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcDetailsState
import ru.mugalimov.volthome.ui.screens.explication.sheets.DecisionSummaryUi
import ru.mugalimov.volthome.ui.screens.explication.sheets.DecisionToneUi
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoDetailSectionUi
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetPayload
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetType
import ru.mugalimov.volthome.ui.screens.explication.sheets.RuleCheckStatusUi
import ru.mugalimov.volthome.ui.screens.explication.sheets.RuleCheckUi
import ru.mugalimov.volthome.ui.format.UiTextFormat

/**
 * Единый перевод фактических данных группы в человекочитаемое объяснение.
 * Здесь нет выбора параметров — только объяснение уже принятого решения.
 */
object GroupDecisionPayloadFactory {

    fun group(group: CircuitGroup): InfoSheetPayload {
        val installedCurrent = group.installedCurrentA()
        val connection = if (group.devices.firstOrNull()?.requiresSocketConnection == true) {
            "подключаемые через розетку приборы"
        } else {
            "стационарная или розеточная линия"
        }

        return InfoSheetPayload(
            title = "Почему сформирована группа ${group.groupNumber}",
            sheetType = InfoSheetType.REFERENCE,
            currentValueText = "${fmt(installedCurrent)} А установленного тока",
            bullets = listOf(
                "Помещение: ${group.roomName}",
                "Тип нагрузки: ${group.groupType.name}",
                "Состав: ${group.devices.joinToString { it.name }}",
                "Способ подключения: $connection",
                "В одну группу объединяются устройства одного типа, числа фаз и способа подключения."
            ),
            interpretation = if (group.devices.size == 1) {
                "В этой группе одно устройство: оно выделено отдельно либо не поместилось в другую совместимую группу."
            } else {
                "Устройства размещены вместе, потому что их суммарный установленный ток не превышает номинал автомата."
            },
            sourceText = group.sourceLabel()
        )
    }

    fun breaker(group: CircuitGroup): InfoSheetPayload {
        val installedCurrent = group.installedCurrentA()
        val rounded = ceil(installedCurrent).toInt()
        val floor = BreakerPolicyDefaults.floorByDeviceType(group.groupType)
        val required = maxOf(rounded, floor)
        val hasMotor = group.devices.any { it.hasMotor }

        val curveReason = when {
            hasMotor && group.circuitBreaker >= 25 ->
                "Характеристика D выбрана из-за двигателя и номинала от 25 А."
            hasMotor ->
                "Характеристика C выбрана для нагрузки с двигателем при номинале менее 25 А."
            group.circuitBreaker == 10 && group.breakerType.equals("B", ignoreCase = true) ->
                "Характеристика B используется для немоторной линии 10 А."
            else ->
                "Характеристика C является базовой для немоторной линии этого номинала."
        }

        return InfoSheetPayload(
            title = "Почему ${group.breakerType}${group.circuitBreaker}",
            sheetType = InfoSheetType.REFERENCE,
            currentValueText = "${group.breakerType}${group.circuitBreaker}",
            bullets = listOf(
                "Установленный ток группы: ${fmt(installedCurrent)} А",
                "Минимум для типа ${group.groupType.name}: $floor А",
                curveReason
            ),
            formulaLines = listOf(
                "Округление вверх: ceil(${fmt(installedCurrent)}) = $rounded А",
                "Требуемый номинал: max($rounded, $floor) = $required А",
                "Из ряда 10, 16, 20, 25, 32, 40, 50, 63 А выбран ${group.circuitBreaker} А."
            ),
            limitations = listOf(
                "Ток короткого замыкания и требуемая отключающая способность группового автомата не рассчитываются.",
                "Пусковой ток оценивается по признаку двигателя, без моделирования конкретной характеристики оборудования."
            ),
            sourceText = group.sourceLabel()
        )
    }

    fun cable(group: CircuitGroup): InfoSheetPayload {
        val base = CablePolicyDefaults.productFloorByBreaker(group.circuitBreaker)
        return InfoSheetPayload(
            title = "Почему кабель ${fmtSection(group.cableSection)} мм²",
            sheetType = InfoSheetType.REFERENCE,
            currentValueText = "${fmtSection(group.cableSection)} мм²",
            bullets = listOf(
                "Сначала выбран автомат ${group.breakerType}${group.circuitBreaker}.",
                "Базовое сечение продуктовой матрицы для ${group.circuitBreaker} А: ${fmtSection(base)} мм²."
            ),
            formulaLines = listOf(
                "Выбрано первое поддерживаемое сечение не меньше ${fmtSection(base)} мм².",
                "Это предварительное значение. Полный расчёт открывается нажатием на параметр кабеля в карточке линии."
            ),
            limitations = listOf(
                "Для проверки допустимого тока нужны длина линии, материал, способ прокладки, температура и число совместно проложенных цепей.",
                "Отдельно падение напряжения рассчитывается после задания длины линии и допустимого предела.",
                "Ток КЗ, петля повреждения и время автоматического отключения пока не рассчитываются."
            ),
            sourceText = group.sourceLabel()
        )
    }

    fun rcd(group: CircuitGroup): InfoSheetPayload {
        val reasons = group.rcdReasonCodes.mapNotNull { code ->
            runCatching { RcdSelectionReason.valueOf(code) }.getOrNull()
        }
        val specialRoomMatched = RcdSelectionReason.SPECIAL_ROOM in reasons
        val generalSocketMatched =
            RcdSelectionReason.GENERAL_PURPOSE_SOCKET in reasons ||
                group.devices.any { it.isGeneralPurposeSocket() }
        val plugConnectedDevices = group.devices.filter { it.requiresSocketConnection }
        val socketConnectedMatched =
            RcdSelectionReason.SOCKET_CONNECTED_LOAD in reasons ||
                (group.rcdRequired && plugConnectedDevices.isNotEmpty())
        val exactReasonUnavailable = group.rcdRequired && reasons.isEmpty()
        val socketRuleMismatch = !group.rcdRequired && plugConnectedDevices.isNotEmpty()

        val decisionSummary = when {
            group.rcdRequired -> DecisionSummaryUi(
                title = "Групповое УЗО требуется",
                detail = buildList {
                    if (specialRoomMatched) add("особое помещение")
                    if (generalSocketMatched) add("бытовая розетка в составе линии")
                    if (socketConnectedMatched) {
                        add(
                            "розеточное подключение: " +
                                plugConnectedDevices.joinToString { it.name }
                        )
                    }
                    if (isEmpty()) add("сохранённое или ручное решение")
                }.joinToString(prefix = "Основание: ", separator = " и ") +
                    ". Чувствительность: ${group.rcdCurrent} мА.",
                tone = DecisionToneUi.POSITIVE
            )
            socketRuleMismatch -> DecisionSummaryUi(
                title = "Результат требует пересчёта",
                detail = "Группа содержит приборы с розеточным подключением, но сохранённое " +
                    "решение не содержит УЗО 30 мА. Это результат предыдущей версии алгоритма.",
                tone = DecisionToneUi.WARNING
            )
            else -> DecisionSummaryUi(
                title = "Отдельное УЗО не назначено",
                detail = "Алгоритм не обнаружил автоматических оснований для отдельной " +
                    "дифференциальной защиты этой группы.",
                tone = DecisionToneUi.NEUTRAL
            )
        }

        val ruleChecks = buildList {
            add(
                RuleCheckUi(
                    title = "Тип помещения",
                    detail = when {
                        specialRoomMatched ->
                            "Группа относится к особому помещению: правило УЗО 30 мА сработало."
                        exactReasonUnavailable ->
                            "Точный результат этой проверки не сохранён в старом или ручном решении."
                        else ->
                            "Специальное правило помещения для этой группы не сработало."
                    },
                    status = when {
                        specialRoomMatched -> RuleCheckStatusUi.MATCHED
                        exactReasonUnavailable -> RuleCheckStatusUi.WARNING
                        else -> RuleCheckStatusUi.NOT_MATCHED
                    }
                )
            )
            add(
                RuleCheckUi(
                    title = "Бытовая розетка",
                    detail = if (generalSocketMatched) {
                        "В составе линии найден элемент «Розетка бытовая»: правило УЗО 30 мА сработало."
                    } else {
                        "Элемента «Розетка бытовая» в составе этой группы нет."
                    },
                    status = if (generalSocketMatched) {
                        RuleCheckStatusUi.MATCHED
                    } else {
                        RuleCheckStatusUi.NOT_MATCHED
                    }
                )
            )
            add(
                RuleCheckUi(
                    title = "Розеточное подключение нагрузки",
                    detail = when {
                        socketConnectedMatched ->
                            "${plugConnectedDevices.joinToString { it.name }}. " +
                                "Эта группа заканчивается розеточными подключениями, поэтому " +
                                "правило УЗО 30 мА сработало для самой группы."
                        socketRuleMismatch ->
                            "${plugConnectedDevices.joinToString { it.name }}. Признак " +
                                "розеточного подключения есть, но УЗО не сохранено; нужен пересчёт."
                        else ->
                            "В группе нет устройств с признаком «Подключение через розетку»."
                    },
                    status = when {
                        socketConnectedMatched -> RuleCheckStatusUi.MATCHED
                        socketRuleMismatch -> RuleCheckStatusUi.WARNING
                        else -> RuleCheckStatusUi.NOT_MATCHED
                    }
                )
            )
            if (exactReasonUnavailable) {
                add(
                    RuleCheckUi(
                        title = "История решения",
                        detail = "Результат создан старой версией или изменён вручную, поэтому " +
                            "точный код сработавшего правила не сохранён.",
                        status = RuleCheckStatusUi.WARNING
                    )
                )
            }
        }

        val protectedLineLines = buildList {
            add("Помещение: ${group.roomName}")
            add("Группа: ${group.groupNumber}")
            add("Состав: ${group.devices.joinToString { it.name }.ifBlank { "устройства не указаны" }}")
            add("Автомат: ${group.breakerType}${group.circuitBreaker} — перегрузка и короткое замыкание")
            if (group.rcdRequired) {
                add("УЗО ${group.rcdCurrent} мА — ток утечки")
            } else {
                add("Отдельное УЗО в этой группе не предусмотрено")
            }
        }

        return InfoSheetPayload(
            title = "Обоснование дифференциальной защиты",
            sheetType = InfoSheetType.REFERENCE,
            currentValueText = if (group.rcdRequired) {
                "УЗО, чувствительность ${group.rcdCurrent} мА"
            } else {
                "отдельное УЗО не назначено"
            },
            currentValueLabel = "Решение",
            decisionSummary = decisionSummary,
            ruleChecks = ruleChecks,
            interpretation = if (group.rcdRequired) {
                "УЗО реагирует на ток утечки. Автомат ${group.breakerType}${group.circuitBreaker} " +
                    "отвечает за перегрузку и короткое замыкание — эти аппараты выполняют разные задачи."
            } else {
                "Отсутствие автоматического назначения не является запретом на УЗО: " +
                    "проектировщик может добавить его по фактической схеме и условиям объекта."
            },
            formulaTitle = "Почему выбрано 30 мА",
            formulaLines = if (group.rcdRequired) {
                listOf(
                    "30 мА — чувствительность к току утечки, а не номинальный рабочий ток аппарата.",
                    "В текущей модели 30 мА используется для дополнительной защиты групповых линий " +
                        "особых помещений, линий с бытовыми розетками и групп, устройства которых " +
                        "подключаются через розетку.",
                    "Номинальный ток УЗО должен быть согласован с автоматом группы; приложение его пока не выбирает."
                )
            } else {
                emptyList()
            },
            detailSections = listOf(
                InfoDetailSectionUi(
                    title = "Что относится к этой линии",
                    lines = protectedLineLines
                ),
                InfoDetailSectionUi(
                    title = "Что проверить перед монтажом",
                    lines = buildList {
                        if (plugConnectedDevices.isNotEmpty()) {
                            add("Что каждое указанное устройство действительно подключается через розетку.")
                            add("Что УЗО или дифавтомат 30 мА установлен именно в цепи этой группы.")
                        }
                        add("Номинальный ток и тип УЗО, полюсность и совместимость конкретной серии.")
                        add("Селективность с вводной и другими ступенями дифференциальной защиты.")
                    }
                )
            ),
            limitations = listOf(
                "Приложение выбирает необходимость отдельного УЗО и чувствительность, но не номинальный ток и тип аппарата.",
                "Розеточное окончание определяется по сохранённому признаку устройства; фактический способ монтажа должен проверить специалист.",
                "Границы зон, система заземления и параметры конкретного объекта в этом решении не анализируются."
            ),
            sourceText = group.sourceLabel()
        )
    }

    private fun ru.mugalimov.volthome.domain.model.Device.isGeneralPurposeSocket(): Boolean =
        deviceType == DeviceType.SOCKET && !requiresSocketConnection

    private fun CircuitGroup.installedCurrentA(): Double =
        CircuitLoadCalculator.calculate(devices).installedCurrentA

    private fun CircuitGroup.sourceLabel(): String {
        val source = when (calculationSource) {
            CalculationSource.AUTO -> "Автоматический расчёт"
            CalculationSource.MANUAL -> "Изменено вручную"
            CalculationSource.LEGACY -> "Результат предыдущей версии"
        }
        return "$source · алгоритм ${algorithmVersion.takeIf { it > 0 } ?: "—"}"
    }

    private fun fmt(value: Double): String = UiTextFormat.decimal(value, digits = 2)

    private fun fmtSection(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().replace('.', ',')
}
