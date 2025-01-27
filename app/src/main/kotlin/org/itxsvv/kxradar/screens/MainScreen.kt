package org.itxsvv.kxradar.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.PlayBeepPattern
import kotlinx.coroutines.launch
import org.itxsvv.kxradar.RadarSettings
import org.itxsvv.kxradar.beep
import org.itxsvv.kxradar.saveSettings
import org.itxsvv.kxradar.streamSettings

@Composable
fun MainScreen() {
    val pattern = remember { Regex("^\\d*\\d*\$") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val karooSystem = remember { KarooSystemService(ctx) }
    var savedDialogVisible by remember { mutableStateOf(false) }

    var uiThreatLevelFreq by remember { mutableStateOf(0) }
    var uiThreatLevelDur by remember { mutableStateOf(0) }
    var uiThreatPassedLevelFreq by remember { mutableStateOf(0) }
    var uiThreatPassedLevelDur by remember { mutableStateOf(0) }
    var uiInRideOnlyEnabled by remember { mutableStateOf(true) }
    var uiBeepEnabled by remember { mutableStateOf(true) }

    fun saveUISettings() {
        scope.launch {
            val radarSettings = RadarSettings(
                threatLevelFreq = uiThreatLevelFreq,
                threatLevelDur = uiThreatLevelDur,
                threaPassedtLevelFreq = uiThreatPassedLevelFreq,
                threatPassedLevelDur = uiThreatPassedLevelDur,
                inRideOnly = uiInRideOnlyEnabled,
                enabled = uiBeepEnabled
            )
            saveSettings(ctx, radarSettings)
        }
    }

    LaunchedEffect(Unit) {
        ctx.streamSettings().collect { settings ->
            uiThreatLevelFreq = settings.threatLevelFreq
            uiThreatLevelDur = settings.threatLevelDur
            uiThreatPassedLevelFreq = settings.threaPassedtLevelFreq
            uiThreatPassedLevelDur = settings.threatPassedLevelDur
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
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.size(1.dp))
        Text("Threat sound")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            OutlinedTextField(
                value = uiThreatLevelFreq.toString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { newText ->
                    if (!newText.isEmpty() && newText.matches(pattern)) {
                        uiThreatLevelFreq = newText.replace("\n", "").toInt()
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(text = "Freq.") }

            )
            OutlinedTextField(
                value = uiThreatLevelDur.toString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                onValueChange = { newText ->
                    if (!newText.isEmpty() && newText.matches(pattern)) {
                        uiThreatLevelDur = (newText.replace("\n", "")).toInt()
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
                    karooSystem.beep(uiThreatLevelFreq, uiThreatLevelDur)
                }
            }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "")
            }
        }
        Text("All clear sound (0 disable)")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            OutlinedTextField(
                value = uiThreatPassedLevelFreq.toString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { newText ->
                    if (!newText.isEmpty() && newText.matches(pattern)) {
                        uiThreatPassedLevelFreq = newText.replace("\n", "").toInt()
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(text = "Freq.") }
            )
            OutlinedTextField(
                value = uiThreatPassedLevelDur.toString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                onValueChange = { newText ->
                    if (!newText.isEmpty() && newText.matches(pattern)) {
                        uiThreatPassedLevelDur = (newText.replace("\n", "")).toInt()
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
                    karooSystem.beep(uiThreatPassedLevelFreq, uiThreatPassedLevelDur)
                }
            }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "")
            }
        }
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






