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

    /**
     * Sumber pelengkap (bawaan: false) tidak menyediakan kategori utama —
     * judulnya memperkaya judul utama yang id/anilistId-nya sama, dan yang
     * tidak berpadanan tampil sebagai kategori tambahan di akhir.
     */
    val isSupplemental: Boolean get() = false

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

/** Bisa di-stream + diunduh: punya magnet judul (film) atau magnet per episode (serial). */
private fun Title.isPlayable(): Boolean =
    magnet != null || episodes.any { it.magnet != null }

/**
 * Agregator katalog: network-first. Sumber utama pertama yang sukses menjadi
 * katalog dasar; sumber pelengkap ([CatalogSource.isSupplemental]) memperkaya
 * judul dasar dengan data tambahannya (mis. magnet per episode) dan judul
 * pelengkap yang tidak berpadanan tampil sebagai kategori "Bisa Diputar".
 * Semua sumber gagal -> jatuh ke cache Room. Hasil gabungan selalu ditulis
 * ke cache agar kunjungan offline berikutnya utuh.
 */
@Singleton
class AggregatingCatalogRepository @Inject constructor(
    private val sources: List<@JvmSuppressWildcards CatalogSource>,
    private val cache: CachedTitleDao,
    private val dispatchers: DispatcherProvider,
) : CatalogRepository {

    override suspend fun categories(): List<Category> = withContext(dispatchers.io) {
        var primary: List<Category>? = null
        val supplemental = mutableListOf<Title>()
        for (source in sources) {
            val result = source.categories()
            if (result is AppResult.Success && result.data.isNotEmpty()) {
                if (source.isSupplemental) {
                    supplemental += result.data.flatMap { it.titles }
                } else if (primary == null) {
                    primary = result.data
                }
            }
        }

        if (primary == null && supplemental.isEmpty()) {
            val cached = runCatching { cache.all().map { it.toTitle() } }.getOrDefault(emptyList())
            return@withContext if (cached.isNotEmpty()) listOf(Category("Tersimpan", cached)) else emptyList()
        }

        val base = primary.orEmpty()
        val primaryTitles = base.flatMap { it.titles }
        val knownIds = primaryTitles.map { it.id }.toSet()
        val knownAnilistIds = primaryTitles.mapNotNull { it.anilistId }.toSet()
        val byId = supplemental.associateBy { it.id }
        val byAnilistId = supplemental.filter { it.anilistId != null }.associateBy { it.anilistId }

        val enriched = base.map { category ->
            category.copy(titles = category.titles.map { title ->
                title.enrichedWith(byAnilistId[title.anilistId] ?: byId[title.id])
            })
        }
        val leftovers = supplemental.filter {
            it.id !in knownIds && (it.anilistId == null || it.anilistId !in knownAnilistIds)
        }.distinctBy { it.id }
        val merged = enriched + if (leftovers.isNotEmpty()) {
            listOf(Category("Bisa Diputar", leftovers))
        } else {
            emptyList()
        }

        val now = System.currentTimeMillis()
        runCatching {
            cache.upsertAll(merged.flatMap { it.titles }.distinctBy { it.id }.map { it.toCachedEntity(now) })
        }
        // Gerbang tampil (aturan user): yang tampil di aplikasi HANYA judul
        // yang benar-benar bisa di-stream dan diunduh (punya magnet judul
        // atau magnet per episode). Judul metadata saja tidak ditampilkan.
        merged.map { category ->
            category.copy(titles = category.titles.filter { it.isPlayable() })
        }.filter { it.titles.isNotEmpty() }
    }

    override suspend fun title(id: String): Title? = withContext(dispatchers.io) {
        // Katalog jaringan (sudah diperkaya) didahulukan; cache hanya cadangan
        // agar salinan cache yang belum diperkaya tidak menutupi data jaringan.
        categories().flatMap { it.titles }.firstOrNull { it.id == id }
            ?: runCatching { cache.byId(id)?.toTitle() }.getOrNull()
    }
}

/** Kompatibilitas: repository contoh lama untuk preview/test sederhana. */
class FakeCatalogRepository(private val source: SampleCatalogSource = SampleCatalogSource()) : CatalogRepository {
    override suspend fun categories(): List<Category> = source.categories().getOrNull().orEmpty()
    override suspend fun title(id: String): Title? = allTitles().firstOrNull { it.id == id }
}
