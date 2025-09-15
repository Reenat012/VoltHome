package ru.mugalimov.volthome.di.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import com.google.crypto.tink.Aead
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import ru.mugalimov.volthome.data.remote.secure.CryptoProvider
import ru.mugalimov.volthome.data.remote.secure.JwtStore
import ru.mugalimov.volthome.data.remote.secure.JwtStoreSerializer
import ru.mugalimov.volthome.data.remote.secure.TokenStorage
import java.io.File
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object SecureDataStoreModule {

    private const val FILE_NAME = "jwt_store.bin"

    @Provides @Singleton
    fun provideAead(@ApplicationContext ctx: Context): Aead = CryptoProvider.aead(ctx)

    // ВОЗВРАЩАЕМ АБСТРАКЦИЮ, не конкретный класс
    @Provides @Singleton
    fun provideJwtSerializer(aead: Aead, gson: Gson): Serializer<JwtStore> =
        JwtStoreSerializer(aead, gson)

    @Provides @Singleton
    fun provideJwtDataStore(
        @ApplicationContext ctx: Context,
        serializer: Serializer<JwtStore>
    ): DataStore<JwtStore> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        return DataStoreFactory.create(
            serializer = serializer,
            corruptionHandler = ReplaceFileCorruptionHandler { JwtStore() },
            scope = scope,
            produceFile = { File(ctx.filesDir, FILE_NAME) }
        )
    }

    @Provides @Singleton
    fun provideTokenStorage(ds: DataStore<JwtStore>) = TokenStorage(ds)
}