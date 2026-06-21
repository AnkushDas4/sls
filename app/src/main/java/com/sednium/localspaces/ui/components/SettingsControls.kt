package com.sednium.localspaces.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.ui.theme.SedRedAlpha
import com.sednium.localspaces.ui.theme.SedniumColors
import com.sednium.localspaces.ui.theme.SedniumRadii

/** Section header — e.g. "PROVIDER & MODEL", "GENERATION", "HISTORY". */
@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = SedRedAlpha.a60,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

/** A labeled on/off row, e.g. "Enable Tools", "Save Chat History". */
@Composable
fun SettingsSwitchRow(label: String, description: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SedniumColors.SedRed)
            description?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = SedRedAlpha.a60)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SedniumColors.SedYellow,
                checkedTrackColor = SedniumColors.SedRed,
                uncheckedThumbColor = SedniumColors.SedRed,
                uncheckedTrackColor = SedRedAlpha.a20
            )
        )
    }
}

/** A labeled slider with a live numeric readout — temperature / topP / topK / maxTokens. */
@Composable
fun SettingsSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    displayFormat: (Float) -> String = { String.format("%.2f", it) }
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SedniumColors.SedRed)
            Text(displayFormat(value), style = MaterialTheme.typography.labelSmall, color = SedRedAlpha.a70)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = SedniumColors.SedRed,
                activeTrackColor = SedniumColors.SedRed,
                inactiveTrackColor = SedRedAlpha.a20
            )
        )
    }
}

/** Underlined text field used for API keys, base URLs, system instructions. */
@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isSecret: Boolean = false,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SedRedAlpha.a70, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            readOnly = readOnly,
            placeholder = { Text(placeholder, color = SedRedAlpha.a40) },
            visualTransformation = if (isSecret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            shape = RoundedCornerShape(SedniumRadii.sm),
            trailingIcon = trailingIcon,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SedniumColors.SedYellow,
                unfocusedContainerColor = SedniumColors.SedYellow,
                focusedIndicatorColor = SedniumColors.SedRed,
                unfocusedIndicatorColor = SedRedAlpha.a30,
                focusedTextColor = SedniumColors.SedRed,
                unfocusedTextColor = SedniumColors.SedRed
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

/** Selectable chip used for the provider grid (Google / OpenAI / Anthropic / xAI / …). */
@Composable
fun ProviderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) SedniumColors.SedYellow else SedniumColors.SedRed,
        modifier = Modifier
            .clip(RoundedCornerShape(SedniumRadii.md))
            .background(if (selected) SedniumColors.SedRed else SedRedAlpha.a05)
            .border(1.dp, if (selected) SedniumColors.SedRed else SedRedAlpha.a20, RoundedCornerShape(SedniumRadii.md))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
