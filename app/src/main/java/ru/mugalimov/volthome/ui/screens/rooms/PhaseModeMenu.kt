package ru.mugalimov.volthome.ui.screens.rooms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.PopupProperties
import ru.mugalimov.volthome.domain.model.PhaseMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhaseModeMenu(
    mode: PhaseMode,
    onSelect: (PhaseMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val (title, desc, icon) = when (mode) {
        PhaseMode.SINGLE -> Triple("1 фаза", "Для квартир и частных домов", Icons.Outlined.Bolt)
        PhaseMode.THREE  -> Triple("3 фазы", "Для мощных сетей и мастерских", Icons.Outlined.ElectricBolt)
    }

    val accentColor = when (mode) {
        PhaseMode.SINGLE -> MaterialTheme.colorScheme.primary
        PhaseMode.THREE  -> MaterialTheme.colorScheme.tertiary
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = title,
            onValueChange = {},
            readOnly = true,
            label = { Text("Сеть") },
            leadingIcon = { Icon(icon, contentDescription = null, tint = accentColor) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = accentColor,
                focusedLabelColor = accentColor
            ),
            shape = RoundedCornerShape(16.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 300.dp, max = 360.dp)
        ) {
            // 1 фаза
            DropdownItem(
                title = "1 фаза",
                subtitle = "Для квартир и частных домов",
                selected = mode == PhaseMode.SINGLE,
                icon = Icons.Outlined.Bolt,
                onClick = {
                    if (mode != PhaseMode.SINGLE) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(PhaseMode.SINGLE)
                    }
                    expanded = false
                }
            )
            // 3 фазы
            DropdownItem(
                title = "3 фазы",
                subtitle = "Для мощных вводов/мастерских",
                selected = mode == PhaseMode.THREE,
                icon = Icons.Outlined.ElectricBolt,
                onClick = {
                    if (mode != PhaseMode.THREE) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(PhaseMode.THREE)
                    }
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun DropdownItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) }
    )
}