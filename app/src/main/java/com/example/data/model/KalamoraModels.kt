package com.example.data.model

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

// --- Enums with Safe Deserialization ---

enum class ShayariCategory(val label: String, val hindiLabel: String) {
    LOVE("Love", "मोहब्बत"),
    SAD("Sad", "दर्द"),
    LIFE("Life", "ज़िंदगी"),
    FRIENDSHIP("Friendship", "दोस्ती"),
    MOTIVATION("Motivation", "प्रेरणा"),
    NATURE("Nature", "प्रकृति"),
    ATTITUDE("Attitude", "अंदाज़"),
    OTHER("Other", "अन्य");

    companion object {
        fun fromRaw(raw: String?): ShayariCategory =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: OTHER
    }
}

enum class WritingLanguage(val label: String) {
    HINDI("Hindi"),
    ENGLISH("English"),
    HINGLISH("Hinglish"),
    URDU("Urdu"),
    MARATHI("Marathi"),
    PUNJABI("Punjabi"),
    BENGALI("Bengali"),
    OTHER("Other");

    companion object {
        fun fromRaw(raw: String?): WritingLanguage =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: HINDI
    }
}

enum class BookGenre(val label: String) {
    FICTION("Fiction"),
    POETRY_COLLECTION("Poetry Collection"),
    ROMANCE("Romance"),
    MEMOIR("Memoir"),
    DRAMA("Drama"),
    PHILOSOPHY("Philosophy"),
    SHORT_STORIES("Short Stories"),
    NON_FICTION("Non-Fiction"),
    OTHER("Other");

    companion object {
        fun fromRaw(raw: String?): BookGenre =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: FICTION
    }
}

enum class PrivacyStatus(val label: String) {
    PRIVATE("Private"),
    PUBLIC("Public");

    companion object {
        fun fromRaw(raw: String?): PrivacyStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PRIVATE
    }
}

enum class PublishStatus(val label: String) {
    DRAFT("Draft"),
    PUBLISHED("Published");

    companion object {
        fun fromRaw(raw: String?): PublishStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DRAFT
    }
}

enum class ShayariCardTheme(
    val label: String,
    val backgroundHex: Long,
    val accentHex: Long,
    val textHex: Long,
    val subtextHex: Long
) {
    PARCHMENT("Warm Parchment", 0xFFFAF3E4, 0xFFB6833A, 0xFF231F1D, 0xFF6B5C4E),
    ESPRESSO("Espresso Gold", 0xFF2C1E18, 0xFFD4A359, 0xFFF7EFE2, 0xFFCDB9A0),
    MARIGOLD("Marigold Dusk", 0xFFF7E5C8, 0xFFA65E2E, 0xFF2B1D14, 0xFF744C33),
    MIDNIGHT("Midnight Ink", 0xFF1A2230, 0xFFD4B270, 0xFFF2EDE4, 0xFFAAB4C4),
    TERRACOTTA("Terracotta Linen", 0xFF5E3A2A, 0xFFE3BA78, 0xFFFAF3E8, 0xFFDEC5AD);

    val backgroundColor: Color get() = Color(backgroundHex)
    val accentColor: Color get() = Color(accentHex)
    val textColor: Color get() = Color(textHex)
    val subtextColor: Color get() = Color(subtextHex)

    companion object {
        fun fromRaw(raw: String?): ShayariCardTheme =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PARCHMENT
    }
}

enum class BookCoverStyle(
    val label: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val goldHex: Long,
    val textHex: Long
) {
    MAHOGANY("Mahogany Linen", 0xFF5E3A2A, 0xFF3E2419, 0xFFD8AB62, 0xFFFAF3E8),
    IVORY_GOLD("Ivory & Gold", 0xFFF3E9D7, 0xFFE5D4B7, 0xFFA3722B, 0xFF261E19),
    DEEP_FOREST("Forest Cloth", 0xFF274237, 0xFF1A2E26, 0xFFD4AC63, 0xFFF4EFE6),
    MIDNIGHT_INK("Midnight Velvet", 0xFF1F2736, 0xFF131924, 0xFFD4AF6A, 0xFFF2ECE1),
    SANDSTONE("Warm Sandstone", 0xFF8C5A3C, 0xFF68412B, 0xFFF0CB88, 0xFFFFF8EE);

    val primaryColor: Color get() = Color(primaryHex)
    val secondaryColor: Color get() = Color(secondaryHex)
    val goldColor: Color get() = Color(goldHex)
    val textColor: Color get() = Color(textHex)

    companion object {
        fun fromRaw(raw: String?): BookCoverStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MAHOGANY
    }
}

enum class ChapterAlignment(val label: String) {
    START("Left"),
    CENTER("Center"),
    END("Right"),
    JUSTIFY("Justify");

    fun toComposeTextAlign(): TextAlign = when (this) {
        START -> TextAlign.Start
        CENTER -> TextAlign.Center
        END -> TextAlign.End
        JUSTIFY -> TextAlign.Justify
    }

    companion object {
        fun fromRaw(raw: String?): ChapterAlignment =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: START
    }
}

// --- Firestore Domain Data Classes ---

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val penName: String = "",
    val bio: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val preferredLanguage: String = WritingLanguage.HINDI.label,
    val publicProfileEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveAuthorName: String
        get() = penName.trim().ifBlank { displayName.trim().ifBlank { "Kalamora Writer" } }
}

@Keep
@IgnoreExtraProperties
data class Shayari(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = ShayariCategory.LIFE.label,
    val language: String = WritingLanguage.HINDI.label,
    val privacyStatus: String = PrivacyStatus.PRIVATE.name,
    val publishStatus: String = PublishStatus.DRAFT.name,
    val favorite: Boolean = false,
    val wordCount: Long = 0L,
    val charCount: Long = 0L,
    val authorName: String = "",
    val cardTheme: String = ShayariCardTheme.PARCHMENT.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Book(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val authorName: String = "",
    val description: String = "",
    val coverStyle: String = BookCoverStyle.MAHOGANY.name,
    val language: String = WritingLanguage.HINDI.label,
    val genre: String = BookGenre.FICTION.label,
    val privacyStatus: String = PrivacyStatus.PRIVATE.name,
    val publishStatus: String = PublishStatus.DRAFT.name,
    val favorite: Boolean = false,
    val chapterCount: Long = 0L,
    val totalWordCount: Long = 0L,
    val totalCharCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Chapter(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val title: String = "",
    val content: String = "",
    val orderIndex: Long = 0L,
    val textAlignment: String = ChapterAlignment.START.name,
    val wordCount: Long = 0L,
    val charCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// --- Save / Sync Status & UI State ---

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface SaveSyncState {
    data object Idle : SaveSyncState
    data object UnsavedChanges : SaveSyncState
    data object Saving : SaveSyncState
    data object SavedToCloud : SaveSyncState
    data object SavedLocallyPendingSync : SaveSyncState
    data class SyncFailedLocally(val message: String) : SaveSyncState
}

// --- Unicode-Safe Word/Character Counting & Lightweight Rich Text Formatter ---

object TextMetrics {
    private val whitespaceRegex = Regex("\\s+")

    fun countWords(text: String): Long {
        val cleaned = stripFormattingMarkers(text).trim()
        if (cleaned.isEmpty()) return 0L
        return cleaned.split(whitespaceRegex).count { it.isNotBlank() }.toLong()
    }

    fun countCharacters(text: String): Long = stripFormattingMarkers(text).length.toLong()

    fun stripFormattingMarkers(text: String): String {
        return text
            .replace(Regex("(?m)^#{1,2}\\s+"), "")
            .replace("**", "")
            .replace("__", "")
            .replace("*", "")
    }
}

object RichTextFormatter {
    fun wrapSelection(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        prefix: String,
        suffix: String = prefix
    ): Pair<String, Int> {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)
        val selected = text.substring(start, end)
        val replacement = if (selected.isEmpty()) "$prefix$suffix" else "$prefix$selected$suffix"
        val updated = text.substring(0, start) + replacement + text.substring(end)
        val cursor = if (selected.isEmpty()) start + prefix.length else start + replacement.length
        return updated to cursor
    }

    fun applyLinePrefix(
        text: String,
        cursorPosition: Int,
        prefix: String
    ): Pair<String, Int> {
        val pos = cursorPosition.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val updated = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        return updated to (pos + prefix.length)
    }

    fun renderFormattedText(raw: String): AnnotatedString = buildAnnotatedString {
        val lines = raw.split("\n")
        lines.forEachIndexed { lineIdx, line ->
            val trimmedStart = line.trimStart()
            when {
                trimmedStart.startsWith("# ") -> {
                    val headingText = trimmedStart.removePrefix("# ")
                    val startIdx = length
                    appendInlineFormatted(headingText)
                    addStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold), startIdx, length)
                }
                trimmedStart.startsWith("## ") -> {
                    val headingText = trimmedStart.removePrefix("## ")
                    val startIdx = length
                    appendInlineFormatted(headingText)
                    addStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), startIdx, length)
                }
                else -> appendInlineFormatted(line)
            }
            if (lineIdx < lines.lastIndex) append("\n")
        }
    }

    private fun AnnotatedString.Builder.appendInlineFormatted(line: String) {
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("**", i) -> {
                    val close = line.indexOf("**", i + 2)
                    if (close != -1) {
                        val start = length
                        append(line.substring(i + 2, close))
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                        i = close + 2
                    } else {
                        append(line[i])
                        i++
                    }
                }
                line.startsWith("__", i) -> {
                    val close = line.indexOf("__", i + 2)
                    if (close != -1) {
                        val start = length
                        append(line.substring(i + 2, close))
                        addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, length)
                        i = close + 2
                    } else {
                        append(line[i])
                        i++
                    }
                }
                line.startsWith("*", i) -> {
                    val close = line.indexOf("*", i + 1)
                    if (close != -1) {
                        val start = length
                        append(line.substring(i + 1, close))
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                        i = close + 1
                    } else {
                        append(line[i])
                        i++
                    }
                }
                else -> {
                    append(line[i])
                    i++
                }
            }
        }
    }
}
