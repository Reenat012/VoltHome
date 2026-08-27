package ru.mugalimov.volthome.ui.model

import ru.mugalimov.volthome.ui.navigation.Screens

enum class ManualModeControlAvailability {
    /** На экране можно включить ручной режим и управлять активным черновиком. */
    EDITABLE,

    /** Экран не редактирует структуру, но активный глобальный режим нельзя скрывать. */
    STATUS_ONLY,

    /** Ручной режим не активен, а текущий экран не умеет с ним работать. */
    HIDDEN
}

/** Единая политика показа глобального переключателя режима для маршрутов приложения. */
object ManualModeUiPolicy {
    fun availability(
        route: String?,
        manualModeActive: Boolean
    ): ManualModeControlAvailability {
        val editable = route == Screens.LoadsScreen.route ||
            route == Screens.ExplicationScreen.route

        return when {
            editable -> ManualModeControlAvailability.EDITABLE
            manualModeActive -> ManualModeControlAvailability.STATUS_ONLY
            else -> ManualModeControlAvailability.HIDDEN
        }
    }
}
