package com.anibix.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Klien katalog screaperv1 (repo GitHub adiytharpansa/screaperv1):
 * file `output/catalog.json` berisi metadata AniList + magnet torrent
 * per episode hasil scraper AniList + Nyaa.si.
 *
 * File dibaca lewat cermin jsDelivr (3 pintu, dicoba berurutan).
 * Panggilan bersifat blocking — panggil hanya dari dispatcher IO
 * (AggregatingCatalogRepository sudah membungkusnya).
 */
@Singleton
class ScreaperClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    fun fetchCatalog(): List<ScreaperEntryDto> {
        var lastError: Exception? = null
        for (door in DOORS) {
            try {
                val request = Request.Builder()
                    .url(door)
                    .header("User-Agent", "Anibix")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} dari $door")
                    val body = response.body?.string() ?: throw IOException("Badan kosong dari $door")
                    return json.decodeFromString(body)
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("Semua pintu katalog gagal: ${lastError?.message}")
    }

    companion object {
        private const val PATH = "gh/adiytharpansa/screaperv1@main/output/catalog.json"
        val DOORS = listOf(
            "https://cdn.jsdelivr.net/$PATH",
            "https://fastly.jsdelivr.net/$PATH",
            "https://gcore.jsdelivr.net/$PATH",
        )
    }
}

@Serializable
data class ScreaperEntryDto(
    val anilistId: Int,
    val title: ScreaperTitleDto = ScreaperTitleDto(),
    val synopsis: String? = null,
    val poster: String? = null,
    val banner: String? = null,
    val year: Int? = null,
    val score: Int? = null,
    val genres: List<String> = emptyList(),
    val episodesTotal: Int? = null,
    val torrents: ScreaperTorrentsDto = ScreaperTorrentsDto(),
)

@Serializable
data class ScreaperTitleDto(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
data class ScreaperTorrentsDto(
    val episodes: List<ScreaperEpisodeDto> = emptyList(),
    val batches: List<ScreaperReleaseDto> = emptyList(),
)

@Serializable
data class ScreaperEpisodeDto(
    val episode: Int,
    val magnet: String? = null,
)

@Serializable
data class ScreaperReleaseDto(
    val magnet: String? = null,
    val seeders: Int = 0,
)
