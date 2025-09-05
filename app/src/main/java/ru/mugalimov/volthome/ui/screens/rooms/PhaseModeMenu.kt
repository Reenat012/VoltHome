package ru.mugalimov.volthome.ui.screens.rooms

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.mugalimov.volthome.domain.model.PhaseMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhaseModeMenu(
    selected: PhaseMode,
    onSelect: (PhaseMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = if (selected == PhaseMode.SINGLE) "1 фаза" else "3 фазы",
            onValueChange = {},
            readOnly = true,
            label = { Text("Сеть") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("1 фаза") },
                onClick = {
                    onSelect(PhaseMode.SINGLE)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("3 фазы") },
                onClick = {
                    onSelect(PhaseMode.THREE)
                    expanded = false
                }
            )
        }
    }
}