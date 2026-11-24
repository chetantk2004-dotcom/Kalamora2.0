package com.example

import com.example.data.model.BookCoverStyle
import com.example.data.model.BookGenre
import com.example.data.model.ChapterAlignment
import com.example.data.model.PrivacyStatus
import com.example.data.model.PublishStatus
import com.example.data.model.RichTextFormatter
import com.example.data.model.ShayariCardTheme
import com.example.data.model.ShayariCategory
import com.example.data.model.TextMetrics
import com.example.data.model.WritingLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KalamoraUnitTest {

    @Test
    fun textMetrics_countsHindiDevanagariAndEnglishWords() {
        val hindiShayari = "वक़्त की रेत पर कुछ ख़्वाब लिख दिए,\nहमने ख़ामोशी में भी सौ जवाब लिख दिए।"
        assertEquals(16L, TextMetrics.countWords(hindiShayari))
        assertTrue(TextMetrics.countCharacters(hindiShayari) > 50L)

        val formattedChapter = "# अध्याय 1\n\nयह **महत्वपूर्ण** और *सुंदर* कहानी है।"
        assertEquals(8L, TextMetrics.countWords(formattedChapter))
    }

    @Test
    fun richTextFormatter_boldItalicUnderlineAndHeadings() {
        val (boldText, boldCursor) = RichTextFormatter.wrapSelection("Hello world", 6, 11, "**")
        assertEquals("Hello **world**", boldText)
        assertEquals(15, boldCursor)

        val (italicText, _) = RichTextFormatter.wrapSelection("ख़्वाब", 0, 6, "*")
        assertEquals("*ख़्वाब*", italicText)

        val (underlined, _) = RichTextFormatter.wrapSelection("कहानी", 0, 5, "__")
        assertEquals("__कहानी__", underlined)

        val (heading, _) = RichTextFormatter.applyLinePrefix("पहली बारिश", 0, "# ")
        assertEquals("# पहली बारिश", heading)

        val annotated = RichTextFormatter.renderFormattedText("# शीर्षक\n\nयह **बोल्ड** और *इटैलिक* है।")
        assertTrue(annotated.text.contains("शीर्षक"))
        assertTrue(annotated.text.contains("बोल्ड"))
        assertTrue(annotated.text.contains("इटैलिक"))
        assertTrue(annotated.spanStyles.size >= 3)
    }

    @Test
    fun safeEnumDeserialization_handlesUnknownValuesGracefully() {
        assertEquals(ShayariCategory.OTHER, ShayariCategory.fromRaw("NON_EXISTENT"))
        assertEquals(ShayariCategory.LOVE, ShayariCategory.fromRaw("Love"))
        assertEquals(WritingLanguage.HINDI, WritingLanguage.fromRaw(null))
        assertEquals(BookGenre.FICTION, BookGenre.fromRaw("UNKNOWN"))
        assertEquals(PrivacyStatus.PRIVATE, PrivacyStatus.fromRaw(""))
        assertEquals(PublishStatus.DRAFT, PublishStatus.fromRaw("LEGACY"))
        assertEquals(ShayariCardTheme.PARCHMENT, ShayariCardTheme.fromRaw("BAD"))
        assertEquals(BookCoverStyle.MAHOGANY, BookCoverStyle.fromRaw("BAD"))
        assertEquals(ChapterAlignment.START, ChapterAlignment.fromRaw("BAD"))
    }
}
