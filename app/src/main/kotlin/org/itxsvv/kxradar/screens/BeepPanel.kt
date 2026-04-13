package org.itxsvv.kxradar.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.itxsvv.kxradar.Beep
import org.itxsvv.kxradar.beep

@Composable
fun DrawBeepPanel(
    karooSystem: KarooSystemService,
    scope: CoroutineScope,
    beep: Beep,
    pattern: Regex,
    onFreqChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
) {
    var frequencyText by remember { mutableStateOf(beep.frequency.toString()) }
    var durationText by remember { mutableStateOf(beep.duration.toString()) }

    LaunchedEffect(beep.frequency) {
        if (frequencyText != beep.frequency.toString()) {
            frequencyText = beep.frequency.toString()
        }
    }

    LaunchedEffect(beep.duration) {
        if (durationText != beep.duration.toString()) {
            durationText = beep.duration.toString()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        OutlinedTextField(
            value = frequencyText,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onValueChange = { newFreq ->
                if (newFreq.matches(pattern)) {
                    frequencyText = newFreq
                    onFreqChange(newFreq.toIntOrNull() ?: 0)
                }
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && frequencyText.isEmpty()) {
                        frequencyText = "0"
                        onFreqChange(0)
                    }
                },
            singleLine = true,
            label = { Text(text = "Freq.") }
        )
        OutlinedTextField(
            value = durationText,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onValueChange = { newDuration ->
                if (newDuration.matches(pattern)) {
                    durationText = newDuration
                    onDurationChange(newDuration.toIntOrNull() ?: 0)
                }
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && durationText.isEmpty()) {
                        durationText = "0"
                        onDurationChange(0)
                    }
                },
            singleLine = true,
            label = { Text(text = "Dur.") }
        )
        FilledTonalButton(
            modifier = Modifier
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
