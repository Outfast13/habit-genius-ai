package com.app.habits

enum class TrackerItemType {
    HABIT,
    TASK
}

data class TrackerItem(
    val id: String,
    val title: String,
    val type: TrackerItemType,
    val isCompleted: Boolean = false,
    val streakCount: Int = 0,
    val category: String = "General",
    val notes: String = ""
)

data class HabitsTasksState(
    val items: List<TrackerItem> = emptyList()
)
