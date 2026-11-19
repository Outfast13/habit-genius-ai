package com.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.app.ai.AiProvider
import com.app.ai.AiSettingsState
import com.app.ai.ProviderSettings

@Composable
fun AiSettingsScreen(
    initialState: AiSettingsState = AiSettingsState(),
    onSave: (AiSettingsState) -> Unit
) {
    var state by remember { mutableStateOf(initialState) }
    var bulkKeyInput by remember { mutableStateOf("") }

    val currentProvider = state.activeProvider
    val currentConfig = state.providers[currentProvider] ?: ProviderSettings()

    fun updateProviderConfig(transform: (ProviderSettings) -> ProviderSettings) {
        val updated = state.providers.toMutableMap()
        updated[currentProvider] = transform(currentConfig)
        state = state.copy(providers = updated)
    }

    val bgDark = Color(0xFF0E110F)
    val cardDark = Color(0xFF171C18)
    val accentGreen = Color(0xFF7FD18B)
    val bannerGreen = Color(0xFF28422E)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Settings",
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
                    text = if (state.isOnline) "AI online" else "AI offline",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Using ${currentProvider.label.lowercase()}",
                    color = Color(0xFFB7D4BC),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardDark, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "AI Provider",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto failover", color = Color.LightGray)
                Switch(
                    checked = state.autoFailover,
                    onCheckedChange = { state = state.copy(autoFailover = it) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show paid models", color = Color.LightGray)
                Switch(
                    checked = state.showPaidModels,
                    onCheckedChange = { state = state.copy(showPaidModels = it) }
                )
            }

            val providers = AiProvider.entries
            for (rowIndex in 0..1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (colIndex in 0..1) {
                        val provider = providers[rowIndex * 2 + colIndex]
                        val selected = provider == currentProvider
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) bannerGreen else Color(0xFF111512),
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selected) accentGreen else Color(0xFF2B332C),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { state = state.copy(activeProvider = provider) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selected) "✓ ${provider.label}" else provider.label,
                                color = if (selected) accentGreen else Color.LightGray,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Text("API keys", color = Color.LightGray, fontWeight = FontWeight.Medium)

            currentConfig.apiKeys.forEachIndexed { index, key ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111512), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "••••••••${key.takeLast(4).ifEmpty { "key" }}",
                        color = Color.Gray
                    )
                    Text(
                        text = "✕",
                        color = Color(0xFFE57373),
                        modifier = Modifier.clickable {
                            updateProviderConfig { cfg ->
                                cfg.copy(apiKeys = cfg.apiKeys.filterIndexed { i, _ -> i != index })
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = bulkKeyInput,
                    onValueChange = { bulkKeyInput = it },
                    placeholder = { Text("Add key (paste several)", color = Color.Gray) },
                    singleLine = false,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val newKeys = bulkKeyInput
                            .split(",", "\n", " ")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        if (newKeys.isNotEmpty()) {
                            updateProviderConfig { cfg ->
                                cfg.copy(apiKeys = (cfg.apiKeys + newKeys).distinct())
                            }
                            bulkKeyInput = ""
                        }
                    }
                ) {
                    Text("Add")
                }
            }

            OutlinedTextField(
                value = currentConfig.photoModelId,
                onValueChange = { valStr ->
                    updateProviderConfig { it.copy(photoModel = valStr, photoModelId = valStr) }
                },
                label = { Text("Photo model id") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = currentConfig.textModelId,
                onValueChange = { valStr ->
                    updateProviderConfig { it.copy(textModel = valStr, textModelId = valStr) }
                },
                label = { Text("Text model id") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onSave(state) },
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save AI Settings", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
