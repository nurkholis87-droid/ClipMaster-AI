package com.clipmaster.ai

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var clipSeconds = 60
    private var captionsEnabled = true
    private val durations = listOf(30, 45, 60, 90)

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
                    val grantFlags = (result.data?.flags ?: 0) and
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (grantFlags != 0) {
                        try {
                            contentResolver.takePersistableUriPermission(uri, grantFlags)
                        } catch (_: SecurityException) {
                        }
                    }
                    selectedName = getFileName(uri)
                    videoInfo.text = "Video: $selectedName\nDurasi: ${getVideoDuration(uri)}"
                    addLog("Video dipilih: $selectedName")
                }
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                addLog("Izin notifikasi diberikan; status proses dapat ditampilkan")
            } else {
                addLog("Izin notifikasi ditolak; aktifkan melalui Setelan > Aplikasi > ClipMaster AI > Notifikasi")
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
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
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

    private val processingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ProcessingForegroundService.ACTION_PROGRESS) return
            intent.getStringExtra(ProcessingForegroundService.EXTRA_LOG)?.let { addLog(it) }
            intent.getStringExtra(ProcessingForegroundService.EXTRA_STATUS)?.let {
                updateStatus(it, intent.getBooleanExtra(ProcessingForegroundService.EXTRA_SUCCESS, false))
            }
            if (intent.getBooleanExtra(ProcessingForegroundService.EXTRA_DONE, false)) {
                setProcessingUi(false)
            }
        }
    }

    private fun setProcessingUi(processing: Boolean) {
        pickButton.isEnabled = !processing
        createButton.isEnabled = !processing
        durationSpinner.isEnabled = !processing
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            processingReceiver,
            IntentFilter(ProcessingForegroundService.ACTION_PROGRESS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val prefs = getSharedPreferences(
            ProcessingForegroundService.PREFS_NAME,
            MODE_PRIVATE
        )
        setProcessingUi(prefs.getBoolean(ProcessingForegroundService.PREF_IS_PROCESSING, false))
        prefs.getString(ProcessingForegroundService.PREF_STATUS, null)?.let {
            updateStatus(it, prefs.getBoolean(ProcessingForegroundService.PREF_SUCCESS, false))
        }
    }

    override fun onStop() {
        unregisterReceiver(processingReceiver)
        super.onStop()
    }

    private fun startProcessing(outputUri: Uri) {
        val inputUri = selectedUri ?: return
        val selectedDuration = clipSeconds

        setProcessingUi(true)
        statusText.text = "Status: Memproses di latar belakang..."
        statusText.setTextColor(android.graphics.Color.YELLOW)
        logText.text = ""
        addLog("--------------------------------")
        addLog("MEMULAI AUTO HIGHLIGHT 3.2")
        addLog("Target durasi: $selectedDuration detik")

        try {
            val serviceIntent = Intent(this, ProcessingForegroundService::class.java).apply {
                action = ProcessingForegroundService.ACTION_START
                putExtra(ProcessingForegroundService.EXTRA_INPUT_URI, inputUri.toString())
                putExtra(ProcessingForegroundService.EXTRA_OUTPUT_URI, outputUri.toString())
                putExtra(ProcessingForegroundService.EXTRA_DURATION, selectedDuration)
                putExtra(ProcessingForegroundService.EXTRA_CAPTIONS, captionsEnabled)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            addLog("Layanan foreground mengambil alih seluruh proses; aplikasi boleh diminimize")
        } catch (e: Exception) {
            setProcessingUi(false)
            updateStatus("Status: GAGAL MEMULAI LAYANAN", false)
            addLog("Tidak dapat memulai layanan background: ${e.message}")
        }
    }

}
