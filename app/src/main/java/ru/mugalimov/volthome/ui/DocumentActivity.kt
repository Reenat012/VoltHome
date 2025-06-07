package ru.mugalimov.volthome.ui

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
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

        val url = intent.getStringExtra("URL") ?: ""

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
    val webView = remember { WebView(context).apply {
        settings.javaScriptEnabled = true
        webViewClient = WebViewClient()
    } }

    LaunchedEffect(url) {
        webView.loadUrl(url)
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}