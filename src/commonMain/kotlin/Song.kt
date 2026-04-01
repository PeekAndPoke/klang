package io.peekandpoke.klang

data class Song(
    val id: String,
    val title: String,
    val rpm: Double,
    val code: String,
    val icon: String? = null,
)
