package ru.mugalimov.volthome.application

import android.os.StrictMode

// вспомогательный файл, специально для изоляции логики StrictMode от основного кода приложения.
object DebugStrictMode {

    fun enable() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }
}