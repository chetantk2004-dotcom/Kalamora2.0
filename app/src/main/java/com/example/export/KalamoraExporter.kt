package com.example.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChapterAlignment
import com.example.data.model.Shayari
import com.example.data.model.ShayariCardTheme
import com.example.data.model.TextMetrics
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportedFile(
    val shareUri: Uri,
    val fileName: String,
    val mimeType: String,
    val savedDisplayLocation: String
)

object KalamoraExporter {

    private const val PDF_PAGE_WIDTH = 595 // A4 points (72 dpi)
    private const val PDF_PAGE_HEIGHT = 842
    private const val PDF_MARGIN_X = 56f
    private const val PDF_MARGIN_TOP = 64f
    private const val PDF_MARGIN_BOTTOM = 64f

    private fun loadDevanagariSerif(context: Context): Typeface {
        return ResourcesCompat.getFont(context, R.font.noto_serif_devanagari) ?: Typeface.SERIF
    }

    private fun loadDisplaySerif(context: Context): Typeface {
        return ResourcesCompat.getFont(context, R.font.playfair_display) ?: Typeface.SERIF
    }

    private fun loadUiSans(context: Context): Typeface {
        return ResourcesCompat.getFont(context, R.font.noto_sans_devanagari) ?: Typeface.SANS_SERIF
    }

    private fun sanitizeFileName(raw: String, fallback: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^a-zA-Z0-9\\u0900-\\u097F _-]"), "")
            .replace(Regex("\\s+"), "_")
            .take(40)
        return cleaned.ifBlank { fallback }
    }

    private fun buildStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        lineSpacingMult: Float = 1.38f
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(alignment)
            .setLineSpacing(0f, lineSpacingMult)
            .setIncludePad(true)
            .build()
    }

    private fun mapAlignment(alignment: ChapterAlignment): Layout.Alignment = when (alignment) {
        ChapterAlignment.START -> Layout.Alignment.ALIGN_NORMAL
        ChapterAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        ChapterAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
        ChapterAlignment.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
    }

    // --- 1. SHAREABLE SHAYARI IMAGE RENDERING (HIGH-RES 1080x1350 PNG WITH HINDI UNICODE SUPPORT) ---

    fun renderShayariCardBitmap(
        context: Context,
        shayari: Shayari,
        theme: ShayariCardTheme
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val devSerif = loadDevanagariSerif(context)
        val displaySerif = loadDisplaySerif(context)
        val uiSans = loadUiSans(context)

        // Background fill
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.backgroundHex.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Outer & inner fine ornamental gold frames
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentHex.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
            alpha = 180
        }
        canvas.drawRoundRect(RectF(48f, 48f, width - 48f, height - 48f), 28f, 28f, framePaint)

        val innerFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentHex.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            alpha = 110
        }
        canvas.drawRoundRect(RectF(66f, 66f, width - 66f, height - 66f), 20f, 20f, innerFramePaint)

        // Top ornamental divider & category pill
        val categoryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentHex.toInt()
            textSize = 30f
            typeface = uiSans
            letterSpacing = 0.14f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(shayari.category.uppercase(Locale.getDefault()), width / 2f, 152f, categoryPaint)

        // Decorative quote mark
        val quoteMarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentHex.toInt()
            alpha = 150
            textSize = 110f
            typeface = displaySerif
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("“", width / 2f, 278f, quoteMarkPaint)

        // Optional Shayari Title
        val contentMaxWidth = width - 200
        var contentTopY = 300f
        if (shayari.title.isNotBlank()) {
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.accentHex.toInt()
                textSize = 44f
                typeface = Typeface.create(devSerif, Typeface.BOLD)
            }
            val titleLayout = buildStaticLayout(
                shayari.title.trim(),
                titlePaint,
                contentMaxWidth,
                Layout.Alignment.ALIGN_CENTER,
                1.25f
            )
            canvas.save()
            canvas.translate(100f, contentTopY)
            titleLayout.draw(canvas)
            canvas.restore()
            contentTopY += titleLayout.height + 42f
        }

        // Shayari Body (auto-sized for long vs short verses while preserving Hindi matras)
        val bodyText = shayari.content.trim().ifBlank { "…" }
        val chosenFontSize = when {
            bodyText.length > 420 -> 34f
            bodyText.length > 240 -> 40f
            bodyText.length > 130 -> 46f
            else -> 54f
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textHex.toInt()
            textSize = chosenFontSize
            typeface = devSerif
        }
        val bodyLayout = buildStaticLayout(
            bodyText,
            bodyPaint,
            contentMaxWidth,
            Layout.Alignment.ALIGN_CENTER,
            1.45f
        )

        val availableCenterAreaTop = contentTopY
        val availableCenterAreaBottom = height - 270f
        val availableHeight = (availableCenterAreaBottom - availableCenterAreaTop).coerceAtLeast(100f)
        val drawY = availableCenterAreaTop + ((availableHeight - bodyLayout.height) / 2f).coerceAtLeast(0f)

        canvas.save()
        canvas.clipRect(90f, availableCenterAreaTop - 10f, width - 90f, availableCenterAreaBottom)
        canvas.translate(100f, drawY)
        bodyLayout.draw(canvas)
        canvas.restore()

        // Horizontal gold rule above author attribution
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentHex.toInt()
            strokeWidth = 2f
            alpha = 150
        }
        canvas.drawLine(width / 2f - 90f, height - 220f, width / 2f + 90f, height - 220f, rulePaint)

        // Author attribution
        val authorText = "— ${shayari.authorName.ifBlank { "Kalamora Writer" }}"
        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.subtextHex.toInt()
            textSize = 34f
            typeface = Typeface.create(devSerif, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(authorText, width / 2f, height - 160f, authorPaint)

        // Subtle Kalamora studio watermark
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentHex.toInt()
            alpha = 170
            textSize = 24f
            typeface = displaySerif
            letterSpacing = 0.22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("KALAMORA · कलम की दुनिया", width / 2f, height - 102f, brandPaint)

        return bitmap
    }

    fun exportShayariImage(
        context: Context,
        shayari: Shayari,
        theme: ShayariCardTheme
    ): Result<ExportedFile> = runCatching {
        val bitmap = renderShayariCardBitmap(context, shayari, theme)
        val safeTitle = sanitizeFileName(shayari.title, "Kalamora_Shayari")
        val fileName = "${safeTitle}_${System.currentTimeMillis() % 10000}.png"

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val cacheFile = File(exportDir, fileName)
        FileOutputStream(cacheFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Also save to user Pictures/Kalamora via MediaStore (zero permissions on API 29+)
        val savedLocation = saveImageToMediaStore(context, bitmap, fileName)
        bitmap.recycle()

        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
        ExportedFile(
            shareUri = shareUri,
            fileName = fileName,
            mimeType = "image/png",
            savedDisplayLocation = savedLocation
        )
    }

    private fun saveImageToMediaStore(context: Context, bitmap: Bitmap, fileName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Kalamora")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    "Pictures/Kalamora/$fileName"
                } else {
                    "App Exports/$fileName"
                }
            } else {
                val appDir = File(context.filesDir, "exports").apply { mkdirs() }
                val outFile = File(appDir, fileName)
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                "App Exports/$fileName"
            }
        } catch (_: Exception) {
            "App Exports/$fileName"
        }
    }

    // --- 2. SHAYARI PDF EXPORT (UNICODE & HINDI DEVANAGARI SAFE) ---

    fun exportShayariPdf(
        context: Context,
        shayari: Shayari
    ): Result<ExportedFile> = runCatching {
        val devSerif = loadDevanagariSerif(context)
        val displaySerif = loadDisplaySerif(context)
        val uiSans = loadUiSans(context)
        val pdf = PdfDocument()

        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            // Warm ivory page tint
            canvas.drawColor(0xFFFAF6EE.toInt())

            // Gold border frame
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFB6833A.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }
            canvas.drawRect(36f, 36f, PDF_PAGE_WIDTH - 36f, PDF_PAGE_HEIGHT - 36f, borderPaint)

            val contentWidth = (PDF_PAGE_WIDTH - PDF_MARGIN_X * 2).toInt()
            var y = 90f

            // Header label
            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFB6833A.toInt()
                textSize = 10f
                typeface = uiSans
                letterSpacing = 0.18f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "KALAMORA · ${shayari.category.uppercase(Locale.getDefault())} · ${shayari.language.uppercase(Locale.getDefault())}",
                PDF_PAGE_WIDTH / 2f,
                y,
                headerPaint
            )
            y += 42f

            // Title
            if (shayari.title.isNotBlank()) {
                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF231F1D.toInt()
                    textSize = 22f
                    typeface = Typeface.create(devSerif, Typeface.BOLD)
                }
                val titleLayout = buildStaticLayout(
                    shayari.title.trim(),
                    titlePaint,
                    contentWidth,
                    Layout.Alignment.ALIGN_CENTER,
                    1.3f
                )
                canvas.save()
                canvas.translate(PDF_MARGIN_X, y)
                titleLayout.draw(canvas)
                canvas.restore()
                y += titleLayout.height + 28f
            }

            // Divider
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFD4A359.toInt()
                strokeWidth = 1f
            }
            canvas.drawLine(PDF_PAGE_WIDTH / 2f - 50f, y, PDF_PAGE_WIDTH / 2f + 50f, y, linePaint)
            y += 36f

            // Content
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF231F1D.toInt()
                textSize = 15f
                typeface = devSerif
            }
            val bodyLayout = buildStaticLayout(
                shayari.content.trim().ifBlank { "…" },
                bodyPaint,
                contentWidth,
                Layout.Alignment.ALIGN_CENTER,
                1.6f
            )
            canvas.save()
            canvas.translate(PDF_MARGIN_X, y)
            bodyLayout.draw(canvas)
            canvas.restore()
            y += bodyLayout.height + 44f

            // Author Attribution
            val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF5E3A2A.toInt()
                textSize = 13f
                typeface = Typeface.create(devSerif, Typeface.ITALIC)
                textAlign = Paint.Align.CENTER
            }
            val authorName = shayari.authorName.ifBlank { "Kalamora Writer" }
            canvas.drawText("— $authorName", PDF_PAGE_WIDTH / 2f, y.coerceAtMost(PDF_PAGE_HEIGHT - 100f), authorPaint)

            // Footer
            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(
                shayari.updatedAt?.toDate() ?: Date()
            )
            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF8A7A6B.toInt()
                textSize = 9f
                typeface = displaySerif
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Kalamora – कलम की दुनिया · $dateStr", PDF_PAGE_WIDTH / 2f, PDF_PAGE_HEIGHT - 54f, footerPaint)

            pdf.finishPage(page)

            val safeName = sanitizeFileName(shayari.title, "Kalamora_Shayari")
            val fileName = "${safeName}_Shayari.pdf"
            persistAndWrapPdf(context, pdf, fileName)
        } finally {
            pdf.close()
        }
    }

    // --- 3. COMPLETE MULTI-CHAPTER BOOK PDF EXPORT (COVER + TOC + CHAPTERS + PAGE NUMBERS) ---

    fun exportBookPdf(
        context: Context,
        book: Book,
        chapters: List<Chapter>
    ): Result<ExportedFile> = runCatching {
        val devSerif = loadDevanagariSerif(context)
        val displaySerif = loadDisplaySerif(context)
        val uiSans = loadUiSans(context)
        val sortedChapters = chapters.sortedBy { it.orderIndex }

        val contentWidth = (PDF_PAGE_WIDTH - PDF_MARGIN_X * 2).toInt()
        val usableBodyHeight = PDF_PAGE_HEIGHT - PDF_MARGIN_TOP - PDF_MARGIN_BOTTOM - 28f

        val chapterTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5E3A2A.toInt()
            textSize = 20f
            typeface = Typeface.create(devSerif, Typeface.BOLD)
        }
        val heading1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF231F1D.toInt()
            textSize = 15.5f
            typeface = Typeface.create(devSerif, Typeface.BOLD)
        }
        val heading2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF3E261B.toInt()
            textSize = 13.5f
            typeface = Typeface.create(devSerif, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF231F1D.toInt()
            textSize = 12f
            typeface = devSerif
        }

        // Pre-paginate all chapters so the Table of Contents has exact, truthful page numbers!
        data class RenderedBlock(val layout: StaticLayout, val topSpacing: Float)
        data class ChapterPageChunk(val chapterTitle: String, val isFirstPageOfChapter: Boolean, val blocks: List<RenderedBlock>)

        val chapterStartPages = mutableListOf<Pair<String, Int>>()
        val allChapterPages = mutableListOf<ChapterPageChunk>()
        var runningPageNum = 3 // Page 1 = Cover, Page 2 = Table of Contents

        for ((index, chapter) in sortedChapters.withIndex()) {
            val chapterHeaderText = "${index + 1}. ${chapter.title.ifBlank { "Chapter ${index + 1}" }}"
            chapterStartPages.add(chapterHeaderText to runningPageNum)

            val align = mapAlignment(ChapterAlignment.fromRaw(chapter.textAlignment))
            val lines = chapter.content.split("\n")
            val currentBlocks = mutableListOf<RenderedBlock>()
            var isFirst = true

            val titleLayout = buildStaticLayout(chapterHeaderText, chapterTitlePaint, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1.3f)
            var currentY = titleLayout.height + 24f

            for (rawLine in lines) {
                val trimmed = rawLine.trimStart()
                val (paint, cleanText, extraGap) = when {
                    trimmed.startsWith("# ") -> Triple(heading1Paint, TextMetrics.stripFormattingMarkers(trimmed.removePrefix("# ")), 14f)
                    trimmed.startsWith("## ") -> Triple(heading2Paint, TextMetrics.stripFormattingMarkers(trimmed.removePrefix("## ")), 10f)
                    rawLine.isBlank() -> Triple(bodyPaint, " ", 6f)
                    else -> Triple(bodyPaint, TextMetrics.stripFormattingMarkers(rawLine), 6f)
                }

                val layout = buildStaticLayout(cleanText, paint, contentWidth, align, 1.45f)
                if (currentY + layout.height + extraGap > usableBodyHeight && currentBlocks.isNotEmpty()) {
                    allChapterPages.add(ChapterPageChunk(chapterHeaderText, isFirst, currentBlocks.toList()))
                    currentBlocks.clear()
                    isFirst = false
                    runningPageNum++
                    currentY = 0f
                }
                currentBlocks.add(RenderedBlock(layout, extraGap))
                currentY += layout.height + extraGap
            }
            allChapterPages.add(ChapterPageChunk(chapterHeaderText, isFirst, currentBlocks.toList()))
            runningPageNum++
        }

        val pdf = PdfDocument()
        try {
            // --- PAGE 1: COVER PAGE ---
            val coverPageInfo = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, 1).create()
            val coverPage = pdf.startPage(coverPageInfo)
            val coverCanvas = coverPage.canvas
            coverCanvas.drawColor(0xFFFAF5EC.toInt())

            val coverFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF5E3A2A.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
            }
            coverCanvas.drawRect(36f, 36f, PDF_PAGE_WIDTH - 36f, PDF_PAGE_HEIGHT - 36f, coverFramePaint)
            val coverInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFB6833A.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            coverCanvas.drawRect(44f, 44f, PDF_PAGE_WIDTH - 44f, PDF_PAGE_HEIGHT - 44f, coverInnerPaint)

            val genrePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFB6833A.toInt()
                textSize = 11f
                typeface = uiSans
                letterSpacing = 0.2f
                textAlign = Paint.Align.CENTER
            }
            coverCanvas.drawText(
                "${book.genre.uppercase(Locale.getDefault())} · ${book.language.uppercase(Locale.getDefault())}",
                PDF_PAGE_WIDTH / 2f,
                160f,
                genrePaint
            )

            val coverTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF231F1D.toInt()
                textSize = 32f
                typeface = Typeface.create(devSerif, Typeface.BOLD)
            }
            val coverTitleLayout = buildStaticLayout(
                book.title.ifBlank { "Untitled Manuscript" },
                coverTitlePaint,
                contentWidth,
                Layout.Alignment.ALIGN_CENTER,
                1.25f
            )
            coverCanvas.save()
            coverCanvas.translate(PDF_MARGIN_X, 240f)
            coverTitleLayout.draw(coverCanvas)
            coverCanvas.restore()

            var coverY = 240f + coverTitleLayout.height + 28f
            val goldRule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFB6833A.toInt()
                strokeWidth = 1.5f
            }
            coverCanvas.drawLine(PDF_PAGE_WIDTH / 2f - 60f, coverY, PDF_PAGE_WIDTH / 2f + 60f, coverY, goldRule)
            coverY += 36f

            val coverAuthorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF5E3A2A.toInt()
                textSize = 16f
                typeface = Typeface.create(devSerif, Typeface.ITALIC)
                textAlign = Paint.Align.CENTER
            }
            coverCanvas.drawText(
                "By ${book.authorName.ifBlank { "Kalamora Writer" }}",
                PDF_PAGE_WIDTH / 2f,
                coverY,
                coverAuthorPaint
            )
            coverY += 44f

            if (book.description.isNotBlank()) {
                val descPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF5C534A.toInt()
                    textSize = 11.5f
                    typeface = devSerif
                }
                val descLayout = buildStaticLayout(
                    book.description.trim(),
                    descPaint,
                    contentWidth - 40,
                    Layout.Alignment.ALIGN_CENTER,
                    1.5f
                )
                coverCanvas.save()
                coverCanvas.translate(PDF_MARGIN_X + 20f, coverY)
                descLayout.draw(coverCanvas)
                coverCanvas.restore()
            }

            val imprintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF8A7A6B.toInt()
                textSize = 10f
                typeface = displaySerif
                letterSpacing = 0.16f
                textAlign = Paint.Align.CENTER
            }
            coverCanvas.drawText("KALAMORA PUBLISHING STUDIO", PDF_PAGE_WIDTH / 2f, PDF_PAGE_HEIGHT - 84f, imprintPaint)
            coverCanvas.drawText("Every Word Has a Story.", PDF_PAGE_WIDTH / 2f, PDF_PAGE_HEIGHT - 66f, imprintPaint)
            pdf.finishPage(coverPage)

            // --- PAGE 2: TABLE OF CONTENTS ---
            val tocPageInfo = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, 2).create()
            val tocPage = pdf.startPage(tocPageInfo)
            val tocCanvas = tocPage.canvas
            tocCanvas.drawColor(0xFFFFFFFF.toInt())

            val tocHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF5E3A2A.toInt()
                textSize = 22f
                typeface = Typeface.create(displaySerif, Typeface.BOLD)
            }
            tocCanvas.drawText("Table of Contents", PDF_MARGIN_X, PDF_MARGIN_TOP + 24f, tocHeaderPaint)
            tocCanvas.drawLine(PDF_MARGIN_X, PDF_MARGIN_TOP + 38f, PDF_PAGE_WIDTH - PDF_MARGIN_X, PDF_MARGIN_TOP + 38f, goldRule)

            val tocEntryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF231F1D.toInt()
                textSize = 12.5f
                typeface = devSerif
            }
            val tocPageNumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF76695D.toInt()
                textSize = 12f
                typeface = uiSans
                textAlign = Paint.Align.RIGHT
            }
            var tocY = PDF_MARGIN_TOP + 72f
            for ((chTitle, pageNumber) in chapterStartPages) {
                if (tocY > PDF_PAGE_HEIGHT - PDF_MARGIN_BOTTOM - 20f) break
                val shortTitle = if (chTitle.length > 55) chTitle.take(52) + "…" else chTitle
                tocCanvas.drawText(shortTitle, PDF_MARGIN_X, tocY, tocEntryPaint)
                tocCanvas.drawText(pageNumber.toString(), PDF_PAGE_WIDTH - PDF_MARGIN_X, tocY, tocPageNumPaint)
                tocY += 28f
            }
            drawPageFooter(tocCanvas, book.title, 2, uiSans)
            pdf.finishPage(tocPage)

            // --- PAGES 3+: CHAPTERS ---
            var pageNo = 3
            for (chunk in allChapterPages) {
                val pInfo = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, pageNo).create()
                val page = pdf.startPage(pInfo)
                val canvas = page.canvas
                canvas.drawColor(0xFFFFFFFF.toInt())

                // Running header
                val runningHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF9B8E80.toInt()
                    textSize = 9f
                    typeface = uiSans
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(book.title, PDF_PAGE_WIDTH / 2f, 36f, runningHeaderPaint)

                var y = PDF_MARGIN_TOP
                if (chunk.isFirstPageOfChapter) {
                    val chTitleLayout = buildStaticLayout(
                        chunk.chapterTitle,
                        chapterTitlePaint,
                        contentWidth,
                        Layout.Alignment.ALIGN_NORMAL,
                        1.3f
                    )
                    canvas.save()
                    canvas.translate(PDF_MARGIN_X, y)
                    chTitleLayout.draw(canvas)
                    canvas.restore()
                    y += chTitleLayout.height + 10f
                    canvas.drawLine(PDF_MARGIN_X, y, PDF_MARGIN_X + 70f, y, goldRule)
                    y += 18f
                }

                for (block in chunk.blocks) {
                    canvas.save()
                    canvas.translate(PDF_MARGIN_X, y)
                    block.layout.draw(canvas)
                    canvas.restore()
                    y += block.layout.height + block.topSpacing
                }

                drawPageFooter(canvas, book.title, pageNo, uiSans)
                pdf.finishPage(page)
                pageNo++
            }

            val safeName = sanitizeFileName(book.title, "Kalamora_Book")
            val fileName = "${safeName}_Book.pdf"
            persistAndWrapPdf(context, pdf, fileName)
        } finally {
            pdf.close()
        }
    }

    private fun drawPageFooter(canvas: Canvas, bookTitle: String, pageNumber: Int, typeface: Typeface) {
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8A7A6B.toInt()
            textSize = 9.5f
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("— $pageNumber —", PDF_PAGE_WIDTH / 2f, PDF_PAGE_HEIGHT - 32f, footerPaint)
    }

    private fun persistAndWrapPdf(context: Context, pdf: PdfDocument, fileName: String): ExportedFile {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val cacheFile = File(exportDir, fileName)
        FileOutputStream(cacheFile).use { out ->
            pdf.writeTo(out)
        }

        val savedLocation = savePdfToDownloads(context, cacheFile, fileName)
        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
        return ExportedFile(
            shareUri = shareUri,
            fileName = fileName,
            mimeType = "application/pdf",
            savedDisplayLocation = savedLocation
        )
    }

    private fun savePdfToDownloads(context: Context, sourceFile: File, fileName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Kalamora")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    "Downloads/Kalamora/$fileName"
                } else {
                    "App Exports/$fileName"
                }
            } else {
                val appDir = File(context.filesDir, "exports").apply { mkdirs() }
                val outFile = File(appDir, fileName)
                sourceFile.copyTo(outFile, overwrite = true)
                "App Exports/$fileName"
            }
        } catch (_: Exception) {
            "App Exports/$fileName"
        }
    }

    fun shareExportedFile(context: Context, exportedFile: ExportedFile) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = exportedFile.mimeType
            putExtra(Intent.EXTRA_STREAM, exportedFile.shareUri)
            putExtra(Intent.EXTRA_SUBJECT, exportedFile.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share via Kalamora").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun sharePlainShayariText(context: Context, shayari: Shayari) {
        val text = buildString {
            if (shayari.title.isNotBlank()) {
                append(shayari.title.trim())
                append("\n\n")
            }
            append(shayari.content.trim())
            append("\n\n— ")
            append(shayari.authorName.ifBlank { "Kalamora Writer" })
            append("\nWritten on Kalamora · कलम की दुनिया")
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Shayari").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
