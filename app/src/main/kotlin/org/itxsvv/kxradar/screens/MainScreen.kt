package org.itxsvv.kxradar.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import org.itxsvv.kxradar.KarooRadarExtension
import org.itxsvv.kxradar.RadarSettings
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
    var uiBeepEnabled by remember { mutableStateOf(true) }

    fun saveUISettings() {
        scope.launch {
            val radarSettings = RadarSettings(
                threatLevelFreq = uiThreatLevelFreq,
                threatLevelDur = uiThreatLevelDur,
                enabled = uiBeepEnabled
            )
            saveSettings(ctx, radarSettings)
        }
    }

    LaunchedEffect(Unit) {
        ctx.streamSettings().collect { settings ->
            uiThreatLevelFreq = settings.threatLevelFreq
            uiThreatLevelDur = settings.threatLevelDur
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
            .padding(5.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Radar Sound")
        OutlinedTextField(
            value = uiThreatLevelFreq.toString(),
            leadingIcon = { Icon(imageVector = Icons.Default.Menu, contentDescription = "") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onValueChange = { newText ->
                if (!newText.isEmpty() && newText.matches(pattern)) {
                    uiThreatLevelFreq = newText.replace("\n", "").toInt()
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Freq") }
        )
        OutlinedTextField(
            value = uiThreatLevelDur.toString(),
            leadingIcon = { Icon(imageVector = Icons.Default.Menu, contentDescription = "") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            onValueChange = { newText ->
                if (!newText.isEmpty() && newText.matches(pattern)) {
                    uiThreatLevelDur = (newText.replace("\n", "")).toInt()
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Duration") }
        )

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

        FilledTonalButton(modifier = Modifier
            .fillMaxWidth()
            .height(50.dp), onClick = {
            scope.launch {
                if(karooSystem.dispatch(
                    PlayBeepPattern(
                        listOf(
                            PlayBeepPattern.Tone(uiThreatLevelFreq, uiThreatLevelDur)
                        )
                    )
                )) {
                    Log.i(KarooRadarExtension.TAG, "Beep! $uiThreatLevelFreq $uiThreatLevelDur")
                } else {
                    Log.i(KarooRadarExtension.TAG, "Not connected!")
                }
            }
        }) {
            Icon(Icons.Default.PlayArrow, contentDescription = "")
            Spacer(modifier = Modifier.width(5.dp))
            Text("Test Beep")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                uiBeepEnabled,
                onCheckedChange = {
                    uiBeepEnabled = it
                    scope.launch {
                        saveUISettings()
                    }
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Enabled")
        }

        if (savedDialogVisible){
            AlertDialog(onDismissRequest = { savedDialogVisible = false },
                confirmButton = { Button(onClick = {
                    savedDialogVisible = false
                }) { Text("OK") } },
                text = { Text("Settings saved successfully.") }
            )
        }
    }

}






