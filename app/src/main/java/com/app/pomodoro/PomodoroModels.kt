package com.app.pomodoro

enum class PomodoroPhase(val label: String) {
    FOCUS("Focus"),
    BREAK("Break")
}

data class PomodoroState(
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val completedSessions: Int = 0
)
