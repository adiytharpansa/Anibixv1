package com.anibix.app.data

/** Satu episode dari sebuah judul serial; film tunggal memakai satu episode implisit. */
data class Episode(
    val number: Int,
    val name: String = "Episode $number",
    /** Magnet khusus episode ini; null = pakai magnet judul (film/konten satu berkas). */
    val magnet: String? = null,
)

data class Title(
    val id: String,
    val name: String,
    val synopsis: String,
    val posterUrl: String,
    val backdropUrl: String = posterUrl,
    val magnet: String? = null,          // sumber torrent (magnet URI); null = metadata saja
    val year: Int = 0,
    val genre: String = "",
    val rating: Double = 0.0,            // 0..10 (AniList averageScore / 10)
    val episodes: List<Episode> = emptyList(),
    val anilistId: Int? = null,
) {
    /** Daftar putar efektif: episode eksplisit, atau satu "episode" untuk film. */
    fun playlist(): List<Episode> =
        if (episodes.isNotEmpty()) episodes else listOf(Episode(number = 1, name = name))

    /** Magnet untuk sebuah episode (jatuh ke magnet judul bila episode tak punya sendiri). */
    fun magnetFor(ep: Episode): String? = ep.magnet ?: magnet

    /**
     * Gabungkan data dari sumber pelengkap (magnet per episode, magnet judul,
     * poster/sinopsis cadangan) tanpa menimpa data yang sudah ada di judul ini.
     */
    fun enrichedWith(other: Title?): Title {
        if (other == null) return this
        val otherByNumber = other.episodes.associateBy { it.number }
        val mergedEpisodes = when {
            episodes.isEmpty() -> other.episodes
            else -> episodes.map { ep ->
                val merged = ep.magnet ?: otherByNumber[ep.number]?.magnet
                if (merged != null && merged != ep.magnet) ep.copy(magnet = merged) else ep
            }
        }
        return copy(
            magnet = magnet ?: other.magnet,
            episodes = mergedEpisodes,
            posterUrl = posterUrl.ifBlank { other.posterUrl },
            backdropUrl = backdropUrl.ifBlank { other.backdropUrl },
            synopsis = synopsis.ifBlank { other.synopsis },
        )
    }
}

data class Category(val name: String, val titles: List<Title>)
