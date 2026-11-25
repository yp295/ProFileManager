# ProFileManager

File manager Android offline, dual-pane (landscape) / tab satu panel (portrait).
Package: `com.yuda.filemanager` — Kotlin + ViewBinding + Coroutines.
compileSdk 34, minSdk 23, targetSdk 34, abi arm64-v8a + armeabi-v7a.

## Fitur v1.0.0 (kode ini)
- Dua panel berdampingan (landscape), tab Panel 1 / Panel 2 (portrait)
- Toolbar per panel: breadcrumb path, tombol sort, ganti list/grid
- Panel aktif ditandai warna latar, tap untuk ganti panel aktif
- Bottom action bar: Copy, Move, Delete, Rename, New, Select, More
- Navigation drawer (storage, bookmark, Tools)
- Izin All Files Access (MANAGE_EXTERNAL_STORAGE), Shizuku provider terdaftar
- Copy file antar panel di background (Coroutines) + progress bar
- Dependensi arsip (commons-compress, xz), web (nanohttpd), media (Media3 ExoPlayer) sudah dideklarasikan untuk pengembangan fitur lanjutan

Catatan jujur: daftar file (RecyclerView adapter), operasi Move/Delete/Rename, arsip, text editor, viewer/player, terminal, FTP, dan App Manager dari daftar spesifikasi belum diimplementasikan di v1 ini — kerangka UI + copy sudah jalan, sisanya pengembangan bertahap.

## Build & install
1. Build otomatis: push ke branch `main` -> GitHub Actions `Build ProFileManager APK` -> artifact `ProFileManager-debug-apk`.
2. Atau lokal: `./gradlew assembleDebug`, APK di `app/build/outputs/apk/debug/app-debug.apk`.
3. Install di POCO F4 (Android 13): instal APK, buka, izinkan "Akses semua file" saat diminta, lalu pakai.

## Perbaikan build vs spek awal
- Workflow: `android-actions/setup-android@v3` dihapus — action itu gagal karena paket `tools` sudah tidak ada. Diganti install SDK 34 langsung pakai `sdkmanager` (cara yang terbukti sukses di FastFileTransfer).
- Ditambah: `gradlew` + `gradle-wrapper.jar` resmi, `gradle.properties`, `proguard-rules.pro`, `themes.xml` / `strings.xml`.
