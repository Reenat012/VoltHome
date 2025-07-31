package ru.mugalimov.volthome.ui.screens.explication

import GroupCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.CircuitGroup

@Composable
fun GroupList(groups: List<CircuitGroup>, onRecalculate: () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Заголовок экрана
        Text(
            text = "Распределение по группам",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

//        // Кнопка перерасчета
//        Button(
//            onClick = onRecalculate,
//            modifier = Modifier.align(Alignment.End)
//        ) {
//            Text("Пересчитать группы")
//        }

        Spacer(modifier = Modifier.height(16.dp))

        // Сводная информация
        GroupSummary(groups)

        Spacer(modifier = Modifier.height(24.dp))

        // Список групп
        groups.forEach { group ->
            GroupCard(group = group)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}





