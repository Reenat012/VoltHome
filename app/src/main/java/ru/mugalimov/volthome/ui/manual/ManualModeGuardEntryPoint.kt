package ru.mugalimov.volthome.ui.manual

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.use_case.manual.CancelManualAndAutoRecalcUseCase
import ru.mugalimov.volthome.domain.use_case.manual.CommitManualDraftToLocalDbUseCase

/**
 * EntryPoint для получения зависимостей guard-а на верхнем уровне приложения.
 * Здесь нет "UI" — только usecase/repo.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ManualModeGuardEntryPoint {
    fun manualRepo(): ManualEditSessionRepository
    fun commitManualDraftToLocalDb(): CommitManualDraftToLocalDbUseCase
    fun cancelManualAndAutoRecalc(): CancelManualAndAutoRecalcUseCase
}