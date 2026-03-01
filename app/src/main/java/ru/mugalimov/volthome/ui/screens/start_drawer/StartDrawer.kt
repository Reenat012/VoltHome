package ru.mugalimov.volthome.ui.screens.start_drawer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.model.ProjectUi
import ru.mugalimov.volthome.ui.model.UserProfileUi
import ru.mugalimov.volthome.ui.screens.debug.DebugProPanel
import ru.mugalimov.volthome.ui.utilities.TelegramConsultationDialog

// ✅ Состояние кнопки "Ручной режим" в AppBar.
// AUTO  — ручной режим выключен
// MANUAL — ручной режим включен, без несохранённых правок
// DIRTY — ручной режим включен, есть несохранённые правки (черновик отличается)
enum class ManualModeChipState {
    AUTO, MANUAL, DIRTY
}

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

    // ✅ клик по чипу (AUTO -> enter, MANUAL/DIRTY -> открыть диалог — решает MainApp)
    onManualModeClick: (() -> Unit)? = null,
    manualChipState: ManualModeChipState = ManualModeChipState.AUTO,

    // ✅ единый диалог Save/Cancel/Stay (контроль снаружи)
    manualExitDialogVisible: Boolean = false,
    onManualExitDialogDismiss: () -> Unit = {},
    onManualSaveClick: () -> Unit = {},
    onManualCancelClick: () -> Unit = {},

    bottomBar: @Composable () -> Unit = {},
    content: @Composable (openDrawer: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    var menuForProjectId by remember { mutableStateOf<String?>(null) }
    var renameDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteConfirmForId by remember { mutableStateOf<String?>(null) }
    var showConsultDialog by remember { mutableStateOf(false) }

    val t = VhColors.tokens

    val userPlan = LocalUserPlan.current
    val caps = userPlan.capabilities

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
                modifier = Modifier.widthIn(min = 280.dp, max = 320.dp),
                drawerContainerColor = t.bg,
                drawerContentColor = t.textPrimary
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
                                            color = t.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box {
                                            IconButton(
                                                onClick = { menuForProjectId = p.id },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.MoreVert,
                                                    contentDescription = "Меню проекта",
                                                    tint = t.textSecondary
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = menuForProjectId == p.id,
                                                onDismissRequest = { menuForProjectId = null },
                                                offset = DpOffset(x = (-8).dp, y = (-4).dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Переименовать", color = t.textPrimary) },
                                                    onClick = {
                                                        menuForProjectId = null
                                                        renameDialog = p.id to p.name
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Удалить", color = t.textPrimary) },
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
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = t.surfaceAlt,
                                    unselectedContainerColor = t.bg,
                                    selectedTextColor = t.textPrimary,
                                    unselectedTextColor = t.textPrimary,
                                    selectedIconColor = t.textSecondary,
                                    unselectedIconColor = t.textSecondary
                                ),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }

                        item {
                            // UI не "вратарь": onClick всегда идёт в VM → UseCase решает.
                            val count = projects.count { !it.isDeleted }

                            // Только отображение. Истина по лимиту — в домене/usecase.
                            val freeLimit = 3
                            val isUnlimited = caps.unlimitedProjects

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                            ) {
                                NavigationDrawerItem(
                                    label = { Text("+ Добавить проект", color = t.textPrimary) },
                                    selected = false,
                                    onClick = {
                                        scope.launch {
                                            drawerState.close()
                                            onCreateProject()
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = t.textSecondary) },
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = t.surfaceAlt,
                                        unselectedContainerColor = t.bg,
                                        selectedTextColor = t.textPrimary,
                                        unselectedTextColor = t.textPrimary,
                                        selectedIconColor = t.textSecondary,
                                        unselectedIconColor = t.textSecondary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Инфо-строка: реактивно меняется при смене плана
                                val info = if (isUnlimited) {
                                    "PRO: без лимита • Проекты: $count"
                                } else {
                                    val capped = minOf(count, freeLimit)
                                    "Free: $capped/$freeLimit • Проекты: $count"
                                }

                                Text(
                                    text = info,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.textSecondary,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                                )
                            }
                        }

                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = t.divider) }
                        item { DrawerSectionTitle("Разделы") }

                        item {
                            NavigationDrawerItem(
                                label = { Text("Профиль") },
                                selected = false,
                                onClick = { scope.launch { drawerState.close(); onOpenProfile() } },
                                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = t.surfaceAlt,
                                    unselectedContainerColor = t.bg,
                                    selectedTextColor = t.textPrimary,
                                    unselectedTextColor = t.textPrimary,
                                    selectedIconColor = t.textSecondary,
                                    unselectedIconColor = t.textSecondary
                                ),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }

                        item {
                            NavigationDrawerItem(
                                label = { Text("Информация") },
                                selected = false,
                                onClick = { scope.launch { drawerState.close(); onOpenSettings() } },
                                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = t.surfaceAlt,
                                    unselectedContainerColor = t.bg,
                                    selectedTextColor = t.textPrimary,
                                    unselectedTextColor = t.textPrimary,
                                    selectedIconColor = t.textSecondary,
                                    unselectedIconColor = t.textSecondary
                                ),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }

                        // --- Соцсети ---
                        item {
                            Divider(modifier = Modifier.padding(vertical = 8.dp), color = t.divider)
                            DrawerSectionTitle("Соцсети")

                            NavigationDrawerItem(
                                label = {
                                    Column {
                                        Text("Telegram", color = t.textPrimary)
                                        Text(
                                            text = "Инженерные компромиссы",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = t.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                selected = false,
                                onClick = { openUrl("https://t.me/volthomeapp") },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.telegram),
                                        contentDescription = "Telegram",
                                        tint = t.textSecondary
                                    )
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = t.surfaceAlt,
                                    unselectedContainerColor = t.bg,
                                    selectedTextColor = t.textPrimary,
                                    unselectedTextColor = t.textPrimary,
                                    selectedIconColor = t.textSecondary,
                                    unselectedIconColor = t.textSecondary
                                ),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }

                    if (BuildConfig.DEBUG) {
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = t.divider
                        )

                        DebugProPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // ✅ Название проекта занимает всё свободное место слева
                            Text(
                                text = title,
                                color = t.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // ✅ Чип "Ручной режим" справа, прижат к правому краю AppBar
                            if (onManualModeClick != null) {
                                Spacer(Modifier.width(8.dp))
                                ManualModeChip(
                                    state = manualChipState,
                                    onClick = { onManualModeClick.invoke() }
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Открыть меню",
                                tint = t.textSecondary
                            )
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

    // -----------------------------
    // ✅ Единый диалог Save/Cancel/Stay для ручного режима (AppBar уровень)
    // -----------------------------
    if (manualExitDialogVisible) {
        AlertDialog(
            onDismissRequest = onManualExitDialogDismiss,
            title = { Text("Ручной режим", color = t.textPrimary) },
            text = {
                Text(
                    text = when (manualChipState) {
                        ManualModeChipState.DIRTY ->
                            "Есть несохранённые изменения. Сохранить изменения или отменить и выйти из ручного режима?"
                        ManualModeChipState.MANUAL ->
                            "Завершить ручной режим? Можно сохранить текущее распределение или выйти без сохранения."
                        ManualModeChipState.AUTO ->
                            "Ручной режим не активен."
                    },
                    color = t.textSecondary
                )
            },
            confirmButton = {
                // ✅ Save
                TextButton(onClick = onManualSaveClick) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    // ✅ Cancel
                    TextButton(onClick = onManualCancelClick) { Text("Отменить") }
                    // ✅ Stay
                    TextButton(onClick = onManualExitDialogDismiss) { Text("Остаться") }
                }
            }
        )
    }

    val renameData = renameDialog
    if (renameData != null) {
        var text by remember(renameData.first) { mutableStateOf(renameData.second) }
        AlertDialog(
            onDismissRequest = { renameDialog = null },
            title = { Text("Переименовать проект", color = t.textPrimary) },
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
                    onClick = { onRenameProject(renameData.first, text.trim()); renameDialog = null }
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { renameDialog = null }) { Text("Отмена") } }
        )
    }

    val toDelete = deleteConfirmForId
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmForId = null },
            title = { Text("Удалить проект?", color = t.textPrimary) },
            text = { Text("Проект и связанные данные будут удалены. Это действие нельзя отменить.", color = t.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProject(toDelete)
                    deleteConfirmForId = null
                    scope.launch { drawerState.close() }
                }) { Text("Удалить", color = t.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmForId = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun DrawerHeader(
    profile: UserProfileUi?,
    onLogout: () -> Unit
) {
    val t = VhColors.tokens

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
                    color = t.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val mail = profile?.email ?: ""
                if (mail.isNotBlank()) {
                    Text(
                        text = mail,
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onLogout) {
                Icon(
                    Icons.Default.ExitToApp,
                    contentDescription = "Выйти",
                    tint = t.textSecondary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Divider(color = t.divider)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DrawerSectionTitle(text: String) {
    val t = VhColors.tokens
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = t.primary
    )
}

@Composable
private fun ManualModeChip(
    state: ManualModeChipState,
    onClick: () -> Unit
) {
    val t = VhColors.tokens

    // ✅ Текст и “грязность” черновика
    val label = when (state) {
        ManualModeChipState.AUTO -> "Авто"
        ManualModeChipState.MANUAL -> "Ручной"
        ManualModeChipState.DIRTY -> "Ручной"
    }

    val showDirtyDot = (state == ManualModeChipState.DIRTY)

    // ✅ Простая визуальная логика:
    // - AUTO: нейтрально
    // - MANUAL: подсвечено primary
    // - DIRTY: как MANUAL + точка
    val container = when (state) {
        ManualModeChipState.AUTO -> t.surfaceAlt
        ManualModeChipState.MANUAL -> t.primary.copy(alpha = 0.14f)
        ManualModeChipState.DIRTY -> t.primary.copy(alpha = 0.14f)
    }

    val content = when (state) {
        ManualModeChipState.AUTO -> t.textPrimary
        ManualModeChipState.MANUAL -> t.primary
        ManualModeChipState.DIRTY -> t.primary
    }

    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(container)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        color = content,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (showDirtyDot) {
                        Spacer(Modifier.width(6.dp))
                        // ✅ “DIRTY” индикатор: маленькая точка
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(t.error)
                        )
                    }
                }
            }
        }
    }
}