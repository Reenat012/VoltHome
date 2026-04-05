package ru.mugalimov.volthome.data.billing.pending.storage

import ru.mugalimov.volthome.data.billing.pending.model.PendingConfirmRecord

/**
 * Хранилище pending confirm записей.
 *
 * Пока интерфейс сделан как storage списка, а не single-record.
 * Это безопаснее на будущее и не привязывает нас к одной покупке навсегда.
 */
interface PendingConfirmStorage {

    /**
     * Прочитать все записи.
     * Unknown format / corrupted payload не должны ронять вызывающий код.
     */
    suspend fun getAll(): List<PendingConfirmRecord>

    /**
     * Найти запись по purchaseFlowId.
     */
    suspend fun getByFlowId(flowId: String): PendingConfirmRecord?

    /**
     * Создать или обновить запись.
     */
    suspend fun upsert(record: PendingConfirmRecord)

    /**
     * Удалить запись после server-consistent success.
     */
    suspend fun removeByFlowId(flowId: String)

    /**
     * Удалить все записи user scope.
     *
     * Нужен для logout, чтобы pending хвост одного пользователя
     * не утёк в следующую сессию.
     */
    suspend fun removeByUserId(
        userId: String,
        includeUnknownUser: Boolean = false
    )

    /**
     * Полная очистка storage.
     * Нужно редко: только для recovery/debug/rollback сценариев.
     */
    suspend fun clear()
}