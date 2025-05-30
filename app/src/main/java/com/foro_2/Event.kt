package com.foro_2

data class Event(
    var id: String = "",
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val location: String = "",
    val description: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "title" to title,
            "date" to date,
            "time" to time,
            "location" to location,
            "description" to description
        )
    }
}