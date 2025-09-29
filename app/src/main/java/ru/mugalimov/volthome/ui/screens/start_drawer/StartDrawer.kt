package ru.mugalimov.volthome.ui.screens.start_drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.model.ProjectUi
import ru.mugalimov.volthome.ui.model.UserProfileUi

/**
 * Левый Start Drawer + AppBar.
 * Секция проектов: без плейсхолдера «Проектов пока нет».
 */
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
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (openDrawer: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(min = 280.dp, max = 320.dp)
            ) {
                // --- Шапка аккаунта ---
                DrawerHeader(
                    profile = profile,
                    onLogout = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    }
                )

                // --- Мои проекты ---
                DrawerSectionTitle("Мои проекты")

                // без плейсхолдера — просто список, если пусто, ниже есть «+ Новый проект»
                projects.forEach { p ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = p.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (p.isActive) FontWeight.SemiBold else null
                            )
                        },
                        selected = p.isActive,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onSelectProject(p.id)
                        },
                        icon = { Icon(Icons.Default.Shield, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                NavigationDrawerItem(
                    label = { Text("+ Новый проект") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onCreateProject()
                    },
                    icon = { Icon(Icons.Default.Shield, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // --- Нижние разделы ---
                DrawerSectionTitle("Разделы")
                NavigationDrawerItem(
                    label = { Text("Настройки") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Профиль") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenProfile()
                    },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Подписка PRO") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSubscription()
                    },
                    icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("О приложении") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenAbout()
                    },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
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
                Box(
                    avatarModifier,
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null
                    )
                }
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