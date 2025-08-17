import android.app.Activity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

import ru.mugalimov.volthome.R

@Composable
fun TrueGlassExtendedFabSafe(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blurRadius: Float = 20f,   // 0<r<=25 — юнит без px/dp!
    downscale: Float = 0.15f   // 0..1, меньше = быстрее/сильнее размыто визуально
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(28.dp)

    // берём цвета из темы ЗДЕСЬ (не внутри post {})
    val overlayArgb = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f).toArgb()
    val clampedRadius = blurRadius.coerceIn(1f, 25f)

    Box(modifier) {
        AndroidView(
            modifier = Modifier
                .matchParentSize()
                .clip(shape),
            factory = { ctx ->
                val blurView = eightbitlab.com.blurview.BlurView(ctx)

                // откладываем setupUntil после attach/layout
                blurView.viewTreeObserver.addOnGlobalLayoutListener(
                    object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            blurView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            (ctx as? Activity)?.let { act ->
                                val root = act.window.decorView.findViewById<ViewGroup>(android.R.id.content)
                                blurView.post {
                                    // На Android 12+ можно любой алгоритм, но RenderScriptBlur тоже ок.
                                    // Если хочешь, на S+ можно так:
                                    // if (Build.VERSION.SDK_INT >= 31) blurView.setBlurAlgorithm(RenderEffectBlur())
                                    blurView.setupWith(root)
                                        .setBlurRadius(clampedRadius)         // ← ВАЖНО: 1..25, без px!
                                        .setBlurAutoUpdate(true)
                                        .setBlurEnabled(true)
                                        .setOverlayColor(overlayArgb)
                                        .setBlurEnabled(true)

                                    // Дополнительно уменьшим входную картинку (быстрее и «сильнее» визуально)
                                    // В BlurView v2 downscale задаётся через background/clip.
                                    // Если используешь v2.0.3, можно так:
                                    blurView.setBlurAutoUpdate(true)
                                    blurView.setBlurEnabled(true)
                                    // У v2 явного setDownsampleFactor нет — он берётся из Default: 8.
                                    // Если нужна точная настройка — можно перейти на RenderEffectBlur и играть radius.
                                }
                            }
                        }
                    }
                )
                blurView
            },
            onRelease = { view ->
                (view as? eightbitlab.com.blurview.BlurView)?.setBlurAutoUpdate(false)
            }
        )

        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_pdf_bolt),
                    contentDescription = null,
                    tint = Color.Unspecified
                )
            },
            text = { Text("Экспорт PDF") },
            containerColor = Color.Transparent, // фон даёт BlurView overlay
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
        )
    }
}