package ru.mugalimov.volthome.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.UserPlanRepository

@HiltViewModel
class DebugProViewModel @Inject constructor(
    app: Application,
    private val userPlanRepository: UserPlanRepository
) : AndroidViewModel(app) {

    private val prefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        app.getSharedPreferences("debug_pro", Application.MODE_PRIVATE)
    }
    private val mutableEnabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = mutableEnabled

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = prefs.getBoolean("force_pro", false)
            mutableEnabled.value = saved
            userPlanRepository.setDebugForcePro(saved)
        }
    }

    fun setEnabled(enabled: Boolean) {
        mutableEnabled.value = enabled
        userPlanRepository.setDebugForcePro(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putBoolean("force_pro", enabled).apply()
        }
    }
}
