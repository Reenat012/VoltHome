// app/src/main/java/ru/mugalimov/volthome/ui/utilities/ComposeToBase64.kt
package ru.mugalimov.volthome.ui.utilities

import android.graphics.Bitmap
import android.util.Base64
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.core.graphics.applyCanvas
import androidx.core.view.doOnPreDraw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.ByteArrayOutputStream

/**
 * Безопасно рендерит Composable в base64 PNG:
 * 1) Временно добавляет ComposeView в корень Activity (attached-to-window),
 * 2) Наследует CompositionContext, чтобы был WindowRecomposer,
 * 3) Рендерит, снимает bitmap, удаляет из иерархии.
 */
suspend fun renderComposableToBase64Png(
    activity: ComponentActivity,
    widthPx: Int,
    heightPx: Int,
    content: @Composable () -> Unit
): String = withContext(Dispatchers.Main) {
    val root = activity.findViewById<ViewGroup>(android.R.id.content)

    suspendCancellableCoroutine { cont ->
        val composeView = ComposeView(activity).apply {
            // Наследуем контекст композиции ОТ уже прикреплённого root
            setParentCompositionContext(root.findViewTreeCompositionContext())
            layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
            setContent { content() }
        }

        // 1) Добавляем во View-иерархию, чтобы был windowRecomposer
        root.addView(composeView)

        // 2) Ждём первого пред-рисунка, когда вью уже измерен/разложен
        composeView.doOnPreDraw {
            try {
                // Создаём Bitmap и рисуем туда View
                val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                bmp.applyCanvas { composeView.draw(this) }

                // 3) Удаляем ComposeView из иерархии сразу после съёмки
                root.removeView(composeView)

                // Конвертируем в base64
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                cont.resume("data:image/png;base64,$base64")
            } catch (t: Throwable) {
                root.removeView(composeView)
                cont.resume("data:image/png;base64,") // или пробросьте исключение по своему вкусу
            }
        }

        // При отмене корутины — приберёмся
        cont.invokeOnCancellation {
            root.removeView(composeView)
        }
    }
}