package com.sayaem.nebula.ui.screens

import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors



data class SleepTimerState(
    val isActive: Boolean = false,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
) {
    val remainingFormatted: String get() {
        val m = remainingSeconds / 60
        val s = remainingSeconds % 60
        return "%d:%02d".format(m, s)
    }
    val progress: Float get() =
        if (totalSeconds == 0) 0f else 1f - (remainingSeconds.toFloat() / totalSeconds)
}

@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(5, 10, 15, 20, 30, 45, 60, 90)
    var customText by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf(-1) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(LocalAppColors.current.bgSecondary)
                .clickable(enabled = false) {}
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .navigationBarsPadding()
        ) {
            // Handle
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(LocalAppColors.current.border).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Sleep Timer", style = MaterialTheme.typography.headlineSmall,
                    color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.Bedtime, null, tint = NebulaCyan, modifier = Modifier.size(24.dp))
            }

            Spacer(Modifier.height(20.dp))

            // Active timer display
            if (state.isActive) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(NebulaCyan.copy(0.1f))
                        .border(1.dp, NebulaCyan.copy(0.3f), RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Time remaining", style = MaterialTheme.typography.labelMedium,
                            color = LocalAppColors.current.textSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text(state.remainingFormatted,
                            style = MaterialTheme.typography.displayMedium,
                            color = NebulaCyan, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = NebulaCyan,
                            trackColor = LocalAppColors.current.border,
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { onCancel(); onDismiss() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NebulaRed),
                            border = BorderStroke(1.dp, NebulaRed),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Cancel Timer", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                // Custom time input
                Text("Custom time (minutes)", style = MaterialTheme.typography.labelMedium,
                    color = LocalAppColors.current.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) { customText = it; selectedPreset = -1 } },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("e.g. 45", color = LocalAppColors.current.textTertiary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor   = LocalAppColors.current.textPrimary,
                            unfocusedTextColor = LocalAppColors.current.textPrimary,
                            focusedBorderColor = NebulaViolet,
                            unfocusedBorderColor = LocalAppColors.current.border,
                        )
                    )
                    Button(
                        onClick = {
                            val mins = customText.toIntOrNull()
                            if (mins != null && mins in 1..180) { onStart(mins); onDismiss() }
                        },
                        enabled = customText.toIntOrNull()?.let { it in 1..180 } == true,
                        colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet),
                        modifier = Modifier.height(56.dp).padding(top = 2.dp)
                    ) {
                        Text("Start", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Preset grid
                Text("Quick presets", style = MaterialTheme.typography.labelMedium,
                    color = LocalAppColors.current.textSecondary)
                Spacer(Modifier.height(10.dp))
                val rows = presets.chunked(4)
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { mins ->
                            val sel = selectedPreset == mins
                            Box(
                                Modifier.weight(1f).height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) NebulaViolet else LocalAppColors.current.card)
                                    .border(0.5.dp, if (sel) NebulaViolet else LocalAppColors.current.border, RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedPreset = mins
                                        customText = ""
                                        onStart(mins)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (mins < 60) "${mins}m" else "${mins / 60}h",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (sel) Color.White else TextPrimaryDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        // Fill remaining slots if row has < 4
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SpeedPickerSheet(
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(LocalAppColors.current.bgSecondary)
                .clickable(enabled = false) {}
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .navigationBarsPadding()
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(LocalAppColors.current.border).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Playback Speed", style = MaterialTheme.typography.headlineSmall,
                    color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
                Text("${current}×", style = MaterialTheme.typography.titleLarge,
                    color = NebulaViolet, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                speeds.forEach { s ->
                    val sel = s == current
                    Box(
                        Modifier.weight(1f).height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) NebulaViolet else LocalAppColors.current.card)
                            .border(0.5.dp, if (sel) NebulaViolet else LocalAppColors.current.border, RoundedCornerShape(14.dp))
                            .clickable { onSelect(s); onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${s}×", style = MaterialTheme.typography.labelLarge,
                            color = if (sel) Color.White else TextPrimaryDark,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
