# ProFileManager

File manager Android offline, dual-pane (landscape) / tab satu panel (portrait).
Package: `com.yuda.filemanager` — Kotlin + ViewBinding + Coroutines.
compileSdk 34, minSdk 24, targetSdk 34, abi arm64-v8a + armeabi-v7a.

## Fitur v1.1.0 (kode ini — fungsional)
- Dua panel berdampingan (landscape), tab Panel 1 / Panel 2 (portrait)
- Daftar file beneran: ikon per tipe, nama, ukuran, tanggal, folder di atas
- Navigasi folder (tap folder), back/forward, breadcrumb yang bisa diklik per segmen
- Toolbar per panel: breadcrumb, tombol sort (nama/ukuran/tanggal, naik/turun), ganti list/grid
- Panel aktif ditandai warna latar, tap untuk ganti panel aktif
- Multi-select (long-press), select all / batal pilih, tombol Back HP = batal pilih / kembali folder / tutup drawer
- Copy / Move antar panel (rekursif, folder ikut), Delete (konfirmasi), Rename, Folder Baru, File Baru, Properti, Refresh
- Buka file lewat aplikasi lain (FileProvider), drawer: Internal, Download, DCIM, Pictures, Documents, Music, Movies
- Banner izin "Akses semua file" + tombol Izinkan, Izin All Files Access (MANAGE_EXTERNAL_STORAGE), Shizuku provider terdaftar
- Operasi berat di background (Coroutines) + progress bar
- Dependensi arsip (commons-compress, xz), web (nanohttpd), media (Media3 ExoPlayer) sudah dideklarasikan untuk pengembangan fitur lanjutan

Belum ada di v1.1.0: arsip (buka/ekstrak/buat ZIP), text editor / hex editor, viewer & player built-in, terminal, FTP / web manager, app manager, MCP server. Itu pengembangan bertahap.

Catatan minSdk: Shizuku 13.1.5 butuh minSdk 24 (api) — minSdk 21 gagal, 23 gagal, 24 lolos.

## Build & install
1. Build otomatis: push ke branch `main` -> GitHub Actions `Build ProFileManager APK` -> artifact `ProFileManager-debug-apk`.
2. Atau lokal: `./gradlew assembleDebug`, APK di `app/build/outputs/apk/debug/app-debug.apk`.
3. Install di POCO F4 (Android 13): instal APK, buka, izinkan "Akses semua file" saat diminta, lalu pakai.

## Perbaikan build vs spek awal
- Workflow: `android-actions/setup-android@v3` dihapus — action itu gagal karena paket `tools` sudah tidak ada. Diganti install SDK 34 langsung pakai `sdkmanager` (cara yang terbukti sukses di FastFileTransfer).
- Ditambah: `gradlew` + `gradle-wrapper.jar` resmi, `gradle.properties`, `proguard-rules.pro`, `themes.xml` / `strings.xml`.
