package ru.mugalimov.volthome.di.database

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository
import ru.mugalimov.volthome.data.repository.impl.ProjectOwnershipRepositoryImpl

/**
 * Hilt биндинг ownership-репозитория.
 *
 * Комментарий:
 * - отдельный модуль, чтобы Commit 2 был самодостаточным.
 * - если у тебя уже есть общий RepositoryModule — можно перенести биндинг туда.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProjectOwnershipModule {

    @Binds
    @Singleton
    abstract fun bindProjectOwnershipRepository(
        impl: ProjectOwnershipRepositoryImpl
    ): ProjectOwnershipRepository
}