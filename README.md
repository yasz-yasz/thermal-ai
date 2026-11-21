# StrukAI Thermal

Aplikasi Android (Kotlin + Jetpack Compose) untuk mengonversi gambar screenshot transaksi (transfer bank, e-wallet, PPOB, token listrik, pulsa, pembayaran QRIS, dll.) menjadi struk thermal siap cetak menggunakan **AI Vision Multi-Provider** serta mendukung **edit struk sebelum cetak** dan **printer thermal Bluetooth 58mm / 80mm (ESC/POS)**.

## Fitur Utama

- **Konversi Screenshot ke Struk via AI Vision**:
  - Pilih gambar dari galeri atau langsung *Share* screenshot dari aplikasi lain ke **StrukAI Thermal**.
  - Ekstraksi otomatis header transaksi, tanggal/waktu, nomor referensi/SN, rincian key-value, dan total pembayaran.
- **Multi-Provider AI Vision (Mendukung Free Tier)**:
  - **Google Gemini** (default **Gemini 4 Hydrogen** `gemini-4-hydrogen` dengan `thinkingLevel: LOW` untuk OCR cepat, auto-fallback saat model usang, plus tombol *Cek Model Aktif* langsung dari API Google).
  - **OpenRouter** (model Vision gratis seperti `google/gemini-2.0-flash-exp:free`, `meta-llama/llama-3.2-11b-vision-instruct:free`, `qwen/qwen2.5-vl-72b-instruct:free`, atau model kustom).
  - **OpenAI / OpenAI-Compatible** (`gpt-4o-mini`, `gpt-4o`, dengan dukungan Base URL kustom).
- **Editor Struk Lengkap Sebelum Cetak**:
  - Mode **Form Terstruktur**: edit nama toko, alamat, judul transaksi, status, tanggal, no. referensi, nominal total, footer, serta tambah/hapus/ubah urutan baris detail.
  - Mode **Teks Bebas (Raw Editor)**: kustomisasi teks struk secara manual 100% bila diperlukan.
  - **Live Thermal Preview**: pratinjau tampilan kertas struk sesuai lebar 58mm (32 karakter) atau 80mm (48 karakter).
- **Cetak Thermal Bluetooth 58mm & 80mm**:
  - Format perintah ESC/POS native via Bluetooth Classic SPP.
  - Pilihan lebar kertas **58mm** dan **80mm**, opsi potong kertas (*auto-cut*), test print, serta salin/bagikan teks struk.
- **Profil Toko Otomatis**:
  - Simpan header toko, alamat/kontak, dan catatan footer agar otomatis menggantikan header bawaan screenshot.

## Tech Stack

- **Bahasa**: Kotlin 2.0.21
- **UI**: Jetpack Compose + Material 3
- **Networking**: OkHttp 4.12 + Gson 2.11
- **Async**: Kotlin Coroutines & StateFlow
- **Printer**: Android Bluetooth Classic SPP + ESC/POS Formatter
- **CI/CD**: GitHub Actions (otomatis build & upload artifact APK debug)

## Prasyarat

- Android Studio Ladybug (2024.2+) atau lebih baru
- JDK 17
- Android SDK 34 (Min SDK 26 / Android 8.0+)

## Cara Setup & Build

1. Buka folder proyek ini di **Android Studio**, atau upload langsung ke repository GitHub.
2. Karena biner `gradle-wrapper.jar` tidak disertakan pada repository awal, lakukan salah satu langkah berikut:
   - **Di Android Studio**: langsung lakukan *Sync Project with Gradle Files*, atau jalankan `gradle wrapper` di terminal lokal.
   - **Di GitHub Actions**: workflow `.github/workflows/android-ci.yml` sudah dikonfigurasi menggunakan `gradle/actions/setup-gradle@v4` (Gradle 8.7) sehingga otomatis menjalankan unit test dan menghasilkan file `app-debug.apk` di menu **Actions -> Artifacts**.
3. Masukkan API Key provider pilihan Anda (Gemini / OpenRouter / OpenAI) di menu **Pengaturan** di dalam aplikasi. API Key disimpan secara lokal di perangkat dan tidak pernah dimasukkan ke kode sumber.

## Struktur Proyek

```text
struk-ai-thermal/
├── .github/workflows/android-ci.yml
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/strukai/thermal/
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/
│       │   │   ├── model/
│       │   │   ├── network/
│       │   │   ├── printer/
│       │   │   └── ui/
│       │   └── res/
│       └── test/java/com/strukai/thermal/
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```
