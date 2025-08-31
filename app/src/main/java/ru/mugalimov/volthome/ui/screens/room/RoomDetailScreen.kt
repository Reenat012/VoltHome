 package ru.mugalimov.volthome.ui.screens.room

 import androidx.compose.foundation.layout.*
 import androidx.compose.foundation.shape.CircleShape
 import androidx.compose.foundation.shape.RoundedCornerShape
 import androidx.compose.material.icons.Icons
 import androidx.compose.material.icons.rounded.Add
 import androidx.compose.material.icons.rounded.ArrowBack
 import androidx.compose.material.icons.rounded.Bathtub
 import androidx.compose.material.icons.rounded.Home
 import androidx.compose.material.icons.rounded.Kitchen
 import androidx.compose.material.icons.rounded.Park
 import androidx.compose.material3.*
 import androidx.compose.runtime.*
 import androidx.compose.ui.Alignment
 import androidx.compose.ui.Modifier
 import androidx.compose.ui.draw.clip
 import androidx.compose.ui.text.style.TextOverflow
 import androidx.compose.ui.unit.dp
 import androidx.hilt.navigation.compose.hiltViewModel
 import ru.mugalimov.volthome.domain.model.DefaultDevice
 import ru.mugalimov.volthome.domain.model.Room
 import ru.mugalimov.volthome.domain.model.RoomType
 import ru.mugalimov.volthome.ui.sheets.AllDevicesSheet
 import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel

 @OptIn(ExperimentalMaterial3Api::class)
 @Composable
 fun RoomDetailScreen(
     onBack: () -> Unit,
     vm: RoomDetailViewModel = hiltViewModel()
 ) {
     val room: Room? by vm.room.collectAsState()
     val uiState by vm.uiState.collectAsState()
     val defaultDevices by vm.defaultDevices.collectAsState()

     var showAllDevices by remember { mutableStateOf(false) }

     Scaffold(
         topBar = {
             TopAppBar(
                 navigationIcon = {
                     IconButton(onClick = onBack) {
                         Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
                     }
                 },
                 title = {
                     Row(verticalAlignment = Alignment.CenterVertically) {
                         RoomTypeAvatar(
                             type = room?.roomType ?: RoomType.STANDARD,
                             modifier = Modifier
                                 .size(40.dp)
                                 .clip(CircleShape)
                         )
                         Spacer(Modifier.width(12.dp))
                         Column {
                             Text(
                                 text = room?.name ?: "Комната",
                                 style = MaterialTheme.typography.titleLarge,
                                 maxLines = 1,
                                 overflow = TextOverflow.Ellipsis
                             )
                             Text(
                                 text = "Тип комнаты: ${roomTypeLabel(room?.roomType ?: RoomType.STANDARD)}",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )
                         }
                     }
                 }
             )
         },
         floatingActionButton = {
             ExtendedFloatingActionButton(
                 onClick = { showAllDevices = true },
                 icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                 text = { Text("Добавить устройства") }
             )
         }
     ) { padding ->
         Column(
             modifier = Modifier
                 .padding(padding)
                 .fillMaxSize()
         ) {
             RoomSummary(
                 count = uiState.devices.size,
                 totalPowerW = uiState.devices.sumOf { it.power }
             )
             androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
             DeviceList(
                 devices = uiState.devices,
                 modifier = Modifier.fillMaxSize(),
                 onDelete = { id -> vm.deleteDevice(id)}
             )
         }
     }

     if (showAllDevices) {
         AllDevicesSheet(
             defaultDevices = defaultDevices,
             onConfirm = { selected: List<DefaultDevice> ->
                 val pairs: List<Pair<DefaultDevice, Int>> =
                     selected.groupingBy { it }.eachCount().map { (dev, qty) -> dev to qty }
                 vm.addDevicesToCurrentRoom(pairs)
                 showAllDevices = false
             },
             onDismiss = { showAllDevices = false }
         )
     }
 }

 /* ---------- Room summary: акцентные статистические бейджи ---------- */

 @Composable
 private fun RoomSummary(count: Int, totalPowerW: Int) {
     Surface(
         shape = RoundedCornerShape(16.dp),
         color = MaterialTheme.colorScheme.primaryContainer,
         modifier = Modifier
             .padding(horizontal = 16.dp, vertical = 8.dp)
             .fillMaxWidth()
     ) {
         Row(
             modifier = Modifier.padding(14.dp),
             horizontalArrangement = Arrangement.spacedBy(10.dp),
             verticalAlignment = Alignment.CenterVertically
         ) {
             StatBadge(text = "$count устройств(а)")
             StatBadge(text = "${(totalPowerW / 1000.0).format(2)} кВт")
         }
     }
 }

 @Composable
 private fun StatBadge(text: String) {
     Surface(
         shape = RoundedCornerShape(14.dp),
         color = MaterialTheme.colorScheme.surface,               // без обводки
         contentColor = MaterialTheme.colorScheme.onSurface
     ) {
         Text(
             text = text,
             style = MaterialTheme.typography.labelLarge,
             modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
         )
     }
 }

 /* ---------- UI helpers (без изменений) ---------- */

 @Composable
 private fun RoomTypeAvatar(type: RoomType, modifier: Modifier = Modifier) {
     val bg = when (type) {
         RoomType.STANDARD -> MaterialTheme.colorScheme.primaryContainer
         RoomType.BATHROOM -> MaterialTheme.colorScheme.tertiaryContainer
         RoomType.KITCHEN  -> MaterialTheme.colorScheme.secondaryContainer
         RoomType.OUTDOOR  -> MaterialTheme.colorScheme.surfaceVariant
     }
     val fg = when (type) {
         RoomType.STANDARD -> MaterialTheme.colorScheme.onPrimaryContainer
         RoomType.BATHROOM -> MaterialTheme.colorScheme.onTertiaryContainer
         RoomType.KITCHEN  -> MaterialTheme.colorScheme.onSecondaryContainer
         RoomType.OUTDOOR  -> MaterialTheme.colorScheme.onSurfaceVariant
     }
     val icon = when (type) {
         RoomType.STANDARD -> Icons.Rounded.Home
         RoomType.BATHROOM -> Icons.Rounded.Bathtub
         RoomType.KITCHEN  -> Icons.Rounded.Kitchen
         RoomType.OUTDOOR  -> Icons.Rounded.Park
     }
     Surface(color = bg, contentColor = fg, modifier = modifier) {
         Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
             Icon(icon, contentDescription = null)
         }
     }
 }

 private fun Double.format(d: Int) = "%.${d}f".format(this).replace(',', '.')
 private fun roomTypeLabel(type: RoomType): String = when (type) {
     RoomType.STANDARD -> "Стандартная"
     RoomType.BATHROOM -> "Ванная"
     RoomType.KITCHEN  -> "Кухня"
     RoomType.OUTDOOR  -> "Улица"
 }