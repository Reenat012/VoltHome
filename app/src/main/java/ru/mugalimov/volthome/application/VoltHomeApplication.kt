package ru.mugalimov.volthome.application

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoltHomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
//        VKID.init(this) // Инициализация SDK один раз на процесс
    }
}