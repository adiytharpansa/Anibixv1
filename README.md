# Anibix — aplikasi streaming anime gaya Netflix (Kotlin + Compose + torrent)

Arsitektur: Clean Architecture + MVVM + UDF.
UI (Compose) → MainViewModel (StateFlow/UiState) → Repository (network-first,
cache Room) → sumber data (AniList API / ekstensi / contoh) dan TorrentEngine
(jlibtorrent) → TorrentStreamServer (NanoHTTPD, localhost) → Media3/ExoPlayer.

## Lapisan

- `core/` — `AppResult<T>` + `AppError` (pembungkus error seragam), `DispatcherProvider`.
- `data/` — model `Title`/`Episode`/`Category`; `CatalogSource` yang bisa
  dipasang-copot (`AniListCatalogSource` = metadata, `SampleCatalogSource` =
  contoh legal Blender, `ScreaperCatalogSource` = pelengkap magnet);
  `AggregatingCatalogRepository` (network-first → tulis cache Room →
  fallback cache → fallback contoh). Sumber pelengkap (`isSupplemental`)
  memperkaya judul utama ber-anilistId sama — untuk Screaper: magnet per
  episode dari katalog screaperv1 (AniList + Nyaa.si) — dan judul pelengkap
  tanpa padanan tampil sebagai kategori "Bisa Diputar".
- `data/local/` — Room: cache katalog, riwayat tonton (Lanjutkan Menonton),
  bookmark (Daftarku), antrean unduhan persisten.
- `data/remote/` — Retrofit + OkHttp + kotlinx.serialization ke AniList
  GraphQL; `ScreaperClient` membaca `catalog.json` screaperv1 lewat
  cermin jsDelivr (3 pintu).
- `data/settings/` — DataStore: pengaturan torrent & pemutar.
- `torrent/` — `TorrentEngine` (interface) + `LibTorrentEngine` (jlibtorrent):
  sequential download, SettingsPack (DHT, LSD, UPnP, NAT-PMP, batas bandwidth,
  batas koneksi), cek ruang disk, server HTTP lokal dengan Range + MIME dinamis.
- `di/` — modul Hilt (app, database, network).
- `ui/` — Compose Material 3: Beranda (hero, baris kategori, Lanjutkan
  Menonton, skeleton shimmer, state error+retry), Grid + cari, Detail
  (episode grid, bookmark, unduh), Pemutar, Unduhan, Pengaturan.

## Pemutar (Media3)

Kontrol kustom: putar/jeda, seek bar, kecepatan 0.5x–2.0x, pilihan trek
audio & subtitle bawaan, subtitle sideload SRT/ASS/VTT + penyesuaian delay
(SRT), gaya subtitle (ukuran/warna/latar dari Pengaturan), ketuk-ganda
±10 dtk, geser vertikal kecerahan (kiri) / volume (kanan), PiP, mode sinema
landscape imersif, simpan posisi otomatis, hitung mundur episode berikutnya.

Streaming: magnet → ambil metadata → pilih berkas video terbesar →
sequential download → HTTP lokal Range → ExoPlayer `http://127.0.0.1:8888/stream/{id}`.
Unduhan: sama tanpa sequential, plus TorrentService (foreground) dan antrean
persisten Room yang dilanjutkan saat app dibuka lagi.

## Uji

`app/src/test/` — unit test untuk repository agregator (fallback cache),
model (playlist/magnet per episode), dan penggeser subtitle.

Gunakan hanya konten legal / yang kamu punya haknya.
Magnet untuk judul AniList belum tersedia (metadata saja) — pasang
`CatalogSource` ekstensi milikmu untuk menyediakannya.
