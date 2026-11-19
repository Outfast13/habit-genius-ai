package com.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.ai.AiSettingsState

@Composable
fun AppSettingsScreen(
    aiState: AiSettingsState,
    onSaveAiState: (AiSettingsState) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E110F))
    ) {
        Text(
            text = "App Settings",
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp)
        )
        AiSettingsScreen(
            initialState = aiState,
            onSave = onSaveAiState
        )
    }
}
