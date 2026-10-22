# Anibix — kerangka app gaya Netflix (Kotlin + Compose + torrent)

Arsitektur: UI (Compose) -> MainViewModel -> TorrentEngine (jlibtorrent) -> TorrentStreamServer (NanoHTTPD, localhost) -> ExoPlayer.

Streaming: magnet -> fetch metadata -> pilih file video terbesar -> sequential download
-> HTTP lokal dengan Range -> ExoPlayer baca http://127.0.0.1:8888/stream/{id}.
Download: sama tanpa sequential, plus TorrentService (foreground) agar jalan di background.

TODO: hero banner, resume download setelah app mati (simpan resume data),
timeout/cancel di PieceAwareStream, MIME dinamis (mkv/webm), subtitle,
notifikasi progres, izin POST_NOTIFICATIONS, katalog API sungguhan,
cek versi jlibtorrent terbaru (API TorrentFlags/download bisa beda antar versi).
Gunakan hanya konten legal / yang kamu punya haknya.
