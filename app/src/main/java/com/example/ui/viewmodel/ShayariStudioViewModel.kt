package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PrivacyStatus
import com.example.data.model.PublishStatus
import com.example.data.model.SaveSyncState
import com.example.data.model.Shayari
import com.example.data.model.ShayariCardTheme
import com.example.data.model.ShayariCategory
import com.example.data.model.TextMetrics
import com.example.data.model.WritingLanguage
import com.example.data.remote.KalamoraRepository
import com.example.data.remote.SaveOutcome
import com.google.firebase.Timestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ShayariEditorUiState(
    val shayariId: String? = null,
    val isNewDocument: Boolean = true,
    val title: String = "",
    val content: String = "",
    val category: ShayariCategory = ShayariCategory.LIFE,
    val language: WritingLanguage = WritingLanguage.HINDI,
    val privacyStatus: PrivacyStatus = PrivacyStatus.PRIVATE,
    val publishStatus: PublishStatus = PublishStatus.DRAFT,
    val favorite: Boolean = false,
    val authorName: String = "",
    val cardTheme: ShayariCardTheme = ShayariCardTheme.PARCHMENT,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val saveSyncState: SaveSyncState = SaveSyncState.Idle,
    val restoredLocalDraftBanner: Boolean = false
) {
    val wordCount: Long get() = TextMetrics.countWords(content)
    val charCount: Long get() = TextMetrics.countCharacters(content)

    fun toShayari(userId: String): Shayari = Shayari(
        id = shayariId.orEmpty(),
        userId = userId,
        title = title,
        content = content,
        category = category.label,
        language = language.label,
        privacyStatus = privacyStatus.name,
        publishStatus = publishStatus.name,
        favorite = favorite,
        wordCount = wordCount,
        charCount = charCount,
        authorName = authorName,
        cardTheme = cardTheme.name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

class ShayariStudioViewModel(
    private val repository: KalamoraRepository,
    private val currentUserId: String,
    private val defaultAuthorName: String,
    private val initialShayari: Shayari?
) : ViewModel() {

    private val _state = MutableStateFlow(
        if (initialShayari != null) {
            ShayariEditorUiState(
                shayariId = initialShayari.id,
                isNewDocument = false,
                title = initialShayari.title,
                content = initialShayari.content,
                category = ShayariCategory.fromRaw(initialShayari.category),
                language = WritingLanguage.fromRaw(initialShayari.language),
                privacyStatus = PrivacyStatus.fromRaw(initialShayari.privacyStatus),
                publishStatus = PublishStatus.fromRaw(initialShayari.publishStatus),
                favorite = initialShayari.favorite,
                authorName = initialShayari.authorName.ifBlank { defaultAuthorName },
                cardTheme = ShayariCardTheme.fromRaw(initialShayari.cardTheme),
                createdAt = initialShayari.createdAt,
                updatedAt = initialShayari.updatedAt,
                saveSyncState = SaveSyncState.SavedToCloud
            )
        } else {
            ShayariEditorUiState(
                authorName = defaultAuthorName,
                saveSyncState = SaveSyncState.Idle
            )
        }
    )
    val state: StateFlow<ShayariEditorUiState> = _state.asStateFlow()

    private var autoSaveJob: Job? = null

    init {
        // Restore any pending unsynced local draft if newer than cloud snapshot
        val existingId = initialShayari?.id
        if (!existingId.isNullOrBlank()) {
            viewModelScope.launch {
                val localDraft = repository.getLocalShayariDraft(currentUserId, existingId)
                val cloudMillis = initialShayari.updatedAt?.toDate()?.time ?: 0L
                if (localDraft != null && localDraft.pendingSync && localDraft.lastEditedMillis > cloudMillis) {
                    _state.update {
                        it.copy(
                            title = localDraft.title,
                            content = localDraft.content,
                            category = ShayariCategory.fromRaw(localDraft.categoryOrAlignment),
                            language = WritingLanguage.fromRaw(localDraft.language),
                            privacyStatus = PrivacyStatus.fromRaw(localDraft.privacyStatus),
                            publishStatus = PublishStatus.fromRaw(localDraft.publishStatus),
                            favorite = localDraft.favorite,
                            authorName = localDraft.authorName.ifBlank { defaultAuthorName },
                            cardTheme = ShayariCardTheme.fromRaw(localDraft.cardTheme),
                            saveSyncState = SaveSyncState.SavedLocallyPendingSync,
                            restoredLocalDraftBanner = true
                        )
                    }
                }
            }
        }
    }

    fun dismissDraftBanner() {
        _state.update { it.copy(restoredLocalDraftBanner = false) }
    }

    fun onTitleChange(newTitle: String) {
        _state.update { it.copy(title = newTitle.take(160), saveSyncState = SaveSyncState.UnsavedChanges) }
        scheduleAutoSave()
    }

    fun onContentChange(newContent: String) {
        _state.update { it.copy(content = newContent.take(10000), saveSyncState = SaveSyncState.UnsavedChanges) }
        scheduleAutoSave()
    }

    fun onCategoryChange(category: ShayariCategory) {
        _state.update { it.copy(category = category, saveSyncState = SaveSyncState.UnsavedChanges) }
        scheduleAutoSave()
    }

    fun onLanguageChange(language: WritingLanguage) {
        _state.update { it.copy(language = language, saveSyncState = SaveSyncState.UnsavedChanges) }
        scheduleAutoSave()
    }

    fun onPrivacyChange(privacy: PrivacyStatus) {
        _state.update { it.copy(privacyStatus = privacy, saveSyncState = SaveSyncState.UnsavedChanges) }
        scheduleAutoSave()
    }

    fun onPublishStatusChange(status: PublishStatus) {
        _state.update { it.copy(publishStatus = status, saveSyncState = SaveSyncState.UnsavedChanges) }
        scheduleAutoSave()
    }

    fun onFavoriteToggle() {
        _state.update { it.copy(favorite = !it.favorite, saveSyncState = SaveSyncState.UnsavedChanges) }
        scheduleAutoSave()
    }

    fun onCardThemeChange(theme: ShayariCardTheme) {
        _state.update { it.copy(cardTheme = theme, saveSyncState = SaveSyncState.UnsavedChanges) }
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1200L)
            val snap = _state.value
            if (snap.title.isNotBlank() || snap.content.isNotBlank()) {
                performSave(snap)
            }
        }
    }

    fun saveNow(onDone: (SaveSyncState) -> Unit = {}) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            val snap = _state.value
            if (snap.title.isBlank() && snap.content.isBlank()) {
                onDone(SaveSyncState.Idle)
                return@launch
            }
            val resultState = performSave(snap)
            onDone(resultState)
        }
    }

    fun flushBeforeExit() {
        autoSaveJob?.cancel()
        val snap = _state.value
        if (snap.saveSyncState == SaveSyncState.UnsavedChanges && (snap.title.isNotBlank() || snap.content.isNotBlank())) {
            viewModelScope.launch {
                withContext(NonCancellable) {
                    performSave(snap)
                }
            }
        }
    }

    private suspend fun performSave(snap: ShayariEditorUiState): SaveSyncState {
        _state.update { it.copy(saveSyncState = SaveSyncState.Saving) }
        val outcome = repository.saveShayari(
            userId = currentUserId,
            existingId = snap.shayariId,
            title = snap.title,
            content = snap.content,
            category = snap.category,
            language = snap.language,
            privacyStatus = snap.privacyStatus,
            publishStatus = snap.publishStatus,
            favorite = snap.favorite,
            authorName = snap.authorName.ifBlank { defaultAuthorName },
            cardTheme = snap.cardTheme,
            isNewDocument = snap.isNewDocument
        )
        val nextSyncState = when (outcome) {
            is SaveOutcome.SavedCloud -> SaveSyncState.SavedToCloud
            is SaveOutcome.SavedLocalPending -> SaveSyncState.SavedLocallyPendingSync
            is SaveOutcome.SyncFailedLocal -> SaveSyncState.SyncFailedLocally(outcome.message)
        }
        _state.update {
            it.copy(
                shayariId = outcome.itemId,
                isNewDocument = false,
                updatedAt = Timestamp.now(),
                saveSyncState = nextSyncState
            )
        }
        return nextSyncState
    }

    fun deleteCurrentShayari(onDeleted: () -> Unit) {
        val id = _state.value.shayariId
        if (id.isNullOrBlank()) {
            onDeleted()
            return
        }
        autoSaveJob?.cancel()
        viewModelScope.launch {
            repository.deleteShayari(currentUserId, id).onSuccess {
                onDeleted()
            }
        }
    }
}
