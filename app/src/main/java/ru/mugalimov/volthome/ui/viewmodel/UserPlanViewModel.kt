package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.UserPlan

@HiltViewModel
class UserPlanViewModel @Inject constructor(
    userPlanRepository: UserPlanRepository
) : ViewModel() {

    /**
     * Просто прокидывает planFlow из репозитория.
     * Никакой логики — только мост между DI и Compose.
     */
    val plan: StateFlow<UserPlan> = userPlanRepository.planFlow
}