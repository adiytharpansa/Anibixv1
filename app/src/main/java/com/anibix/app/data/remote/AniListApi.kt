package com.anibix.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Klien AniList GraphQL (metadata publik, tanpa kunci API).
 * Torrent/magnet TIDAK berasal dari AniList — itu tugas sumber ekstensi terpisah
 * (lihat CatalogSource); AniList hanya menyediakan metadata katalog.
 */
interface AniListApi {
    @POST(".")
    suspend fun query(@Body body: GraphQlRequest): AniListResponse
}

@Serializable
data class GraphQlRequest(val query: String, val variables: Map<String, kotlinx.serialization.json.JsonElement>)

@Serializable
data class AniListResponse(val data: AniListData? = null)

@Serializable
data class AniListData(@SerialName("Page") val page: AniListPage? = null)

@Serializable
data class AniListPage(val media: List<AniListMedia> = emptyList())

@Serializable
data class AniListMedia(
    val id: Int,
    val title: AniListTitle = AniListTitle(),
    val description: String? = null,
    val coverImage: AniListCover = AniListCover(),
    val bannerImage: String? = null,
    val averageScore: Int? = null,
    val startDate: AniListDate? = null,
    val genres: List<String> = emptyList(),
    val episodes: Int? = null,
)

@Serializable
data class AniListTitle(val romaji: String? = null, val english: String? = null)

@Serializable
data class AniListCover(val extraLarge: String? = null, val large: String? = null)

@Serializable
data class AniListDate(val year: Int? = null)

object AniListQueries {
    const val TRENDING = """
        query (${'$'}page: Int, ${'$'}perPage: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
              id title { romaji english } description
              coverImage { extraLarge large } bannerImage
              averageScore startDate { year } genres episodes
            }
          }
        }
    """

    const val POPULAR = """
        query (${'$'}page: Int, ${'$'}perPage: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, sort: POPULARITY_DESC, isAdult: false) {
              id title { romaji english } description
              coverImage { extraLarge large } bannerImage
              averageScore startDate { year } genres episodes
            }
          }
        }
    """
}
