package ru.mugalimov.volthome.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.data.repository.impl.UserPlanRepositoryImpl

@HiltViewModel
class DebugProViewModel @Inject constructor(
    app: Application,
    private val userPlanRepository: UserPlanRepository
) : AndroidViewModel(app) {

    private val prefs =
        app.getSharedPreferences("debug_pro", Application.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    init {
        val saved = prefs.getBoolean("force_pro", false)
        _enabled.value = saved
        apply(saved)
    }

    fun setEnabled(v: Boolean) {
        _enabled.value = v
        prefs.edit().putBoolean("force_pro", v).apply()
        apply(v)
    }

    private fun apply(v: Boolean) {
        (userPlanRepository as? UserPlanRepositoryImpl)?.setDebugForcePro(v)
        // если вдруг подставишь другой repo — просто не применится, без крашей
    }
}