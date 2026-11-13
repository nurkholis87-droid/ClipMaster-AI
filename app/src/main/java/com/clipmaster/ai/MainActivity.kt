package com.clipmaster.ai

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
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

    private var selectedVideoUri: Uri? = null
    private var selectedVideoName = "Belum ada video dipilih"

    private val clipDurationSeconds = 30

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

                    val duration = getVideoDuration(uri)

                    videoInfo.text =
                        "Video: $selectedVideoName\nDurasi sumber: $duration"

                    addLog("Video berhasil dipilih: $selectedVideoName")
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

        addLog("ClipMaster AI siap digunakan")
        addLog("Versi 2.0.0")
        addLog("Mode: AUTO HIGHLIGHT 30 DETIK")
    }

    private fun buildInterface() {

        val scroll = ScrollView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(android.graphics.Color.rgb(12, 18, 32))
        }

        scroll.addView(root)

        val title = TextView(this).apply {
            text = "ClipMaster AI"
            textSize = 28f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
        }

        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "AUTO HIGHLIGHT • SMART CLIPPING"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, 8, 0, 24)
        }

        root.addView(subtitle)

        pickButton = Button(this).apply {
            text = "PILIH VIDEO"
            setOnClickListener {
                openVideoPicker()
            }
        }

        root.addView(pickButton, buttonParams())

        videoInfo = TextView(this).apply {
            text = "Belum ada video dipilih"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(8, 18, 8, 18)
        }

        root.addView(videoInfo)

        val modeLabel = TextView(this).apply {
            text = "MODE PEMOTONGAN OTOMATIS"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 12, 0, 8)
        }

        root.addView(modeLabel)

        val modeInfo = TextView(this).apply {
            text = "AUTO HIGHLIGHT 30 DETIK\n\n" +
                    "Aplikasi menganalisis aktivitas visual video " +
                    "dan memilih bagian dengan perubahan visual paling tinggi."
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(190, 205, 225))
            setPadding(14, 14, 14, 14)
            setBackgroundColor(android.graphics.Color.rgb(20, 29, 45))
        }

        root.addView(
            modeInfo,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        createButton = Button(this).apply {
            text = "BUAT AUTO CLIP 30 DETIK"
            setOnClickListener {
                requestOutputFile()
            }
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

        val logTitle = TextView(this).apply {
            text = "DIAGNOSTIC LOG"
            textSize = 15f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 12, 0, 8)
        }

        root.addView(logTitle)

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

        val clearButton = Button(this).apply {
            text = "BERSIHKAN LOG"
            setOnClickListener {
                logText.text = ""
                addLog("Log dibersihkan")
            }
        }

        root.addView(clearButton, buttonParams())

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

            val seconds = duration / 1000

            "${seconds / 60} menit ${seconds % 60} detik"

        } catch (e: Exception) {

            "Tidak diketahui"

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

                addLog("Input siap: ${inputFile.length()} bytes")

                addLog("Membaca durasi video")

                val durationMs = getLocalVideoDuration(inputFile)

                if (durationMs <= 0) {
                    throw Exception("Durasi video tidak dapat dibaca")
                }

                val durationSeconds = durationMs / 1000.0

                addLog(
                    "Durasi sumber: ${
                        String.format(Locale.US, "%.1f", durationSeconds)
                    } detik"
                )

                if (durationSeconds < 1.0) {
                    throw Exception("Video terlalu pendek")
                }

                val actualClipDuration = min(
                    clipDurationSeconds.toDouble(),
                    durationSeconds
                )

                if (durationSeconds < clipDurationSeconds) {

                    addLog(
                        "Video kurang dari 30 detik. " +
                                "Akan menggunakan seluruh durasi sumber."
                    )
                }

                addLog("Menganalisis aktivitas visual video")

                val bestStart = if (
                    durationSeconds > clipDurationSeconds
                ) {
                    findBestVisualMoment(inputFile, durationMs)
                } else {
                    0.0
                }

                addLog(
                    "Bagian terpilih mulai detik: ${
                        String.format(Locale.US, "%.2f", bestStart)
                    }"
                )

                addLog("Menyiapkan proses pemotongan")

                val startValue = String.format(
                    Locale.US,
                    "%.3f",
                    bestStart
                )

                val durationValue = String.format(
                    Locale.US,
                    "%.3f",
                    actualClipDuration
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
                addLog("Mengekspor satu video MP4")

                FFmpegKit.executeAsync(command) { session ->

                    try {

                        val returnCode = session.returnCode

                        addLog("FFmpeg selesai")
                        addLog("Return code: $returnCode")

                        val success = ReturnCode.isSuccess(returnCode)

                        if (
                            success &&
                            outputFile.exists() &&
                            outputFile.length() > 0
                        ) {

                            try {

                                addLog("Menyimpan hasil video")

                                contentResolver.openOutputStream(outputUri).use { output ->

                                    if (output == null) {
                                        throw Exception(
                                            "Gagal membuka lokasi penyimpanan"
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

                            } catch (e: Exception) {

                                addLog("Gagal menyimpan: ${e.message}")

                                updateStatus(
                                    "Status: GAGAL MENYIMPAN",
                                    false
                                )
                            }

                        } else {

                            addLog("FFmpeg gagal memproses video")

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

    private fun getLocalVideoDuration(file: File): Long {

        val retriever = MediaMetadataRetriever()

        return try {

            retriever.setDataSource(file.absolutePath)

            retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

        } finally {

            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun findBestVisualMoment(
        file: File,
        durationMs: Long
    ): Double {

        val retriever = MediaMetadataRetriever()

        val intervalMs = max(
            2000L,
            durationMs / 1799L
        )

        val sampleCount = min(
            1800,
            (durationMs / intervalMs).toInt() + 1
        )

        val scores = ArrayList<Double>()

        var previousPixels: IntArray? = null

        try {

            retriever.setDataSource(file.absolutePath)

            for (index in 0 until sampleCount) {

                val timeMs = min(
                    index * intervalMs,
                    durationMs
                )

                val bitmap = retriever.getFrameAtTime(
                    timeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (bitmap == null) {

                    if (previousPixels != null) {
                        scores.add(0.0)
                    }

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

                    val averageDifference =
                        difference / currentPixels.size / 255.0

                    scores.add(averageDifference)
                }

                previousPixels = currentPixels

                if (index % 30 == 0) {
                    addLog("Analisis visual: sampel ${index + 1}/$sampleCount")
                }
            }

        } finally {

            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        if (scores.isEmpty()) {
            addLog("Analisis visual tidak menemukan data cukup")
            return 0.0
        }

        val windowIntervals = ceil(
            clipDurationSeconds * 1000.0 / intervalMs
        ).toInt()

        val lastPossibleStart = min(
            scores.size - windowIntervals,
            ((durationMs - clipDurationSeconds * 1000L) / intervalMs).toInt()
        )

        if (lastPossibleStart <= 0) {
            return 0.0
        }

        val step = max(1, windowIntervals / 4)

        var bestIndex = 0
        var bestScore = Double.NEGATIVE_INFINITY

        var index = 0

        while (index <= lastPossibleStart) {

            val endIndex = min(
                scores.size,
                index + windowIntervals
            )

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

        // Periksa juga posisi terakhir agar bagian akhir video tidak terlewat.
        if (bestIndex != lastPossibleStart) {

            var total = 0.0

            for (
                i in lastPossibleStart until
                        min(scores.size, lastPossibleStart + windowIntervals)
            ) {
                total += scores[i]
            }

            val count = min(
                windowIntervals,
                scores.size - lastPossibleStart
            )

            val lastAverage = total / max(1, count)

            if (lastAverage > bestScore) {
                bestIndex = lastPossibleStart
            }
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
                    (x * bitmap.width / columns)
                )

                val sourceY = min(
                    bitmap.height - 1,
                    (y * bitmap.height / rows)
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
