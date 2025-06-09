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

class DocumentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("URL") ?: run {
            Toast.makeText(this, "Неверный URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            AppTheme {
                WebViewScreen(url)
            }
        }
    }
}

@Composable
fun WebViewScreen(url: String) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true // Важно для современных сайтов
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webViewClient = object : WebViewClient() {
                // Обработка ошибок загрузки
                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String,
                    failingUrl: String
                ) {
                    Toast.makeText(context, "Ошибка загрузки: $description", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(url) {
        webView.loadUrl(url)
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}