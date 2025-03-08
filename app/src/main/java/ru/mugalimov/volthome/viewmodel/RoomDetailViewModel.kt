package ru.mugalimov.volthome.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.mugalimov.volthome.model.Room
import ru.mugalimov.volthome.repository.RoomRepository
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@HiltViewModel
class RoomDetailViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    savedStateHandle: SavedStateHandle // Конверт с запросом (ID комнаты)
) : ViewModel() {

    private val roomId = savedStateHandle.get<Int>("roomId") ?: 0

    //хранилище комнат
    private val _room = MutableStateFlow<Room?>(null)
    val room: StateFlow<Room?> = _room.asStateFlow()

    init {
        loadRoom()
    }

    private fun loadRoom() {
        viewModelScope.launch {
            try {
                //ищем комнату по roomId
                val foundRoom = roomRepository.getRoomById(roomId)

                //записываем комнату в хранилище
                _room.value = foundRoom
            } catch (e: Exception) {
                //если комната не найдена, оставляем хранилище пустым
                _room.value = null
            }
        }
    }
}