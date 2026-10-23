package com.anibix.app.data

import com.anibix.app.core.AppResult
import com.anibix.app.core.safeCall
import com.anibix.app.data.remote.ScreaperClient
import com.anibix.app.data.remote.ScreaperEntryDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sumber pelengkap magnet dari katalog screaperv1 (AniList + Nyaa.si).
 *
 * Bukan penyedia kategori utama: judul-judulnya memperkaya judul AniList
 * yang anilistId-nya sama dengan magnet per episode; judul yang tidak ada
 * padanannya di katalog utama tampil di kategori "Bisa Diputar".
 * ID judul sengaja memakai skema yang sama dengan AniList ("anilist-<id>")
 * supaya agregator bisa memasangkannya.
 */
@Singleton
class ScreaperCatalogSource @Inject constructor(
    private val client: ScreaperClient,
) : CatalogSource {
    override val name = "Screaper"
    override val isSupplemental = true

    override suspend fun categories(): AppResult<List<Category>> = safeCall {
        val titles = client.fetchCatalog().mapNotNull { it.toTitle() }
        listOf(Category("Bisa Diputar", titles)).filter { it.titles.isNotEmpty() }
    }

    private fun ScreaperEntryDto.toTitle(): Title? {
        val withMagnet = torrents.episodes
            .filter { !it.magnet.isNullOrBlank() }
            .sortedBy { it.episode }
        val episodes = withMagnet.map { Episode(number = it.episode, magnet = it.magnet) }
        val bestBatch = torrents.batches
            .filter { !it.magnet.isNullOrBlank() }
            .maxByOrNull { it.seeders }
        // Judul tanpa magnet sama sekali tidak berguna sebagai pelengkap.
        if (episodes.isEmpty() && bestBatch == null) return null
        return Title(
            id = "anilist-$anilistId",
            name = title.english ?: title.romaji ?: "Tanpa judul",
            synopsis = synopsis?.replace(Regex("<[^>]*>"), "")?.trim().orEmpty(),
            posterUrl = poster.orEmpty(),
            backdropUrl = banner ?: poster.orEmpty(),
            // Magnet judul hanya untuk konten tanpa episode bermagnet
            // (film/batch tunggal); serial memakai magnet per episode.
            magnet = if (episodes.isEmpty()) bestBatch?.magnet else null,
            year = year ?: 0,
            genre = genres.firstOrNull().orEmpty(),
            rating = (score ?: 0) / 10.0,
            episodes = episodes,
            anilistId = anilistId,
        )
    }
}
