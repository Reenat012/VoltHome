package ru.mugalimov.volthome.ui.screens.explication.export_pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.R
import androidx.compose.ui.Alignment

@Composable
fun PdfFabIcon() {
    Box(Modifier.size(28.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_pdf_bolt),
            contentDescription = null,
            tint = Color.Unspecified
        )
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = RoundedCornerShape(6.dp),
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 0.dp, y = 0.dp)
        ) {
            Text(
                "PDF",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}