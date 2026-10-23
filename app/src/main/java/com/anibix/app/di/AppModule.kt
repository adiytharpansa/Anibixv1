package com.anibix.app.di

import android.content.Context
import androidx.room.Room
import com.anibix.app.core.DefaultDispatcherProvider
import com.anibix.app.core.DispatcherProvider
import com.anibix.app.data.AggregatingCatalogRepository
import com.anibix.app.data.AniListCatalogSource
import com.anibix.app.data.CatalogRepository
import com.anibix.app.data.CatalogSource
import com.anibix.app.data.SampleCatalogSource
import com.anibix.app.data.ScreaperCatalogSource
import com.anibix.app.data.local.AnibixDatabase
import com.anibix.app.data.local.BookmarkDao
import com.anibix.app.data.local.CachedTitleDao
import com.anibix.app.data.local.DownloadQueueDao
import com.anibix.app.data.local.WatchHistoryDao
import com.anibix.app.data.remote.AniListApi
import com.anibix.app.torrent.LibTorrentEngine
import com.anibix.app.torrent.TorrentEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun dispatchers(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @Singleton
    fun torrentEngine(engine: LibTorrentEngine): TorrentEngine = engine

    // Urutan = prioritas: metadata AniList sebagai katalog utama, Screaper
    // sebagai pelengkap magnet per episode, contoh legal fallback terakhir.
    @Provides
    @Singleton
    fun catalogSources(
        anilist: AniListCatalogSource,
        screaper: ScreaperCatalogSource,
        sample: SampleCatalogSource,
    ): List<CatalogSource> = listOf(anilist, screaper, sample)

    @Provides
    @Singleton
    fun catalogRepository(impl: AggregatingCatalogRepository): CatalogRepository = impl
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AnibixDatabase =
        Room.databaseBuilder(context, AnibixDatabase::class.java, "anibix.db").build()

    @Provides
    fun cachedTitles(db: AnibixDatabase): CachedTitleDao = db.cachedTitles()

    @Provides
    fun watchHistory(db: AnibixDatabase): WatchHistoryDao = db.watchHistory()

    @Provides
    fun bookmarks(db: AnibixDatabase): BookmarkDao = db.bookmarks()

    @Provides
    fun downloadQueue(db: AnibixDatabase): DownloadQueueDao = db.downloadQueue()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun aniListApi(client: OkHttpClient, json: Json): AniListApi = Retrofit.Builder()
        .baseUrl("https://graphql.anilist.co/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AniListApi::class.java)
}
