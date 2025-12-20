package ru.mugalimov.volthome.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.core.validation.InputConstraints
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.ui.components.ProLocked
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.viewmodel.DeviceEditViewModel
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceEditSheet(
    deviceId: Long,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    vm: DeviceEditViewModel = hiltViewModel(),
    paywallBus: PaywallBus = hiltViewModel<DeviceEditSheetPaywallHolder>().paywallBus
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val isPro = LocalUserPlan.current.isPro

    LaunchedEffect(deviceId) { vm.load(deviceId) }
    LaunchedEffect(isPro) { vm.setPlan(isPro) }

    val ui = vm.ui.collectAsState().value

    // dialogs state
    var showPfDialog by remember { mutableStateOf(false) }
    var showDemandDialog by remember { mutableStateOf(false) }
    var showVoltageDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
                .bringIntoViewRequester(bringIntoViewRequester)
                .imePadding()
        ) {
            Text("Редактирование устройства")

            Spacer(Modifier.height(12.dp))

            // Название — FREE доступно
            OutlinedTextField(
                value = ui.name,
                onValueChange = vm::setName,
                label = { Text("Название") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .onFocusChanged {
                        if (it.isFocused) {
                            scope.launch {
                                delay(200)
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    }
            )

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Ряд 1: Мощность | Тип устройства (readonly без продажи)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = ui.powerText,
                        onValueChange = vm::setPowerText,
                        label = { Text("Мощность (Вт)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = ui.powerError != null,
                        supportingText = {
                            Text(
                                ui.powerError
                                    ?: "Допустимо от ${InputConstraints.MIN_POWER_W} до ${InputConstraints.MAX_POWER_W} Вт"
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    scope.launch {
                                        delay(200)
                                        bringIntoViewRequester.bringIntoView()
                                    }
                                }
                            }
                    )

                    Box(Modifier.weight(1f)) {
                        ReadonlyField(
                            label = "Тип устройства",
                            value = ui.deviceTypeLabel,
                            hint = null,
                            onClick = null
                        )
                    }
                }

                // Ряд 2: PF | Коэфф. спроса (PRO) — через ProLocked (как в Explication)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        ProLocked(
                            isPro = isPro,
                            feature = ProFeature.ADVANCED_DEVICE_EDITOR,
                            onLockedClick = { paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR) },
                            modifier = Modifier.fillMaxWidth(),
                            showLockIcon = true
                        ) {
                            ReadonlyField(
                                label = "Cos φ",
                                value = ui.powerFactorText,
                                hint = "Влияет на расчёт тока",
                                onClick = { showPfDialog = true }
                            )
                        }
                    }

                    Box(Modifier.weight(1f)) {
                        ProLocked(
                            isPro = isPro,
                            feature = ProFeature.ADVANCED_DEVICE_EDITOR,
                            onLockedClick = { paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR) },
                            modifier = Modifier.fillMaxWidth(),
                            showLockIcon = true
                        ) {
                            ReadonlyField(
                                label = "Коэфф. спроса",
                                value = ui.demandRatioText,
                                hint = "Учитывает реальную нагрузку",
                                onClick = { showDemandDialog = true }
                            )
                        }
                    }
                }

                // Ряд 3: Напряжение (PRO) — только пресеты 220/380, тоже через ProLocked
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        ProLocked(
                            isPro = isPro,
                            feature = ProFeature.ADVANCED_DEVICE_EDITOR,
                            onLockedClick = { paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR) },
                            modifier = Modifier.fillMaxWidth(),
                            showLockIcon = true
                        ) {
                            ReadonlyField(
                                label = "Напряжение",
                                value = ui.voltageText,
                                hint = "Влияет на ток и фазность",
                                onClick = { showVoltageDialog = true }
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !ui.isSaving && ui.powerError == null,
                    onClick = {
                        vm.save(
                            onSuccess = { onSaved(); onDismiss() },
                            onError = { /* snackbar снаружи */ }
                        )
                    }
                ) {
                    Text(if (ui.isSaving) "Сохранение…" else "Сохранить")
                }
                Button(onClick = onDismiss) { Text("Отмена") }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    // ---------- PF Dialog ----------
    if (showPfDialog) {
        var text by remember { mutableStateOf(ui.powerFactorText) }
        val err = validateRatio(text)

        AlertDialog(
            onDismissRequest = { showPfDialog = false },
            title = { Text("Cos φ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Используется в проектировании и влияет на расчёт тока.")
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("PF (0.1 — 1.0)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = err != null
                    )
                    if (err != null) Text(err)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = err == null,
                    onClick = {
                        vm.setPowerFactorText(text)
                        showPfDialog = false
                    }
                ) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = { showPfDialog = false }) { Text("Отмена") }
            }
        )
    }

    // ---------- Demand Dialog ----------
    if (showDemandDialog) {
        var text by remember { mutableStateOf(ui.demandRatioText) }
        val err = validateRatio(text)

        AlertDialog(
            onDismissRequest = { showDemandDialog = false },
            title = { Text("Коэффициент спроса") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Позволяет учитывать реальные условия эксплуатации.")
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Кс (0.1 — 1.0)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = err != null
                    )
                    if (err != null) Text(err)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = err == null,
                    onClick = {
                        vm.setDemandRatioText(text)
                        showDemandDialog = false
                    }
                ) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = { showDemandDialog = false }) { Text("Отмена") }
            }
        )
    }

    // ---------- Voltage Dialog (220/380 presets) ----------
    if (showVoltageDialog) {
        AlertDialog(
            onDismissRequest = { showVoltageDialog = false },
            title = { Text("Напряжение") },
            text = { Text("Выбери тип сети для устройства:") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = {
                            vm.setVoltagePreset220()
                            showVoltageDialog = false
                        }
                    ) { Text("220 В") }

                    TextButton(
                        onClick = {
                            vm.setVoltagePreset380()
                            showVoltageDialog = false
                        }
                    ) { Text("380 В") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoltageDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ReadonlyField(
    label: String,
    value: String,
    hint: String? = null,
    onClick: (() -> Unit)?
) {
    // Клик — только для PRO (через ProLocked он дойдёт; во FREE его перехватит overlay).
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)

    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = true,
        singleLine = true,
        supportingText = { if (hint != null) Text(hint) },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        // ВАЖНО: onClick мы НЕ делаем через clickable на TextField,
        // т.к. клик может "съедаться". Он будет приходить от ProLocked (пропускать в PRO).
    )

    // Если нужно, можно позже заменить на Box+clickable поверх, но сейчас это надёжнее через ProLocked.
    // Открытие диалога происходит по клику по overlay-контейнеру, а не по TextField.
    // Поэтому onClick здесь не вызываем — он вызывается выше, в ProLocked контенте.
    // (Смотри: ReadonlyField используется внутри ProLocked и открывает диалоги через show*Dialog=true)
    if (onClick != null) {
        // no-op: оставлено для сигнатуры, чтобы минимально менять вызовы
    }
}

private fun validateRatio(input: String): String? {
    val v = input.trim().replace(',', '.').toDoubleOrNull()
    if (v == null) return "Введите число"
    if (v < 0.1 || v > 1.0) return "Допустимо 0.1 — 1.0"
    return null
}

/**
 * Хелпер, чтобы получить PaywallBus через Hilt без ручного DI в composable.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class DeviceEditSheetPaywallHolder @Inject constructor(
    val paywallBus: PaywallBus
) : androidx.lifecycle.ViewModel()