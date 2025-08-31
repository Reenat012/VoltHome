package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ru.mugalimov.volthome.domain.use_case.RecalculateGroupsOnDeviceChangeUseCase
import javax.inject.Inject

@HiltViewModel
class AppSyncViewModel @Inject constructor(
    private val autoRecalc: RecalculateGroupsOnDeviceChangeUseCase
) : ViewModel() {

    init {
        // запускаем авто‑пересчёт на всё время жизни приложения
        autoRecalc.launch(viewModelScope)
    }
}