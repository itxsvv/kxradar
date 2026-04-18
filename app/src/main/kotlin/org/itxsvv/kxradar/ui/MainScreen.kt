package org.itxsvv.kxradar.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.launch
import org.itxsvv.kxradar.Beep
import org.itxsvv.kxradar.KarooRadarExtension.Companion.TAG
import org.itxsvv.kxradar.RadarSettings
import org.itxsvv.kxradar.lightcontrol.LightMode
import org.itxsvv.kxradar.saveSettings
import org.itxsvv.kxradar.streamSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val pattern = remember { Regex("^\\d*\\d*\$") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val karooSystem = remember { KarooSystemService(ctx) }
    var savedDialogVisible by remember { mutableStateOf(false) }
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Sounds", "Light", "Settings")

    var uiThreatBeep by remember { mutableStateOf(Beep(200, 100)) }
    var uiPassedBeep by remember { mutableStateOf(Beep(0, 0)) }
    var uiInRideOnlyEnabled by remember { mutableStateOf(false) }
    var uiBeepEnabled by remember { mutableStateOf(true) }
    var uiWakeUpScreen by remember { mutableStateOf(true) }
    var uiRedThreadAlert by remember { mutableStateOf(false) }
    var uiLightControlEnabled by remember { mutableStateOf(false) }
    var uiLightControlMode by remember { mutableStateOf(LightMode.STEADY_HIGH) }
    var lightModeDropdownExpanded by remember { mutableStateOf(false) }

    fun saveUISettings() {
        scope.launch {
            val radarSettings = RadarSettings(
                threatBeep = uiThreatBeep,
                passedBeep = uiPassedBeep,
                inRideOnly = uiInRideOnlyEnabled,
                enabled = uiBeepEnabled,
                wakeUpScreen = uiWakeUpScreen,
                redThreadAlert = uiRedThreadAlert,
                lightControlEnabled = uiLightControlEnabled,
                lightControlMode = uiLightControlMode,
            )
            Log.i(TAG, "" + radarSettings)
            saveSettings(ctx, radarSettings)
        }
    }

    @Composable
    fun drawSettingsScreen() {
        Row(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
        ) {}
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                modifier = Modifier
                    .weight(0.5f)
                    .padding(5.dp),
                checked = uiInRideOnlyEnabled,
                onCheckedChange = {
                    uiInRideOnlyEnabled = it
                    scope.launch {
                        saveUISettings()
                    }
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(modifier = Modifier.weight(1f), text = "In-ride only")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                modifier = Modifier
                    .weight(0.5f)
                    .padding(5.dp),
                checked = uiWakeUpScreen,
                onCheckedChange = {
                    uiWakeUpScreen = it
                    scope.launch {
                        saveUISettings()
                    }
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(modifier = Modifier.weight(1f), text = "Wake Up Screen")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                modifier = Modifier
                    .weight(0.5f)
                    .padding(5.dp),
                checked = uiRedThreadAlert,
                onCheckedChange = {
                    uiRedThreadAlert = it
                    scope.launch {
                        saveUISettings()
                    }
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(modifier = Modifier.weight(1f), text = "Two beep on first red")
        }

        HorizontalDivider(
            thickness = 2.dp, modifier = Modifier
                .padding(vertical = 10.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                modifier = Modifier.weight(0.5f),
                checked = uiBeepEnabled,
                onCheckedChange = {
                    uiBeepEnabled = it
                    scope.launch {
                        saveUISettings()
                    }
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(modifier = Modifier.weight(1f), text = "Enabled")
        }
    }

    @Composable
    fun drawLightScreen() {
        Row(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
        ) {}
        Text("Ensure that the light is")
        Text("turned off in the Karoo settings")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                modifier = Modifier
                    .weight(0.5f)
                    .padding(5.dp),
                checked = uiLightControlEnabled,
                onCheckedChange = {
                    uiLightControlEnabled = it
                    scope.launch {
                        saveUISettings()
                    }
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(modifier = Modifier.weight(1f), text = "Enabled")
        }

        ExposedDropdownMenuBox(
            expanded = lightModeDropdownExpanded,
            onExpandedChange = { lightModeDropdownExpanded = !lightModeDropdownExpanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
        ) {
            OutlinedTextField(
                value = uiLightControlMode.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Alert Mode") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = lightModeDropdownExpanded)
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = lightModeDropdownExpanded,
                onDismissRequest = { lightModeDropdownExpanded = false },
            ) {
                LightMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.displayName) },
                        onClick = {
                            uiLightControlMode = mode
                            lightModeDropdownExpanded = false
                            scope.launch {
                                saveUISettings()
                            }
                        },
                    )
                }
            }
        }
    }

    @Composable
    fun drawSoundScreen() {
        Row(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
        ) {}
        Text("Threat sound")
        DrawBeepPanel(
            karooSystem, scope, uiThreatBeep, pattern,
            onDurationChange = { newDur ->
                uiThreatBeep = uiThreatBeep.copy(duration = newDur)
            },
            onFreqChange = { newFreq ->
                uiThreatBeep = uiThreatBeep.copy(frequency = newFreq)
            })
        Text("All clear sound (0 disable)")
        DrawBeepPanel(
            karooSystem, scope, uiPassedBeep, pattern,
            onDurationChange = { newDur ->
                uiPassedBeep = uiPassedBeep.copy(duration = newDur)
            },
            onFreqChange = { newFreq ->
                uiPassedBeep = uiPassedBeep.copy(frequency = newFreq)
            })
        Spacer(modifier = Modifier.size(10.dp))
        FilledTonalButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp), onClick = {
                scope.launch {
                    saveUISettings()
                    savedDialogVisible = true
                }
            }) {
            Icon(Icons.Default.Done, contentDescription = "")
            Spacer(modifier = Modifier.width(5.dp))
            Text("Save")
        }
        if (savedDialogVisible) {
            AlertDialog(
                onDismissRequest = { savedDialogVisible = false },
                confirmButton = {
                    Button(onClick = {
                        savedDialogVisible = false
                    }) { Text("OK") }
                },
                text = { Text("Settings saved successfully.") }
            )
        }
    }

    LaunchedEffect(Unit) {
        ctx.streamSettings().collect { settings ->
            uiThreatBeep = settings.threatBeep
            uiPassedBeep = settings.passedBeep
            uiInRideOnlyEnabled = settings.inRideOnly
            uiBeepEnabled = settings.enabled
            uiWakeUpScreen = settings.wakeUpScreen
            uiRedThreadAlert = settings.redThreadAlert
            uiLightControlEnabled = settings.lightControlEnabled
            uiLightControlMode = settings.lightControlMode
        }
    }

    LaunchedEffect(Unit) {
        karooSystem.connect()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .padding(2.dp)
            .background(MaterialTheme.colorScheme.background)
            .clickable { focusManager.clearFocus() },
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                    text = { Text(title, maxLines = 1, softWrap = false) },
                    selected = tabIndex == index,
                    onClick = { tabIndex = index }
                )
            }
        }
        when (tabIndex) {
            0 -> {
                drawSoundScreen()
            }

            1 -> {
                drawLightScreen()
            }

            2 -> {
                drawSettingsScreen()
            }
        }
    }
}
