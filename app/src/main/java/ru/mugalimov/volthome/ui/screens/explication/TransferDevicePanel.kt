package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.model.manual.ManualGroupComposition

/**
 * Панель переноса устройства (выбор цели).
 * Это UI-слой: только рендер и колбэки.
 */
@Composable
fun TransferDevicePanel(
    groups: List<ManualGroupDraft>,
    onMoveToGroup: (targetGroupId: Long) -> Unit,
    onMoveToNewGroup: () -> Unit,
    onMoveToUnassigned: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Переместить устройство",
            style = MaterialTheme.typography.titleMedium
        )

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = "В существующую группу",
            style = MaterialTheme.typography.labelLarge
        )

        groups
            .sortedBy { it.groupNumber }
            .forEach { g ->
                val mixedBadge = if (g.composition == ManualGroupComposition.MIXED_MANUAL) " • MIXED" else ""
                Text(
                    text = "Группа #${g.groupNumber}${mixedBadge}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMoveToGroup(g.groupId) }
                        .padding(vertical = 10.dp)
                )
            }

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = "Создать новую группу",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMoveToNewGroup() }
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = "Переместить в «Нераспределённые»",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMoveToUnassigned() }
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}