package ru.mugalimov.volthome.data.remote.secure


import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TokenStorage(private val ds: DataStore<JwtStore>) {

    val tokenFlow: Flow<String?> = ds.data.map { if (it.token.isBlank()) null else it.token }
    val expiryFlow: Flow<Long> = ds.data.map { it.expiryEpochSec }

    suspend fun read(): JwtStore = ds.data.first()

    suspend fun write(token: String?, expiryEpochSec: Long?) {
        ds.updateData { cur ->
            cur.copy(
                token = token ?: "",
                expiryEpochSec = expiryEpochSec ?: cur.expiryEpochSec
            )
        }
    }

    suspend fun clear() {
        ds.updateData { JwtStore() }
    }
}

// маленькие экстеншены удобства
private suspend fun <T> DataStore<T>.readOnce(): T = data.first()
private suspend inline fun <T> DataStore<T>.mutate(crossinline block: (T) -> T) {
    updateData { cur -> block(cur) }
}