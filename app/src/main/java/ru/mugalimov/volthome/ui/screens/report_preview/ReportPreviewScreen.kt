package ru.mugalimov.volthome.ui.screens.report_preview

import android.annotation.SuppressLint
import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

private const val APP_ASSETS_BASE = "https://appassets.androidplatform.net/assets/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReportPreviewScreen(
    html: String,
    showProHint: Boolean,
    onUnlockClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // ✅ Запрещаем скриншоты/запись экрана на этом preview
    DisposableEffect(Unit) {
        activity?.enableSecureFlag()
        onDispose { activity?.disableSecureFlag() }
    }

    val bg = MaterialTheme.colorScheme.background

    if (html.isBlank()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text("Отчёт недоступен")
        }
        return
    }

    // ✅ file:///android_asset/... → appassets + гарантируем viewport
    val safeHtml = remember(html) {
        val replaced = html.replace("file:///android_asset/", APP_ASSETS_BASE)
        ensureViewport(replaced)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // ✅ Вариант A: компактная PRO-плашка сверху
        if (showProHint) {
            ProHintBar(
                onUnlockClick = onUnlockClick,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val assetLoader = WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                        .build()

                    WebView(ctx).apply {
                        webViewRef = this

                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // ---------- ЖЁСТКИЙ SAFE-PREVIEW ----------
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

                        // сеть/внешние ресурсы режем
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.blockNetworkLoads = true
                        settings.loadsImagesAutomatically = true
                        settings.blockNetworkImage = true

                        // UX / масштаб
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = false
                        settings.loadWithOverviewMode = false
                        settings.textZoom = 100

                        webViewClient = object : WebViewClient() {

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest
                            ): WebResourceResponse? {
                                val url = request.url ?: return null
                                val scheme = url.scheme?.lowercase().orEmpty()
                                val host = url.host?.lowercase().orEmpty()

                                // Главный документ НЕ режем (loadDataWithBaseURL).
                                if (request.isForMainFrame) return null

                                // Разрешаем системные схемы.
                                if (scheme == "data" || scheme == "about") return null

                                // Разрешаем только appassets.
                                val isAppAssets =
                                    scheme == "https" && host == "appassets.androidplatform.net"
                                if (isAppAssets) {
                                    return assetLoader.shouldInterceptRequest(url)
                                }

                                // Всё остальное блокируем валидным ответом.
                                return WebResourceResponse(
                                    "text/plain",
                                    "utf-8",
                                    403,
                                    "Blocked",
                                    mapOf("Cache-Control" to "no-store"),
                                    ByteArrayInputStream(ByteArray(0))
                                )
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = true

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

                            override fun onPageFinished(view: WebView?, url: String?) {
                                // ✅ Под device-width и разумный initial scale
                                view?.setInitialScale(0)
                                isLoading = false
                            }
                        }

                        isLoading = true
                        loadDataWithBaseURL(
                            APP_ASSETS_BASE,
                            safeHtml,
                            "text/html",
                            "utf-8",
                            null
                        )
                    }
                },
                update = { wv ->
                    isLoading = true
                    wv.loadDataWithBaseURL(
                        APP_ASSETS_BASE,
                        safeHtml,
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
}

@Composable
private fun ProHintBar(
    onUnlockClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Сохранение и отправка PDF доступны в PRO.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.padding(4.dp))

            TextButton(
                onClick = { onUnlockClick?.invoke() },
                enabled = onUnlockClick != null
            ) {
                Text("PRO")
            }
        }
    }
}

private fun ensureViewport(html: String): String {
    val lower = html.lowercase()
    val hasViewport =
        lower.contains("""<meta name="viewport"""") ||
                lower.contains("""name='viewport'""") ||
                lower.contains("""name="viewport"""")

    if (hasViewport) return html

    val meta =
        """<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes">"""

    val headOpenIndex = lower.indexOf("<head>")
    return if (headOpenIndex != -1) {
        val insertPos = headOpenIndex + "<head>".length
        html.substring(0, insertPos) + meta + html.substring(insertPos)
    } else {
        "<head>$meta</head>$html"
    }
}

private fun Activity.enableSecureFlag() {
    window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
    )
}

private fun Activity.disableSecureFlag() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
}