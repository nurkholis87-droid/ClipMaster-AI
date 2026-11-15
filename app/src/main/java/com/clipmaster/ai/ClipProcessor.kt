package com.clipmaster.ai

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Activity-independent processing pipeline. It is created and owned by the
 * foreground service so the Activity can be destroyed without cancelling work.
 */
class ClipProcessor(
    private val appContext: Context,
    private val selectedDuration: Int,
    private val captionsEnabled: Boolean,
    private val onLog: (String) -> Unit,
    private val onStatus: (String, Boolean) -> Unit
) {
    private val sampleRate = 16000
    private val modelUrl =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"

    private fun log(message: String) = onLog(message)

    private fun getDurationMs(file: File): Long {
        val session = FFprobeKit.getMediaInformation(file.absolutePath)
        val seconds =
            session.mediaInformation?.duration?.toDoubleOrNull() ?: 0.0

        return if (seconds > 0.0) (seconds * 1000).toLong() else 0L
    }

    private fun extractPcm16k(input: File, pcm: File): Boolean {
        pcm.delete()
        log("Mengekstrak audio mono 16 kHz untuk Whisper")

        val command =
            "-y -i \"${input.absolutePath}\" -map 0:a:0 -vn -ac 1 -ar 16000 -c:a pcm_s16le -f s16le \"${pcm.absolutePath}\""

        val session = FFmpegKit.execute(command)

        log("Ekstraksi audio selesai. Return code: ${session.returnCode}")

        if (!ReturnCode.isSuccess(session.returnCode)) {
            log(session.allLogsAsString ?: "FFmpeg audio gagal")
            return false
        }

        return pcm.exists() && pcm.length() > 0
    }

    private data class Candidate(
        val startSeconds: Double,
        val audioScore: Double
    )

    private fun findAudioCandidates(pcm: File): List<Candidate> {
        val windowSeconds = 0.5
        val samplesPerWindow = (sampleRate * windowSeconds).toInt()
        val bytesPerWindow = samplesPerWindow * 2
        val rmsValues = ArrayList<Double>()

        log("Menganalisis intensitas audio 16 kHz")

        DataInputStream(FileInputStream(pcm)).use { input ->
            val buffer = ByteArray(bytesPerWindow)

            while (true) {
                var read = 0

                while (read < buffer.size) {
                    val n = input.read(buffer, read, buffer.size - read)
                    if (n < 0) break
                    read += n
                }

                if (read < buffer.size) break

                var sum = 0.0
                var p = 0

                while (p < buffer.size) {
                    val lo = buffer[p].toInt() and 0xff
                    val hi = buffer[p + 1].toInt()
                    val sample = (hi shl 8) or lo

                    sum += sample.toDouble() * sample
                    p += 2
                }

                rmsValues.add(
                    sqrt(sum / samplesPerWindow) / 32768.0
                )
            }
        }

        log("Jumlah jendela audio: ${rmsValues.size}")

        val clipWindows = (selectedDuration / windowSeconds).toInt()

        if (rmsValues.size < clipWindows) return emptyList()

        val prefix = DoubleArray(rmsValues.size + 1)

        for (i in rmsValues.indices) {
            prefix[i + 1] = prefix[i] + rmsValues[i]
        }

        val raw = ArrayList<Candidate>()
        val step = 2

        for (start in 0..(rmsValues.size - clipWindows) step step) {
            val end = start + clipWindows
            val average = (prefix[end] - prefix[start]) / clipWindows

            var active = 0

            for (i in start until end) {
                if (rmsValues[i] >= 0.012) active++
            }

            val activeRatio = active.toDouble() / clipWindows
            val score = average * 0.65 + activeRatio * 0.035

            raw.add(
                Candidate(
                    start * windowSeconds,
                    score
                )
            )
        }

        val sorted = raw.sortedByDescending { it.audioScore }
        val chosen = ArrayList<Candidate>()

        for (candidate in sorted) {
            if (
                chosen.none {
                    kotlin.math.abs(
                        it.startSeconds - candidate.startSeconds
                    ) < selectedDuration
                }
            ) {
                chosen.add(candidate)
                // Three passes keep long on-device inference bounded.
                if (chosen.size == 3) break
            }
        }

        log("Kandidat audio yang akan diperiksa: ${chosen.size} (maksimal 3 untuk menjaga memori HP)")
        log("Durasi setiap kandidat: $selectedDuration detik")

        return chosen
    }

    private fun ensureWhisperModel(): File {
        val model = File(appContext.filesDir, "ggml-base-q5_1.bin")

        if (model.exists() && model.length() > 10_000_000L) {
            log("Model Whisper sudah tersedia (${model.length()} bytes)")
            return model
        }

        log("Mengunduh model Whisper base multilingual (sekali saja)")

        val temp = File(appContext.filesDir, "ggml-base-q5_1.download")
        temp.delete()

        val connection =
            URL(modelUrl).openConnection() as HttpURLConnection

        connection.connectTimeout = 20000
        connection.readTimeout = 60000
        connection.instanceFollowRedirects = true

        try {
            connection.connect()

            if (connection.responseCode !in 200..299) {
                throw Exception(
                    "Unduhan model gagal: HTTP ${connection.responseCode}"
                )
            }

            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(1024 * 1024)

                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        if (temp.length() < 10_000_000L) {
            val size = temp.length()
            temp.delete()
            throw Exception("File model tidak lengkap ($size bytes)")
        }

        if (model.exists()) model.delete()

        if (!temp.renameTo(model)) {
            temp.copyTo(model, overwrite = true)
            temp.delete()
        }

        log("Model Whisper siap: ${model.length()} bytes")
        return model
    }

    private fun transcriptScore(text: String): Double {
        val normalized = text.lowercase(Locale("id", "ID"))
            .replace(Regex("[^\\p{L}\\p{N}? ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return -1000.0

        val words = normalized.split(" ").filter { it.isNotBlank() }

        val uniqueRatio =
            words.toSet().size.toDouble() / words.size.coerceAtLeast(1)

        val cues = listOf(
            "kenapa", "mengapa", "bagaimana", "gimana",
            "ternyata", "menurut", "karena", "solusi",
            "masalah", "setuju", "benar", "rahasia",
            "tips", "pengalaman", "pertama", "kedua",
            "penting", "artinya", "maksudnya", "contohnya",
            "jadi", "sebab", "akibat", "hasilnya", "kesimpulan"
        )

        val cueCount = cues.sumOf { cue ->
            Regex("\\b${Regex.escape(cue)}\\b")
                .findAll(normalized)
                .count()
        }

        val questionCount = text.count { it == '?' }

        val conversational = listOf(
            "apa", "siapa", "kapan", "dimana",
            "di mana", "kok", "lho"
        ).sumOf { cue ->
            Regex("\\b${Regex.escape(cue)}\\b")
                .findAll(normalized)
                .count()
        }

        val wordScore = min(words.size, 150) * 0.35
        val diversityScore = uniqueRatio * 12.0

        return wordScore +
            cueCount * 3.0 +
            questionCount * 4.0 +
            conversational * 1.5 +
            diversityScore
    }

    private data class TranscriptionResult(
        val startSeconds: Double,
        val timedTranscript: String
    )

    private fun transcribeCandidates(
        pcm: File,
        candidates: List<Candidate>
    ): TranscriptionResult {
        if (candidates.isEmpty()) {
            throw Exception("Tidak ada kandidat audio")
        }

        val model = ensureWhisperModel()
        log("Memuat model Whisper ke memori")

        val context = WhisperNative.initModel(model.absolutePath)
        if (context == 0L) throw Exception("Whisper gagal memuat model")

        var bestStart = candidates.first().startSeconds
        var bestScore = Double.NEGATIVE_INFINITY
        var bestTimedTranscript = ""

        try {
            RandomAccessFile(pcm, "r").use { file ->
                for ((index, candidate) in candidates.withIndex()) {
                    log("Transkripsi kandidat ${index + 1}/${candidates.size}")

                    val offset = (candidate.startSeconds * sampleRate * 2).toLong()
                    val byteCount = selectedDuration * sampleRate * 2
                    if (offset + byteCount > file.length()) continue

                    file.seek(offset)
                    val bytes = ByteArray(byteCount)
                    file.readFully(bytes)
                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    val audio = FloatArray(selectedDuration * sampleRate) {
                        buffer.short.toFloat() / 32768.0f
                    }

                    // One timestamped inference provides both candidate scoring and
                    // the exact transcript later burned into the selected clip.
                    val timed = WhisperNative.transcribeTimed(context, audio)
                    val transcript = timed.lineSequence()
                        .mapNotNull { line ->
                            val parts = line.split('\t', limit = 3)
                            if (parts.size == 3) parts[2] else null
                        }
                        .joinToString(" ")

                    val score = transcriptScore(transcript) + candidate.audioScore * 8.0
                    log("Teks ${index + 1}: ${transcript.replace("\n", " ").take(180)}")
                    log("Skor isi ${index + 1}: " +
                        String.format(Locale.US, "%.2f", score))

                    if (timed.isNotBlank() && score > bestScore) {
                        bestScore = score
                        bestStart = candidate.startSeconds
                        bestTimedTranscript = timed
                    }
                }
            }
        } finally {
            WhisperNative.freeModel(context)
        }

        if (bestTimedTranscript.isBlank()) {
            throw Exception("Whisper tidak menghasilkan transkrip bertimestamp")
        }

        log("Skor transkrip terbaik: " +
            String.format(Locale.US, "%.2f", bestScore))
        log("Transkrip kandidat terbaik akan dipakai ulang untuk caption (tanpa inferensi kedua)")

        return TranscriptionResult(bestStart, bestTimedTranscript)
    }

    private fun createCaptionAss(
        input: File,
        start: Double,
        duration: Double,
        cachedTimedTranscript: String?
    ): File? {
        var pcm: File? = null
        var context = 0L
        try {
            val timed = if (!cachedTimedTranscript.isNullOrBlank()) {
                log("Memakai ulang hasil transkripsi kandidat untuk caption")
                cachedTimedTranscript
            } else {
                // Short source videos do not need candidate ranking, so transcribe
                // their selected audio once here to obtain caption timestamps.
                pcm = File(appContext.cacheDir, "clipmaster_caption_audio.pcm")
                val ss = String.format(Locale.US, "%.3f", start)
                val tt = String.format(Locale.US, "%.3f", duration)
                val cmd = "-y -ss $ss -i \"${input.absolutePath}\" -t $tt -map 0:a:0 -vn -ac 1 -ar 16000 -c:a pcm_s16le -f s16le \"${pcm.absolutePath}\""
                val audioSession = FFmpegKit.execute(cmd)
                if (!ReturnCode.isSuccess(audioSession.returnCode) || !pcm.exists() || pcm.length() < 2L) {
                    throw Exception("Audio caption tidak tersedia: FFmpeg gagal mengekstrak audio (${audioSession.returnCode}). ${audioSession.allLogsAsString ?: ""}")
                }

                context = try {
                    WhisperNative.initModel(ensureWhisperModel().absolutePath)
                } catch (e: OutOfMemoryError) {
                    throw Exception("Memori tidak cukup untuk memuat model Whisper saat membuat caption")
                }
                if (context == 0L) {
                    throw Exception("Model Whisper gagal dimuat untuk membuat caption")
                }
                val bytes = pcm.readBytes()
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val audio = FloatArray(bytes.size / 2) { buffer.short.toFloat() / 32768.0f }
                WhisperNative.transcribeTimed(context, audio)
            }

            if (timed.isBlank()) throw Exception("Whisper menghasilkan transkrip kosong; caption tidak dapat dibuat")
            fun stamp(value: Double): String {
                val cs = (value.coerceAtLeast(0.0) * 100.0).toLong()
                return String.format(Locale.US, "%d:%02d:%02d.%02d",
                    cs / 360000, (cs % 360000) / 6000, (cs % 6000) / 100, cs % 100)
            }
            val events = StringBuilder()
            for (line in timed.lines()) {
                val p = line.split('\t', limit = 3)
                if (p.size != 3) continue
                val from = p[0].toDoubleOrNull() ?: continue
                val until = p[1].toDoubleOrNull() ?: continue
                val words = p[2].trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.isEmpty() || until <= from) continue
                val total = ((until - from) * 100).toInt().coerceAtLeast(1)
                val text = StringBuilder()
                words.forEachIndexed { i, raw ->
                    val word = raw.replace("{", "(").replace("}", ")").replace("\\", "")
                    if (i > 0 && i % 7 == 0) text.append("\\N") else if (i > 0) text.append(" ")
                    val a = i * total / words.size
                    val b = (i + 1) * total / words.size
                    text.append("{\\kf").append((b - a).coerceAtLeast(1)).append("}").append(word)
                }
                events.append("Dialogue: 0,").append(stamp(from)).append(',')
                    .append(stamp(until)).append(",Default,,0,0,0,,")
                    .append(text).append('\n')
            }
            if (events.isEmpty()) throw Exception("Tidak ada segmen bertimestamp valid untuk file caption ASS")
            val header = listOf(
                "[Script Info]", "ScriptType: v4.00+", "PlayResX: 1080", "PlayResY: 1920",
                "WrapStyle: 2", "ScaledBorderAndShadow: yes", "",
                "[V4+ Styles]",
                "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
                "Style: Default,Arial,58,&H0000FFFF,&H00FFFFFF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,4,1,2,70,70,100,1", "",
                "[Events]", "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
            ).joinToString("\n") + "\n"
            val ass = File(appContext.cacheDir, "clipmaster_captions.ass")
            ass.writeText(header + events.toString())
            log("Caption karaoke tersinkron dibuat")
            return ass
        } catch (e: OutOfMemoryError) {
            throw Exception("Memori tidak cukup saat membuat caption ASS", e)
        } catch (e: Exception) {
            throw Exception("Pembuatan caption gagal: ${e.message}", e)
        } finally {
            if (context != 0L) WhisperNative.freeModel(context)
            pcm?.delete()
        }
    }

    private fun exportClip(
        input: File,
        output: File,
        start: Double,
        duration: Double,
        cachedTimedTranscript: String?
    ): Boolean {
        val ss = String.format(Locale.US, "%.3f", start)
        val tt = String.format(Locale.US, "%.3f", duration)

        val captionFilter = if (captionsEnabled) {
            val ass = createCaptionAss(input, start, duration, cachedTimedTranscript)
            if (ass != null) {
                val escaped = ass.absolutePath.replace("\\", "\\\\").replace(":", "\\:")
                "-vf \"subtitles='$escaped'\" "
            } else ""
        } else ""

        val command =
            "-y -ss $ss -i \"${input.absolutePath}\" -t $tt " +
                captionFilter +
                "-map 0:v:0 -map 0:a? " +
                "-c:v libopenh264 -b:v 4000k -pix_fmt yuv420p " +
                "-c:a aac -b:a 128k -movflags +faststart " +
                "\"${output.absolutePath}\""

        if (captionsEnabled) log("Caption WAJIB disematkan ke MP4; ekspor dibatalkan jika subtitle gagal")
        log("Mengekspor MP4 berdurasi $duration detik")

        val session = FFmpegKit.execute(command)

        log("FFmpeg selesai. Return code: ${session.returnCode}")

        if (!ReturnCode.isSuccess(session.returnCode)) {
            log(session.allLogsAsString ?: "FFmpeg gagal")
            return false
        }

        return output.exists() && output.length() > 0
    }

    fun process(inputUri: Uri, outputUri: Uri): Boolean {
        val input = File(appContext.cacheDir, "clipmaster_input.mp4")
        val pcm = File(appContext.cacheDir, "clipmaster_audio_16k.pcm")
        val output = File(appContext.cacheDir, "clipmaster_output.mp4")
        var success = false

        try {
            input.delete()
            pcm.delete()
            output.delete()

            log("Menyalin video ke penyimpanan sementara")
            appContext.contentResolver.openInputStream(inputUri).use { source ->
                if (source == null) throw Exception("Tidak dapat membaca video")
                FileOutputStream(input).use { dest -> source.copyTo(dest) }
            }

            if (!input.exists() || input.length() == 0L) {
                throw Exception("Video sumber kosong")
            }
            log("Input siap: ${input.length()} bytes")

            val durationMs = getDurationMs(input)
            if (durationMs <= 0L) throw Exception("Durasi video tidak dapat dibaca")
            val duration = durationMs / 1000.0
            log("Durasi sumber: " + String.format(Locale.US, "%.1f", duration) + " detik")

            val clipDuration = min(selectedDuration.toDouble(), duration)
            var chosenStart = 0.0
            var selectedTimedTranscript: String? = null

            if (duration > selectedDuration) {
                if (!extractPcm16k(input, pcm)) throw Exception("Audio gagal diekstrak")
                val candidates = findAudioCandidates(pcm)
                if (candidates.isEmpty()) throw Exception("Tidak ditemukan kandidat audio")

                chosenStart = try {
                    val selection = transcribeCandidates(pcm, candidates)
                    selectedTimedTranscript = selection.timedTranscript
                    selection.startSeconds
                } catch (e: Exception) {
                    log("Whisper tidak tersedia: ${e.message}")
                    log("Fallback: memakai skor aktivitas audio")
                    candidates.first().startSeconds
                }
            } else {
                log("Video lebih pendek dari pilihan; memakai seluruh durasi")
            }

            log("Bagian terpilih mulai: " +
                String.format(Locale.US, "%.2f", chosenStart) + " detik")
            log("Durasi hasil: $clipDuration detik")

            if (!exportClip(input, output, chosenStart, clipDuration, selectedTimedTranscript)) {
                throw Exception("Ekspor FFmpeg gagal")
            }

            log("Menyimpan hasil video")
            appContext.contentResolver.openOutputStream(outputUri).use { dest ->
                if (dest == null) throw Exception("Lokasi penyimpanan tidak dapat dibuka")
                output.inputStream().use { source -> source.copyTo(dest) }
            }

            log("AUTO CLIP BERHASIL DISIMPAN")
            onStatus("Status: AUTO CLIP BERHASIL", true)
            success = true
        } catch (e: OutOfMemoryError) {
            log("MEMORI HP TIDAK CUKUP: proses dihentikan dengan aman. Coba video lebih pendek atau nonaktifkan caption.")
            onStatus("Status: MEMORI TIDAK CUKUP", false)
        } catch (e: Exception) {
            log("ERROR: ${e.message}")
            onStatus("Status: GAGAL", false)
        } finally {
            input.delete()
            pcm.delete()
            output.delete()
        }
        return success
    }
}
