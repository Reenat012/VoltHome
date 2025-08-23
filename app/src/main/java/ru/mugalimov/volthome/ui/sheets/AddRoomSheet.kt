package ru.mugalimov.volthome.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRoomSheet(
    roomTemplates: List<RoomTemplateUi>,
    onCreateRoom: (RoomTemplateUi) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var search by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val filtered = remember(search.text, roomTemplates) {
        val q = search.text.trim().lowercase()
        if (q.isEmpty()) roomTemplates
        else roomTemplates.filter { it.name.lowercase().contains(q) }
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Добавить комнату",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск по шаблонам…") },
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) { tpl ->
                    RoomTemplateCard(
                        template = tpl,
                        onCreate = { onCreateRoom(tpl) }
                    )
                }
            }
        }
    }
}

/** UI-модель шаблона комнаты (подстрой под свою) */
data class RoomTemplateUi(
    val id: String,
    val name: String,
    val devices: List<DeviceUi>
)

/** UI-модель устройства для списка добавления */
data class DeviceUi(
    val id: String,
    val name: String,
    val powerW: Int,
    val powerFactor: Double,
    val demandRatio: Double,
    val voltageLabel: String,
    val typeLabel: String? = null
)

/** Карточка шаблона комнаты со списком устройств и разворотами */
@Composable
private fun RoomTemplateCard(
    template: RoomTemplateUi,
    onCreate: () -> Unit
) {
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Устройств: ${template.devices.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = onCreate) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Создать")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Список устройств шаблона (read-only, с раскрытием параметров)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                template.devices.forEach { d ->
                    val isExpanded = expandedMap[d.id] == true
                    DevicePreviewCard(
                        device = d,
                        expanded = isExpanded,
                        onToggle = { expandedMap[d.id] = !(expandedMap[d.id] ?: false) }
                    )
                }
            }
        }
    }
}

/** Превью карточка устройства — раскрывается вниз, параметры только для просмотра */
@Composable
private fun DevicePreviewCard(
    device: DeviceUi,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() },
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.bodyLarge)
                    if (!expanded) {
                        Text(
                            "${device.powerW} Вт · PF ${device.powerFactor.format(2)} · DR ${device.demandRatio.format(2)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReadOnlyParamChipsRow(
                        powerW = device.powerW,
                        pf = device.powerFactor,
                        dr = device.demandRatio,
                        voltage = device.voltageLabel
                    )
                    device.typeLabel?.let {
                        ReadOnlyChip("Тип: $it")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyParamChipsRow(
    powerW: Int,
    pf: Double,
    dr: Double,
    voltage: String
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReadOnlyChip("${powerW} Вт")
        ReadOnlyChip("PF ${pf.format(2)}")
        ReadOnlyChip("DR ${dr.format(2)}")
        ReadOnlyChip(voltage)
    }
}

@Composable
private fun ReadOnlyChip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Double.format(digits: Int) =
    "%.${digits}f".format(this).replace(',', '.')