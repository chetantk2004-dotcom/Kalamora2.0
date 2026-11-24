package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferencesStore
import com.example.data.model.Book
import com.example.data.model.BookCoverStyle
import com.example.data.model.BookGenre
import com.example.data.model.PrivacyStatus
import com.example.data.model.PublishStatus
import com.example.data.model.Shayari
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.WritingLanguage
import com.example.data.remote.KalamoraRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WritingStats(
    val totalBooks: Int = 0,
    val totalShayari: Int = 0,
    val totalWords: Long = 0L,
    val totalDrafts: Int = 0,
    val totalPublished: Int = 0,
    val totalFavorites: Int = 0
)

class KalamoraViewModel(
    val repository: KalamoraRepository,
    private val preferencesStore: UserPreferencesStore,
    val currentUserId: String
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val isDarkSepiaTheme: StateFlow<Boolean> = preferencesStore.isDarkSepiaTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val isOnline: StateFlow<Boolean> = repository.observeIsOnline()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    val pendingDraftCount: StateFlow<Int> = repository.observePendingDraftCount(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    val localBookCovers: StateFlow<Map<String, String>> = repository.observeLocalBookCovers(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing user profile", root)
            emit(UiState.Error(root.localizedMessage ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val booksState: StateFlow<UiState<List<Book>>> = repository.observeBooks(currentUserId)
        .map<List<Book>, UiState<List<Book>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing books", root)
            emit(UiState.Error(root.localizedMessage ?: "Failed to load books"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val shayariState: StateFlow<UiState<List<Shayari>>> = repository.observeShayariList(currentUserId)
        .map<List<Shayari>, UiState<List<Shayari>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing shayari", root)
            emit(UiState.Error(root.localizedMessage ?: "Failed to load Shayari"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val writingStats: StateFlow<WritingStats> = combine(booksState, shayariState) { booksUi, shayariUi ->
        val books = (booksUi as? UiState.Success)?.data.orEmpty()
        val shayari = (shayariUi as? UiState.Success)?.data.orEmpty()
        val bookWords = books.sumOf { it.totalWordCount }
        val shayariWords = shayari.sumOf { it.wordCount }
        val drafts = books.count { it.publishStatus == PublishStatus.DRAFT.name } +
            shayari.count { it.publishStatus == PublishStatus.DRAFT.name }
        val published = books.count { it.publishStatus == PublishStatus.PUBLISHED.name } +
            shayari.count { it.publishStatus == PublishStatus.PUBLISHED.name }
        val favorites = books.count { it.favorite } + shayari.count { it.favorite }
        WritingStats(
            totalBooks = books.size,
            totalShayari = shayari.size,
            totalWords = bookWords + shayariWords,
            totalDrafts = drafts,
            totalPublished = published,
            totalFavorites = favorites
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WritingStats())

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { user ->
                repository.ensureUserProfile(user)
            }
        }
        // Automatically synchronize pending offline drafts when network returns
        viewModelScope.launch {
            combine(isOnline, pendingDraftCount) { online, count -> online && count > 0 }
                .distinctUntilChanged()
                .collect { shouldSync ->
                    if (shouldSync) {
                        val summary = repository.syncAllPendingDrafts(currentUserId)
                        if (summary.syncedCount > 0) {
                            _messages.tryEmit("Synced ${summary.syncedCount} offline draft(s) to Cloud")
                        }
                    }
                }
        }
    }

    fun emitMessage(message: String) {
        _messages.tryEmit(message)
    }

    fun setDarkSepiaTheme(enabled: Boolean) {
        viewModelScope.launch {
            preferencesStore.setDarkSepiaTheme(enabled)
        }
    }

    fun syncPendingDraftsNow() {
        viewModelScope.launch {
            val summary = repository.syncAllPendingDrafts(currentUserId)
            when {
                summary.syncedCount > 0 && summary.failedCount == 0 ->
                    _messages.tryEmit("All ${summary.syncedCount} local draft(s) synced to Cloud")
                summary.syncedCount > 0 && summary.failedCount > 0 ->
                    _messages.tryEmit("Synced ${summary.syncedCount}; ${summary.failedCount} still pending locally")
                summary.failedCount > 0 ->
                    _messages.tryEmit("Unable to reach Cloud. Your drafts remain safe locally.")
                else ->
                    _messages.tryEmit("Everything is already up to date in the Cloud")
            }
        }
    }

    fun createBook(
        title: String,
        authorName: String,
        description: String,
        coverStyle: BookCoverStyle,
        language: WritingLanguage,
        genre: BookGenre,
        privacyStatus: PrivacyStatus,
        publishStatus: PublishStatus,
        localCoverUri: String?,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.createBook(
                userId = currentUserId,
                title = title,
                authorName = authorName,
                description = description,
                coverStyle = coverStyle,
                language = language,
                genre = genre,
                privacyStatus = privacyStatus,
                publishStatus = publishStatus,
                localCoverUri = localCoverUri
            )
            result.onSuccess { bookId ->
                _messages.tryEmit("Created \"$title\"")
                onCreated(bookId)
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Could not create book")
            }
        }
    }

    fun updateBook(
        bookId: String,
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
        updateLocalCover: Boolean,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.updateBook(
                userId = currentUserId,
                bookId = bookId,
                title = title,
                authorName = authorName,
                description = description,
                coverStyle = coverStyle,
                language = language,
                genre = genre,
                privacyStatus = privacyStatus,
                publishStatus = publishStatus,
                favorite = favorite,
                localCoverUri = localCoverUri,
                updateLocalCover = updateLocalCover
            )
            result.onSuccess {
                _messages.tryEmit("Book details updated")
                onComplete()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Failed to update book")
            }
        }
    }

    fun toggleBookFavorite(book: Book) {
        viewModelScope.launch {
            repository.toggleBookFavorite(currentUserId, book.id, !book.favorite)
                .onFailure { _messages.tryEmit("Failed to update favorite status") }
        }
    }

    fun deleteBook(bookId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteBookWithChapters(currentUserId, bookId)
                .onSuccess {
                    _messages.tryEmit("Book deleted")
                    onDeleted()
                }
                .onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Failed to delete book")
                }
        }
    }

    fun toggleShayariFavorite(shayari: Shayari) {
        viewModelScope.launch {
            repository.toggleShayariFavorite(currentUserId, shayari.id, !shayari.favorite)
                .onFailure { _messages.tryEmit("Failed to update favorite status") }
        }
    }

    fun deleteShayari(shayariId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteShayari(currentUserId, shayariId)
                .onSuccess {
                    _messages.tryEmit("Shayari deleted")
                    onDeleted()
                }
                .onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Failed to delete Shayari")
                }
        }
    }

    fun updateProfile(
        displayName: String,
        penName: String,
        bio: String,
        preferredLanguage: WritingLanguage,
        publicProfileEnabled: Boolean
    ) {
        viewModelScope.launch {
            repository.updateUserProfile(
                userId = currentUserId,
                displayName = displayName,
                penName = penName,
                bio = bio,
                preferredLanguage = preferredLanguage,
                publicProfileEnabled = publicProfileEnabled
            ).onSuccess {
                _messages.tryEmit("Author profile saved")
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Could not save profile")
            }
        }
    }

    private companion object {
        const val TAG = "KalamoraVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
