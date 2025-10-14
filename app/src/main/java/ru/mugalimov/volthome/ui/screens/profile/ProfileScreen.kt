package ru.mugalimov.volthome.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authVm: AuthViewModel,
    onBack: () -> Unit = {}
) {
    val profileVm: ProfileViewModel = hiltViewModel()
    val profileState by profileVm.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { profileVm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { inner ->
        when (val s = profileState) {
            is ProfileViewModel.UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            is ProfileViewModel.UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(20.dp),
                ) { Text(s.message, color = MaterialTheme.colorScheme.error) }
            }

            is ProfileViewModel.UiState.Data -> {
                val me = s.me
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Карточка профиля
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val avatarModifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)

                                if (me.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = "file:///android_asset/report_pdf/img/logo.png",
                                        contentDescription = "VoltHome",
                                        contentScale = ContentScale.Fit,
                                        modifier = avatarModifier
                                    )
                                } else {
                                    AsyncImage(
                                        model = me.avatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = avatarModifier
                                    )
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = me.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = me.email ?: "—",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (me.plan.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        PlanChip(
                                            plan = me.plan,
                                            untilEpochSeconds = me.planUntilEpochSeconds
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Кнопка выхода
                    item {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { scope.launch { authVm.signOut() } }) {
                                Icon(Icons.Default.ExitToApp, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Выйти")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanChip(
    plan: String,
    untilEpochSeconds: Long?
) {
    val label = buildString {
        append(
            when (plan.lowercase()) {
                "free" -> "Free"
                "pro" -> "PRO"
                else -> plan
            }
        )
        val untilStr = untilEpochSeconds
            ?.takeIf { it > 0 }
            ?.let {
                java.time.Instant.ofEpochSecond(it)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .toString()
            }
        if (!untilStr.isNullOrBlank()) append(" • до $untilStr")
    }

    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}