
package com.clipmaster.ai

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
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

    private lateinit var videoInfo: TextView
    private lateinit var status: TextView
    private lateinit var selectButton: TextView
    private lateinit var clipButton: TextView
    private lateinit var durationSpinner: Spinner
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
    }

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

        content.addView(
            text("✂ ClipMaster AI", 28, cyan, true)
        )

        content.addView(
            text("LOCAL VIDEO CLIP GENERATOR", 11, muted, true)
        )

        content.addView(space(25))

        val hero = newCard()

        hero.addView(
            text("TURN LONG VIDEOS INTO", 13, muted, true)
        )

        hero.addView(
            text("SHORT VIDEO CLIPS", 25, white, true)
        )

        hero.addView(
            text(
                "Pilih video, tentukan durasi, dan buat klip MP4 secara otomatis.",
                14, muted, false
            )
        )

        content.addView(hero)
        content.addView(space(25))

        content.addView(
            section("01 • SELECT VIDEO")
        )

        selectButton = button("▶  PILIH VIDEO DARI HP")

        selectButton.setOnClickListener {
            pickVideo()
        }

        content.addView(selectButton)
        content.addView(space(15))

        val infoCard = newCard()

        infoCard.addView(
            text("VIDEO INFORMATION", 12, cyan, true)
        )

        infoCard.addView(space(8))

        videoInfo = text(
            "Belum ada video dipilih.",
            14, white, true
        )

        infoCard.addView(videoInfo)

        content.addView(infoCard)
        content.addView(space(25))

        content.addView(
            section("02 • CLIP SETTINGS")
        )

        content.addView(
            text("Pilih durasi klip", 13, muted, true)
        )

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

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedDuration = when (position) {
                        0 -> 15
                        1 -> 30
                        else -> 60
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }

        content.addView(durationSpinner)
        content.addView(space(25))

        content.addView(
            section("03 • CREATE CLIP")
        )

        val processCard = newCard()

        processCard.addView(
            text("✦ FFmpeg VIDEO ENGINE", 16, white, true)
        )

        processCard.addView(
            text(
                "Mesin pemrosesan akan memotong video dan menghasilkan file MP4 dengan format H.264 dan AAC.",
                13, muted, false
            )
        )

        content.addView(processCard)
        content.addView(space(20))

        clipButton = button("✦  CREATE VIDEO CLIP")

        clipButton.alpha = 0.5f
        clipButton.isEnabled = false

        clipButton.setOnClickListener {
            createClip()
        }

        content.addView(clipButton)
        content.addView(space(15))

        progress = ProgressBar(this).apply {
            visibility = View.GONE
        }

        content.addView(progress)

        status = text(
            "READY TO CREATE CLIPS",
            12, cyan, true
        )

        status.gravity = Gravity.CENTER

        content.addView(status)
        content.addView(space(30))

        val footer = text(
            "CLIPMASTER AI v1.1.0\nCUT • EDIT • CREATE",
            11, muted, true
        )

        footer.gravity = Gravity.CENTER

        content.addView(footer)

        setContentView(root)
    }

    // SELECT VIDEO

    private fun pickVideo() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }

        startActivityForResult(intent, PICK_VIDEO)
    }

    // RECEIVE SELECTED VIDEO

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data?.data == null) {
            return
        }

        when (requestCode) {

            PICK_VIDEO -> {

                val uri = data.data!!

                selectedUri = uri

                try {
                    val retriever = MediaMetadataRetriever()

                    retriever.setDataSource(this, uri)

                    videoDuration = retriever
                        .extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION
                        )
                        ?.toLongOrNull() ?: 0L

                    retriever.release()

                    val seconds = videoDuration / 1000

                    videoInfo.text =
                        "Video berhasil dipilih\n\n" +
                        "Durasi: ${formatTime(seconds)}\n" +
                        "Sumber: Penyimpanan HP"

                    clipButton.isEnabled = true
                    clipButton.alpha = 1f

                    status.text = "✓ VIDEO READY"

                    toast("Video berhasil dipilih")

                } catch (e: Exception) {

                    selectedUri = null
                    clipButton.isEnabled = false
                    clipButton.alpha = 0.5f

                    videoInfo.text =
                        "Tidak dapat membaca video: ${e.message}"

                    status.text = "VIDEO ERROR"
                }
            }

            SAVE_VIDEO -> {

                val output = pendingOutput

                if (output == null || !output.exists()) {
                    toast("File hasil tidak ditemukan")
                    return
                }

                try {
                    val destination = contentResolver
                        .openOutputStream(data.data!!)

                    if (destination == null) {
                        throw Exception("Gagal membuka lokasi penyimpanan")
                    }

                    FileInputStream(output).use { input ->
                        destination.use { out ->
                            input.copyTo(out)
                        }
                    }

                    status.text = "✓ CLIP BERHASIL DISIMPAN"

                    toast("Video berhasil disimpan!")

                } catch (e: Exception) {

                    status.text = "SAVE FAILED"
                    toast("Gagal menyimpan: ${e.message}")

                } finally {

                    output.delete()
                    pendingOutput = null
                }
            }
        }
    }

    // CREATE CLIP USING FFMPEG

    private fun createClip() {

        val uri = selectedUri

        if (uri == null) {
            toast("Pilih video terlebih dahulu")
            return
        }

        if (processing) return

        processing = true

        clipButton.isEnabled = false
        selectButton.isEnabled = false

        progress.visibility = View.VISIBLE

        status.text = "MENYIAPKAN VIDEO..."

        Thread {

            var inputFile: File? = null
            var outputFile: File? = null

            try {

                inputFile = File(cacheDir, "clipmaster_input.mp4")
                outputFile = File(cacheDir, "clipmaster_output.mp4")

                outputFile.delete()

                contentResolver.openInputStream(uri).use { input ->

                    if (input == null) {
                        throw Exception("Tidak dapat membaca video")
                    }

                    FileOutputStream(inputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (!inputFile.exists() || inputFile.length() == 0L) {
                    throw Exception("File video kosong")
                }

                val duration = selectedDuration

                val inputPath = inputFile.absolutePath
                val outputPath = outputFile.absolutePath

                val command =
                    "-y -i \"$inputPath\" " +
                    "-t $duration " +
                    "-map 0:v:0 -map '0:a?' " +
                    "-c:v libx264 " +
                    "-preset veryfast " +
                    "-crf 23 " +
                    "-pix_fmt yuv420p " +
                    "-c:a aac " +
                    "-b:a 128k " +
                    "-movflags +faststart " +
                    "\"$outputPath\""

                runOnUiThread {
                    status.text = "MEMPROSES VIDEO..."
                }

                FFmpegKit.executeAsync(command) { session ->

                    val success = ReturnCode.isSuccess(
                        session.returnCode
                    )

                    runOnUiThread {

                        processing = false

                        progress.visibility = View.GONE
                        selectButton.isEnabled = true
                        clipButton.isEnabled = selectedUri != null

                        if (success &&
                            outputFile.exists() &&
                            outputFile.length() > 0
                        ) {

                            status.text = "✓ CLIP BERHASIL DIBUAT"

                            pendingOutput = outputFile

                            saveClip()

                        } else {

                            status.text = "VIDEO PROCESSING FAILED"

                            outputFile.delete()

                            val error = session.failStackTrace
                                ?: session.allLogsAsString

                            toast(
                                "FFmpeg gagal: " +
                                error.take(250)
                            )
                        }

                        inputFile.delete()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    processing = false

                    progress.visibility = View.GONE

                    selectButton.isEnabled = true
                    clipButton.isEnabled = selectedUri != null

                    status.text = "PROCESSING ERROR"

                    toast("Error: ${e.message}")
                }

                inputFile?.delete()
                outputFile?.delete()
            }

        }.start()
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

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
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

    private fun section(value: String): TextView {
        return text(value, 14, cyan, true)
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
}
