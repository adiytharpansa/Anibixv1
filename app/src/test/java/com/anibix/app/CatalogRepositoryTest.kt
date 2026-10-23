package com.anibix.app

import com.anibix.app.core.AppError
import com.anibix.app.core.AppResult
import com.anibix.app.core.DispatcherProvider
import com.anibix.app.data.AggregatingCatalogRepository
import com.anibix.app.data.CatalogSource
import com.anibix.app.data.Category
import com.anibix.app.data.Title
import com.anibix.app.data.local.CachedTitleDao
import com.anibix.app.data.local.CachedTitleEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCacheDao : CachedTitleDao {
    val stored = mutableListOf<CachedTitleEntity>()
    override suspend fun all(): List<CachedTitleEntity> = stored.toList()
    override suspend fun byId(id: String): CachedTitleEntity? = stored.firstOrNull { it.id == id }
    override suspend fun upsertAll(items: List<CachedTitleEntity>) {
        items.forEach { item ->
            stored.removeAll { it.id == item.id }
            stored.add(item)
        }
    }
    override suspend fun clear() = stored.clear()
}

private fun sourceOf(result: AppResult<List<Category>>) = object : CatalogSource {
    override val name = "fake"
    override suspend fun categories(): AppResult<List<Category>> = result
}

private fun supplementalOf(result: AppResult<List<Category>>) = object : CatalogSource {
    override val name = "fake-pelengkap"
    override val isSupplemental = true
    override suspend fun categories(): AppResult<List<Category>> = result
}

private val sampleTitle = Title(id = "x", name = "X", synopsis = "", posterUrl = "", magnet = "magnet:?xt=urn:btih:abc")

class CatalogRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }

    @Test
    fun `sumber pertama sukses dipakai dan ditulis ke cache`() = runTest(dispatcher) {
        val cache = FakeCacheDao()
        val repo = AggregatingCatalogRepository(
            listOf(sourceOf(AppResult.Success(listOf(Category("Tren", listOf(sampleTitle)))))),
            cache, dispatchers
        )
        val cats = repo.categories()
        assertEquals(1, cats.size)
        assertEquals("x", cats[0].titles[0].id)
        assertTrue(cache.stored.any { it.id == "x" })
    }

    @Test
    fun `semua sumber gagal jatuh ke cache`() = runTest(dispatcher) {
        val cache = FakeCacheDao()
        cache.stored.add(
            CachedTitleEntity("c1", "Cache", "", "", "", null, 2020, "", 0.0, null, 1L)
        )
        val repo = AggregatingCatalogRepository(
            listOf(sourceOf(AppResult.Failure(AppError.Network()))),
            cache, dispatchers
        )
        val cats = repo.categories()
        assertEquals(listOf("Tersimpan"), cats.map { it.name })
        assertEquals("c1", cats[0].titles[0].id)
    }

    @Test
    fun `sumber pelengkap memperkaya magnet judul utama dan sisa tampil sebagai kategori`() = runTest(dispatcher) {
        val cache = FakeCacheDao()
        val utama = Title(
            id = "anilist-7", name = "Tujuh", synopsis = "s", posterUrl = "p",
            anilistId = 7,
            episodes = listOf(com.anibix.app.data.Episode(1), com.anibix.app.data.Episode(2)),
        )
        val pelengkap = utama.copy(
            episodes = listOf(com.anibix.app.data.Episode(1, magnet = "m1")),
        )
        val sisa = Title(id = "anilist-9", name = "Sembilan", synopsis = "", posterUrl = "", magnet = "m9", anilistId = 9)
        val repo = AggregatingCatalogRepository(
            listOf(
                sourceOf(AppResult.Success(listOf(Category("Tren", listOf(utama))))),
                supplementalOf(AppResult.Success(listOf(Category("Bisa Diputar", listOf(pelengkap, sisa))))),
            ),
            cache, dispatchers
        )
        val cats = repo.categories()
        assertEquals(listOf("Tren", "Bisa Diputar"), cats.map { it.name })
        assertEquals("m1", cats[0].titles[0].episodes[0].magnet)
        assertEquals(null, cats[0].titles[0].episodes[1].magnet)
        assertEquals("anilist-9", cats[1].titles[0].id)
    }

    @Test
    fun `playlist dan magnetFor pada model`() {
        val film = Title(id = "f", name = "F", synopsis = "", posterUrl = "", magnet = "m1")
        assertEquals(1, film.playlist().size)
        assertEquals("m1", film.magnetFor(film.playlist()[0]))
        val series = film.copy(episodes = listOf(
            com.anibix.app.data.Episode(1),
            com.anibix.app.data.Episode(2, magnet = "m2"),
        ))
        assertEquals(2, series.playlist().size)
        assertEquals("m2", series.magnetFor(series.playlist()[1]))
        assertEquals("m1", series.magnetFor(series.playlist()[0]))
    }
}
