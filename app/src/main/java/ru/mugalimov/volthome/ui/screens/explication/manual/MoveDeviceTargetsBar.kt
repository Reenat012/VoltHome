package ru.mugalimov.volthome.ui.screens.explication.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft

@Composable
fun MoveDeviceTargetsBar(
    title: String,
    fromGroupId: Long,
    groups: List<ManualGroupDraft>,
    onDismiss: () -> Unit,
    onMoveToGroup: (targetGroupId: Long) -> Unit,
    onMoveToNewGroup: () -> Unit,
    onMoveToUnassigned: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                items = groups.sortedBy { it.groupNumber },
                key = { it.groupId }
            ) { g ->
                val disabled = g.groupId == fromGroupId
                AssistChip(
                    onClick = { if (!disabled) onMoveToGroup(g.groupId) },
                    enabled = !disabled,
                    label = { Text("Группа ${g.groupNumber}") },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            item {
                AssistChip(
                    onClick = onMoveToNewGroup,
                    label = { Text("В новую группу") }
                )
            }

            item {
                AssistChip(
                    onClick = onMoveToUnassigned,
                    label = { Text("В нераспределённые") }
                )
            }
        }
    }
}