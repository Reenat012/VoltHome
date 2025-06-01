package ru.mugalimov.volthome.core.error

class GroupNotFoundException : Exception {
    constructor() : super("Группа не найдено")
    constructor(message: String) : super(message)
    constructor(id: Int) : super("Группа с ID $id не найдено")
}