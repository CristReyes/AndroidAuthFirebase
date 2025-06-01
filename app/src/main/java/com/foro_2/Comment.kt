package com.foro_2

data class Comment(
    val userId: String = "",
    val userEmail: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
