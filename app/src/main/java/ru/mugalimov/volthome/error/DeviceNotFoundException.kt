package ru.mugalimov.volthome.error

class DeviceNotFoundException : Exception {
    constructor() : super("Устройство не найдено")
    constructor(message: String) : super(message)
    constructor(id: Int) : super("Устройство с ID $id не найдено")
}