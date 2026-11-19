package com.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.ai.AiSettingsStorage
import com.app.habits.HabitStorage
import com.app.habits.TrackerItem
import com.app.habits.TrackerItemType
import com.app.journal.JournalStorage
import com.app.ui.AiWorkspaceScreen
import com.app.ui.AppSettingsScreen
import com.app.ui.JournalScreen
import com.app.ui.SingleTypeTrackerScreen
import java.util.UUID

private enum class AppPage(val label: String, val icon: String) {
    HABITS("Habits", "↻"),
    TASKS("Tasks", "✓"),
    JOURNAL("Journal", "✎"),
    POMODORO("Timer", "⏱"),
    AI("AI", "✦"),
    SETTINGS("Settings", "⚙")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val aiStorage = AiSettingsStorage(applicationContext)
        val habitStorage = HabitStorage(applicationContext)
        val journalStorage = JournalStorage(applicationContext)

        val initialAiState = aiStorage.load()
        val initialHabitsState = habitStorage.load()
        val initialJournalState = journalStorage.load()

        setContent {
            var currentPage by remember { mutableStateOf(AppPage.HABITS) }
            var aiState by remember { mutableStateOf(initialAiState) }
            var habitsState by remember { mutableStateOf(initialHabitsState) }
            var journalState by remember { mutableStateOf(initialJournalState) }

            Scaffold(
                containerColor = Color(0xFF0E110F),
                bottomBar = {
                    NavigationBar(containerColor = Color(0xFF171C18)) {
                        AppPage.entries.forEach { page ->
                            NavigationBarItem(
                                selected = currentPage == page,
                                onClick = { currentPage = page },
                                label = { Text(page.label) },
                                icon = { Text(page.icon) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color(0xFF0E110F))
                ) {
                    when (currentPage) {
                        AppPage.HABITS -> SingleTypeTrackerScreen(
                            fixedType = TrackerItemType.HABIT,
                            initialState = habitsState,
                            onStateChanged = { updated ->
                                habitsState = updated
                                habitStorage.save(updated)
                            }
                        )

                        AppPage.TASKS -> SingleTypeTrackerScreen(
                            fixedType = TrackerItemType.TASK,
                            initialState = habitsState,
                            onStateChanged = { updated ->
                                habitsState = updated
                                habitStorage.save(updated)
                            }
                        )

                        AppPage.JOURNAL -> JournalScreen(
                            initialState = journalState,
                            storage = journalStorage,
                            onStateChanged = { updated ->
                                journalState = updated
                                journalStorage.save(updated)
                            }
                        )

                        AppPage.POMODORO -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text("Pomodoro Timer", color = Color.White)
                            Text(
                                "Next step: focus/break countdown timer.",
                                color = Color.Gray
                            )
                        }

                        AppPage.AI -> AiWorkspaceScreen(
                            aiState = aiState,
                            onApproveProposedItem = { proposed ->
                                val newItem = TrackerItem(
                                    id = UUID.randomUUID().toString(),
                                    title = proposed.title,
                                    type = proposed.type,
                                    category = proposed.category,
                                    notes = proposed.reason
                                )
                                val updatedHabits = habitsState.copy(
                                    items = listOf(newItem) + habitsState.items
                                )
                                habitsState = updatedHabits
                                habitStorage.save(updatedHabits)
                            },
                            onOpenSettings = { currentPage = AppPage.SETTINGS }
                        )

                        AppPage.SETTINGS -> AppSettingsScreen(
                            aiState = aiState,
                            onSaveAiState = { updated ->
                                aiState = updated
                                aiStorage.save(updated)
                            }
                        )
                    }
                }
            }
        }
    }
}
