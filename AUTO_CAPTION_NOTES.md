# ClipMaster AI – Auto Caption patch

Perubahan:
- Menambahkan native JNI `transcribeSrt()` pada Whisper.
- Whisper dijalankan dengan timestamp segmen untuk menghasilkan file SRT.
- Audio dari bagian video terpilih diekstrak ulang untuk menjaga timestamp caption relatif terhadap klip.
- FFmpeg membakar subtitle ke MP4 dengan teks tebal kuning, outline hitam, dan posisi tengah-bawah.

Catatan penting:
- Gaya yang diimplementasikan saat ini adalah caption per-segmen dengan tampilan karaoke (teks kuning tebal), bukan highlight kata aktif satu per satu.
- ZIP sumber yang diterima tidak menyertakan `third_party/whisper.cpp`, padahal `app/src/main/cpp/CMakeLists.txt` mewajibkan folder tersebut. Karena itu build Android belum dapat diverifikasi dari ZIP ini. Pastikan submodule/dependensi whisper.cpp tersedia pada project lokal.
- Build juga bergantung pada Android SDK/NDK dan FFmpegKit sesuai konfigurasi project.
- Jika filter `subtitles` tidak tersedia pada binary FFmpegKit yang dipakai, perlu memakai paket FFmpegKit yang menyertakan libass.
