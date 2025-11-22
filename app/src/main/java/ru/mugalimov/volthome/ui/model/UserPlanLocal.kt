package ru.mugalimov.volthome.ui.model

import androidx.compose.runtime.staticCompositionLocalOf
import ru.mugalimov.volthome.domain.model.UserPlan

/**
 * Глобальный доступ к текущему тарифу пользователя в UI-дереве.
 * По умолчанию — FREE, пока не подтянули профиль.
 */
val LocalUserPlan = staticCompositionLocalOf { UserPlan.FREE }