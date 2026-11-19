package com.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.pomodoro.PomodoroPhase
import com.app.pomodoro.PomodoroState
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PomodoroScreen(
    initialState: PomodoroState,
    onStateChanged: (PomodoroState) -> Unit
) {
    var configState by remember(initialState) { mutableStateOf(initialState) }
    var phase by remember { mutableStateOf(PomodoroPhase.FOCUS) }
    var remainingSeconds by remember(configState.focusMinutes) {
        mutableIntStateOf(configState.focusMinutes * 60)
    }
    var isRunning by remember { mutableStateOf(false) }

    var focusInput by remember(configState.focusMinutes) {
        mutableStateOf(configState.focusMinutes.toString())
    }
    var breakInput by remember(configState.breakMinutes) {
        mutableStateOf(configState.breakMinutes.toString())
    }

    val bgDark = Color(0xFF0E110F)
    val cardDark = Color(0xFF171C18)
    val accentGreen = Color(0xFF7FD18B)
    val bannerGreen = Color(0xFF28422E)

    fun updateConfig(next: PomodoroState) {
        configState = next
        onStateChanged(next)
    }

    fun switchPhase(target: PomodoroPhase) {
        isRunning = false
        phase = target
        remainingSeconds = (if (target == PomodoroPhase.FOCUS) configState.focusMinutes else configState.breakMinutes) * 60
    }

    LaunchedEffect(isRunning, phase) {
        while (isRunning) {
            delay(1000L)
            if (remainingSeconds > 1) {
                remainingSeconds -= 1
            } else {
                if (phase == PomodoroPhase.FOCUS) {
                    val updated = configState.copy(completedSessions = configState.completedSessions + 1)
                    updateConfig(updated)
                    switchPhase(PomodoroPhase.BREAK)
                } else {
                    switchPhase(PomodoroPhase.FOCUS)
                }
            }
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val clockText = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Pomodoro Timer",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bannerGreen, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "${configState.completedSessions} focus sessions completed",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Current phase: ${phase.label}",
                    color = Color(0xFFB7D4BC),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardDark, RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = phase.label.uppercase(),
                color = accentGreen,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                text = clockText,
                color = Color.White,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { isRunning = !isRunning },
                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isRunning) "Pause" else "Start",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { switchPhase(phase) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }

                Button(
                    onClick = {
                        val next = if (phase == PomodoroPhase.FOCUS) PomodoroPhase.BREAK else PomodoroPhase.FOCUS
                        switchPhase(next)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Switch")
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardDark, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Interval Lengths (Minutes)",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = focusInput,
                    onValueChange = { focusInput = it },
                    label = { Text("Focus min") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = breakInput,
                    onValueChange = { breakInput = it },
                    label = { Text("Break min") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    val fMin = focusInput.toIntOrNull()?.coerceIn(1, 180) ?: configState.focusMinutes
                    val bMin = breakInput.toIntOrNull()?.coerceIn(1, 60) ?: configState.breakMinutes
                    val updated = configState.copy(focusMinutes = fMin, breakMinutes = bMin)
                    updateConfig(updated)
                    isRunning = false
                    remainingSeconds = (if (phase == PomodoroPhase.FOCUS) fMin else bMin) * 60
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Intervals")
            }
        }
    }
}
