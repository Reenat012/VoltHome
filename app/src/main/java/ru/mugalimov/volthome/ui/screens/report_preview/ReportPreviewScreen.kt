package ru.mugalimov.volthome.ui.screens.report_preview

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * Внутренний preview отчёта.
 *
 * ✅ Free + PRO: показывает HTML отчёта оффлайн, без внешних приложений.
 * 🚫 Здесь НЕТ export/share/save UI — это сознательное ограничение Free.
 *
 * HTML должен прийти "сверху" (обычно из VM). Экран только рендерит.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReportPreviewScreen(
    html: String,
    modifier: Modifier = Modifier
) {
    val bg = MaterialTheme.colorScheme.background

    if (html.isBlank()) {
        Box(
            modifier = modifier.fillMaxSize().background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text("Отчёт недоступен")
        }
        return
    }

    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                try {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.clearHistory()
                    wv.removeAllViews()
                    wv.destroy()
                } catch (_: Throwable) {
                    // ignore
                }
            }
            webViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this

                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // ---------- ЖЁСТКИЙ SAFE-PREVIEW ПАКЕТ ----------
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    settings.databaseEnabled = false

                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false

                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setGeolocationEnabled(false)
                    settings.saveFormData = false

                    // оффлайн/без кеша/без сети
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.blockNetworkLoads = true
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true

                    // UX
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    webViewClient = object : WebViewClient() {

                        // Блокируем любые клики/редиректы
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = true

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

                        // Режем любые подгрузки ресурсов (http/https/file/content/intent и т.д.)
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val uri = request?.url ?: return null
                            val scheme = uri.scheme?.lowercase()
                            val allowed = scheme == "data" || scheme == "about"
                            return if (allowed) {
                                null
                            } else {
                                WebResourceResponse(
                                    "text/plain",
                                    "utf-8",
                                    ByteArrayInputStream(ByteArray(0))
                                )
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }

                    // Стартовый лоад
                    isLoading = true
                    loadDataWithBaseURL(
                        /* baseUrl = */ "about:blank",
                        /* data = */ html,
                        /* mimeType = */ "text/html",
                        /* encoding = */ "utf-8",
                        /* historyUrl = */ null
                    )
                }
            },
            update = { wv ->
                // если html обновился — перезагружаем (у тебя html приходит один раз, но пусть будет корректно)
                isLoading = true
                wv.loadDataWithBaseURL(
                    "about:blank",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        )

        if (isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}