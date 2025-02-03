package org.itxsvv.kxradar.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.launch
import org.itxsvv.kxradar.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainScreen() {
    val pattern = remember { Regex("^\\d*\\d*\$") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val karooSystem = remember { KarooSystemService(ctx) }
    var savedDialogVisible by remember { mutableStateOf(false) }

    var uiThreatLevelPattern by remember { mutableStateOf(listOf(BeepPattern(200, 100, 0))) }
    var uiThreatPassedLevelPattern by remember { mutableStateOf(listOf(BeepPattern(0, 0, 0))) }
    var uiInRideOnlyEnabled by remember { mutableStateOf(true) }
    var uiBeepEnabled by remember { mutableStateOf(true) }

    fun saveUISettings() {
        scope.launch {
            val radarSettings = RadarSettings(
                threatLevelPattern = uiThreatLevelPattern,
                threatPassedLevelPattern = uiThreatPassedLevelPattern,
                inRideOnly = uiInRideOnlyEnabled,
                enabled = uiBeepEnabled
            )
            saveSettings(ctx, radarSettings)
        }
    }

    LaunchedEffect(Unit) {
        ctx.streamSettings().collect { settings ->
            uiThreatLevelPattern = settings.threatLevelPattern
            uiThreatPassedLevelPattern = settings.threatPassedLevelPattern
            uiInRideOnlyEnabled = settings.inRideOnly
            uiBeepEnabled = settings.enabled
        }
    }

    LaunchedEffect(Unit) {
        karooSystem.connect()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .padding(2.dp)
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .clickable { focusManager.clearFocus() },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.size(1.dp))
        Text("Threat sound")
        uiThreatLevelPattern.forEachIndexed { index, beepPattern ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    OutlinedTextField(
                        value = beepPattern.freq.toString(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        onValueChange = { newText ->
                            if (!newText.isEmpty() && newText.matches(pattern)) {
                                uiThreatLevelPattern = uiThreatLevelPattern.toMutableList().apply {
                                    this[index] = this[index].copy(freq = newText.toInt())
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        focusManager.clearFocus()
                                        focusManager.moveFocus(FocusDirection.Enter)
                                    }
                                )
                            },
                        singleLine = true,
                        label = { Text(text = "Freq.") }
                    )
                    OutlinedTextField(
                        value = beepPattern.duration.toString(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        onValueChange = { newText ->
                            if (!newText.isEmpty() && newText.matches(pattern)) {
                                uiThreatLevelPattern = uiThreatLevelPattern.toMutableList().apply {
                                    this[index] = this[index].copy(duration = newText.toInt())
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        focusManager.clearFocus()
                                        focusManager.moveFocus(FocusDirection.Enter)
                                    }
                                )
                            },
                        singleLine = true,
                        label = { Text(text = "Dur.") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp)) // Separación entre los bloques
                if (index < uiThreatLevelPattern.size - 1) {
                    var delayText by remember { mutableStateOf(beepPattern.delay.toString()) }
                    OutlinedTextField(
                        value = delayText,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (beepPattern.delay < 300) {
                                    uiThreatLevelPattern = uiThreatLevelPattern.toMutableList().apply {
                                        this[index] = this[index].copy(delay = 300)
                                    }
                                    delayText = "300"
                                }
                                focusManager.clearFocus()
                            }
                        ),
                        onValueChange = { newText ->
                            if (!newText.isEmpty() && newText.matches(pattern)) {
                                delayText = newText
                                uiThreatLevelPattern = uiThreatLevelPattern.toMutableList().apply {
                                    this[index] = this[index].copy(delay = newText.toInt())
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        focusManager.clearFocus()
                                        focusManager.moveFocus(FocusDirection.Enter)
                                    }
                                )
                            }
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused && beepPattern.delay < 300) {
                                    uiThreatLevelPattern = uiThreatLevelPattern.toMutableList().apply {
                                        this[index] = this[index].copy(delay = 300)
                                    }
                                    delayText = "300"
                                }
                            },
                        singleLine = true,
                        label = { Text(text = "Delay") }
                    )
                }
            }
        }
        if (uiThreatLevelPattern.size < 10) {
            FilledTonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                onClick = {
                    uiThreatLevelPattern = uiThreatLevelPattern.toMutableList().apply {
                        this[lastIndex] = this[lastIndex].copy(delay = 500)
                        add(BeepPattern(200, 100, 0))
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "")
                Spacer(modifier = Modifier.width(5.dp))
                Text("Añadir")
            }
        }
        FilledTonalButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            onClick = {
                scope.launch {
                    karooSystem.beep(uiThreatLevelPattern)
                }
            }
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "")
            Spacer(modifier = Modifier.width(5.dp))
            Text("Play")
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