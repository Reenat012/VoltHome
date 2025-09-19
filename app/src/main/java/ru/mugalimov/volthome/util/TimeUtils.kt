package ru.mugalimov.volthome.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object TimeUtils {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    fun now(): Instant = Instant.now()

    fun formatIso(instant: Instant): String = formatter.format(instant)

    fun parseIso(str: String): Instant = Instant.parse(str)
}