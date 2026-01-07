package ru.mugalimov.volthome.ui.screens.welcome

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.compose.runtime.Composable
import ru.mugalimov.volthome.core.theme.VoltHomeTheme
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

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    // thin-wrapper: единый entrypoint темы живёт в core/theme/Theme.kt
    VoltHomeTheme(content = content)
}