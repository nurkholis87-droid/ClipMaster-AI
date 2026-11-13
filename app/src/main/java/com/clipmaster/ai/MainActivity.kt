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
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val clipDurationSeconds = 30

    private var selectedVideoUri: Uri? = null
    private var selectedVideoName = "Belum ada video dipilih"

    private lateinit var videoInfo: TextView
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var pickButton: Button
    private lateinit var createButton: Button

    private val videoPicker =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->

                    selectedVideoUri = uri
                    selectedVideoName = getFileName(uri)

                    videoInfo.text =
                        "Video: $selectedVideoName\nDurasi: ${getVideoDuration(uri)}"

                    addLog("Video dipilih: $selectedVideoName")
                }
            }
        }

    private val outputPicker =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("video/mp4")
        ) { uri ->

            if (uri != null) {
                startProcessing(uri)
            } else {
                addLog("Penyimpanan dibatalkan")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildInterface()

        addLog("ClipMaster AI siap")
        addLog("Versi 2.4.0")
        addLog("AUTO PODCAST HIGHLIGHT")
        addLog("Analisis audio: PCM 8000 Hz")
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
            text = "PODCAST HIGHLIGHT • SMART CLIPPING"
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
            text = "MODE PEMOTONGAN OTOMATIS"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 12, 0, 8)
        })

        root.addView(TextView(this).apply {
            text = "AUTO PODCAST HIGHLIGHT 30 DETIK\n\n" +
                    "Menganalisis intensitas suara untuk mencari " +
                    "bagian percakapan yang paling aktif."
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(190, 205, 225))
            setPadding(14, 14, 14, 14)
            setBackgroundColor(android.graphics.Color.rgb(20, 29, 45))
        })

        createButton = Button(this).apply {
            text = "BUAT AUTO CLIP 30 DETIK"
            setOnClickListener { requestOutputFile() }
        }

        val createParams = buttonParams()
        createParams.topMargin = 20
        root.addView(createButton, createParams)

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
                360
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

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

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
                val index = cursor.getColumnIndex(
                    android.provider.OpenableColumns.DISPLAY_NAME
                )

                if (index >= 0) {
                    name = cursor.getString(index) ?: name
                }
            }
        }

        return name
    }

    private fun getVideoDuration(uri: Uri): String {

        val retriever =
            android.media.MediaMetadataRetriever()

        return try {

            retriever.setDataSource(this, uri)

            val duration = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            if (duration <= 0L) {
                "Akan diperiksa saat proses"
            } else {
                val seconds = duration / 1000
                "${seconds / 60} menit ${seconds % 60} detik"
            }

        } catch (e: Exception) {

            "Akan diperiksa saat proses"

        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun requestOutputFile() {

        if (selectedVideoUri == null) {
            Toast.makeText(
                this,
                "Pilih video terlebih dahulu",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        outputPicker.launch("ClipMaster_PodcastHighlight_30s.mp4")
    }

    private fun getDurationWithFFprobe(file: File): Long {

        addLog("Memeriksa durasi menggunakan FFprobe")

        val session =
            FFprobeKit.getMediaInformation(file.absolutePath)

        val information = session.mediaInformation

        if (information == null) {

            addLog("FFprobe tidak mendapatkan informasi media")

            val logs = session.allLogsAsString
            if (!logs.isNullOrBlank()) addLog(logs)

            return 0L
        }

        val seconds =
            information.duration?.toDoubleOrNull() ?: 0.0

        addLog("Durasi FFprobe: $seconds detik")

        return if (seconds > 0.0) {
            (seconds * 1000.0).toLong()
        } else {
            0L
        }
    }

    private fun getReliableDuration(
        uri: Uri,
        file: File
    ): Long {

        var duration = getDurationWithFFprobe(file)

        if (duration > 0L) {
            addLog("Durasi berhasil dibaca FFprobe")
            return duration
        }

        addLog("Mencoba metadata URI")

        val retriever =
            android.media.MediaMetadataRetriever()

        try {
            retriever.setDataSource(this, uri)

            duration = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

        } catch (e: Exception) {
            addLog("Metadata URI gagal: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        if (duration > 0L) return duration

        addLog("Mencoba metadata file sementara")

        val fileRetriever =
            android.media.MediaMetadataRetriever()

        try {
            fileRetriever.setDataSource(file.absolutePath)

            duration = fileRetriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

        } catch (e: Exception) {
            addLog("Metadata file gagal: ${e.message}")
        } finally {
            try {
                fileRetriever.release()
            } catch (_: Exception) {
            }
        }

        return duration
    }

    private fun extractAudioPcm(
        inputFile: File,
        pcmFile: File
    ): Boolean {

        pcmFile.delete()

        addLog("Mengekstrak audio podcast")
        addLog("Mengubah audio menjadi PCM mono 8000 Hz")

        val command =
            "-y " +
            "-i \"${inputFile.absolutePath}\" " +
            "-map 0:a:0 " +
            "-vn " +
            "-ac 1 " +
            "-ar 8000 " +
            "-c:a pcm_s16le " +
            "-f s16le " +
            "\"${pcmFile.absolutePath}\""

        val session = FFmpegKit.execute(command)

        val success = ReturnCode.isSuccess(session.returnCode)

        addLog("Ekstraksi audio selesai")
        addLog("Return code audio: ${session.returnCode}")

        if (!success) {
            val logs = session.allLogsAsString
            if (!logs.isNullOrBlank()) addLog(logs)
            return false
        }

        return pcmFile.exists() && pcmFile.length() > 0L
    }

    private fun findBestAudioMoment(
        pcmFile: File,
        durationSeconds: Double
    ): Double {

        val sampleRate = 8000
        val windowSeconds = 0.5

        val samplesPerWindow =
            (sampleRate * windowSeconds).toInt()

        val bytesPerWindow = samplesPerWindow * 2

        val windowScores = ArrayList<Double>()

        addLog("Memulai analisis intensitas audio")

        DataInputStream(
            FileInputStream(pcmFile)
        ).use { input ->

            val buffer = ByteArray(bytesPerWindow)

            while (true) {

                var totalRead = 0

                try {
                    while (totalRead < buffer.size) {
                        val count = input.read(
                            buffer,
                            totalRead,
                            buffer.size - totalRead
                        )

                        if (count < 0) break
                        totalRead += count
                    }
                } catch (_: Exception) {
                    break
                }

                if (totalRead < bytesPerWindow) break

                var sumSquares = 0.0

                var index = 0

                while (index < bytesPerWindow) {

                    val low = buffer[index].toInt() and 0xFF
                    val high = buffer[index + 1].toInt()

                    val sample = (high shl 8) or low

                    sumSquares += sample.toDouble() * sample
                    index += 2
                }

                val rms = sqrt(
                    sumSquares / samplesPerWindow
                ) / 32768.0

                windowScores.add(rms)

                if (windowScores.size % 120 == 0) {
                    val elapsed =
                        windowScores.size * windowSeconds

                    addLog(
                        "Analisis audio: ${
                            String.format(
                                Locale.US,
                                "%.1f",
                                elapsed
                            )
                        } detik"
                    )
                }
            }
        }

        addLog("Jumlah jendela audio: ${windowScores.size}")

        val clipWindows =
            (clipDurationSeconds / windowSeconds).toInt()

        if (windowScores.size < clipWindows) {
            addLog("Data audio kurang untuk analisis 30 detik")
            return 0.0
        }

        val prefix = DoubleArray(windowScores.size + 1)

        for (i in windowScores.indices) {
            prefix[i + 1] =
                prefix[i] + windowScores[i]
        }

        var bestIndex = 0
        var bestScore = Double.NEGATIVE_INFINITY
        var bestAverageRms = 0.0
        var bestActiveRatio = 0.0

        val lastStart =
            windowScores.size - clipWindows

        addLog("Membandingkan seluruh kandidat 30 detik")

        for (start in 0..lastStart) {

            val end = start + clipWindows

            val total =
                prefix[end] - prefix[start]

            val averageRms =
                total / clipWindows

            var activeWindows = 0

            for (i in start until end) {
                if (windowScores[i] >= 0.012) {
                    activeWindows++
                }
            }

            val activeRatio =
                activeWindows.toDouble() / clipWindows

            // Kombinasi intensitas suara dan konsistensi aktivitas.
            val score =
                (averageRms * 0.65) +
                (activeRatio * 0.035)

            if (score > bestScore) {

                bestScore = score
                bestIndex = start
                bestAverageRms = averageRms
                bestActiveRatio = activeRatio
            }
        }

        addLog(
            "Rata-rata intensitas terbaik: ${
                String.format(Locale.US, "%.5f", bestAverageRms)
            }"
        )

        addLog(
            "Aktivitas suara: ${
                String.format(
                    Locale.US,
                    "%.1f",
                    bestActiveRatio * 100
                )
            }%"
        )

        addLog(
            "Skor audio terbaik: ${
                String.format(Locale.US, "%.5f", bestScore)
            }"
        )

        if (bestAverageRms < 0.003) {

            addLog("Audio terlalu senyap untuk dipilih")
            addLog("Menggunakan bagian tengah sebagai cadangan")

            return max(
                0.0,
                (durationSeconds - clipDurationSeconds) / 2.0
            )
        }

        val startSeconds =
            bestIndex * windowSeconds

        addLog("Highlight audio berhasil ditemukan")

        return startSeconds
    }

    private fun startProcessing(outputUri: Uri) {

        val inputUri = selectedVideoUri ?: return

        pickButton.isEnabled = false
        createButton.isEnabled = false

        statusText.text = "Status: Menyiapkan video..."
        statusText.setTextColor(android.graphics.Color.YELLOW)

        logText.text = ""

        addLog("--------------------------------")
        addLog("MEMULAI PODCAST AUTO HIGHLIGHT")
        addLog("Target durasi: 30 detik")

        executor.execute {

            var inputFile: File? = null
            var outputFile: File? = null
            var pcmFile: File? = null

            try {

                addLog("Menyalin video ke penyimpanan sementara")

                inputFile =
                    File(cacheDir, "clipmaster_input.mp4")

                outputFile =
                    File(cacheDir, "clipmaster_output.mp4")

                pcmFile =
                    File(cacheDir, "clipmaster_audio.pcm")

                inputFile.delete()
                outputFile.delete()
                pcmFile.delete()

                contentResolver.openInputStream(inputUri).use { input ->

                    if (input == null) {
                        throw Exception("Tidak dapat membaca video sumber")
                    }

                    FileOutputStream(inputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (!inputFile.exists() || inputFile.length() == 0L) {
                    throw Exception("File video sementara kosong")
                }

                addLog("Input siap: ${inputFile.length()} bytes")

                val durationMs =
                    getReliableDuration(inputUri, inputFile)

                if (durationMs <= 0L) {
                    throw Exception("Durasi video tidak dapat dibaca")
                }

                val durationSeconds =
                    durationMs / 1000.0

                addLog(
                    "Durasi sumber: ${
                        String.format(
                            Locale.US,
                            "%.2f",
                            durationSeconds
                        )
                    } detik"
                )

                val actualDuration = min(
                    clipDurationSeconds.toDouble(),
                    durationSeconds
                )

                val bestStart: Double

                if (durationSeconds <= clipDurationSeconds) {

                    addLog("Video kurang dari 30 detik")
                    addLog("Menggunakan seluruh durasi sumber")

                    bestStart = 0.0

                } else {

                    val audioReady = extractAudioPcm(
                        inputFile,
                        pcmFile
                    )

                    bestStart = if (audioReady) {

                        try {
                            findBestAudioMoment(
                                pcmFile,
                                durationSeconds
                            )
                        } catch (e: Exception) {

                            addLog(
                                "Analisis audio gagal: ${e.message}"
                            )

                            addLog(
                                "Menggunakan bagian tengah video"
                            )

                            max(
                                0.0,
                                (durationSeconds - actualDuration) / 2.0
                            )
                        }

                    } else {

                        addLog("Audio tidak berhasil diekstrak")
                        addLog("Menggunakan bagian tengah video")

                        max(
                            0.0,
                            (durationSeconds - actualDuration) / 2.0
                        )
                    }
                }

                addLog(
                    "Bagian terpilih: ${
                        String.format(
                            Locale.US,
                            "%.2f",
                            bestStart
                        )
                    } detik"
                )

                val startValue = String.format(
                    Locale.US,
                    "%.3f",
                    bestStart
                )

                val durationValue = String.format(
                    Locale.US,
                    "%.3f",
                    actualDuration
                )

                val command =
                    "-y " +
                    "-ss $startValue " +
                    "-i \"${inputFile.absolutePath}\" " +
                    "-t $durationValue " +
                    "-map 0:v:0 " +
                    "-map 0:a? " +
                    "-c:v libopenh264 " +
                    "-b:v 4000k " +
                    "-pix_fmt yuv420p " +
                    "-c:a aac " +
                    "-b:a 128k " +
                    "-movflags +faststart " +
                    "\"${outputFile.absolutePath}\""

                addLog("Menjalankan FFmpeg")
                addLog("Mengekspor MP4")

                FFmpegKit.executeAsync(command) { session ->

                    try {

                        val success =
                            ReturnCode.isSuccess(session.returnCode)

                        addLog("FFmpeg selesai")
                        addLog("Return code: ${session.returnCode}")

                        if (
                            success &&
                            outputFile.exists() &&
                            outputFile.length() > 0L
                        ) {

                            addLog("Menyimpan hasil video")

                            contentResolver
                                .openOutputStream(outputUri).use { output ->

                                    if (output == null) {
                                        throw Exception(
                                            "Tidak dapat membuka lokasi penyimpanan"
                                        )
                                    }

                                    outputFile.inputStream().use { input ->
                                        input.copyTo(output)
                                    }
                                }

                            addLog("AUTO CLIP BERHASIL DISIMPAN")

                            updateStatus(
                                "Status: AUTO CLIP BERHASIL",
                                true
                            )

                        } else {

                            addLog("FFmpeg gagal")

                            val errorLogs =
                                session.allLogsAsString

                            if (!errorLogs.isNullOrBlank()) {
                                addLog(errorLogs)
                            }

                            updateStatus("Status: GAGAL", false)
                        }

                    } catch (e: Exception) {

                        addLog("ERROR HASIL: ${e.message}")
                        updateStatus("Status: ERROR", false)

                    } finally {

                        inputFile.delete()
                        outputFile.delete()
                        pcmFile.delete()

                        runOnUiThread {
                            pickButton.isEnabled = true
                            createButton.isEnabled = true
                        }
                    }
                }

            } catch (e: Exception) {

                addLog("ERROR: ${e.message}")

                updateStatus("Status: ERROR", false)

                inputFile?.delete()
                outputFile?.delete()
                pcmFile?.delete()

                runOnUiThread {
                    pickButton.isEnabled = true
                    createButton.isEnabled = true
                }
            }
        }
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

    private fun updateStatus(
        message: String,
        success: Boolean
    ) {

        runOnUiThread {

            if (!::statusText.isInitialized) return@runOnUiThread

            statusText.text = message

            statusText.setTextColor(
                if (success) {
                    android.graphics.Color.GREEN
                } else {
                    android.graphics.Color.RED
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
