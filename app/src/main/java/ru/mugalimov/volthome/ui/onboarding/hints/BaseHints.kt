package ru.mugalimov.volthome.ui.onboarding.hints

import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.ui.onboarding.model.LoadsOnboardingFacts
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.model.ProjectsOnboardingFacts
import ru.mugalimov.volthome.ui.onboarding.model.RoomsOnboardingFacts

/**
 * Базовые hint-спеки для rollout base flow.
 *
 * Важно:
 * - здесь только декларация
 * - никаких side-effects
 * - выбор hint идёт только по canonical facts
 */
data class BaseHintSpec(
    val hintId: OnboardingHintId,
    val screen: OnboardingScreen,
    val targetTag: OnboardingTargetTag?,
    val priority: Int,
    val title: String,
    val body: String
)

object BaseHints {

    val PROJECTS_ADD_FIRST_PROJECT = BaseHintSpec(
        hintId = OnboardingHintId.PROJECTS_ADD_FIRST_PROJECT,
        screen = OnboardingScreen.PROJECTS,
        targetTag = OnboardingTargetTag.PROJECTS_ADD_BUTTON,
        priority = 100,
        title = "Добавь первый проект",
        body = "Создай проект, чтобы начать собирать комнаты, нагрузки и щит."
    )

    val PROJECTS_SELECT_PROJECT = BaseHintSpec(
        hintId = OnboardingHintId.PROJECTS_SELECT_PROJECT,
        screen = OnboardingScreen.PROJECTS,
        targetTag = OnboardingTargetTag.PROJECTS_FIRST_ITEM,
        priority = 90,
        title = "Выбери проект",
        body = "Открой проект из списка, чтобы продолжить расчёт."
    )

    val ROOMS_ADD_FIRST_ROOM = BaseHintSpec(
        hintId = OnboardingHintId.ROOMS_ADD_FIRST_ROOM,
        screen = OnboardingScreen.ROOMS,
        targetTag = OnboardingTargetTag.ROOMS_ADD_FAB,
        priority = 90,
        title = "Добавь комнату",
        body = "Сначала добавь комнату, а потом наполни её устройствами."
    )

    val LOADS_VIEW_PHASE_BALANCE = BaseHintSpec(
        hintId = OnboardingHintId.LOADS_VIEW_PHASE_BALANCE,
        screen = OnboardingScreen.LOADS,
        targetTag = OnboardingTargetTag.LOADS_DONUT_CHART,
        priority = 80,
        title = "Смотри баланс фаз",
        body = "Здесь видно, как нагрузка распределена между фазами A, B и C."
    )

    /**
     * Выбор base hint для Projects.
     *
     * Приоритеты:
     * - если проектов нет -> только add
     * - если проекты есть -> select
     */
    fun forProjects(facts: ProjectsOnboardingFacts): BaseHintSpec? {
        val candidates = buildList {
            if (facts.projectsCount == 0) add(PROJECTS_ADD_FIRST_PROJECT)
            if (facts.projectsCount > 0) add(PROJECTS_SELECT_PROJECT)
        }
        return pickHighestPriority(candidates)
    }

    /**
     * Выбор base hint для Rooms.
     */
    fun forRooms(facts: RoomsOnboardingFacts): BaseHintSpec? {
        val candidates = buildList {
            if (!facts.isLoading && facts.roomsCount == 0) {
                add(ROOMS_ADD_FIRST_ROOM)
            }
        }
        return pickHighestPriority(candidates)
    }

    /**
     * Выбор base hint для Loads.
     */
    fun forLoads(facts: LoadsOnboardingFacts): BaseHintSpec? {
        val candidates = buildList {
            if (!facts.isLoading && facts.phaseMode == PhaseMode.THREE && facts.groupsCount > 0) {
                add(LOADS_VIEW_PHASE_BALANCE)
            }
        }
        return pickHighestPriority(candidates)
    }

    /**
     * Единая точка выбора по priority.
     *
     * Это и есть минимальная формализация priority resolution для base rollout.
     */
    fun pickHighestPriority(candidates: List<BaseHintSpec>): BaseHintSpec? {
        return candidates.maxByOrNull { it.priority }
    }
}