// app/src/main/java/ru/mugalimov/volthome/ui/utilities/PdfPrinter.kt
package ru.mugalimov.volthome.ui.utilities

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

class PdfPrinter(private val context: Context) {

    fun printHtml(html: String, jobName: String = "VoltHome — Экспликация") {
        val wv = WebView(context)
        wv.settings.defaultTextEncodingName = "utf-8"
        wv.settings.javaScriptEnabled = false
        // ВАЖНО: baseUrl указывает на твою подпапку
        wv.loadDataWithBaseURL(
            "file:///android_asset/report_pdf/",
            html,
            "text/html; charset=utf-8",
            "utf-8",
            null
        )
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = view.createPrintDocumentAdapter(jobName)
                val attrs = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                pm.print(jobName, adapter, attrs)
            }
        }
    }
}