package ru.mugalimov.volthome.data.repository.impl

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.UserPlan

@Singleton
class UserPlanRepositoryImpl @Inject constructor() : UserPlanRepository {

    private val _planFlow = MutableStateFlow(UserPlan.FREE)
    override val planFlow: StateFlow<UserPlan> = _planFlow

    override suspend fun setPlan(plan: UserPlan) {
        _planFlow.value = plan
    }
}