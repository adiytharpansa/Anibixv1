package com.anibix.app.data

import com.anibix.app.core.AppResult
import com.anibix.app.core.DispatcherProvider
import com.anibix.app.core.safeCall
import com.anibix.app.data.local.CachedTitleDao
import com.anibix.app.data.local.toCachedEntity
import com.anibix.app.data.local.toTitle
import com.anibix.app.data.remote.AniListApi
import com.anibix.app.data.remote.AniListQueries
import com.anibix.app.data.remote.GraphQlRequest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

interface CatalogRepository {
    suspend fun categories(): List<Category>
    suspend fun title(id: String): Title?

    /** Semua judul tanpa duplikat — dipakai layar grid. */
    suspend fun allTitles(): List<Title> = categories().flatMap { it.titles }.distinctBy { it.id }
}

/**
 * Sumber katalog yang bisa dipasang-copot (API metadata, ekstensi scraper, contoh lokal).
 * Sumber metadata (AniList) menghasilkan judul TANPA magnet; sumber ekstensi
 * yang menyediakan magnet tinggal mengisi [Title.magnet]/[Episode.magnet].
 */
interface CatalogSource {
    val name: String
    suspend fun categories(): AppResult<List<Category>>
}

/** Metadata katalog dari AniList (trending + populer). Magnet tidak tersedia di sini. */
@Singleton
class AniListCatalogSource @Inject constructor(private val api: AniListApi) : CatalogSource {
    override val name = "AniList"

    override suspend fun categories(): AppResult<List<Category>> = safeCall {
        val trending = fetch(AniListQueries.TRENDING)
        val popular = fetch(AniListQueries.POPULAR)
        listOf(
            Category("Sedang Tren", trending),
            Category("Paling Populer", popular),
        ).filter { it.titles.isNotEmpty() }
    }

    private suspend fun fetch(query: String): List<Title> {
        val vars = buildJsonObject {
            put("page", JsonPrimitive(1))
            put("perPage", JsonPrimitive(24))
        }
        val resp = api.query(GraphQlRequest(query, vars.mapValues { it.value }))
        return resp.data?.page?.media.orEmpty().map { m ->
            val poster = m.coverImage.extraLarge ?: m.coverImage.large.orEmpty()
            val episodeCount = (m.episodes ?: 0).coerceIn(0, 500)
            Title(
                id = "anilist-${m.id}",
                name = m.title.english ?: m.title.romaji ?: "Tanpa judul",
                synopsis = m.description?.replace(Regex("<[^>]*>"), "")?.trim().orEmpty(),
                posterUrl = poster,
                backdropUrl = m.bannerImage ?: poster,
                magnet = null,
                year = m.startDate?.year ?: 0,
                genre = m.genres.firstOrNull().orEmpty(),
                rating = (m.averageScore ?: 0) / 10.0,
                episodes = (1..episodeCount).map { Episode(it) },
                anilistId = m.id,
            )
        }
    }
}

/**
 * Contoh konten legal (film open-source Blender) — fallback terakhir bila
 * jaringan dan cache sama-sama kosong, sekaligus data demo yang bisa diputar.
 */
@Singleton
class SampleCatalogSource @Inject constructor() : CatalogSource {
    override val name = "Contoh"

    private val sintel = Title(
        id = "sintel",
        name = "Sintel",
        synopsis = "Film pendek animasi open-source dari Blender Foundation.",
        posterUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8f/Sintel_poster.jpg/400px-Sintel_poster.jpg",
        magnet = "magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Sintel&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337",
        year = 2010, genre = "Animasi"
    )
    private val bigBuckBunny = Title(
        id = "bbb", name = "Big Buck Bunny",
        synopsis = "Film pendek animasi open-source dari Blender Foundation.",
        posterUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/400px-Big_buck_bunny_poster_big.jpg",
        magnet = "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=Big+Buck+Bunny&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337",
        year = 2008, genre = "Animasi"
    )
    private val tearsOfSteel = Title(
        id = "tos", name = "Tears of Steel",
        synopsis = "Film pendek fiksi ilmiah open-source dari Blender Foundation.",
        posterUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/70/Tears_of_Steel_poster.jpg/400px-Tears_of_Steel_poster.jpg",
        magnet = "magnet:?xt=urn:btih:209c8226b299b308beaf2b9cd3fb49212dbd13ec&dn=Tears+of+Steel&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337",
        year = 2012, genre = "Fiksi Ilmiah"
    )

    override suspend fun categories(): AppResult<List<Category>> = AppResult.Success(
        listOf(
            Category("Populer", listOf(sintel, bigBuckBunny, tearsOfSteel)),
            Category("Animasi", listOf(sintel, bigBuckBunny)),
        )
    )
}

/**
 * Agregator katalog: network-first dari daftar [CatalogSource] berurutan,
 * jatuh ke cache Room bila semua sumber gagal, terakhir ke sumber contoh.
 * Hasil jaringan selalu ditulis ke cache agar kunjungan offline berikutnya utuh.
 */
@Singleton
class AggregatingCatalogRepository @Inject constructor(
    private val sources: List<@JvmSuppressWildcards CatalogSource>,
    private val cache: CachedTitleDao,
    private val dispatchers: DispatcherProvider,
) : CatalogRepository {

    override suspend fun categories(): List<Category> = withContext(dispatchers.io) {
        for (source in sources) {
            val result = source.categories()
            if (result is AppResult.Success && result.data.isNotEmpty()) {
                val now = System.currentTimeMillis()
                runCatching {
                    cache.upsertAll(result.data.flatMap { it.titles }.distinctBy { it.id }.map { it.toCachedEntity(now) })
                }
                return@withContext result.data
            }
        }
        val cached = runCatching { cache.all().map { it.toTitle() } }.getOrDefault(emptyList())
        if (cached.isNotEmpty()) return@withContext listOf(Category("Tersimpan", cached))
        emptyList()
    }

    override suspend fun title(id: String): Title? = withContext(dispatchers.io) {
        runCatching { cache.byId(id)?.toTitle() }.getOrNull()
            ?: categories().flatMap { it.titles }.firstOrNull { it.id == id }
    }
}

/** Kompatibilitas: repository contoh lama untuk preview/test sederhana. */
class FakeCatalogRepository(private val source: SampleCatalogSource = SampleCatalogSource()) : CatalogRepository {
    override suspend fun categories(): List<Category> = source.categories().getOrNull().orEmpty()
    override suspend fun title(id: String): Title? = allTitles().firstOrNull { it.id == id }
}
