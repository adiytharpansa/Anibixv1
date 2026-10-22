package com.anibix.app.data.local

import com.anibix.app.data.Title

fun Title.toCachedEntity(now: Long): CachedTitleEntity = CachedTitleEntity(
    id = id, name = name, synopsis = synopsis, posterUrl = posterUrl,
    backdropUrl = backdropUrl, magnet = magnet, year = year, genre = genre,
    rating = rating, anilistId = anilistId, cachedAt = now,
)

fun CachedTitleEntity.toTitle(): Title = Title(
    id = id, name = name, synopsis = synopsis, posterUrl = posterUrl,
    backdropUrl = backdropUrl, magnet = magnet, year = year, genre = genre,
    rating = rating, anilistId = anilistId,
)
