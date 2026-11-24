package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Book
import com.example.data.model.BookCoverStyle
import com.example.data.model.BookGenre
import com.example.data.model.Chapter
import com.example.data.model.ChapterAlignment
import com.example.data.model.PrivacyStatus
import com.example.data.model.PublishStatus
import com.example.data.model.RichTextFormatter
import com.example.data.model.SaveSyncState
import com.example.data.model.UiState
import com.example.data.model.WritingLanguage
import com.example.export.ExportedFile
import com.example.export.KalamoraExporter
import com.example.ui.components.EmptyStudioState
import com.example.ui.components.SaveStatusPill
import com.example.ui.theme.LiteraryManuscriptFont
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberBg
import com.example.ui.viewmodel.BookStudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookStudioScreen(
    book: Book,
    localCoverUri: String?,
    defaultAuthorName: String,
    viewModel: BookStudioViewModel,
    onUpdateBookMetadata: (
        title: String,
        authorName: String,
        description: String,
        coverStyle: BookCoverStyle,
        language: WritingLanguage,
        genre: BookGenre,
        privacyStatus: PrivacyStatus,
        publishStatus: PublishStatus,
        favorite: Boolean,
        localCoverUri: String?,
        updateLocalCover: Boolean
    ) -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val chaptersState by viewModel.chaptersState.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showChaptersSheet by remember { mutableStateOf(false) }
    var showAddChapterDialog by remember { mutableStateOf(false) }
    var newChapterTitleInput by remember { mutableStateOf("") }
    var showEditBookSheet by remember { mutableStateOf(false) }
    var showReadingMode by remember { mutableStateOf(false) }
    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }
    var exportedPdf by remember { mutableStateOf<ExportedFile?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }

    val chapters = (chaptersState as? UiState.Success)?.data.orEmpty().sortedBy { it.orderIndex }

    // Total book word count live (reflects current unsaved chapter edits in real time!)
    val liveTotalBookWords = remember(chapters, editor.activeChapterId, editor.wordCount) {
        chapters.sumOf { ch ->
            if (ch.id == editor.activeChapterId) editor.wordCount else ch.wordCount
        }
    }

    val handleExit = {
        viewModel.flushBeforeExit()
        onBack()
    }

    BackHandler {
        when {
            showReadingMode -> showReadingMode = false
            editor.distractionFreeMode -> viewModel.toggleDistractionFreeMode()
            else -> handleExit()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.flushBeforeExit() }
    }

    fun triggerBookPdfExport() {
        if (chapters.isEmpty()) {
            onMessage("Add at least one chapter before exporting PDF")
            return
        }
        viewModel.saveCurrentChapterNow()
        // Merge any in-flight editor text into the exported chapters list so nothing is stale
        val mergedChapters = chapters.map { ch ->
            if (ch.id == editor.activeChapterId) {
                ch.copy(
                    title = editor.title,
                    content = editor.contentField.text,
                    textAlignment = editor.alignment.name,
                    wordCount = editor.wordCount,
                    charCount = editor.charCount
                )
            } else ch
        }
        isExportingPdf = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                KalamoraExporter.exportBookPdf(context, book, mergedChapters)
            }
            isExportingPdf = false
            result.onSuccess { file ->
                exportedPdf = file
                onMessage("Book PDF exported to ${file.savedDisplayLocation}")
            }.onFailure { err ->
                onMessage(err.localizedMessage ?: "Book PDF export failed")
            }
        }
    }

    if (showReadingMode) {
        val mergedChapters = chapters.map { ch ->
            if (ch.id == editor.activeChapterId) {
                ch.copy(
                    title = editor.title,
                    content = editor.contentField.text,
                    textAlignment = editor.alignment.name
                )
            } else ch
        }
        BookReadingPreviewScreen(
            book = book,
            chapters = mergedChapters,
            onClose = { showReadingMode = false },
            onExportPdf = { triggerBookPdfExport() }
        )
    } else {
        Scaffold(
            topBar = {
                if (!editor.distractionFreeMode) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                SaveStatusPill(
                                    state = editor.saveSyncState,
                                    onRetryClick = { viewModel.saveCurrentChapterNow() }
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = handleExit,
                                modifier = Modifier.testTag("book_studio_back")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showChaptersSheet = true },
                                modifier = Modifier.testTag("book_chapters_drawer_button")
                            ) {
                                Icon(Icons.Default.List, contentDescription = "Manage chapters")
                            }
                            IconButton(
                                onClick = { showReadingMode = true },
                                modifier = Modifier.testTag("book_reading_mode_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Book preview and reading mode")
                            }
                            IconButton(
                                onClick = { triggerBookPdfExport() },
                                enabled = !isExportingPdf,
                                modifier = Modifier.testTag("book_export_pdf_button")
                            ) {
                                if (isExportingPdf) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Export Book PDF")
                                }
                            }
                            IconButton(
                                onClick = { showEditBookSheet = true },
                                modifier = Modifier.testTag("book_edit_metadata_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit book details")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)
                ) {
                    // Chapter Strip (hidden in distraction-free mode)
                    if (!editor.distractionFreeMode) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(chapters, key = { _, ch -> ch.id }) { idx, chapter ->
                                FilterChip(
                                    selected = chapter.id == editor.activeChapterId,
                                    onClick = { viewModel.selectChapter(chapter) },
                                    label = {
                                        Text(
                                            text = "${idx + 1}. ${chapter.title}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("chapter_chip_${idx}")
                                )
                            }
                            item {
                                OutlinedButton(
                                    onClick = {
                                        newChapterTitleInput = "Chapter ${chapters.size + 1}"
                                        showAddChapterDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("add_chapter_chip_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Chapter", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    // Formatting Toolbar
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (editor.distractionFreeMode) {
                                item {
                                    IconButton(
                                        onClick = { viewModel.toggleDistractionFreeMode() },
                                        modifier = Modifier.testTag("exit_focus_mode_button")
                                    ) {
                                        Icon(Icons.Default.FullscreenExit, contentDescription = "Exit distraction-free mode")
                                    }
                                }
                            } else {
                                item {
                                    IconButton(
                                        onClick = { viewModel.toggleDistractionFreeMode() },
                                        modifier = Modifier.testTag("enter_focus_mode_button")
                                    ) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = "Distraction-free writing mode")
                                    }
                                }
                            }
                            item {
                                IconButton(
                                    onClick = { viewModel.undo() },
                                    enabled = editor.canUndo,
                                    modifier = Modifier.testTag("editor_undo_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                                }
                            }
                            item {
                                IconButton(
                                    onClick = { viewModel.redo() },
                                    enabled = editor.canRedo,
                                    modifier = Modifier.testTag("editor_redo_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                                }
                            }
                            item {
                                IconButton(onClick = { viewModel.applyBold() }) {
                                    Icon(Icons.Default.FormatBold, contentDescription = "Bold")
                                }
                            }
                            item {
                                IconButton(onClick = { viewModel.applyItalic() }) {
                                    Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
                                }
                            }
                            item {
                                IconButton(onClick = { viewModel.applyUnderline() }) {
                                    Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline")
                                }
                            }
                            item {
                                TextButton(onClick = { viewModel.applyHeading1() }) {
                                    Text("H1", fontWeight = FontWeight.Bold)
                                }
                            }
                            item {
                                TextButton(onClick = { viewModel.applyHeading2() }) {
                                    Text("H2", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            item {
                                TextButton(onClick = { viewModel.insertParagraphBreak() }) {
                                    Text("¶", fontWeight = FontWeight.Bold)
                                }
                            }
                            item {
                                IconButton(onClick = { viewModel.cycleAlignment() }) {
                                    val alignIcon = when (editor.alignment) {
                                        ChapterAlignment.START -> Icons.AutoMirrored.Filled.FormatAlignLeft
                                        ChapterAlignment.CENTER -> Icons.Default.FormatAlignCenter
                                        ChapterAlignment.END -> Icons.AutoMirrored.Filled.FormatAlignRight
                                        ChapterAlignment.JUSTIFY -> Icons.Default.FormatAlignJustify
                                    }
                                    Icon(alignIcon, contentDescription = "Text alignment: ${editor.alignment.label}")
                                }
                            }
                            item {
                                IconButton(
                                    onClick = {
                                        viewModel.saveCurrentChapterNow { status ->
                                            val msg = when (status) {
                                                SaveSyncState.SavedToCloud -> "Chapter saved to Cloud"
                                                SaveSyncState.SavedLocallyPendingSync -> "Saved locally · Will sync when online"
                                                is SaveSyncState.SyncFailedLocally -> "Cloud sync failed · Safe locally"
                                                else -> "Saved"
                                            }
                                            onMessage(msg)
                                        }
                                    },
                                    modifier = Modifier.testTag("chapter_manual_save_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Save chapter")
                                }
                            }
                        }
                    }

                    if (editor.restoredLocalDraftBanner) {
                        Surface(
                            color = PendingAmberBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Restored unsynced local draft for this chapter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PendingAmber,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.dismissDraftBanner() }) {
                                    Text("OK", color = PendingAmber)
                                }
                            }
                        }
                    }

                    // Main Chapter Canvas
                    when (chaptersState) {
                        is UiState.Loading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }

                        is UiState.Error -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (chaptersState as UiState.Error).message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        is UiState.Success -> {
                            if (chapters.isEmpty() || editor.activeChapterId.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyStudioState(
                                        icon = Icons.AutoMirrored.Filled.MenuBook,
                                        title = "Add your first chapter",
                                        subtitle = "Every chapter saves independently so you can write freely across your entire book.",
                                        actionLabel = "Add Chapter 1",
                                        onAction = {
                                            newChapterTitleInput = "Chapter 1"
                                            showAddChapterDialog = true
                                        }
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 22.dp, vertical = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Chapter Title Input
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (editor.title.isEmpty()) {
                                            Text(
                                                text = "Chapter Title · अध्याय शीर्षक",
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    fontFamily = LiteraryManuscriptFont
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                        BasicTextField(
                                            value = editor.title,
                                            onValueChange = { viewModel.onChapterTitleChange(it) },
                                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                                fontFamily = LiteraryManuscriptFont,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("chapter_title_input")
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                    // Independent Chapter Content Input
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(420.dp)
                                    ) {
                                        if (editor.contentField.text.isEmpty()) {
                                            Text(
                                                text = "Begin writing this chapter…\nयहाँ से अपनी कहानी लिखें…",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontFamily = LiteraryManuscriptFont,
                                                    fontSize = 17.sp,
                                                    lineHeight = 29.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                            )
                                        }
                                        BasicTextField(
                                            value = editor.contentField,
                                            onValueChange = { viewModel.onChapterContentChange(it) },
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = LiteraryManuscriptFont,
                                                fontSize = 17.sp,
                                                lineHeight = 29.sp,
                                                textAlign = editor.alignment.toComposeTextAlign(),
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("chapter_content_input")
                                        )
                                    }
                                }

                                // Live Writing Stats Footer Bar
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Chapter: ${editor.wordCount}w · ${editor.charCount}c",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.testTag("chapter_word_count")
                                        )
                                        Text(
                                            text = "Book: ${chapters.size} ch · ${liveTotalBookWords}w",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Chapter Manager Bottom Sheet (Reorder, Switch, Add, Delete)
    if (showChaptersSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showChaptersSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Chapters (${chapters.size})", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Total book words: $liveTotalBookWords",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            showChaptersSheet = false
                            newChapterTitleInput = "Chapter ${chapters.size + 1}"
                            showAddChapterDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(320.dp)
                ) {
                    itemsIndexed(chapters, key = { _, c -> c.id }) { idx, chapter ->
                        val isSelected = chapter.id == editor.activeChapterId
                        Surface(
                            onClick = {
                                viewModel.selectChapter(chapter)
                                showChaptersSheet = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${idx + 1}. ${chapter.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${if (isSelected) editor.wordCount else chapter.wordCount} words",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = { viewModel.moveChapter(chapter.id, moveUp = true, onMessage = onMessage) },
                                        enabled = idx > 0,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move chapter up", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveChapter(chapter.id, moveUp = false, onMessage = onMessage) },
                                        enabled = idx < chapters.lastIndex,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move chapter down", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { chapterToDelete = chapter },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete chapter",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add New Chapter Dialog
    if (showAddChapterDialog) {
        AlertDialog(
            onDismissRequest = { showAddChapterDialog = false },
            title = { Text("Add New Chapter") },
            text = {
                OutlinedTextField(
                    value = newChapterTitleInput,
                    onValueChange = { newChapterTitleInput = it.take(160) },
                    label = { Text("Chapter Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_chapter_title_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = newChapterTitleInput.trim().ifBlank { "Chapter ${chapters.size + 1}" }
                        showAddChapterDialog = false
                        viewModel.addNewChapter(title, onMessage)
                    },
                    modifier = Modifier.testTag("confirm_add_chapter_button")
                ) {
                    Text("Create Chapter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChapterDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete Chapter Confirmation Dialog
    chapterToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            title = { Text("Delete \"${target.title}\"?") },
            text = { Text("This permanently deletes this chapter and its content.") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        chapterToDelete = null
                        viewModel.deleteChapter(id, onMessage)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { chapterToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Book Metadata Sheet
    if (showEditBookSheet) {
        BookMetadataModalSheet(
            initialBook = book,
            initialCoverUri = localCoverUri,
            defaultAuthorName = defaultAuthorName,
            onDismiss = { showEditBookSheet = false },
            onConfirm = { title, author, desc, coverStyle, lang, genre, privacy, publish, coverUri, coverChanged ->
                showEditBookSheet = false
                onUpdateBookMetadata(
                    title, author, desc, coverStyle, lang, genre,
                    privacy, publish, book.favorite, coverUri, coverChanged
                )
            }
        )
    }

    // Exported Book PDF Ready Dialog
    exportedPdf?.let { file ->
        AlertDialog(
            onDismissRequest = { exportedPdf = null },
            title = { Text("Book PDF Ready") },
            text = {
                Text("Saved \"${file.fileName}\" with Cover Page, Table of Contents, and page numbers to ${file.savedDisplayLocation}.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = file
                        exportedPdf = null
                        KalamoraExporter.shareExportedFile(context, target)
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportedPdf = null }) { Text("Done") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookReadingPreviewScreen(
    book: Book,
    chapters: List<Chapter>,
    onClose: () -> Unit,
    onExportPdf: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Mode") },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("reading_mode_close")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit reading mode")
                    }
                },
                actions = {
                    TextButton(onClick = onExportPdf) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                // Book Cover Header inside Reader
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "${book.genre.uppercase()} · ${book.language.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.displaySmall.copy(fontFamily = LiteraryManuscriptFont),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "By ${book.authorName.ifBlank { "Kalamora Writer" }}",
                                style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (book.description.isNotBlank()) {
                                HorizontalDivider(
                                    modifier = Modifier.width(64.dp),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = book.description,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LiteraryManuscriptFont),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Rendered Chapters
                itemsIndexed(chapters, key = { _, ch -> ch.id }) { idx, chapter ->
                    val align = ChapterAlignment.fromRaw(chapter.textAlignment).toComposeTextAlign()
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "CHAPTER ${idx + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = LiteraryManuscriptFont),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = RichTextFormatter.renderFormattedText(
                                chapter.content.ifBlank { "(Empty chapter)" }
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = LiteraryManuscriptFont,
                                fontSize = 17.sp,
                                lineHeight = 30.sp,
                                textAlign = align
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}
