package com.clipmaster.ai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private var clipSeconds = 60
    private var captionsEnabled = true
    private val sampleRate = 16000

    private val durations = listOf(30, 45, 60, 90)

    private val modelUrl =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"

    private var selectedUri: Uri? = null
    private var selectedName = "Belum ada video dipilih"

    private lateinit var videoInfo: TextView
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var pickButton: Button
    private lateinit var createButton: Button
    private lateinit var durationSpinner: Spinner

    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    selectedUri = uri
                    selectedName = getFileName(uri)
                    videoInfo.text = "Video: $selectedName\nDurasi: ${getVideoDuration(uri)}"
                    addLog("Video dipilih: $selectedName")
                }
            }
        }

    private val outputPicker =
        registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            if (uri != null) startProcessing(uri)
            else addLog("Penyimpanan dibatalkan")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        addLog("ClipMaster AI 3.1.0 siap")
        addLog("MODE: AUTO PODCAST HIGHLIGHT")
        addLog("Mesin transkripsi: Whisper offline, bahasa Indonesia")
        addLog("Durasi default: 60 detik")
        addLog("Pilihan durasi: 30, 45, 60, 90 detik")
    }

    private fun buildInterface() {
        val scroll = ScrollView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(android.graphics.Color.rgb(12, 18, 32))
        }

        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "ClipMaster AI"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        })

        root.addView(TextView(this).apply {
            text = "OFFLINE WHISPER • SMART HIGHLIGHT • V3.1"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, 8, 0, 24)
        })

        pickButton = Button(this).apply {
            text = "PILIH VIDEO"
            setOnClickListener { openVideoPicker() }
        }
        root.addView(pickButton, buttonParams())

        videoInfo = TextView(this).apply {
            text = "Belum ada video dipilih"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(8, 18, 8, 18)
        }
        root.addView(videoInfo)

        root.addView(TextView(this).apply {
            text = "PILIH DURASI AUTO CLIP"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 14, 0, 8)
        })

        durationSpinner = Spinner(this)

        val durationAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            durations.map { "$it detik" }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        durationSpinner.adapter = durationAdapter
        durationSpinner.setSelection(durations.indexOf(60))

        durationSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    clipSeconds = durations[position]
                    createButton.text = "BUAT AUTO CLIP $clipSeconds DETIK"
                    addLog("Durasi klip dipilih: $clipSeconds detik")
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        root.addView(durationSpinner, buttonParams())

        root.addView(Switch(this).apply {
            text = "CAPTION OTOMATIS • KARAOKE DINAMIS"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            isChecked = true
            setPadding(8, 18, 8, 18)
            setOnCheckedChangeListener { _, checked -> captionsEnabled = checked }
        }, buttonParams())

        root.addView(TextView(this).apply {
            text = "AUTO PODCAST HIGHLIGHT"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 16, 0, 8)
        })

        root.addView(TextView(this).apply {
            text = "ClipMaster mencari beberapa bagian percakapan, mengubahnya menjadi teks bahasa Indonesia, lalu memberi skor pada kalimat, pertanyaan, dan alur percakapan. Hasil berupa satu video MP4 sesuai durasi pilihan."
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(190, 205, 225))
            setPadding(14, 14, 14, 14)
            setBackgroundColor(android.graphics.Color.rgb(20, 29, 45))
        })

        createButton = Button(this).apply {
            text = "BUAT AUTO CLIP 60 DETIK"
            setOnClickListener { requestOutputFile() }
        }

        val cp = buttonParams()
        cp.topMargin = 20
        root.addView(createButton, cp)

        statusText = TextView(this).apply {
            text = "Status: Siap"
            textSize = 14f
            setTextColor(android.graphics.Color.GREEN)
            setPadding(4, 20, 4, 12)
        }
        root.addView(statusText)

        root.addView(TextView(this).apply {
            text = "DIAGNOSTIC LOG"
            textSize = 15f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 12, 0, 8)
        })

        logText = TextView(this).apply {
            text = ""
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(android.graphics.Color.rgb(120, 230, 160))
            setPadding(12, 12, 12, 12)
            setBackgroundColor(android.graphics.Color.rgb(20, 29, 45))
            setTextIsSelectable(true)
        }

        root.addView(
            logText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                380
            )
        )

        root.addView(Button(this).apply {
            text = "BERSIHKAN LOG"
            setOnClickListener {
                logText.text = ""
                addLog("Log dibersihkan")
            }
        }, buttonParams())

        setContentView(scroll)
    }

    private fun buttonParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        videoPicker.launch(intent)
    }

    private fun getFileName(uri: Uri): String {
        var name = "video.mp4"

        contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val i = cursor.getColumnIndex(
                    android.provider.OpenableColumns.DISPLAY_NAME
                )
                if (i >= 0) name = cursor.getString(i) ?: name
            }
        }

        return name
    }

    private fun getVideoDuration(uri: Uri): String {
        val retriever = android.media.MediaMetadataRetriever()

        return try {
            retriever.setDataSource(this, uri)

            val ms = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            if (ms <= 0) {
                "Akan diperiksa saat proses"
            } else {
                "${ms / 60000} menit ${(ms / 1000) % 60} detik"
            }
        } catch (_: Exception) {
            "Akan diperiksa saat proses"
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun requestOutputFile() {
        if (selectedUri == null) {
            Toast.makeText(
                this,
                "Pilih video terlebih dahulu",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        outputPicker.launch("ClipMaster_PodcastHighlight_${clipSeconds}s.mp4")
    }

    private fun addLog(message: String) {
        runOnUiThread {
            if (!::logText.isInitialized) return@runOnUiThread

            val time = java.text.SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
            ).format(java.util.Date())

            logText.append("[$time] $message\n")
        }
    }

    private fun updateStatus(message: String, success: Boolean) {
        runOnUiThread {
            if (!::statusText.isInitialized) return@runOnUiThread

            statusText.text = message
            statusText.setTextColor(
                if (success) android.graphics.Color.GREEN
                else android.graphics.Color.RED
            )
        }
    }

    private fun getDurationMs(file: File): Long {
        val session = FFprobeKit.getMediaInformation(file.absolutePath)
        val seconds =
            session.mediaInformation?.duration?.toDoubleOrNull() ?: 0.0

        return if (seconds > 0.0) (seconds * 1000).toLong() else 0L
    }

    private fun extractPcm16k(input: File, pcm: File): Boolean {
        pcm.delete()
        addLog("Mengekstrak audio mono 16 kHz untuk Whisper")

        val command =
            "-y -i \"${input.absolutePath}\" -map 0:a:0 -vn -ac 1 -ar 16000 -c:a pcm_s16le -f s16le \"${pcm.absolutePath}\""

        val session = FFmpegKit.execute(command)

        addLog("Ekstraksi audio selesai. Return code: ${session.returnCode}")

        if (!ReturnCode.isSuccess(session.returnCode)) {
            addLog(session.allLogsAsString ?: "FFmpeg audio gagal")
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

        addLog("Menganalisis intensitas audio 16 kHz")

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

        addLog("Jumlah jendela audio: ${rmsValues.size}")

        val clipWindows = (clipSeconds / windowSeconds).toInt()

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
                    ) < clipSeconds
                }
            ) {
                chosen.add(candidate)
                // Three passes keep long on-device inference bounded.
                if (chosen.size == 3) break
            }
        }

        addLog("Kandidat audio yang akan diperiksa: ${chosen.size} (maksimal 3 untuk menjaga memori HP)")
        addLog("Durasi setiap kandidat: $clipSeconds detik")

        return chosen
    }

    private fun ensureWhisperModel(): File {
        val model = File(filesDir, "ggml-base-q5_1.bin")

        if (model.exists() && model.length() > 10_000_000L) {
            addLog("Model Whisper sudah tersedia (${model.length()} bytes)")
            return model
        }

        addLog("Mengunduh model Whisper base multilingual (sekali saja)")

        val temp = File(filesDir, "ggml-base-q5_1.download")
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

        addLog("Model Whisper siap: ${model.length()} bytes")
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

    private fun transcribeCandidates(
        pcm: File,
        candidates: List<Candidate>
    ): Double {
        if (candidates.isEmpty()) {
            throw Exception("Tidak ada kandidat audio")
        }

        val model = ensureWhisperModel()
        addLog("Memuat model Whisper ke memori")

        val context = WhisperNative.initModel(model.absolutePath)

        if (context == 0L) {
            throw Exception("Whisper gagal memuat model")
        }

        var bestStart = candidates.first().startSeconds
        var bestScore = Double.NEGATIVE_INFINITY

        try {
            RandomAccessFile(pcm, "r").use { file ->

                for ((index, candidate) in candidates.withIndex()) {
                    addLog(
                        "Transkripsi kandidat ${index + 1}/${candidates.size}"
                    )

                    val offset =
                        (candidate.startSeconds * sampleRate * 2).toLong()

                    val byteCount = clipSeconds * sampleRate * 2

                    if (offset + byteCount > file.length()) continue

                    file.seek(offset)

                    val bytes = ByteArray(byteCount)
                    file.readFully(bytes)

                    val buffer = ByteBuffer.wrap(bytes)
                        .order(ByteOrder.LITTLE_ENDIAN)

                    val audio = FloatArray(clipSeconds * sampleRate)

                    for (i in audio.indices) {
                        audio[i] = buffer.short.toFloat() / 32768.0f
                    }

                    val transcript =
                        WhisperNative.transcribe(context, audio)

                    val score =
                        transcriptScore(transcript) +
                            candidate.audioScore * 8.0

                    val preview =
                        transcript.replace("\n", " ").take(180)

                    addLog("Teks ${index + 1}: $preview")
                    addLog(
                        "Skor isi ${index + 1}: " +
                            String.format(Locale.US, "%.2f", score)
                    )

                    if (score > bestScore) {
                        bestScore = score
                        bestStart = candidate.startSeconds
                    }
                }
            }
        } finally {
            WhisperNative.freeModel(context)
        }

        addLog(
            "Skor transkrip terbaik: " +
                String.format(Locale.US, "%.2f", bestScore)
        )

        return bestStart
    }


    private fun createCaptionAss(input: File, start: Double, duration: Double): File? {
        val pcm = File(cacheDir, "clipmaster_caption_audio.pcm")
        val ss = String.format(Locale.US, "%.3f", start)
        val tt = String.format(Locale.US, "%.3f", duration)
        val cmd = "-y -ss $ss -i \"${input.absolutePath}\" -t $tt -map 0:a:0 -vn -ac 1 -ar 16000 -c:a pcm_s16le -f s16le \"${pcm.absolutePath}\""
        val audioSession = FFmpegKit.execute(cmd)
        if (!ReturnCode.isSuccess(audioSession.returnCode) || !pcm.exists() || pcm.length() < 2L) {
            addLog("Audio caption tidak tersedia; ekspor tanpa caption")
            pcm.delete()
            return null
        }
        val context = try {
            WhisperNative.initModel(ensureWhisperModel().absolutePath)
        } catch (e: OutOfMemoryError) {
            addLog("Memori tidak cukup untuk memuat model caption; lanjut ekspor tanpa caption")
            pcm.delete()
            return null
        } catch (e: Exception) {
            addLog("Model caption gagal disiapkan: ${e.message}")
            pcm.delete()
            return null
        }
        if (context == 0L) { pcm.delete(); return null }
        try {
            val bytes = pcm.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val audio = FloatArray(bytes.size / 2) { buffer.short.toFloat() / 32768.0f }
            val timed = WhisperNative.transcribeTimed(context, audio)
            if (timed.isBlank()) return null
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
            if (events.isEmpty()) return null
            val header = listOf(
                "[Script Info]", "ScriptType: v4.00+", "PlayResX: 1080", "PlayResY: 1920",
                "WrapStyle: 2", "ScaledBorderAndShadow: yes", "",
                "[V4+ Styles]",
                "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
                "Style: Default,Arial,58,&H0000FFFF,&H00FFFFFF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,4,1,2,70,70,100,1", "",
                "[Events]", "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
            ).joinToString("\n") + "\n"
            val ass = File(cacheDir, "clipmaster_captions.ass")
            ass.writeText(header + events.toString())
            addLog("Caption karaoke tersinkron dibuat")
            return ass
        } catch (e: OutOfMemoryError) {
            addLog("Memori tidak cukup saat membuat caption; video akan diekspor tanpa caption")
            return null
        } catch (e: Exception) {
            addLog("Caption gagal: ${e.message}")
            return null
        } finally {
            WhisperNative.freeModel(context)
            pcm.delete()
        }
    }

    private fun exportClip(
        input: File,
        output: File,
        start: Double,
        duration: Double
    ): Boolean {
        val ss = String.format(Locale.US, "%.3f", start)
        val tt = String.format(Locale.US, "%.3f", duration)

        val captionFilter = if (captionsEnabled) {
            val ass = createCaptionAss(input, start, duration)
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

        addLog("Mengekspor MP4 berdurasi $duration detik")

        val session = FFmpegKit.execute(command)

        addLog("FFmpeg selesai. Return code: ${session.returnCode}")

        if (!ReturnCode.isSuccess(session.returnCode)) {
            addLog(session.allLogsAsString ?: "FFmpeg gagal")
            return false
        }

        return output.exists() && output.length() > 0
    }

    private fun startProcessing(outputUri: Uri) {
        val inputUri = selectedUri ?: return
        val selectedDuration = clipSeconds

        pickButton.isEnabled = false
        createButton.isEnabled = false
        durationSpinner.isEnabled = false

        statusText.text = "Status: Memproses..."
        statusText.setTextColor(android.graphics.Color.YELLOW)
        logText.text = ""

        addLog("--------------------------------")
        addLog("MEMULAI AUTO HIGHLIGHT 3.1")
        addLog("Target durasi: $selectedDuration detik")

        // Promote the app to foreground-service priority before starting long
        // Whisper/FFmpeg work. This keeps processing alive when the Activity is minimized.
        startForegroundService(
            Intent(this, ProcessingForegroundService::class.java).apply {
                action = ProcessingForegroundService.ACTION_START
            }
        )

        addLog("Layanan background aktif; aplikasi boleh diminimize")

        executor.execute {
            val input = File(cacheDir, "clipmaster_input.mp4")
            val pcm = File(cacheDir, "clipmaster_audio_16k.pcm")
            val output = File(cacheDir, "clipmaster_output.mp4")

            try {
                input.delete()
                pcm.delete()
                output.delete()

                addLog("Menyalin video ke penyimpanan sementara")

                contentResolver.openInputStream(inputUri).use { source ->
                    if (source == null) {
                        throw Exception("Tidak dapat membaca video")
                    }

                    FileOutputStream(input).use { dest ->
                        source.copyTo(dest)
                    }
                }

                if (!input.exists() || input.length() == 0L) {
                    throw Exception("Video sumber kosong")
                }

                addLog("Input siap: ${input.length()} bytes")

                val durationMs = getDurationMs(input)

                if (durationMs <= 0L) {
                    throw Exception("Durasi video tidak dapat dibaca")
                }

                val duration = durationMs / 1000.0

                addLog(
                    "Durasi sumber: " +
                        String.format(Locale.US, "%.1f", duration) +
                        " detik"
                )

                val clipDuration = min(
                    selectedDuration.toDouble(),
                    duration
                )

                var chosenStart = 0.0

                if (duration > selectedDuration) {
                    if (!extractPcm16k(input, pcm)) {
                        throw Exception("Audio gagal diekstrak")
                    }

                    val candidates = findAudioCandidates(pcm)

                    if (candidates.isEmpty()) {
                        throw Exception("Tidak ditemukan kandidat audio")
                    }

                    chosenStart = try {
                        transcribeCandidates(pcm, candidates)
                    } catch (e: Exception) {
                        addLog("Whisper tidak tersedia: ${e.message}")
                        addLog(
                            "Fallback: memakai skor aktivitas audio"
                        )
                        candidates.first().startSeconds
                    }
                } else {
                    addLog(
                        "Video lebih pendek dari pilihan; memakai seluruh durasi"
                    )
                }

                addLog(
                    "Bagian terpilih mulai: " +
                        String.format(Locale.US, "%.2f", chosenStart) +
                        " detik"
                )

                addLog("Durasi hasil: $clipDuration detik")

                if (!exportClip(input, output, chosenStart, clipDuration)) {
                    throw Exception("Ekspor FFmpeg gagal")
                }

                addLog("Menyimpan hasil video")

                contentResolver.openOutputStream(outputUri).use { dest ->
                    if (dest == null) {
                        throw Exception(
                            "Lokasi penyimpanan tidak dapat dibuka"
                        )
                    }

                    output.inputStream().use { source ->
                        source.copyTo(dest)
                    }
                }

                addLog("AUTO CLIP BERHASIL DISIMPAN")
                updateStatus("Status: AUTO CLIP BERHASIL", true)

            } catch (e: OutOfMemoryError) {
                addLog("MEMORI HP TIDAK CUKUP: proses dihentikan dengan aman. Coba video lebih pendek atau nonaktifkan caption.")
                updateStatus("Status: MEMORI TIDAK CUKUP", false)

            } catch (e: Exception) {
                addLog("ERROR: ${e.message}")
                updateStatus("Status: GAGAL", false)

            } finally {
                input.delete()
                pcm.delete()
                output.delete()
                stopService(Intent(this@MainActivity, ProcessingForegroundService::class.java))

                runOnUiThread {
                    pickButton.isEnabled = true
                    createButton.isEnabled = true
                    durationSpinner.isEnabled = true
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
