package ru.mugalimov.volthome.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.mugalimov.volthome.data.local.datastore.AppPreferences
import ru.mugalimov.volthome.data.repository.impl.PreferencesRepositoryImpl

class OnboardingViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Проверяем, что запрашивается нужная viewModel
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            // Создаем зависимости
            val appPreferences = AppPreferences(context)
            val repository = PreferencesRepositoryImpl(appPreferences)

            // Возвращаем viewModel с зависимостями
            return OnboardingViewModel(repository) as T
        }
        throw IllegalArgumentException("Неизвестный класс ViewModel")
    }
}