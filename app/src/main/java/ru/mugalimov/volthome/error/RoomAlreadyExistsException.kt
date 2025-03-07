package ru.mugalimov.volthome.error


/**
 * Исключение, выбрасываемое при попытке добавить уже существующую комнату
 */
class RoomAlreadyExistsException(
    message: String = "Комната с таким именем уже существует"
) : Exception(message)