package ru.mugalimov.volthome.error

class RoomNotFoundException : Exception {
    constructor() : super("Комната не найдена")
    constructor(message: String) : super(message)
    constructor(id: Int) : super("Комната с ID $id не найдена")
}