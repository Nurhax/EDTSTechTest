# Klik Indomaret – Automation "Add to Cart" (Selenium Java)

Automation web untuk [Klik Indomaret](https://www.klikindomaret.com) **tanpa login**, menggunakan **Selenium WebDriver murni** (tanpa TestNG, JUnit, ataupun Cucumber). Script menjalankan alur dari beranda sampai produk Madu berhasil dimasukkan ke keranjang.

---

## 1. Skenario

| No | Langkah | Method | Expected Results |
|----|---------|--------|-----------------------|
| 1 | Akses `https://www.klikindomaret.com` | `step1OpenHomePage()` | Beranda terbuka, judul halaman memuat "Indomaret" |
| 2 | Pilih meta kategori **Makanan** | `step2SelectMetaCategory()` | Menu/submenu kategori Makanan tampil |
| 3 | Pilih kategori **Madu** | `step3SelectCategory()` | Halaman daftar produk Madu tampil |
| 4 | Pilih salah satu produk Madu | `step4SelectProduct()` | Produk dipilih (default: urutan pertama) |
| 5 | Masuk ke halaman detail produk | `step5VerifyProductDetailPage()` | URL memuat `/product/` dan judul produk tampil |
| 6 | Klik tombol **+ Keranjang** | `step6ClickAddToCart()` | Notifikasi sukses muncul / produk masuk keranjang |

---

## 2. Prasyarat

- **JDK 17** atau lebih baru
- **Apache Maven 3.8+**
- **Google Chrome** terbaru (chromedriver diunduh otomatis oleh *Selenium Manager* bawaan Selenium 4.6+, tidak perlu setup manual)
- Koneksi internet

---

## 3. Struktur Proyek

```
klikindomaret-automation/
├── pom.xml
├── README.md
├── screenshots/                      # dibuat otomatis saat script dijalankan
└── src/main/java/com/example/klikindomaret/
    └── KlikIndomaretAddToCart.java   # seluruh automation (1 file)
```

---

## 4. Cara Menjalankan

```bash
# Jalankan dengan Chrome tampil (disarankan untuk pertama kali)
mvn compile exec:java

# Pilih produk ke-3 di daftar (index mulai dari 0)
mvn compile exec:java -Dproduct.index=2

# Mode headless
mvn compile exec:java -Dheadless=true
```

Opsi dibaca sebagai *system property*:

| Property | Default | Keterangan |
|----------|---------|------------|
| `headless` | `false` | `true` untuk menjalankan Chrome tanpa tampilan |
| `product.index` | `0` | Urutan produk pada daftar Madu yang dipilih (mulai dari 0). Jika melebihi jumlah produk, otomatis memakai produk terakhir |
| `pause.seconds` | `5` | Jeda di akhir sebelum browser ditutup, agar hasil sempat dilihat |

Screenshot tiap langkah disimpan di folder `screenshots/` dengan format `yyyyMMdd_HHmmss_<langkah>.png`. Jika terjadi error, screenshot `ERROR` otomatis dibuat.

---

## 5. Cara Kerja

### 5.1 Strategi locator

Karena Klik Indomaret adalah aplikasi SPA yang class CSS-nya bisa berubah sewaktu-waktu, script memakai locator yang **berbasis teks yang terlihat pengguna**, bukan class acak:

- **Menu & kategori** (`Kategori`, `Makanan`, `Madu`) → XPath `exactText(...)`: mencocokkan teks persis, tidak peka huruf besar/kecil, dan spasi dinormalisasi.
- **Produk** → `a[href*='/product/']`, di-*dedupe* berdasarkan `href` karena satu kartu produk bisa punya beberapa link (gambar + judul).
- **Tombol keranjang** → elemen `button`/`role=button` yang mengandung `+` **dan** `Keranjang`. Syarat `+` mencegah salah klik ikon "Keranjang" di header.
- **Notifikasi sukses** → elemen yang teksnya mengandung `berhasil` atau `ditambahkan ke keranjang`.

Seluruh locator utama dikumpulkan di bagian `LOCATOR` pada awal kelas sehingga mudah diubah.

### 5.2 Sinkronisasi (wait)

- Hanya memakai **explicit wait** (`WebDriverWait`), tanpa implicit wait dan tanpa `Thread.sleep` di tengah alur.
- Hanya elemen yang benar-benar **terlihat** (`isDisplayed`) yang dipakai.
- Timeout default 20 detik, timeout pendek 4 detik untuk pengecekan opsional (misalnya apakah submenu muncul).

### 5.3 Ketahanan (resilience)

| Masalah | Penanganan |
|---------|------------|
| Submenu kategori tidak muncul saat hover | Fallback: klik "Makanan" untuk masuk ke halaman meta kategori, lalu pilih "Madu" |
| Klik terhalang overlay/popup | Fallback ke JavaScript click |
| Elemen *stale* karena re-render SPA | Pencarian ulang elemen, maksimal 3 percobaan |
| Popup umum (`Tutup`, `Nanti saja`, `Close`, `Tolak`) | Ditutup otomatis secara *best-effort* |
| Halaman tidak sesuai (mis. diblokir bot detection) | Gagal lebih awal dengan pesan yang jelas |
| Error di langkah mana pun | Screenshot `ERROR` + pesan di console |

---

## 6. Contoh Output Console

```
[10:21:03] Langkah 1: membuka https://www.klikindomaret.com
[10:21:06] Judul halaman: ...
[10:21:07] Langkah 2: memilih meta kategori 'Makanan'
[10:21:09] Langkah 3: memilih kategori 'Madu'
[10:21:12] Langkah 4: memilih produk #0 dari 24 produk -> https://www.klikindomaret.com/product/...
[10:21:15] Langkah 5: halaman detail produk -> '...'
[10:21:16] Langkah 6: klik tombol '+ Keranjang'
[10:21:18] Konfirmasi dari situs: ...
[10:21:18] SELESAI: seluruh langkah dijalankan.
```

---

## 7. Troubleshooting

**Beranda tidak terbuka / muncul pesan "Kemungkinan diblokir bot detection"**
Situs memakai proteksi anti-bot. Hal yang bisa dicoba:
- Jalankan **tanpa** `-Dheadless=true` (mode headless lebih mudah terdeteksi).
- Pastikan Chrome selalu versi terbaru.
- Hindari menjalankan berulang kali dalam waktu singkat dari IP yang sama.

**`NoSuchElementException: Elemen tidak ditemukan/terlihat: ...`**
Teks atau struktur halaman berubah. Buka halaman di Chrome → klik kanan elemen → *Inspect*, lalu sesuaikan locator di bagian `LOCATOR` atau konstanta `META_CATEGORY` / `CATEGORY`.

**Tombol diklik tetapi muncul peringatan "notifikasi sukses tidak terdeteksi"**
Periksa screenshot `06-setelah-klik-keranjang`. Kemungkinan penyebab:
- Situs meminta pemilihan **lokasi/toko** sebelum produk bisa ditambahkan. Tambahkan langkah pemilihan lokasi sebelum Langkah 6.
- Teks notifikasi berbeda dari `berhasil` / `ditambahkan ke keranjang`. Sesuaikan `SUCCESS_INDICATOR`.
- Produk sedang tidak tersedia/stok habis. Coba `-Dproduct.index=1`.

**Tombol "+ Keranjang" yang terklik bukan milik produk utama**
Halaman detail bisa memuat produk rekomendasi yang juga punya tombol serupa. Script mengambil elemen pertama yang terlihat; jika perlu, persempit locator dengan container produk utama.

**Chromedriver gagal diunduh**
Selenium Manager membutuhkan akses internet. Jika berada di jaringan terbatas, unduh chromedriver yang sesuai versi Chrome secara manual dan set `-Dwebdriver.chrome.driver=/path/ke/chromedriver`.

---

## 8. Catatan Penting

- Locator ditulis tanpa akses langsung ke DOM situs yang sedang aktif, sehingga **pada eksekusi pertama kemungkinan perlu penyesuaian kecil**. Itulah alasan semua locator dipusatkan di satu tempat.
- Gunakan secukupnya dan patuhi Syarat & Ketentuan Klik Indomaret. Script ini hanya menambahkan satu produk ke keranjang dan tidak melakukan checkout.
- Karena tanpa login, keranjang bersifat sesi *guest* dan bisa hilang saat browser ditutup.

---

## 9. Pengembangan Lanjutan

- Refactor ke **Page Object Model** (`HomePage`, `CategoryPage`, `ProductDetailPage`) agar lebih mudah dirawat.
- Tambahkan assertion jumlah item pada ikon keranjang di header.
- Bungkus dengan JUnit 5 / TestNG bila ingin dijalankan di CI dengan laporan test.
- Tambahkan dukungan browser lain (Firefox/Edge) lewat `WebDriverFactory`.
