package ru.mugalimov.volthome.ui

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import ru.mugalimov.volthome.ui.screens.welcome.AppTheme
import ru.mugalimov.volthome.ui.screens.welcome.isOnline

class DocumentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webUrl = intent.getStringExtra("WEB_URL") ?: ""
        val localAssetPath = intent.getStringExtra("LOCAL_ASSET_PATH") ?: ""

        if (webUrl.isEmpty() && localAssetPath.isEmpty()) {
            Toast.makeText(this, "Неверные параметры документа", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            AppTheme {
                WebViewScreen(
                    webUrl = webUrl,
                    localAssetPath = localAssetPath
                )
            }
        }
    }
}

@Composable
fun WebViewScreen(webUrl: String, localAssetPath: String) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            // Создаем функцию для загрузки локальных ресурсов
            val loadLocalAsset = { assetPath: String ->
                try {
                    loadUrl("file:///android_asset/$assetPath")
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Ошибка загрузки локального документа",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String,
                    failingUrl: String
                ) {
                    // При ошибке загрузки пробуем локальный файл
                    if (failingUrl == webUrl && localAssetPath.isNotEmpty()) {
                        loadLocalAsset(localAssetPath)
                    } else {
                        Toast.makeText(
                            context,
                            "Ошибка загрузки: $description",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // Проверяем интернет при запуске
    val urlToLoad = if (context.isOnline() && webUrl.isNotEmpty()) {
        webUrl
    } else if (localAssetPath.isNotEmpty()) {
        "file:///android_asset/$localAssetPath"
    } else {
        ""
    }

    LaunchedEffect(urlToLoad) {
        if (urlToLoad.isNotEmpty()) {
            webView.loadUrl(urlToLoad)
        } else {
            Toast.makeText(
                context,
                "Документ недоступен",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}