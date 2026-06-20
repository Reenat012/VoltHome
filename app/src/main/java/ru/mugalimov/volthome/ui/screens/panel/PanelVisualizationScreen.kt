package ru.mugalimov.volthome.ui.screens.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mugalimov.volthome.domain.model.panel.ModuleType
import ru.mugalimov.volthome.domain.model.panel.PanelGroup
import ru.mugalimov.volthome.domain.model.panel.PanelModule
import ru.mugalimov.volthome.domain.model.panel.PanelVisualization
import ru.mugalimov.volthome.ui.viewmodel.PanelVisualizationUiState
import ru.mugalimov.volthome.ui.viewmodel.PanelVisualizationViewModel

/**
 * Экран MVP-визуализации щита.
 *
 * Важно:
 * экран только отображает уже собранную модель визуализации.
 * Ручного редактирования, экспорта и проектной схемы здесь нет.
 */
@Composable
fun PanelVisualizationScreen(
    viewModel: PanelVisualizationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Визуализация щита",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        )

        when (val state = uiState) {
            PanelVisualizationUiState.Loading -> PanelVisualizationLoading()
            PanelVisualizationUiState.Empty -> PanelVisualizationEmpty()
            is PanelVisualizationUiState.Content -> {
                PanelVisualizationContent(
                    panel = state.panel
                )
            }
        }
    }
}

/**
 * Основной контент визуализации.
 *
 * Структура намеренно вертикальная:
 * вводной аппарат -> защитный аппарат -> группа.
 */
@Composable
fun PanelVisualizationContent(
    panel: PanelVisualization,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        panel.incomer?.let { incomer ->
            item {
                PanelDinRailContainer {
                    PanelModuleView(
                        module = incomer
                    )
                }
            }

            if (panel.modules.isNotEmpty()) {
                item {
                    PanelArrow()
                }
            }
        }

        itemsIndexed(panel.modules) { index, module ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PanelDinRailContainer {
                    PanelModuleView(
                        module = module
                    )
                }

                module.group?.let { group ->
                    PanelArrow()

                    PanelGroupView(
                        group = group
                    )
                }

                if (index < panel.modules.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

/**
 * Отображает один аппарат щита:
 * вводной автомат, автомат, УЗО или дифавтомат.
 */
@Composable
fun PanelModuleView(
    module: PanelModule,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.widthIn(min = 180.dp, max = 260.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = module.label.ifBlank { module.type.toReadableTitle() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = module.buildParamsText(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Отображает группу, которая находится после защитного аппарата.
 *
 * В MVP показываем только название группы и сечение кабеля.
 */
@Composable
fun PanelGroupView(
    group: PanelGroup,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(min = 180.dp, max = 320.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center
            )

            group.cableSection?.let { cableSection ->
                Text(
                    text = "${cableSection.formatCableSection()} мм²",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * UI-имитация DIN-рейки.
 *
 * Важно:
 * DIN-рейка существует только здесь, в UI.
 * В domain-моделях её быть не должно.
 */
@Composable
private fun PanelDinRailContainer(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Простая вертикальная связка элементов.
 */
@Composable
private fun PanelArrow() {
    Text(
        text = "↓",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
}

/**
 * Loading-состояние экрана.
 */
@Composable
private fun PanelVisualizationLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Empty-состояние по ТЗ.
 */
@Composable
private fun PanelVisualizationEmpty() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Сначала добавьте комнаты и устройства, чтобы ВольтХом смог построить визуализацию щита.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Человекочитаемое название типа аппарата.
 */
private fun ModuleType.toReadableTitle(): String {
    return when (this) {
        ModuleType.INCOMER -> "Вводной автомат"
        ModuleType.BREAKER -> "Автомат"
        ModuleType.RCD -> "УЗО"
        ModuleType.RCBO -> "Дифавтомат"
    }
}

/**
 * Формирует строку параметров аппарата для отображения.
 */
private fun PanelModule.buildParamsText(): String {
    val parts = buildList {
        nominalCurrent?.let { current ->
            add("${current}A")
        }

        breakerCurve
            ?.takeIf { it.isNotBlank() }
            ?.let { curve ->
                add(curve)
            }

        leakageCurrent?.let { leakage ->
            add("${leakage}mA")
        }
    }

    return parts.joinToString(separator = " ")
}

/**
 * Форматирует сечение кабеля без лишнего ".0".
 */
private fun Double.formatCableSection(): String {
    return if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        toString()
    }
}