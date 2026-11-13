package com.clipmaster.ai

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : Activity() {

    private val bg = Color.rgb(10, 16, 32)
    private val card = Color.rgb(22, 32, 55)
    private val blue = Color.rgb(35, 145, 255)
    private val cyan = Color.rgb(0, 220, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(155, 170, 195)
    private val green = Color.rgb(70, 220, 140)
    private val red = Color.rgb(255, 100, 100)

    private lateinit var videoInfo: TextView
    private lateinit var status: TextView
    private lateinit var selectButton: TextView
    private lateinit var clipButton: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var progress: ProgressBar

    private var selectedUri: Uri? = null
    private var selectedDuration = 30
    private var videoDuration = 0L
    private var pendingOutput: File? = null
    private var processing = false

    companion object {
        private const val PICK_VIDEO = 1001
        private const val SAVE_VIDEO = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        addLog("ClipMaster AI v1.2.0 dimulai")
        addLog("FFmpeg diagnostic system siap")
    }

    // MAIN INTERFACE

    private fun buildInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(25), dp(20), dp(30))
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        content.addView(text("✂ ClipMaster AI", 28, cyan, true))
        content.addView(text("LOCAL VIDEO CLIP GENERATOR", 11, muted, true))
        content.addView(space(22))

        val hero = newCard()
        hero.addView(text("TURN LONG VIDEOS INTO", 13, muted, true))
        hero.addView(text("SHORT VIDEO CLIPS", 25, white, true))
        hero.addView(
            text(
                "Pilih video dan buat klip MP4 menggunakan mesin FFmpeg.",
                14, muted, false
            )
        )

        content.addView(hero)
        content.addView(space(22))

        content.addView(section("01 • SELECT VIDEO"))

        selectButton = button("▶  PILIH VIDEO DARI HP")

        selectButton.setOnClickListener {
            pickVideo()
        }

        content.addView(selectButton)
        content.addView(space(12))

        val infoCard = newCard()
        infoCard.addView(text("VIDEO INFORMATION", 12, cyan, true))

        videoInfo = text(
            "Belum ada video dipilih.",
            14, white, true
        )

        infoCard.addView(videoInfo)
        content.addView(infoCard)
        content.addView(space(22))

        content.addView(section("02 • CLIP SETTINGS"))
        content.addView(text("Durasi klip", 13, muted, true))

        val spinner = Spinner(this)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("15 detik", "30 detik", "60 detik")
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        spinner.adapter = adapter
        spinner.setSelection(1)

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedDuration = when (position) {
                        0 -> 15
                        1 -> 30
                        else -> 60
                    }

                    addLog("Durasi klip dipilih: $selectedDuration detik")
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {}
            }

        content.addView(spinner)
        content.addView(space(20))

        content.addView(section("03 • CREATE CLIP"))

        clipButton = button("✦  CREATE VIDEO CLIP")
        clipButton.isEnabled = false
        clipButton.alpha = 0.5f

        clipButton.setOnClickListener {
            createClip()
        }

        content.addView(clipButton)
        content.addView(space(15))

        progress = ProgressBar(this).apply {
            visibility = View.GONE
        }

        content.addView(progress)

        status = text("READY TO CREATE CLIPS", 12, cyan, true)
        status.gravity = Gravity.CENTER

        content.addView(status)
        content.addView(space(25))

        // DIAGNOSTIC PANEL

        content.addView(section("04 • DIAGNOSTIC LOG"))

        val diagnosticCard = newCard()

        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logTitle = text(
            "FFmpeg Activity Log",
            14, cyan, true
        )

        logHeader.addView(
            logTitle,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val clearButton = TextView(this).apply {
            text = "CLEAR LOG"
            textSize = 10f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(blue, 8)

            setOnClickListener {
                logView.text = ""
                addLog("Log dibersihkan")
            }
        }

        logHeader.addView(clearButton)
        diagnosticCard.addView(logHeader)
        diagnosticCard.addView(space(10))

        logScroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(5, 10, 20))
        }

        logView = TextView(this).apply {
            text = ""
            textSize = 10f
            setTextColor(Color.rgb(190, 210, 225))
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setTextIsSelectable(true)
        }

        logScroll.addView(logView)

        diagnosticCard.addView(
            logScroll,
            LinearLayout.LayoutParams(-1, dp(280))
        )

        content.addView(diagnosticCard)
        content.addView(space(20))

        val footer = text(
            "CLIPMASTER AI v1.2.0\nCUT • EDIT • CREATE",
            11, muted, true
        )

        footer.gravity = Gravity.CENTER
        content.addView(footer)

        setContentView(root)
    }

    // VIDEO PICKER

    private fun pickVideo() {

        if (processing) return

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }

        startActivityForResult(intent, PICK_VIDEO)
    }

    // RECEIVE VIDEO AND SAVE RESULT

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data?.data == null) {
            if (requestCode == SAVE_VIDEO) {
                addLog("Penyimpanan dibatalkan pengguna")
            }
            return
        }

        when (requestCode) {

            PICK_VIDEO -> {

                val uri = data.data!!

                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                selectedUri = uri
                readVideoInformation(uri)
            }

            SAVE_VIDEO -> saveOutput(data.data!!)
        }
    }

    private fun readVideoInformation(uri: Uri) {

        try {
            val retriever = android.media.MediaMetadataRetriever()

            retriever.setDataSource(this, uri)

            videoDuration = retriever
                .extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L

            retriever.release()

            val seconds = videoDuration / 1000

            videoInfo.text =
                "Video berhasil dipilih\n\n" +
                "Durasi: ${formatTime(seconds)}\n" +
                "Sumber: Penyimpanan HP"

            clipButton.isEnabled = videoDuration > 0
            clipButton.alpha = if (videoDuration > 0) 1f else 0.5f

            status.text = "✓ VIDEO READY"

            addLog("Video berhasil dipilih")
            addLog("Durasi video: ${formatTime(seconds)}")

        } catch (e: Exception) {

            selectedUri = null
            clipButton.isEnabled = false
            clipButton.alpha = 0.5f

            videoInfo.text = "Gagal membaca video: ${e.message}"

            addLog("ERROR membaca video: ${e.message}")
        }
    }

    // FFMPEG PROCESSING

    private fun createClip() {

        val uri = selectedUri

        if (uri == null) {
            toast("Pilih video terlebih dahulu")
            return
        }

        if (processing) return

        if (videoDuration < selectedDuration * 1000L) {
            toast("Durasi video lebih pendek dari durasi klip")
            addLog("ERROR: Durasi sumber terlalu pendek")
            return
        }

        processing = true

        clipButton.isEnabled = false
        selectButton.isEnabled = false
        progress.visibility = View.VISIBLE

        status.text = "MENYIAPKAN VIDEO..."

        addLog("--------------------------------")
        addLog("MEMULAI PROSES PEMOTONGAN")
        addLog("Target durasi: $selectedDuration detik")

        Thread {

            var inputFile: File? = null
            var outputFile: File? = null

            try {

                inputFile = File(cacheDir, "clipmaster_input.mp4")
                outputFile = File(cacheDir, "clipmaster_output.mp4")

                inputFile.delete()
                outputFile.delete()

                addLog("Menyalin video ke penyimpanan sementara")

                contentResolver.openInputStream(uri).use { input ->

                    if (input == null) {
                        throw Exception("Tidak dapat membaca video")
                    }

                    FileOutputStream(inputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (!inputFile.exists() || inputFile.length() == 0L) {
                    throw Exception("File input kosong")
                }

                addLog("Input siap: ${inputFile.length()} bytes")

                val inputPath = inputFile.absolutePath
                val outputPath = outputFile.absolutePath

                val command =
                    "-y -i \"$inputPath\" " +
                    "-t $selectedDuration " +
                    "-map 0:v:0 -map 0:a? " +
                    "-c:v libx264 " +
                    "-preset veryfast " +
                    "-crf 23 " +
                    "-pix_fmt yuv420p " +
                    "-c:a aac " +
                    "-b:a 128k " +
                    "-movflags +faststart " +
                    "\"$outputPath\""

                addLog("Menjalankan FFmpeg")
                addLog("Perintah: $command")

                runOnUiThread {
                    status.text = "FFMPEG PROCESSING..."
                }

                FFmpegKit.executeAsync(
                    command,

                    { session ->

                        val returnCode = session.returnCode

                        val success = ReturnCode.isSuccess(returnCode)

                        val fullLog = session.allLogsAsString

                        runOnUiThread {

                            addLog("--------------------------------")
                            addLog("FFmpeg selesai")
                            addLog("Return code: $returnCode")

                            if (success &&
                                outputFile.exists() &&
                                outputFile.length() > 0
                            ) {

                                addLog("STATUS: BERHASIL")
                                addLog(
                                    "Ukuran output: ${outputFile.length()} bytes"
                                )

                                status.text = "✓ CLIP BERHASIL DIBUAT"

                                pendingOutput = outputFile
                                saveClip()

                            } else {

                                addLog("STATUS: GAGAL")

                                if (fullLog.isNotBlank()) {
                                    addLog("OUTPUT LOG:")
                                    addLog(fullLog)
                                }

                                val fail = session.failStackTrace

                                if (!fail.isNullOrBlank()) {
                                    addLog("FAIL STACK TRACE:")
                                    addLog(fail)
                                }

                                status.text = "FFMPEG PROCESSING FAILED"

                                outputFile.delete()

                                toast("FFmpeg gagal. Periksa DIAGNOSTIC LOG.")
                            }

                            finishProcessing()
                        }

                        inputFile.delete()
                    },

                    { log ->

                        val message = log.message

                        if (message.isNotBlank()) {
                            addLog("[FFmpeg] $message")
                        }
                    },

                    null
                )

            } catch (e: Exception) {

                runOnUiThread {

                    addLog("EXCEPTION: ${e.message}")

                    status.text = "PROCESSING ERROR"

                    toast("Error: ${e.message}")

                    finishProcessing()
                }

                inputFile?.delete()
                outputFile?.delete()
            }

        }.start()
    }

    private fun finishProcessing() {

        processing = false

        progress.visibility = View.GONE

        selectButton.isEnabled = true
        clipButton.isEnabled = selectedUri != null

        clipButton.alpha =
            if (selectedUri != null) 1f else 0.5f
    }

    // SAVE OUTPUT

    private fun saveClip() {

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/mp4"

            putExtra(
                Intent.EXTRA_TITLE,
                "ClipMaster_${System.currentTimeMillis()}.mp4"
            )
        }

        startActivityForResult(intent, SAVE_VIDEO)
    }

    private fun saveOutput(uri: Uri) {

        val output = pendingOutput

        if (output == null || !output.exists()) {
            addLog("ERROR: File output tidak ditemukan")
            toast("File hasil tidak ditemukan")
            return
        }

        try {

            contentResolver.openOutputStream(uri).use { destination ->

                if (destination == null) {
                    throw Exception("Gagal membuka lokasi penyimpanan")
                }

                FileInputStream(output).use { input ->
                    input.copyTo(destination)
                }
            }

            addLog("Video berhasil disimpan")
            addLog("Lokasi penyimpanan dipilih pengguna")

            status.text = "✓ CLIP BERHASIL DISIMPAN"

            toast("Video berhasil disimpan!")

        } catch (e: Exception) {

            addLog("SAVE ERROR: ${e.message}")

            status.text = "SAVE FAILED"

            toast("Gagal menyimpan: ${e.message}")

        } finally {

            output.delete()
            pendingOutput = null
        }
    }

    // DIAGNOSTIC LOG

    private fun addLog(message: String) {

        runOnUiThread {

            val time = java.text.SimpleDateFormat(
                "HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(java.util.Date())

            logView.append("[$time] $message\n")

            logScroll.post {
                logScroll.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun formatTime(seconds: Long): String {

        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    private fun section(value: String): TextView {
        return text(value, 14, cyan, true)
    }

    private fun newCard(): LinearLayout {

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(18), dp(18), dp(18), dp(18)
            )

            background = rounded(card, 20)
        }
    }

    private fun text(
        value: String,
        size: Int,
        color: Int,
        bold: Boolean
    ): TextView {

        return TextView(this).apply {

            text = value
            textSize = size.toFloat()

            setTextColor(color)

            typeface = if (bold) {
                Typeface.DEFAULT_BOLD
            } else {
                Typeface.DEFAULT
            }

            setPadding(0, dp(5), 0, dp(5))
        }
    }

    private fun button(value: String): TextView {

        return TextView(this).apply {

            text = value
            textSize = 15f

            gravity = Gravity.CENTER

            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD

            setPadding(
                dp(12), dp(17), dp(12), dp(17)
            )

            background = rounded(blue, 15)
        }
    }

    private fun space(height: Int): View {

        return View(this).apply {

            layoutParams = LinearLayout.LayoutParams(
                1,
                dp(height)
            )
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
