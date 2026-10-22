package com.anibix.app.torrent

import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * HTTP server lokal dengan dukungan Range request supaya ExoPlayer bisa seek.
 * Data hanya dibaca setelah piece torrent-nya selesai diunduh.
 */
class TorrentStreamServer(
    port: Int,
    private val lookup: (String) -> StreamSource?
) : NanoHTTPD("127.0.0.1", port) {

    override fun serve(session: IHTTPSession): Response {
        val id = session.uri.removePrefix("/stream/")
        val src = lookup(id) ?: return newFixedLengthResponse(
            Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found"
        )
        val total = src.info.files().fileSize(src.fileIndex)
        if (total <= 0) return newFixedLengthResponse(
            Response.Status.NOT_FOUND, MIME_PLAINTEXT, "empty"
        )
        val range = session.headers["range"]

        var start = 0L
        var end = total - 1
        if (range != null && range.startsWith("bytes=")) {
            val parts = range.removePrefix("bytes=").split("-")
            start = (parts[0].toLongOrNull() ?: 0L).coerceIn(0L, total - 1)
            val requestedEnd = parts.getOrNull(1)?.toLongOrNull() ?: (total - 1)
            end = requestedEnd.coerceIn(start, total - 1)   // clamp: jangan lewati ukuran file
        }
        val length = end - start + 1
        val stream = PieceAwareStream(src, start, length)

        val resp = newFixedLengthResponse(
            if (range != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
            mimeFor(src.info.files().filePath(src.fileIndex)), stream, length
        )
        resp.addHeader("Accept-Ranges", "bytes")
        if (range != null) resp.addHeader("Content-Range", "bytes $start-$end/$total")
        return resp
    }

    private fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "mp4", "m4v" -> "video/mp4"
        else -> "video/mp4"
    }
}

private class PieceAwareStream(
    private val src: StreamSource,
    start: Long,
    private var remaining: Long
) : InputStream() {
    private var pos = start
    private val raf by lazy { RandomAccessFile(src.file, "r") }
    private val pieceLen = src.info.pieceLength().toLong()
    private val fileOffset = src.info.files().fileOffset(src.fileIndex)

    override fun read(): Int {
        val b = ByteArray(1)
        return if (read(b, 0, 1) == -1) -1 else b[0].toInt() and 0xff
    }

    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val abs = fileOffset + pos
        val piece = (abs / pieceLen).toInt()

        // Tunggu piece tersedia; prioritaskan piece yang sedang dibutuhkan player
        while (!src.handle.havePiece(piece)) {
            src.handle.setPieceDeadline(piece, 0)
            Thread.sleep(200) // TODO: timeout + cancel kalau client disconnect
        }
        val inPiece = pieceLen - (abs % pieceLen)
        val n = minOf(len.toLong(), remaining, inPiece).toInt()
        raf.seek(pos)
        val read = raf.read(buf, off, n)
        if (read > 0) { pos += read; remaining -= read }
        return read
    }

    override fun close() { runCatching { raf.close() } }
}
