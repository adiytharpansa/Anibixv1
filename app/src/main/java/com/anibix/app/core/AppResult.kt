package com.anibix.app.core

/**
 * Hasil operasi yang bisa gagal, dipakai di seluruh data/domain layer supaya
 * UI tidak pernah menerima exception mentah.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.data

    fun errorOrNull(): AppError? = (this as? Failure)?.error

    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }
}

/** Kategori error yang bisa ditampilkan ke pengguna dengan pesan yang layak. */
sealed class AppError(val userMessage: String) {
    data class Network(val cause: String? = null) : AppError("Tidak ada koneksi / jaringan bermasalah")
    data class Timeout(val cause: String? = null) : AppError("Waktu tunggu habis, coba lagi")
    data class Torrent(val cause: String? = null) : AppError("Torrent bermasalah: ${cause ?: "tidak dikenal"}")
    data class DiskFull(val cause: String? = null) : AppError("Penyimpanan tidak cukup")
    data class NotFound(val cause: String? = null) : AppError("Konten tidak ditemukan")
    data class Unknown(val cause: String? = null) : AppError("Terjadi kesalahan: ${cause ?: "tidak dikenal"}")
}

/** Jalankan blok suspend dan bungkus hasilnya; petakan exception umum ke [AppError]. */
suspend fun <T> safeCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: java.net.UnknownHostException) {
    AppResult.Failure(AppError.Network(e.message))
} catch (e: java.net.SocketTimeoutException) {
    AppResult.Failure(AppError.Timeout(e.message))
} catch (e: java.io.IOException) {
    AppResult.Failure(AppError.Network(e.message))
} catch (e: Exception) {
    AppResult.Failure(AppError.Unknown(e.message))
}
