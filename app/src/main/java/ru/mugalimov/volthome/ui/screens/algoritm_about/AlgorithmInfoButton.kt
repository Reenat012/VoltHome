package ru.mugalimov.volthome.ui.screens.algoritm_about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlgorithmInfoButton(
    onDismiss: () -> Unit
) {
    var open by remember { mutableStateOf(false) }

    FilledTonalButton(onClick = { open = true }) {
        Icon(Icons.Default.Info, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Как считает VoltHome")
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Алгоритм группировки и балансировки",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )

                Bullet("Формирует группы: HEAVY_DUTY, освещение, розетки.")
                Bullet("Подбирает автоматы и кабель с учётом ПУЭ, demandRatio и cos φ.")
                Bullet("Распределяет группы по фазам A/B/C для равномерной нагрузки.")
                Bullet("Контролирует дисбаланс фаз и перераскладывает крупные линии.")

                // Мини-легенда фаз
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhaseDot("Фаза A", Color(0xFFE74C3C))
                    PhaseDot("Фаза B", Color(0xFFF1C40F))
                    PhaseDot("Фаза C", Color(0xFF2ECC71))
                }

                // Короткие «чипы» с параметрами
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChip("cos φ")
                    SmallChip("demandRatio")
                    SmallChip("УЗО 30 мА")
                }

                Spacer(Modifier.height(6.dp))
                FilledTonalButton(
                    onClick = { open = false },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Понятно") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("• ", fontSize = 18.sp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PhaseDot(text: String, color: Color) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, shape = MaterialTheme.shapes.small)
            )
        },
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun SmallChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text, fontSize = 12.sp) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}