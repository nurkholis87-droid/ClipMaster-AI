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
import android.provider.OpenableColumns

class MainActivity : Activity() {

private val bg = Color.rgb(10, 16, 32)
private val card = Color.rgb(22, 32, 55)
private val blue = Color.rgb(35, 145, 255)
private val cyan = Color.rgb(0, 220, 255)
private val white = Color.WHITE
private val muted = Color.rgb(155, 170, 195)

private var videoUri: Uri? = null
private var ratio = "9:16"
private var duration = "30 detik"

private lateinit var videoInfo: TextView
private lateinit var generateButton: TextView

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
    }

    val scroll = ScrollView(this)

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(24), dp(20), dp(30))
    }

    scroll.addView(content)
    root.addView(
        scroll,
        LinearLayout.LayoutParams(
            -1, 0, 1f
        )
    )

    // HEADER

    content.addView(
        text("✂ ClipMaster AI", 28, cyan, true)
    )

    content.addView(
        text("AI VIDEO CLIP GENERATOR", 11, muted, true)
    )

    content.addView(space(25))

    // HERO CARD

    val hero = newCard()

    hero.addView(
        text("TURN LONG VIDEOS INTO", 13, muted, true)
    )

    hero.addView(
        text("VIRAL SHORT CLIPS", 25, white, true)
    )

    hero.addView(
        text(
            "Ubah video panjang menjadi klip pendek untuk media sosial.",
            14, muted, false
        )
    )

    hero.addView(space(10))

    hero.addView(
        text("✦ AI POWERED VIDEO EDITING", 11, cyan, true)
    )

    content.addView(hero)

    content.addView(space(24))

    // VIDEO SELECTION

    content.addView(
        section("01  •  PILIH VIDEO")
    )

    val pickButton = button("＋  PILIH VIDEO DARI GALERI")

    pickButton.setOnClickListener {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        startActivityForResult(intent, 100)
    }

    content.addView(pickButton)

    videoInfo = text(
        "Belum ada video dipilih",
        13, muted, false
    )

    content.addView(videoInfo)

    content.addView(space(24))

    // FORMAT

    content.addView(
        section("02  •  FORMAT VIDEO")
    )

    val ratioRow = horizontal()

    listOf("9:16", "1:1", "16:9").forEach { value ->

        val item = option(value, ratio == value)

        item.setOnClickListener {
            ratio = value
            updateRatio(ratioRow)
        }

        ratioRow.addView(item, weightParams())
    }

    content.addView(ratioRow)

    content.addView(space(24))

    // DURATION

    content.addView(
        section("03  •  DURASI KLIP")
    )

    val durationRow = horizontal()

    listOf("15 detik", "30 detik", "60 detik").forEach { value ->

        val item = option(value, duration == value)

        item.setOnClickListener {
            duration = value
            updateDuration(durationRow)
        }

        durationRow.addView(item, weightParams())
    }

    content.addView(durationRow)

    content.addView(space(24))

    // AI FEATURE

    content.addView(
        section("04  •  AI CLIP DETECTION")
    )

    val aiCard = newCard()

    aiCard.addView(
        text("✦ Smart Clip Generator", 17, white, true)
    )

    aiCard.addView(
        text(
            "Siapkan video untuk proses pencarian momen menarik dan pembuatan klip.",
            13, muted, false
        )
    )

    content.addView(aiCard)

    content.addView(space(28))

    // GENERATE BUTTON

    generateButton = button("✦  GENERATE CLIPS")

    generateButton.setOnClickListener {

        if (videoUri == null) {

            Toast.makeText(
                this,
                "Silakan pilih video terlebih dahulu",
                Toast.LENGTH_SHORT
            ).show()

        } else {

            Toast.makeText(
                this,
                "Pengaturan siap. Mesin AI sedang dalam tahap pengembangan.",
                Toast.LENGTH_LONG
            ).show()

        }
    }

    content.addView(generateButton)

    content.addView(space(25))

    // FOOTER

    val footer = text(
        "CLIPMASTER AI  •  VERSION 1.0.0\nCUT  •  EDIT  •  CREATE",
        11, muted, true
    )

    footer.gravity = Gravity.CENTER

    content.addView(footer)

    setContentView(root)
}

private fun updateRatio(row: LinearLayout) {

    row.removeAllViews()

    listOf("9:16", "1:1", "16:9").forEach {

        val item = option(it, ratio == it)

        item.setOnClickListener {
            ratio = it
            updateRatio(row)
        }

        row.addView(item, weightParams())
    }
}

private fun updateDuration(row: LinearLayout) {

    row.removeAllViews()

    listOf("15 detik", "30 detik", "60 detik").forEach {

        val item = option(it, duration == it)

        item.setOnClickListener {
            duration = it
            updateDuration(row)
        }

        row.addView(item, weightParams())
    }
}

@Deprecated("Deprecated in Android API")
override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == 100 &&
        resultCode == RESULT_OK &&
        data?.data != null
    ) {

        videoUri = data.data

        val uri = videoUri!!

        var fileName = "Video berhasil dipilih"

        contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )?.use { cursor ->

            val index = cursor.getColumnIndex(
                OpenableColumns.DISPLAY_NAME
            )

            if (cursor.moveToFirst() && index >= 0) {
                fileName = cursor.getString(index)
            }
        }

        videoInfo.text = "✓ VIDEO TERPILIH\n$fileName"
        videoInfo.setTextColor(cyan)
    }
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

        typeface = if (bold)
            Typeface.DEFAULT_BOLD
        else
            Typeface.DEFAULT

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

}
