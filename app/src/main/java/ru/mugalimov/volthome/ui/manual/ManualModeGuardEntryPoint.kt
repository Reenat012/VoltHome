package ru.mugalimov.volthome.ui.manual

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.use_case.manual.CommitManualDraftToLocalDbUseCase

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ManualModeGuardEntryPoint {
    fun manualRepo(): ManualEditSessionRepository
    fun commitManualDraftToLocalDb(): CommitManualDraftToLocalDbUseCase

    // ✅ Коммит 8: иначе получишь "откат" фаз при возврате в AUTO
    fun groupPhaseOverrideDao(): GroupPhaseOverrideDao
}