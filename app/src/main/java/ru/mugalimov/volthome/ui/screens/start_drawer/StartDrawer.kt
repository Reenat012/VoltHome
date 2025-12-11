package ru.mugalimov.volthome.ui.screens.start_drawer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.ui.model.ProjectUi
import ru.mugalimov.volthome.ui.model.UserProfileUi
import ru.mugalimov.volthome.ui.utilities.TelegramConsultationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartDrawer(
    title: String,
    drawerState: DrawerState,
    profile: UserProfileUi?,
    projects: List<ProjectUi>,
    onLogout: () -> Unit,
    onSelectProject: (String) -> Unit,
    onCreateProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenAbout: () -> Unit,
    onRenameProject: (id: String, newName: String) -> Unit = { _, _ -> },
    onDeleteProject: (id: String) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (openDrawer: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    var menuForProjectId by remember { mutableStateOf<String?>(null) }
    var renameDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteConfirmForId by remember { mutableStateOf<String?>(null) }
    var showConsultDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(min = 280.dp, max = 320.dp)
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    DrawerHeader(
                        profile = profile,
                        onLogout = {
                            scope.launch {
                                drawerState.close()
                                onLogout()
                            }
                        }
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        item { DrawerSectionTitle("Мои проекты") }

                        items(projects, key = { it.id }) { p ->
                            NavigationDrawerItem(
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = p.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (p.isActive) FontWeight.SemiBold else null,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box {
                                            IconButton(
                                                onClick = { menuForProjectId = p.id },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Меню проекта")
                                            }
                                            DropdownMenu(
                                                expanded = menuForProjectId == p.id,
                                                onDismissRequest = { menuForProjectId = null },
                                                offset = DpOffset(x = (-8).dp, y = (-4).dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Переименовать") },
                                                    onClick = {
                                                        menuForProjectId = null
                                                        renameDialog = p.id to p.name
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Удалить") },
                                                    onClick = {
                                                        menuForProjectId = null
                                                        deleteConfirmForId = p.id
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                selected = p.isActive,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        onSelectProject(p.id)
                                    }
                                },
                                icon = {},
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }

                        item {
                            val disabled = projects.size >= 3
                            NavigationDrawerItem(
                                label = { Text(if (!disabled) "+ Добавить проект" else "Лимит: 3 проекта") },
                                selected = false,
                                onClick = {
                                    if (!disabled) {
                                        scope.launch {
                                            drawerState.close(); onCreateProject()
                                        }
                                    }
                                },
                                icon = { Icon(Icons.Default.Shield, contentDescription = null) },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedTextColor = if (disabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onSurface,
                                    unselectedTextColor = if (disabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onSurface,
                                    selectedIconColor = if (disabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedIconColor = if (disabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }

                        item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }
                        item { DrawerSectionTitle("Разделы") }

                        item {
                            NavigationDrawerItem(
                                label = { Text("Профиль") },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close(); onOpenProfile() }
                                },
                                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }

                        item {
                            NavigationDrawerItem(
                                label = { Text("VoltHome PRO") },
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        onOpenSubscription()
                                    }
                                },
                                icon = { Icon(Icons.Default.Payment, contentDescription = null) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }


                        item {
                            NavigationDrawerItem(
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Консультация")
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Бета",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                    }
                                },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    showConsultDialog = true
                                },
                                icon = { Icon(Icons.Outlined.SupportAgent, contentDescription = null) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }

                        item {
                            NavigationDrawerItem(
                                label = { Text("Информация") },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close(); onOpenSettings() }
                                },
                                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }

// --- Соцсети ---
                        item {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            DrawerSectionTitle("Соцсети")

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(onClick = { openUrl("https://t.me/volthomeapp") }) {
                                    Icon(
                                        painter = painterResource(R.drawable.telegram),
                                        contentDescription = "Telegram"
                                    )
                                }
                                IconButton(onClick = { openUrl("https://www.youtube.com/@volthomeapp") }) {
                                    Icon(
                                        painter = painterResource(R.drawable.youtube),
                                        contentDescription = "YouTube"
                                    )
                                }
                                IconButton(onClick = { openUrl("https://www.instagram.com/volthomeapp?igsh=bWd2aWNwaHY3eGtm") }) {
                                    Icon(
                                        painter = painterResource(R.drawable.instagram_svgrepo_com),
                                        contentDescription = "Instagram"
                                    )
                                }
                                IconButton(onClick = { openUrl("https://www.tiktok.com/@volthome6?_r=1&_t=ZS-91CmJqED9sa") }) {
                                    Icon(
                                        painter = painterResource(R.drawable.tiktok),
                                        contentDescription = "TikTok"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Открыть меню")
                        }
                    }
                )
            },
            bottomBar = bottomBar
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                content { scope.launch { drawerState.open() } }
            }
        }
    }

    if (showConsultDialog) {
        TelegramConsultationDialog(
            botName = "VoltHomeBot",
            startPayloadBase64 = null,
            onDismiss = { showConsultDialog = false }
        )
    }

    val renameData = renameDialog
    if (renameData != null) {
        var text by remember(renameData.first) { mutableStateOf(renameData.second) }
        AlertDialog(
            onDismissRequest = { renameDialog = null },
            title = { Text("Переименовать проект") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Название") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        onRenameProject(renameData.first, text.trim()); renameDialog = null
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { renameDialog = null }) { Text("Отмена") } }
        )
    }

    val toDelete = deleteConfirmForId
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmForId = null },
            title = { Text("Удалить проект?") },
            text = { Text("Проект и связанные данные будут удалены. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProject(toDelete); deleteConfirmForId = null; scope.launch { drawerState.close() }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmForId = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun DrawerHeader(
    profile: UserProfileUi?,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val avatarModifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium)

            if (profile?.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = "file:///android_asset/report_pdf/img/logo.png",
                    contentDescription = "VoltHome",
                    contentScale = ContentScale.Fit,
                    modifier = avatarModifier
                )
            } else {
                Image(
                    painter = rememberAsyncImagePainter(profile!!.avatarUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = avatarModifier
                )
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile?.name ?: "Пользователь",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val mail = profile?.email ?: ""
                if (mail.isNotBlank()) {
                    Text(
                        text = mail,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Выйти")
            }
        }
        Spacer(Modifier.height(8.dp))
        Divider()
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DrawerSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}