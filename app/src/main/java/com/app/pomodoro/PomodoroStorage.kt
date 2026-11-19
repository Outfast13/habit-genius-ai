package com.app.pomodoro

import android.content.Context
import org.json.JSONObject

class PomodoroStorage(context: Context) {

    private val prefs = context.getSharedPreferences("habit_genius_pomodoro_prefs", Context.MODE_PRIVATE)

    fun save(state: PomodoroState) {
        val root = JSONObject().apply {
            put("focusMinutes", state.focusMinutes.coerceIn(1, 180))
            put("breakMinutes", state.breakMinutes.coerceIn(1, 60))
            put("completedSessions", state.completedSessions.coerceAtLeast(0))
        }
        prefs.edit().putString("pomodoro_json", root.toString()).apply()
    }

    fun load(): PomodoroState {
        val raw = prefs.getString("pomodoro_json", null) ?: return PomodoroState()
        return try {
            val root = JSONObject(raw)
            PomodoroState(
                focusMinutes = root.optInt("focusMinutes", 25).coerceIn(1, 180),
                breakMinutes = root.optInt("breakMinutes", 5).coerceIn(1, 60),
                completedSessions = root.optInt("completedSessions", 0).coerceAtLeast(0)
            )
        } catch (_: Exception) {
            PomodoroState()
        }
    }
}
