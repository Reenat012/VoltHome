package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.RoomRepository

class GroupCalculatorFactory @Inject constructor(
    private val roomRepository: RoomRepository,
    private val groupRepository: ExplicationRepository
) {
    fun create(): GroupCalculator {
        return GroupCalculator(roomRepository, groupRepository)
    }
}