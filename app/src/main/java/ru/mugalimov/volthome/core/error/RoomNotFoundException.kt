package ru.mugalimov.volthome.core.error

class RoomNotFoundException : Exception {
    constructor() : super("Комната не найдена")
    constructor(message: String) : super(message)
    constructor(id: Int) : super("Комната с ID $id не найдена")
}