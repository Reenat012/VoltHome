package ru.mugalimov.volthome.core.error


/**
 * Исключение, выбрасываемое при попытке добавить уже существующую комнату
 */
class RoomAlreadyExistsException(
    message: String = "Комната с таким именем уже существует"
) : Exception(message)