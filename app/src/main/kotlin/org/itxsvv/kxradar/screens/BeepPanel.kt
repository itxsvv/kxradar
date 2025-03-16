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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    onFreqChange(newFreq.toInt())
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
                    onDurationChange((newDuration.toInt()))
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
