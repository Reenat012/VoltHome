package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.datastore.AnalyticsChoice
import ru.mugalimov.volthome.data.local.datastore.AppPreferences

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val preferences: AppPreferences
) : ViewModel() {

    val analyticsEnabled = preferences.analyticsChoice
        .map { it == AnalyticsChoice.ENABLED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAnalyticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAnalyticsChoice(
                if (enabled) AnalyticsChoice.ENABLED else AnalyticsChoice.DISABLED
            )
        }
    }
}
