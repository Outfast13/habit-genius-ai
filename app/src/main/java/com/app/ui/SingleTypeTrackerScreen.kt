package com.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.app.habits.HabitsTasksState
import com.app.habits.TrackerItem
import com.app.habits.TrackerItemType
import java.util.UUID

@Composable
fun SingleTypeTrackerScreen(
    fixedType: TrackerItemType,
    initialState: HabitsTasksState,
    onStateChanged: (HabitsTasksState) -> Unit
) {
    var state by remember(initialState) { mutableStateOf(initialState) }
    var titleInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("General") }

    val isHabit = fixedType == TrackerItemType.HABIT
    val pageTitle = if (isHabit) "Habits" else "Tasks"
    val singularLabel = if (isHabit) "Habit" else "Task"

    val bgDark = Color(0xFF0E110F)
    val cardDark = Color(0xFF171C18)
    val accentGreen = Color(0xFF7FD18B)
    val bannerGreen = Color(0xFF28422E)

    val filteredItems = state.items.filter { it.type == fixedType }
    val doneCount = filteredItems.count { it.isCompleted }

    fun commit(newState: HabitsTasksState) {
        state = newState
        onStateChanged(newState)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = pageTitle,
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
                    text = "$doneCount / ${filteredItems.size} $pageTitle completed",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isHabit) "Checking off builds streaks" else "Focused action items",
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Add $singularLabel",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("$singularLabel title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = categoryInput,
                onValueChange = { categoryInput = it },
                label = { Text("Category") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val cleanTitle = titleInput.trim()
                    if (cleanTitle.isNotEmpty()) {
                        val newItem = TrackerItem(
                            id = UUID.randomUUID().toString(),
                            title = cleanTitle,
                            type = fixedType,
                            category = categoryInput.trim().ifEmpty { "General" }
                        )
                        commit(state.copy(items = listOf(newItem) + state.items))
                        titleInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add $singularLabel", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardDark, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No ${pageTitle.lowercase()} yet.", color = Color.Gray)
            }
        } else {
            filteredItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardDark, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = item.isCompleted,
                            onCheckedChange = { checked ->
                                val nextItems = state.items.map { existing ->
                                    if (existing.id != item.id) {
                                        existing
                                    } else {
                                        val nextStreak = if (isHabit) {
                                            if (checked) existing.streakCount + 1
                                            else (existing.streakCount - 1).coerceAtLeast(0)
                                        } else {
                                            existing.streakCount
                                        }
                                        existing.copy(isCompleted = checked, streakCount = nextStreak)
                                    }
                                }
                                commit(state.copy(items = nextItems))
                            }
                        )

                        Column {
                            Text(
                                text = item.title,
                                color = if (item.isCompleted) Color.Gray else Color.White,
                                fontWeight = FontWeight.Medium,
                                textDecoration = if (item.isCompleted) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                }
                            )
                            val subtitle = if (isHabit) {
                                "${item.category} • Streak ${item.streakCount}"
                            } else {
                                item.category
                            }
                            Text(
                                text = subtitle,
                                color = Color(0xFF9FB0A3),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Text(
                        text = "✕",
                        color = Color(0xFFE57373),
                        modifier = Modifier
                            .clickable {
                                commit(state.copy(items = state.items.filterNot { it.id == item.id }))
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
