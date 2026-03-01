package ru.mugalimov.volthome.data.repository

/**
 * Очищает все persisted "manual overrides" для проекта.
 *
 * ВАЖНО:
 * - group_device_join не имеет projectId, поэтому чистим только по groupIds проекта.
 */
interface ManualOverridesCleanerRepository {
    /**
     * @return true если операция выполнена успешно.
     */
    suspend fun clearAllManualOverrides(projectId: String): Boolean
}