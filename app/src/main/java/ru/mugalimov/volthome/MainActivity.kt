// MainActivity.kt
package ru.mugalimov.volthome

import MainScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dagger.hilt.android.AndroidEntryPoint


/**
 * Главная Activity приложения, точка входа в приложение.
 * Наследуется от ComponentActivity для поддержки Jetpack Compose.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Устанавливаем Compose как корневой view
        setContent {
            VoltHomeApp()
        }
    }
}

/**
 * Корневой компонент приложения, обернутый в Material Design тему.
 * Определяет общую тему и стили для всего приложения.
 */
@Composable
private fun VoltHomeApp() {
    MaterialTheme {
        MainScreen()
    }
}




