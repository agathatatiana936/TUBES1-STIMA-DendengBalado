# Battlecode 2025 — DendengBalado

Repositori ini berisi implementasi bot untuk kompetisi **Battlecode 2025** (Java) sebagai Tugas Besar mata kuliah Strategi Algoritma (IF2211). Terdapat tiga bot yang dikembangkan dengan pendekatan algoritma greedy.

---

## Penjelasan Algoritma Greedy

### 1. `alternative_bot_1` — Greedy Berbasis Fase Waktu

Bot ini mengimplementasikan greedy sederhana dengan perilaku yang bergantung pada fase ronde permainan:

- **Soldier**: Pada fase awal (ronde ≤ 200), soldier bergerak menuju estimasi lokasi tower musuh menggunakan simetri peta, lalu menyerang tower musuh jika sudah dalam jangkauan. Pada fase tengah (200–1000), soldier mengejar tower musuh yang diketahui. Pada fase akhir (> 1000), soldier mencari ruin terdekat untuk dibangun menjadi tower.
- **Mopper**: Pada fase awal mengikuti perilaku soldier, pada fase tengah bergerak greedy menuju musuh terdekat (minimasi jarak), pada fase akhir mengikuti ally soldier terdekat lalu membersihkan cat musuh.
- **Tower**: Melakukan spawn unit secara greedy berdasarkan batasan resource (paint dan chips).

Prinsip greedy: **selalu ambil target dengan jarak terdekat** atau **serang tower musuh yang pertama kali terlihat** tanpa mempertimbangkan skenario jangka panjang.

---

### 2. `alternative_bot_2` — Greedy Berbasis Skor (Score-Based Greedy)

Bot ini menggunakan scoring function eksplisit di kelas `Greedy.java` untuk setiap keputusan:

- **Soldier**: Melakukan skor terhadap semua tile yang dapat diserang, memilih tile dengan skor tertinggi berdasarkan apakah tile itu: musuh, kosong, atau belum dikunjungi. Pergerakan dipilih greedy berdasarkan skor gabungan arah menuju target + bonus frontier + peluang cat lokal.
- **Mopper**: Mengevaluasi semua tile dengan skor (kedekatan, bonus lokasi musuh, bonus cat enemy) lalu memilih tile dengan skor tertinggi untuk dipukul. Jika tidak ada target, mop swing ke arah dengan paling banyak cat musuh.
- **Splasher**: Memilih lokasi splash dengan menghitung `splashValue` tiap tile (jumlah cat enemy + cat kosong dalam radius splash). Hanya menyerang jika nilai splash melebihi threshold.
- **Tower**: Spawn unit secara greedy berdasarkan rasio (1 mopper per 3 soldier, 1 splasher per 5 soldier) dengan threshold paint dan chips.

Prinsip greedy: **pilih aksi dengan skor utilitas tertinggi** pada setiap giliran tanpa backtracking.

---

### 3. `main_bot` — Greedy Multi-Fase dengan State Machine

Bot utama yang paling kompleks, menggabungkan greedy dengan state machine per unit:

- **Soldier**: Memiliki 7 state (EXPLORE, GOTO_RUIN, MARK, PAINT, COMPLETE, RETREAT, ATTACK). Transisi state dipilih secara greedy: pilih ruin terdekat yang kosong untuk dibangun tower, pilih jenis tower berdasarkan kebutuhan peta, serang tower musuh jika cat cukup. Setelah ronde 600, fokus beralih ke mode penyerangan.
- **Mopper**: Memilih arah gerak secara greedy dengan menghitung skor tiap arah (jumlah cat musuh di radius sekitar, keberadaan musuh, tile belum dikunjungi). Aksi serangan dipilih greedy: prioritas swing area jika banyak musuh, lalu pilih tile cat musuh dengan skor tertinggi.
- **Splasher**: Menghitung skor tiap lokasi splash (cat kosong +10, cat musuh +15, tile baru +12). Pilih lokasi splash dengan skor tertinggi. Pergerakan dipilih greedy berdasarkan jarak ke target eksplorasi + penalti untuk tile yang sudah dikunjungi.
- **Tower**: Fase 1 (ronde < 601) menggunakan bobot dinamis (W_SOLDIER, W_SPLASHER, W_MOPPER) yang disesuaikan jumlah tower ally. Fase 2 (≥ 601) membanjiri unit Splasher untuk menguasai peta. Upgrade tower dilakukan greedy saat chips cukup.

Prinsip greedy: **pilih aksi lokal optimal berdasarkan state saat ini** — ruin terdekat, skor aksi tertinggi, arah dengan paling banyak utilitas — tanpa pencarian global.

---

## Requirements

| Komponen | Versi Minimum |
|---|---|
| Java JDK | **21** |
| Gradle | sudah termasuk via Gradle Wrapper (tidak perlu install manual) |
| OS | Windows / Linux / macOS |

> Pastikan `JAVA_HOME` sudah mengarah ke JDK 21.

---

## Instalasi & Setup

1. **Clone repositori** (jika belum):
   ```bash
   git clone <url-repositori>
   cd TUBES1-STIMA-DendengBalado
   ```

2. **Verifikasi Java**:
   ```bash
   java -version
   # Output harus: java version "21.x.x" atau lebih baru
   ```

3. Tidak ada dependensi eksternal tambahan — Battlecode engine sudah terkonfigurasi di `build.gradle`.

---

## Compile & Build

### Windows (Command Prompt / PowerShell)
```bat
gradlew.bat build
```

### Linux / macOS
```bash
./gradlew build
```

### Perintah Lengkap

| Perintah | Fungsi |
|---|---|
| `gradlew build` | Kompilasi semua source player |
| `gradlew run` | Jalankan pertandingan sesuai konfigurasi `gradle.properties` |
| `gradlew update` | Update konfigurasi ke versi engine terbaru |
| `gradlew zipForSubmit` | Buat file `.zip` untuk submission |
| `gradlew tasks` | Lihat semua task Gradle yang tersedia |

### Konfigurasi Pertandingan

Edit file `gradle.properties` untuk mengatur bot yang bertanding dan peta:

```properties
teamA=main_bot          # bot tim A (package name di src/)
teamB=alternative_bot_2 # bot tim B
maps=DefaultSmall       # nama peta
```

---

## Struktur Direktori

```
src/
├── main_bot/          # Bot utama (greedy multi-fase + state machine)
├── alternative_bot_1/ # Bot alternatif 1 (greedy berbasis fase waktu)
└── alternative_bot_2/ # Bot alternatif 2 (greedy berbasis skor)
test/                  # Unit test
gradle.properties      # Konfigurasi pertandingan
build.gradle           # Build script Gradle
```

---

## Author

| Nama | NIM |
|---|---|
| \<Gabriella Botimada Lubis\> | \<13524006\> |
| \<Agatha Tatianingseto\> | \<13524008\> |
| \<Junior Natra Situmorang\> | \<13524055\> |

**Kelompok**: DendengBalado  
**Mata Kuliah**: IF2211 Strategi Algoritma  
**Institusi**: Institut Teknologi Bandung

If you are having any problems with the default client, please report to teh devs and
feel free to set the `compatibilityClient` configuration to `true` to download a different version of the client.
