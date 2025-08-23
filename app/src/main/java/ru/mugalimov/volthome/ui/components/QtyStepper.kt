package ru.mugalimov.volthome.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ui/components/QtyStepper.kt
@Composable
fun QtyStepper(qty: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onChange((qty - 1).coerceAtLeast(0)) }) { Icon(Icons.Outlined.Remove, null) }
        Text("$qty", modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
        IconButton({ onChange(qty + 1) }) { Icon(Icons.Outlined.Add, null) }
    }
}