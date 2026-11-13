package com.clipmaster.ai

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

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
        addLog("Versi 2.2.0")
        addLog("AUTO HIGHLIGHT 30 DETIK")
        addLog("Mesin pembaca durasi: FFprobe")
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
            text = "AUTO HIGHLIGHT • SMART CLIPPING"
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
            text = "AUTO HIGHLIGHT 30 DETIK\n\n" +
                    "Aplikasi menganalisis perubahan visual video " +
                    "dan memilih bagian dengan aktivitas visual tinggi."
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

        val retriever = MediaMetadataRetriever()

        return try {

            retriever.setDataSource(this, uri)

            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
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

        outputPicker.launch("ClipMaster_AutoHighlight_30s.mp4")
    }

    // FFprobe menjadi pembaca durasi utama.
    private fun getDurationWithFFprobe(file: File): Long {

        addLog("Memeriksa durasi menggunakan FFprobe")

        val session = FFprobeKit.getMediaInformation(
            file.absolutePath
        )

        val information = session.mediaInformation

        if (information == null) {

            addLog("FFprobe tidak mendapatkan informasi media")

            val logs = session.allLogsAsString
            if (!logs.isNullOrBlank()) addLog(logs)

            return 0L
        }

        val durationText = information.duration

        addLog("Data durasi FFprobe: $durationText")

        val seconds = durationText?.toDoubleOrNull() ?: 0.0

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

        // Metode 1: FFprobe.
        var duration = getDurationWithFFprobe(file)

        if (duration > 0L) {
            addLog("Durasi berhasil dibaca oleh FFprobe")
            return duration
        }

        // Metode 2: Metadata Android dari URI.
        addLog("FFprobe gagal, mencoba metadata URI")

        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(this, uri)

            duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

        } catch (e: Exception) {
            addLog("Metadata URI gagal: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        if (duration > 0L) {
            addLog("Durasi berhasil dibaca dari URI")
            return duration
        }

        // Metode 3: Metadata Android dari file sementara.
        addLog("Mencoba metadata file sementara")

        val fileRetriever = MediaMetadataRetriever()

        try {
            fileRetriever.setDataSource(file.absolutePath)

            duration = fileRetriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
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

    private fun startProcessing(outputUri: Uri) {

        val inputUri = selectedVideoUri ?: return

        pickButton.isEnabled = false
        createButton.isEnabled = false

        statusText.text = "Status: Menyiapkan video..."
        statusText.setTextColor(android.graphics.Color.YELLOW)

        logText.text = ""

        addLog("--------------------------------")
        addLog("MEMULAI AUTO HIGHLIGHT")
        addLog("Target durasi: 30 detik")

        executor.execute {

            var inputFile: File? = null
            var outputFile: File? = null

            try {

                addLog("Menyalin video ke penyimpanan sementara")

                inputFile = File(cacheDir, "clipmaster_input.mp4")
                outputFile = File(cacheDir, "clipmaster_output.mp4")

                inputFile.delete()
                outputFile.delete()

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

                val durationMs = getReliableDuration(inputUri, inputFile)

                if (durationMs <= 0L) {
                    throw Exception(
                        "FFprobe dan pembaca metadata gagal membaca durasi"
                    )
                }

                val durationSeconds = durationMs / 1000.0

                addLog(
                    "Durasi sumber: ${
                        String.format(Locale.US, "%.2f", durationSeconds)
                    } detik"
                )

                val actualDuration = min(
                    clipDurationSeconds.toDouble(),
                    durationSeconds
                )

                if (durationSeconds < clipDurationSeconds) {
                    addLog("Video kurang dari 30 detik")
                    addLog("Menggunakan seluruh durasi sumber")
                }

                addLog("Memulai analisis visual")

                val bestStart = if (
                    durationSeconds > clipDurationSeconds
                ) {
                    try {
                        findBestVisualMoment(inputFile, durationMs)
                    } catch (e: Exception) {
                        addLog("Analisis visual gagal: ${e.message}")
                        addLog("Menggunakan bagian awal video")
                        0.0
                    }
                } else {
                    0.0
                }

                addLog(
                    "Bagian terpilih: ${
                        String.format(Locale.US, "%.2f", bestStart)
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

                        val success = ReturnCode.isSuccess(
                            session.returnCode
                        )

                        addLog("FFmpeg selesai")
                        addLog("Return code: ${session.returnCode}")

                        if (
                            success &&
                            outputFile.exists() &&
                            outputFile.length() > 0L
                        ) {

                            addLog("Menyimpan hasil video")

                            contentResolver.openOutputStream(outputUri).use { output ->

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

                            val errorLogs = session.allLogsAsString

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

                runOnUiThread {
                    pickButton.isEnabled = true
                    createButton.isEnabled = true
                }
            }
        }
    }

    private fun findBestVisualMoment(
        file: File,
        durationMs: Long
    ): Double {

        val retriever = MediaMetadataRetriever()

        val intervalMs = max(2000L, durationMs / 1799L)

        val sampleCount = min(
            1800,
            (durationMs / intervalMs).toInt() + 1
        )

        val scores = ArrayList<Double>()
        var previousPixels: IntArray? = null

        try {

            retriever.setDataSource(file.absolutePath)

            for (index in 0 until sampleCount) {

                val timeMs = min(index * intervalMs, durationMs)

                val bitmap = retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (bitmap == null) {
                    if (previousPixels != null) scores.add(0.0)
                    continue
                }

                val currentPixels = samplePixels(bitmap)
                bitmap.recycle()

                val previous = previousPixels

                if (previous != null) {

                    var difference = 0.0

                    for (i in currentPixels.indices) {

                        val oldPixel = previous[i]
                        val newPixel = currentPixels[i]

                        val oldR = (oldPixel shr 16) and 0xFF
                        val oldG = (oldPixel shr 8) and 0xFF
                        val oldB = oldPixel and 0xFF

                        val newR = (newPixel shr 16) and 0xFF
                        val newG = (newPixel shr 8) and 0xFF
                        val newB = newPixel and 0xFF

                        difference += (
                            kotlin.math.abs(newR - oldR) +
                            kotlin.math.abs(newG - oldG) +
                            kotlin.math.abs(newB - oldB)
                        ) / 3.0
                    }

                    scores.add(
                        difference / currentPixels.size / 255.0
                    )
                }

                previousPixels = currentPixels

                if (index % 30 == 0) {
                    addLog("Analisis visual: ${index + 1}/$sampleCount")
                }
            }

        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        if (scores.isEmpty()) return 0.0

        val windowIntervals = ceil(
            clipDurationSeconds * 1000.0 / intervalMs
        ).toInt()

        val lastPossibleStart = min(
            scores.size - windowIntervals,
            ((durationMs - clipDurationSeconds * 1000L) / intervalMs).toInt()
        )

        if (lastPossibleStart <= 0) return 0.0

        val step = max(1, windowIntervals / 4)

        var bestIndex = 0
        var bestScore = Double.NEGATIVE_INFINITY
        var index = 0

        while (index <= lastPossibleStart) {

            val endIndex = min(scores.size, index + windowIntervals)

            var total = 0.0

            for (i in index until endIndex) {
                total += scores[i]
            }

            val average = total / max(1, endIndex - index)

            if (average > bestScore) {
                bestScore = average
                bestIndex = index
            }

            index += step
        }

        var lastTotal = 0.0

        for (
            i in lastPossibleStart until
            min(scores.size, lastPossibleStart + windowIntervals)
        ) {
            lastTotal += scores[i]
        }

        val lastCount = min(
            windowIntervals,
            scores.size - lastPossibleStart
        )

        val lastAverage = lastTotal / max(1, lastCount)

        if (lastAverage > bestScore) {
            bestIndex = lastPossibleStart
        }

        return bestIndex * intervalMs / 1000.0
    }

    private fun samplePixels(bitmap: Bitmap): IntArray {

        val columns = 32
        val rows = 18
        val pixels = IntArray(columns * rows)

        for (y in 0 until rows) {
            for (x in 0 until columns) {

                val sourceX = min(
                    bitmap.width - 1,
                    x * bitmap.width / columns
                )

                val sourceY = min(
                    bitmap.height - 1,
                    y * bitmap.height / rows
                )

                pixels[y * columns + x] = bitmap.getPixel(
                    sourceX,
                    sourceY
                )
            }
        }

        return pixels
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
