package org.itxsvv.kxradar.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.itxsvv.kxradar.Beep
import org.itxsvv.kxradar.RadarSettings
import org.itxsvv.kxradar.beep
import org.itxsvv.kxradar.saveSettings
import org.itxsvv.kxradar.streamSettings

@Composable
fun MainScreen() {
    val pattern = remember { Regex("^\\d*\\d*\$") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val karooSystem = remember { KarooSystemService(ctx) }
    var savedDialogVisible by remember { mutableStateOf(false) }

    var uiThreatBeep by remember { mutableStateOf(Beep(200, 100)) }
    var uiPassedBeep by remember { mutableStateOf(Beep(0, 0)) }
    var uiInRideOnlyEnabled by remember { mutableStateOf(true) }
    var uiBeepEnabled by remember { mutableStateOf(true) }

    fun saveUISettings() {
        scope.launch {
            val radarSettings = RadarSettings(
                threatBeep = uiThreatBeep,
                passedBeep = uiPassedBeep,
                inRideOnly = uiInRideOnlyEnabled,
                enabled = uiBeepEnabled
            )
            saveSettings(ctx, radarSettings)
        }
    }

    LaunchedEffect(Unit) {
        ctx.streamSettings().collect { settings ->
            uiThreatBeep = settings.threatBeep
            uiPassedBeep = settings.passedBeep
            uiInRideOnlyEnabled = settings.inRideOnly
            uiBeepEnabled = settings.enabled
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
        Spacer(modifier = Modifier.size(1.dp))
        Text("Threat sound")
        DrawBeepPanel(karooSystem, scope, uiThreatBeep, pattern,
            onDurationChange = { newDur ->
                uiThreatBeep = uiThreatBeep.copy(duration = newDur)
            },
            onFreqChange = { newFreq ->
                uiThreatBeep = uiThreatBeep.copy(frequency = newFreq)
            })
        Text("All clear sound (0 disable)")
        DrawBeepPanel(karooSystem, scope, uiPassedBeep, pattern,
            onDurationChange = { newDur ->
                uiPassedBeep = uiPassedBeep.copy(duration = newDur)
            },
            onFreqChange = { newFreq ->
                uiPassedBeep = uiPassedBeep.copy(frequency = newFreq)
            })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                modifier = Modifier.weight(1f),
                checked = uiInRideOnlyEnabled,
                onCheckedChange = {
                    uiInRideOnlyEnabled = it
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(modifier = Modifier.weight(1f), text = "In-ride only")
        }
        FilledTonalButton(modifier = Modifier
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
        HorizontalDivider(thickness = 2.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                modifier = Modifier.weight(1f),
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

        if (savedDialogVisible) {
            AlertDialog(onDismissRequest = { savedDialogVisible = false },
                confirmButton = {
                    Button(onClick = {
                        savedDialogVisible = false
                    }) { Text("OK") }
                },
                text = { Text("Settings saved successfully.") }
            )
        }
    }
}

@Composable
fun DrawBeepPanel(
    karooSystem: KarooSystemService,
    scope: CoroutineScope,
    beep: Beep,
    pattern: Regex,
    onFreqChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        OutlinedTextField(
            value = beep.frequency.toString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onValueChange = { newFreq ->
                if (!newFreq.isEmpty() && newFreq.matches(pattern)) {
                    onFreqChange(newFreq.replace("\n", "").toInt())
                }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(text = "Freq.") }
        )
        OutlinedTextField(
            value = beep.duration.toString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onValueChange = { newDuration ->
                if (!newDuration.isEmpty() && newDuration.matches(pattern)) {
                    onDurationChange((newDuration.replace("\n", "")).toInt())
                }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(text = "Dur.") }
        )
        FilledTonalButton(modifier = Modifier
            .weight(0.8f)
            .height(65.dp), shape = RoundedCornerShape(8.dp), onClick = {
            scope.launch {
                karooSystem.beep(beep.frequency, beep.duration)
            }
        }) {
            Icon(Icons.Default.PlayArrow, contentDescription = "")
        }
    }
}





