package com.clipmaster.ai

import android.app.Activity
import android.os.Bundle
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val bg = Color.rgb(10, 16, 32)
    private val card = Color.rgb(22, 32, 55)
    private val blue = Color.rgb(35, 145, 255)
    private val cyan = Color.rgb(0, 220, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(155, 170, 195)

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var urlInput: EditText
    private lateinit var videoTitle: TextView
    private lateinit var videoAuthor: TextView
    private lateinit var statusText: TextView
    private lateinit var analyzeButton: TextView
    private lateinit var loadButton: TextView

    private var videoUrl = ""
    private var videoLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            text("YOUTUBE VIDEO CLIP GENERATOR", 11, muted, true)
        )

        content.addView(space(25))

        val hero = newCard()

        hero.addView(
            text("TURN LONG VIDEOS INTO", 13, muted, true)
        )

        hero.addView(
            text("VIRAL SHORT CLIPS", 25, white, true)
        )

        hero.addView(
            text(
                "Temukan momen terbaik dan buat klip pendek dengan bantuan AI.",
                14, muted, false
            )
        )

        content.addView(hero)
        content.addView(space(25))

        content.addView(
            section("01  •  YOUTUBE VIDEO URL")
        )

        urlInput = EditText(this).apply {
            hint = "Paste YouTube URL di sini..."
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = rounded(card, 14)
        }

        content.addView(urlInput)
        content.addView(space(12))

        loadButton = button("▶  LOAD VIDEO")

        loadButton.setOnClickListener {
            loadVideo()
        }

        content.addView(loadButton)
        content.addView(space(15))

        val infoCard = newCard()

        infoCard.addView(
            text("VIDEO INFORMATION", 12, cyan, true)
        )

        infoCard.addView(space(10))

        videoTitle = text(
            "Belum ada video dimuat",
            17, white, true
        )

        videoAuthor = text(
            "Informasi kreator akan muncul di sini.",
            13, muted, false
        )

        infoCard.addView(videoTitle)
        infoCard.addView(videoAuthor)

        content.addView(infoCard)
        content.addView(space(25))

        content.addView(
            section("02  •  AI CLIP DETECTION")
        )

        val aiCard = newCard()

        aiCard.addView(
            text("✦ SMART MOMENT DETECTION", 16, white, true)
        )

        aiCard.addView(
            text(
                "AI akan membantu menemukan kandidat momen menarik berdasarkan transkrip dan materi video yang boleh diproses.",
                13, muted, false
            )
        )

        content.addView(aiCard)
        content.addView(space(20))

        content.addView(
            section("03  •  CLIP SETTINGS")
        )

        content.addView(
            text("Format video", 13, muted, true)
        )

        val ratioRow = horizontal()
        val ratios = listOf("9:16", "1:1", "16:9")
        var selectedRatio = "9:16"

        fun updateRatio() {
            ratioRow.removeAllViews()

            ratios.forEach { value ->
                val item = option(
                    value,
                    selectedRatio == value
                )

                item.setOnClickListener {
                    selectedRatio = value
                    updateRatio()
                }

                ratioRow.addView(item, weightParams())
            }
        }

        updateRatio()
        content.addView(ratioRow)
        content.addView(space(18))

        content.addView(
            text("Durasi klip", 13, muted, true)
        )

        val durationRow = horizontal()

        val durations = listOf(
            "15 detik",
            "30 detik",
            "60 detik"
        )

        var selectedDuration = "30 detik"

        fun updateDuration() {
            durationRow.removeAllViews()

            durations.forEach { value ->
                val item = option(
                    value,
                    selectedDuration == value
                )

                item.setOnClickListener {
                    selectedDuration = value
                    updateDuration()
                }

                durationRow.addView(item, weightParams())
            }
        }

        updateDuration()
        content.addView(durationRow)
        content.addView(space(25))

        analyzeButton = button("✦  ANALYZE VIDEO")
        analyzeButton.alpha = 0.5f

        analyzeButton.setOnClickListener {
            if (!videoLoaded) {
                toast("Load video terlebih dahulu")
            } else {
                toast("Tahap berikutnya: AI Video Analysis")
            }
        }

        content.addView(analyzeButton)
        content.addView(space(15))

        statusText = text(
            "READY TO CREATE CLIPS",
            12, cyan, true
        )

        statusText.gravity = Gravity.CENTER
        content.addView(statusText)
        content.addView(space(30))

        val footer = text(
            "CLIPMASTER AI v1.2.0\nCUT • EDIT • CREATE",
            11, muted, true
        )

        footer.gravity = Gravity.CENTER
        content.addView(footer)

        setContentView(root)
    }

    // LOAD VIDEO INFORMATION

    private fun loadVideo() {

        val input = urlInput.text.toString().trim()

        val videoId = extractVideoId(input)

        if (videoId == null) {
            toast("Format URL YouTube tidak valid")
            return
        }

        videoLoaded = false
        videoUrl = input

        videoTitle.text = "Sedang membaca video..."
        videoAuthor.text = "Menghubungi YouTube..."

        statusText.text = "LOADING VIDEO INFORMATION..."

        analyzeButton.alpha = 0.5f
        loadButton.alpha = 0.5f

        val imm = getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager

        imm.hideSoftInputFromWindow(
            urlInput.windowToken, 0
        )

        executor.execute {

            var connection: HttpURLConnection? = null

            try {

                val canonicalUrl =
                    "https://www.youtube.com/watch?v=$videoId"

                val encodedUrl = URLEncoder.encode(
                    canonicalUrl,
                    "UTF-8"
                )

                val apiUrl =
                    "https://www.youtube.com/oembed?url=$encodedUrl&format=json"

                connection =
                    URL(apiUrl).openConnection() as HttpURLConnection

                connection.connectTimeout = 20000
                connection.readTimeout = 20000
                connection.requestMethod = "GET"

                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0"
                )

                val responseCode = connection.responseCode

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP $responseCode")
                }

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val json = JSONObject(response)

                val title = json.optString("title", "")
                val author = json.optString("author_name", "")

                if (title.isBlank()) {
                    throw Exception("Judul video tidak tersedia")
                }

                runOnUiThread {

                    videoTitle.text = title
                    videoAuthor.text = "Creator: $author"

                    videoLoaded = true

                    analyzeButton.alpha = 1f
                    loadButton.alpha = 1f

                    statusText.text =
                        "✓ VIDEO INFORMATION LOADED"

                    toast("Informasi video berhasil dimuat")
                }

            } catch (e: Exception) {

                val errorMessage = e.message
                    ?: "Kesalahan tidak diketahui"

                runOnUiThread {

                    videoLoaded = false

                    videoTitle.text = "Video belum berhasil dimuat"
                    videoAuthor.text = errorMessage

                    analyzeButton.alpha = 0.5f
                    loadButton.alpha = 1f

                    statusText.text =
                        "VIDEO INFORMATION FAILED"

                    toast("Gagal membaca video: $errorMessage")
                }

            } finally {
                connection?.disconnect()
            }
        }
    }

    // EXTRACT YOUTUBE VIDEO ID

    private fun extractVideoId(value: String): String? {

        return try {

            var input = value.trim()

            if (input.isEmpty()) return null

            if (!input.startsWith("http://") &&
                !input.startsWith("https://")
            ) {
                input = "https://$input"
            }

            val uri = Uri.parse(input)

            val host = uri.host
                ?.lowercase()
                ?: return null

            val id = when {

                host == "youtu.be" ||
                host == "www.youtu.be" ->
                    uri.lastPathSegment

                host == "youtube.com" ||
                host == "www.youtube.com" ||
                host == "m.youtube.com" ->
                    when {
                        uri.path == "/watch" ->
                            uri.getQueryParameter("v")

                        uri.path?.startsWith("/shorts/") == true ->
                            uri.pathSegments.getOrNull(1)

                        uri.path?.startsWith("/embed/") == true ->
                            uri.pathSegments.getOrNull(1)

                        uri.path?.startsWith("/live/") == true ->
                            uri.pathSegments.getOrNull(1)

                        else -> null
                    }

                else -> null
            }

            if (id != null &&
                id.matches(Regex("[A-Za-z0-9_-]{11}"))
            ) {
                id
            } else {
                null
            }

        } catch (e: Exception) {
            null
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

    private fun option(
        value: String,
        selected: Boolean
    ): TextView {

        return TextView(this).apply {
            text = value
            textSize = 12f
            gravity = Gravity.CENTER

            setTextColor(
                if (selected) white else muted
            )

            typeface = Typeface.DEFAULT_BOLD

            background = rounded(
                if (selected) blue else card,
                12
            )

            setPadding(
                dp(4), dp(14), dp(4), dp(14)
            )
        }
    }

    private fun horizontal(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
    }

    private fun weightParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            dp(48),
            1f
        ).apply {
            marginEnd = dp(6)
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

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
