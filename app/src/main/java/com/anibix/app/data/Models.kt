package com.anibix.app.data

data class Title(
    val id: String,
    val name: String,
    val synopsis: String,
    val posterUrl: String,
    val backdropUrl: String = posterUrl,
    val magnet: String,          // sumber torrent (magnet URI)
    val year: Int = 0,
    val genre: String = ""
)

data class Category(val name: String, val titles: List<Title>)
