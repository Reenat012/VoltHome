
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import ru.mugalimov.volthome.R

@Composable
fun ExportPdfFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_pdf_bolt),
                contentDescription = "Экспорт PDF",
                tint = Color.Unspecified
            )
        },
        text = { Text("Экспорт PDF") },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,   // как на скрине
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
}