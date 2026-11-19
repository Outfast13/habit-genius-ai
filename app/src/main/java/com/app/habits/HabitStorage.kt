package com.app.habits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HabitStorage(context: Context) {

    private val prefs = context.getSharedPreferences("habit_genius_habits_prefs", Context.MODE_PRIVATE)

    fun save(state: HabitsTasksState) {
        val array = JSONArray()
        for (item in state.items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("type", item.type.name)
                put("isCompleted", item.isCompleted)
                put("streakCount", item.streakCount)
                put("category", item.category)
                put("notes", item.notes)
            }
            array.put(obj)
        }
        val root = JSONObject().put("items", array)
        prefs.edit().putString("habits_tasks_json", root.toString()).apply()
    }

    fun load(): HabitsTasksState {
        val raw = prefs.getString("habits_tasks_json", null) ?: return HabitsTasksState()

        return try {
            val root = JSONObject(raw)
            val array = root.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<TrackerItem>()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val title = obj.optString("title", "").trim()
                if (id.isEmpty() || title.isEmpty()) continue

                val typeName = obj.optString("type", TrackerItemType.TASK.name)
                val type = runCatching { TrackerItemType.valueOf(typeName) }
                    .getOrDefault(TrackerItemType.TASK)

                list.add(
                    TrackerItem(
                        id = id,
                        title = title,
                        type = type,
                        isCompleted = obj.optBoolean("isCompleted", false),
                        streakCount = obj.optInt("streakCount", 0),
                        category = obj.optString("category", "General"),
                        notes = obj.optString("notes", "")
                    )
                )
            }

            HabitsTasksState(items = list)
        } catch (_: Exception) {
            HabitsTasksState()
        }
    }
}
