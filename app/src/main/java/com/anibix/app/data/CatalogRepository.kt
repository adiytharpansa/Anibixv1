package com.anibix.app.data

interface CatalogRepository {
    suspend fun categories(): List<Category>
    suspend fun title(id: String): Title?

    /** Semua judul tanpa duplikat — dipakai layar grid. */
    suspend fun allTitles(): List<Title> = categories().flatMap { it.titles }.distinctBy { it.id }
}

/**
 * Data contoh. Ganti dengan API katalog milikmu (Retrofit/Ktor).
 * Gunakan konten legal: film open-source (Blender), domain publik, atau
 * konten yang kamu punya haknya.
 */
class FakeCatalogRepository : CatalogRepository {
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

    override suspend fun categories() = listOf(
        Category("Populer", listOf(sintel, bigBuckBunny, tearsOfSteel)),
        Category("Animasi", listOf(sintel, bigBuckBunny))
    )

    override suspend fun title(id: String) = allTitles().firstOrNull { it.id == id }

}
