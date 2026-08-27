package ru.mugalimov.volthome.di.database

import androidx.room.migration.Migration

internal fun migrationsFrom(version: Int): Array<Migration> =
    AppDatabase.migrationsFrom(version)
