package com.example.ui.viewmodel

import android.util.Log
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Chapter
import com.example.data.model.ChapterAlignment
import com.example.data.model.RichTextFormatter
import com.example.data.model.SaveSyncState
import com.example.data.model.TextMetrics
import com.example.data.model.UiState
import com.example.data.remote.KalamoraRepository
import com.example.data.remote.SaveOutcome
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChapterEditorState(
    val activeChapterId: String = "",
    val title: String = "",
    val contentField: TextFieldValue = TextFieldValue(""),
    val alignment: ChapterAlignment = ChapterAlignment.START,
    val orderIndex: Long = 0L,
    val saveSyncState: SaveSyncState = SaveSyncState.Idle,
    val distractionFreeMode: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val restoredLocalDraftBanner: Boolean = false
) {
    val wordCount: Long get() = TextMetrics.countWords(contentField.text)
    val charCount: Long get() = TextMetrics.countCharacters(contentField.text)
}

class BookStudioViewModel(
    private val repository: KalamoraRepository,
    private val currentUserId: String,
    val bookId: String
) : ViewModel() {

    val chaptersState: StateFlow<UiState<List<Chapter>>> = repository.observeChapters(currentUserId, bookId)
        .map<List<Chapter>, UiState<List<Chapter>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing chapters for book=$bookId", root)
            emit(UiState.Error(root.localizedMessage ?: "Failed to load chapters"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _editor = MutableStateFlow(ChapterEditorState())
    val editor: StateFlow<ChapterEditorState> = _editor.asStateFlow()

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var autoSaveJob: Job? = null

    init {
        // Automatically select the first chapter once chapters arrive if none is active yet
        viewModelScope.launch {
            chaptersState.collect { ui ->
                if (ui is UiState.Success) {
                    val list = ui.data
                    val activeId = _editor.value.activeChapterId
                    if (activeId.isBlank() && list.isNotEmpty()) {
                        loadChapterIntoEditor(list.first())
                    } else if (activeId.isNotBlank() && list.none { it.id == activeId }) {
                        // Active chapter was deleted; move cleanly to first remaining chapter
                        if (list.isNotEmpty()) {
                            loadChapterIntoEditor(list.first())
                        } else {
                            clearEditor()
                        }
                    }
                }
            }
        }
    }

    private fun clearEditor() {
        autoSaveJob?.cancel()
        undoStack.clear()
        redoStack.clear()
        _editor.value = ChapterEditorState()
    }

    /**
     * Switches to [chapter] while strictly saving the outgoing chapter to its OWN ID first,
     * guaranteeing that saving one chapter can NEVER overwrite another chapter.
     */
    fun selectChapter(chapter: Chapter) {
        val current = _editor.value
        if (current.activeChapterId == chapter.id) return

        // Flush outgoing chapter strictly under current.activeChapterId before loading target chapter
        if (current.activeChapterId.isNotBlank() && current.saveSyncState == SaveSyncState.UnsavedChanges) {
            val outgoingSnapshot = current
            autoSaveJob?.cancel()
            viewModelScope.launch {
                withContext(NonCancellable) {
                    persistChapterSnapshot(outgoingSnapshot, updateEditorUi = false)
                }
            }
        } else {
            autoSaveJob?.cancel()
        }

        viewModelScope.launch {
            loadChapterIntoEditor(chapter)
        }
    }

    private suspend fun loadChapterIntoEditor(chapter: Chapter) {
        undoStack.clear()
        redoStack.clear()
        val localDraft = repository.getLocalChapterDraft(currentUserId, bookId, chapter.id)
        val cloudMillis = chapter.updatedAt?.toDate()?.time ?: 0L
        val useLocal = localDraft != null && localDraft.pendingSync && localDraft.lastEditedMillis > cloudMillis

        val initialText = if (useLocal) localDraft!!.content else chapter.content
        val initialTitle = if (useLocal) localDraft!!.title else chapter.title
        val initialAlign = if (useLocal) {
            ChapterAlignment.fromRaw(localDraft!!.categoryOrAlignment)
        } else {
            ChapterAlignment.fromRaw(chapter.textAlignment)
        }

        _editor.value = ChapterEditorState(
            activeChapterId = chapter.id,
            title = initialTitle,
            contentField = TextFieldValue(initialText, TextRange(initialText.length)),
            alignment = initialAlign,
            orderIndex = chapter.orderIndex,
            saveSyncState = if (useLocal) SaveSyncState.SavedLocallyPendingSync else SaveSyncState.SavedToCloud,
            distractionFreeMode = _editor.value.distractionFreeMode,
            canUndo = false,
            canRedo = false,
            restoredLocalDraftBanner = useLocal
        )
    }

    fun dismissDraftBanner() {
        _editor.update { it.copy(restoredLocalDraftBanner = false) }
    }

    fun toggleDistractionFreeMode() {
        _editor.update { it.copy(distractionFreeMode = !it.distractionFreeMode) }
    }

    fun onChapterTitleChange(newTitle: String) {
        if (_editor.value.activeChapterId.isBlank()) return
        _editor.update {
            it.copy(
                title = newTitle.take(160),
                saveSyncState = SaveSyncState.UnsavedChanges
            )
        }
        scheduleAutoSave()
    }

    fun onChapterContentChange(newValue: TextFieldValue) {
        val prev = _editor.value
        if (prev.activeChapterId.isBlank()) return
        if (newValue.text != prev.contentField.text) {
            pushUndo(prev.contentField)
            redoStack.clear()
            _editor.update {
                it.copy(
                    contentField = newValue.copy(text = newValue.text.take(200000)),
                    saveSyncState = SaveSyncState.UnsavedChanges,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false
                )
            }
            scheduleAutoSave()
        } else {
            // Selection/cursor movement only
            _editor.update { it.copy(contentField = newValue) }
        }
    }

    private fun pushUndo(snapshot: TextFieldValue) {
        if (undoStack.size >= MAX_UNDO_HISTORY) {
            undoStack.removeFirst()
        }
        undoStack.addLast(snapshot)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _editor.value.contentField
        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        _editor.update {
            it.copy(
                contentField = previous,
                saveSyncState = SaveSyncState.UnsavedChanges,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        scheduleAutoSave()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _editor.value.contentField
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        _editor.update {
            it.copy(
                contentField = next,
                saveSyncState = SaveSyncState.UnsavedChanges,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        scheduleAutoSave()
    }

    fun applyBold() = applyWrapFormatting("**")
    fun applyItalic() = applyWrapFormatting("*")
    fun applyUnderline() = applyWrapFormatting("__")

    fun applyHeading1() = applyLinePrefix("# ")
    fun applyHeading2() = applyLinePrefix("## ")

    fun insertParagraphBreak() {
        val current = _editor.value.contentField
        val pos = current.selection.end.coerceIn(0, current.text.length)
        val updated = current.text.substring(0, pos) + "\n\n" + current.text.substring(pos)
        onChapterContentChange(TextFieldValue(updated, TextRange(pos + 2)))
    }

    fun cycleAlignment() {
        if (_editor.value.activeChapterId.isBlank()) return
        val next = when (_editor.value.alignment) {
            ChapterAlignment.START -> ChapterAlignment.CENTER
            ChapterAlignment.CENTER -> ChapterAlignment.END
            ChapterAlignment.END -> ChapterAlignment.JUSTIFY
            ChapterAlignment.JUSTIFY -> ChapterAlignment.START
        }
        _editor.update { it.copy(alignment = next, saveSyncState = SaveSyncState.UnsavedChanges) }
        scheduleAutoSave()
    }

    private fun applyWrapFormatting(marker: String) {
        val current = _editor.value.contentField
        val (updatedText, newCursor) = RichTextFormatter.wrapSelection(
            text = current.text,
            selectionStart = current.selection.min,
            selectionEnd = current.selection.max,
            prefix = marker,
            suffix = marker
        )
        onChapterContentChange(TextFieldValue(updatedText, TextRange(newCursor)))
    }

    private fun applyLinePrefix(prefix: String) {
        val current = _editor.value.contentField
        val (updatedText, newCursor) = RichTextFormatter.applyLinePrefix(
            text = current.text,
            cursorPosition = current.selection.start,
            prefix = prefix
        )
        onChapterContentChange(TextFieldValue(updatedText, TextRange(newCursor)))
    }

    private fun scheduleAutoSave() {
        val targetChapterId = _editor.value.activeChapterId
        if (targetChapterId.isBlank()) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1200L)
            val snap = _editor.value
            // Double-check we are still saving the exact same chapter!
            if (snap.activeChapterId == targetChapterId && snap.activeChapterId.isNotBlank()) {
                persistChapterSnapshot(snap, updateEditorUi = true)
            }
        }
    }

    fun saveCurrentChapterNow(onDone: (SaveSyncState) -> Unit = {}) {
        autoSaveJob?.cancel()
        val snap = _editor.value
        if (snap.activeChapterId.isBlank()) return
        viewModelScope.launch {
            val status = persistChapterSnapshot(snap, updateEditorUi = true)
            onDone(status)
        }
    }

    fun flushBeforeExit() {
        autoSaveJob?.cancel()
        val snap = _editor.value
        if (snap.activeChapterId.isNotBlank() && snap.saveSyncState == SaveSyncState.UnsavedChanges) {
            viewModelScope.launch {
                withContext(NonCancellable) {
                    persistChapterSnapshot(snap, updateEditorUi = false)
                }
            }
        }
    }

    private suspend fun persistChapterSnapshot(
        snap: ChapterEditorState,
        updateEditorUi: Boolean
    ): SaveSyncState {
        if (updateEditorUi && _editor.value.activeChapterId == snap.activeChapterId) {
            _editor.update { it.copy(saveSyncState = SaveSyncState.Saving) }
        }
        val outcome = repository.saveChapter(
            userId = currentUserId,
            bookId = bookId,
            chapterId = snap.activeChapterId,
            title = snap.title,
            content = snap.contentField.text,
            alignment = snap.alignment,
            orderIndex = snap.orderIndex
        )
        val resultState = when (outcome) {
            is SaveOutcome.SavedCloud -> SaveSyncState.SavedToCloud
            is SaveOutcome.SavedLocalPending -> SaveSyncState.SavedLocallyPendingSync
            is SaveOutcome.SyncFailedLocal -> SaveSyncState.SyncFailedLocally(outcome.message)
        }
        if (updateEditorUi && _editor.value.activeChapterId == snap.activeChapterId) {
            _editor.update { it.copy(saveSyncState = resultState) }
        }
        return resultState
    }

    // --- CHAPTER MANAGEMENT ---

    fun addNewChapter(title: String, onMessage: (String) -> Unit) {
        val currentChapters = (chaptersState.value as? UiState.Success)?.data.orEmpty()
        val nextIndex = (currentChapters.maxOfOrNull { it.orderIndex } ?: -1L) + 1L

        // Flush current chapter first so unsaved work is preserved before switching!
        val currentSnap = _editor.value
        viewModelScope.launch {
            if (currentSnap.activeChapterId.isNotBlank() && currentSnap.saveSyncState == SaveSyncState.UnsavedChanges) {
                persistChapterSnapshot(currentSnap, updateEditorUi = false)
            }
            val result = repository.createChapter(
                userId = currentUserId,
                bookId = bookId,
                title = title.ifBlank { "Chapter ${currentChapters.size + 1}" },
                orderIndex = nextIndex
            )
            result.onSuccess { newChapterId ->
                val newChapter = Chapter(
                    id = newChapterId,
                    userId = currentUserId,
                    bookId = bookId,
                    title = title.ifBlank { "Chapter ${currentChapters.size + 1}" },
                    content = "",
                    orderIndex = nextIndex,
                    textAlignment = ChapterAlignment.START.name
                )
                loadChapterIntoEditor(newChapter)
                onMessage("Added \"${newChapter.title}\"")
            }.onFailure { err ->
                onMessage(err.localizedMessage ?: "Failed to add chapter")
            }
        }
    }

    fun deleteChapter(chapterId: String, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            repository.deleteChapter(currentUserId, bookId, chapterId)
                .onSuccess { onMessage("Chapter deleted") }
                .onFailure { err -> onMessage(err.localizedMessage ?: "Failed to delete chapter") }
        }
    }

    fun moveChapter(chapterId: String, moveUp: Boolean, onMessage: (String) -> Unit) {
        val list = (chaptersState.value as? UiState.Success)?.data?.sortedBy { it.orderIndex } ?: return
        val idx = list.indexOfFirst { it.id == chapterId }
        if (idx == -1) return
        val targetIdx = if (moveUp) idx - 1 else idx + 1
        if (targetIdx !in list.indices) return

        val reordered = list.toMutableList()
        val item = reordered.removeAt(idx)
        reordered.add(targetIdx, item)
        viewModelScope.launch {
            repository.reorderChapters(currentUserId, bookId, reordered.map { it.id })
                .onFailure { onMessage("Could not reorder chapters") }
        }
    }

    private companion object {
        const val TAG = "BookStudioVM"
        const val MAX_UNDO_HISTORY = 50
    }
}
