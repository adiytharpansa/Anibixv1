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
