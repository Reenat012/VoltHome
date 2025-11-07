package ru.mugalimov.volthome.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date

object TimeUtils {

    private val ISO_UTC: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    /** Текущее время как Instant */
    fun now(): Instant = Instant.now()

    /** Форматирование Instant в ISO8601 UTC, например: 2025-11-07T12:34:56Z */
    fun formatIso(instant: Instant): String = ISO_UTC.format(instant)

    /** Перегрузка для java.util.Date */
    fun formatIso(date: Date): String = ISO_UTC.format(date.toInstant())

    /** Парсинг ISO8601 в Instant (или now(), если строка битая) */
    fun parseIsoOrNow(iso: String?): Instant =
        try { Instant.parse(iso) } catch (_: Throwable) { now() }

    /** Удобство: ISO “сейчас” */
    fun isoNow(): String = formatIso(now())

    /** Безопасный парсинг из строки в Date (UTC) */
    fun parseDateOrNow(iso: String?): Date =
        try { Date.from(Instant.parse(iso)) } catch (_: Throwable) { Date() }
}