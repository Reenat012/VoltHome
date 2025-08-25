package ru.mugalimov.volthome.ui.screens.welcome

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import ru.mugalimov.volthome.core.theme.Pink40
import ru.mugalimov.volthome.core.theme.Pink80
import ru.mugalimov.volthome.core.theme.Purple40
import ru.mugalimov.volthome.core.theme.Purple80
import ru.mugalimov.volthome.core.theme.PurpleGrey40
import ru.mugalimov.volthome.core.theme.PurpleGrey80
import ru.mugalimov.volthome.core.theme.Typography
import ru.mugalimov.volthome.ui.DocumentActivity

// Добавьте эту функцию для проверки интернета
fun Context.isOnline(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return connectivityManager.activeNetworkInfo?.isConnected == true
}

// Измененная функция для открытия документов
fun Context.openDocument(webUrl: String, localAssetPath: String) {
    val intent = Intent(this, DocumentActivity::class.java).apply {
        putExtra("WEB_URL", webUrl)
        putExtra("LOCAL_ASSET_PATH", localAssetPath)
    }
    startActivity(intent)
}


private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}