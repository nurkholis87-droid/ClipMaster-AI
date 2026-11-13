package com.clipmaster.ai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.media.MediaMetadataRetriever
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()

    private var selectedVideoUri: Uri? = null
    private var selectedVideoName = "Belum ada video dipilih"
    private var selectedDuration = 30

    private lateinit var videoInfo: TextView
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var durationSpinner: Spinner
    private lateinit var pickButton: Button
    private lateinit var createButton: Button

    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    selectedVideoUri = uri
                    selectedVideoName = getFileName(uri)

                    val duration = getVideoDuration(uri)

                    videoInfo.text = "Video: $selectedVideoName\nDurasi sumber: $duration"
                    addLog("Video berhasil dipilih: $selectedVideoName")
                }
            }
        }

    private val outputPicker =
        registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
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
        addLog("Versi 1.2.1")
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
            text = "VIDEO CLIP MAKER • VERSION 1.2.1"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, 8, 0, 24)
        }

        root.addView(subtitle)

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

        val durationLabel = TextView(this).apply {
            text = "DURASI POTONGAN"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 12, 0, 8)
        }

        root.addView(durationLabel)

        durationSpinner = Spinner(this)

        val durationAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("15 detik", "30 detik", "60 detik")
        )

        durationAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        durationSpinner.adapter = durationAdapter
        durationSpinner.setSelection(1)

        durationSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    selectedDuration = when (position) {
                        0 -> 15
                        1 -> 30
                        else -> 60
                    }
                }
            }

        root.addView(
            durationSpinner,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        createButton = Button(this).apply {
            text = "BUAT VIDEO CLIP"
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
                320
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
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)

            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            retriever.release()

            val seconds = duration / 1000
            "${seconds / 60} menit ${seconds % 60} detik"
        } catch (e: Exception) {
            "Tidak diketahui"
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

        outputPicker.launch("ClipMaster_${selectedDuration}s.mp4")
    }

    private fun startProcessing(outputUri: Uri) {
        val inputUri = selectedVideoUri ?: return

        pickButton.isEnabled = false
        createButton.isEnabled = false

        statusText.text = "Status: Memproses video..."
        addLog("--------------------------------")
        addLog("MEMULAI PROSES PEMOTONGAN")
        addLog("Target durasi: $selectedDuration detik")

        executor.execute {
            try {
                addLog("Menyalin video ke penyimpanan sementara")

                val inputFile = File(cacheDir, "clipmaster_input.mp4")
                val outputFile = File(cacheDir, "clipmaster_output.mp4")

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

                val command =
                    "-y -i \"${inputFile.absolutePath}\" " +
                    "-t $selectedDuration " +
                    "-map 0:v:0 -map 0:a? " +
                    "-c:v libopenh264 " +
                    "-b:v 4000k " +
                    "-pix_fmt yuv420p " +
                    "-c:a aac " +
                    "-b:a 128k " +
                    "-movflags +faststart " +
                    "\"${outputFile.absolutePath}\""

                addLog("Menjalankan FFmpeg")
                addLog("Perintah: $command")

                FFmpegKit.executeAsync(command) { session ->

                    val returnCode = session.returnCode

                    addLog("FFmpeg selesai")
                    addLog("Return code: $returnCode")

                    val success = ReturnCode.isSuccess(returnCode)

                    if (success && outputFile.exists() && outputFile.length() > 0) {
                        try {
                            addLog("Menyalin hasil ke lokasi penyimpanan")

                            contentResolver.openOutputStream(outputUri).use { output ->
                                if (output == null) {
                                    throw Exception("Gagal membuka lokasi penyimpanan")
                                }

                                outputFile.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }

                            addLog("VIDEO BERHASIL DISIMPAN")
                            updateStatus("Status: BERHASIL", true)

                        } catch (e: Exception) {
                            addLog("Gagal menyimpan: ${e.message}")
                            updateStatus("Status: GAGAL MENYIMPAN", false)
                        }
                    } else {
                        addLog("FFmpeg gagal memproses video")

                        val errorLogs = session.allLogsAsString
                        if (!errorLogs.isNullOrBlank()) {
                            addLog(errorLogs)
                        }

                        updateStatus("Status: GAGAL", false)
                    }

                    inputFile.delete()
                    outputFile.delete()

                    runOnUiThread {
                        pickButton.isEnabled = true
                        createButton.isEnabled = true
                    }
                }

            } catch (e: Exception) {
                addLog("ERROR: ${e.message}")
                updateStatus("Status: ERROR", false)

                runOnUiThread {
                    pickButton.isEnabled = true
                    createButton.isEnabled = true
                }
            }
        }
    }

    private fun addLog(message: String) {
        runOnUiThread {
            val time = java.text.SimpleDateFormat(
                "HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(java.util.Date())

            logText.append("[$time] $message\n")
        }
    }

    private fun updateStatus(message: String, success: Boolean) {
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
