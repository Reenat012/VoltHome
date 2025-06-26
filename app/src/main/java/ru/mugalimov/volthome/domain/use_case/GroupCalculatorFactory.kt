package ru.mugalimov.volthome.domain.use_case

import GroupCalculator
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import javax.inject.Inject

class GroupCalculatorFactory @Inject constructor(
    private val roomRepository: RoomRepository,
    private val groupRepository: ExplicationRepository
) {
    fun create(): GroupCalculator {
        return GroupCalculator(roomRepository, groupRepository)
    }
}
