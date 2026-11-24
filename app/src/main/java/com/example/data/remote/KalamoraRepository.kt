package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.KalamoraDatabase
import com.example.data.local.LocalBookCoverEntity
import com.example.data.local.LocalDraftDao
import com.example.data.local.LocalDraftEntity
import com.example.data.model.Book
import com.example.data.model.BookCoverStyle
import com.example.data.model.BookGenre
import com.example.data.model.Chapter
import com.example.data.model.ChapterAlignment
import com.example.data.model.PrivacyStatus
import com.example.data.model.PublishStatus
import com.example.data.model.Shayari
import com.example.data.model.ShayariCardTheme
import com.example.data.model.ShayariCategory
import com.example.data.model.TextMetrics
import com.example.data.model.UserProfile
import com.example.data.model.WritingLanguage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SaveOutcome {
    val itemId: String

    data class SavedCloud(override val itemId: String) : SaveOutcome
    data class SavedLocalPending(override val itemId: String) : SaveOutcome
    data class SyncFailedLocal(override val itemId: String, val message: String) : SaveOutcome
}

data class SyncSummary(
    val syncedCount: Int,
    val failedCount: Int
)

class KalamoraRepository(
    private val db: FirebaseFirestore,
    private val localDraftDao: LocalDraftDao,
    private val connectivityObserver: ConnectivityObserver,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        localDraftDao = KalamoraDatabase.getInstance(context.applicationContext).localDraftDao(),
        connectivityObserver = ConnectivityObserver(context.applicationContext)
    )

    private fun requireAuthorizedUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user mismatch; refusing cross-user access." }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun shayariCollection(uid: String) = userDoc(uid).collection("shayari")
    private fun booksCollection(uid: String) = userDoc(uid).collection("books")
    private fun chaptersCollection(uid: String, bookId: String) =
        booksCollection(uid).document(bookId).collection("chapters")

    fun shayariDraftKey(userId: String, shayariId: String) = "shayari:$userId:$shayariId"
    fun chapterDraftKey(userId: String, bookId: String, chapterId: String) =
        "chapter:$userId:$bookId:$chapterId"

    fun observeIsOnline(): Flow<Boolean> = connectivityObserver.observeIsOnline()
    fun observePendingDraftCount(userId: String): Flow<Int> = localDraftDao.observePendingDraftCount(userId)

    fun observeLocalBookCovers(userId: String): Flow<Map<String, String>> =
        localDraftDao.observeBookCovers(userId).map { list ->
            list.associate { it.bookId to it.imageUri }
        }

    suspend fun saveLocalBookCover(userId: String, bookId: String, imageUri: String?) {
        if (imageUri.isNullOrBlank()) {
            localDraftDao.deleteBookCover(bookId)
        } else {
            localDraftDao.upsertBookCover(
                LocalBookCoverEntity(
                    bookId = bookId,
                    userId = userId,
                    imageUri = imageUri,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    // --- USER PROFILE ---

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val path = "users/${user.uid}"
        return try {
            val uid = requireAuthorizedUserId(user.uid)
            val docRef = userDoc(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeDisplayName = (user.displayName?.trim()?.ifBlank { null }
                    ?: user.email?.substringBefore("@")
                    ?: "Kalamora Writer").take(80)
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeDisplayName,
                    "penName" to "",
                    "bio" to "",
                    "preferredLanguage" to WritingLanguage.HINDI.label,
                    "publicProfileEnabled" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                user.email?.take(120)?.let { payload["email"] = it }
                user.photoUrl?.toString()?.take(500)?.let { payload["photoUrl"] = it }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireAuthorizedUserId(userId)
        val path = "users/$uid"
        emitAll(
            userDoc(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateUserProfile(
        userId: String,
        displayName: String,
        penName: String,
        bio: String,
        preferredLanguage: WritingLanguage,
        publicProfileEnabled: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val cleanName = displayName.trim().ifBlank { "Kalamora Writer" }.take(80)
            val cleanPen = penName.trim().take(80)
            val cleanBio = bio.trim().take(300)
            val docRef = userDoc(uid)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "displayName" to cleanName,
                        "penName" to cleanPen,
                        "bio" to cleanBio,
                        "preferredLanguage" to preferredLanguage.label,
                        "publicProfileEnabled" to publicProfileEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "penName" to cleanPen,
                    "bio" to cleanBio,
                    "preferredLanguage" to preferredLanguage.label,
                    "publicProfileEnabled" to publicProfileEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                auth.currentUser?.email?.take(120)?.let { payload["email"] = it }
                auth.currentUser?.photoUrl?.toString()?.take(500)?.let { payload["photoUrl"] = it }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- SHAYARI ---

    fun observeShayariList(userId: String): Flow<List<Shayari>> = flow {
        val uid = requireAuthorizedUserId(userId)
        val path = "users/$uid/shayari"
        emitAll(
            shayariCollection(uid)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Shayari::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getLocalShayariDraft(userId: String, shayariId: String): LocalDraftEntity? =
        localDraftDao.getDraft(shayariDraftKey(userId, shayariId))

    suspend fun saveShayariLocalOnly(
        userId: String,
        shayariId: String,
        title: String,
        content: String,
        category: ShayariCategory,
        language: WritingLanguage,
        privacyStatus: PrivacyStatus,
        publishStatus: PublishStatus,
        favorite: Boolean,
        authorName: String,
        cardTheme: ShayariCardTheme,
        pendingSync: Boolean = true
    ) {
        localDraftDao.upsertDraft(
            LocalDraftEntity(
                draftKey = shayariDraftKey(userId, shayariId),
                userId = userId,
                entityType = "SHAYARI",
                bookId = null,
                itemId = shayariId,
                title = title.take(160),
                content = content.take(10000),
                categoryOrAlignment = category.label,
                language = language.label,
                privacyStatus = privacyStatus.name,
                publishStatus = publishStatus.name,
                favorite = favorite,
                authorName = authorName.take(80),
                cardTheme = cardTheme.name,
                orderIndex = 0L,
                pendingSync = pendingSync,
                lastEditedMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveShayari(
        userId: String,
        existingId: String?,
        title: String,
        content: String,
        category: ShayariCategory,
        language: WritingLanguage,
        privacyStatus: PrivacyStatus,
        publishStatus: PublishStatus,
        favorite: Boolean,
        authorName: String,
        cardTheme: ShayariCardTheme,
        isNewDocument: Boolean
    ): SaveOutcome {
        val uid = try {
            requireAuthorizedUserId(userId)
        } catch (e: Exception) {
            val fallbackId = existingId ?: shayariCollection(userId).document().id
            return SaveOutcome.SyncFailedLocal(fallbackId, e.localizedMessage ?: "Not signed in")
        }

        val docRef = if (existingId.isNullOrBlank()) {
            shayariCollection(uid).document()
        } else {
            shayariCollection(uid).document(existingId)
        }
        val docId = docRef.id
        val cleanTitle = title.trim().take(160)
        val cleanContent = content.take(10000)
        val cleanAuthor = authorName.trim().take(80)
        val words = TextMetrics.countWords(cleanContent)
        val chars = TextMetrics.countCharacters(cleanContent)
        val draftKey = shayariDraftKey(uid, docId)

        // 1. Always secure local draft FIRST so no user work is ever lost
        saveShayariLocalOnly(
            userId = uid,
            shayariId = docId,
            title = cleanTitle,
            content = cleanContent,
            category = category,
            language = language,
            privacyStatus = privacyStatus,
            publishStatus = publishStatus,
            favorite = favorite,
            authorName = cleanAuthor,
            cardTheme = cardTheme,
            pendingSync = true
        )

        // 2. If offline, honestly report local save pending sync
        if (!connectivityObserver.isCurrentlyOnline()) {
            return SaveOutcome.SavedLocalPending(docId)
        }

        val opType = if (isNewDocument) OperationType.CREATE else OperationType.UPDATE
        return try {
            val synced = withTimeoutOrNull(CLOUD_WRITE_TIMEOUT_MS) {
                if (isNewDocument) {
                    val createPayload = mapOf(
                        "userId" to uid,
                        "title" to cleanTitle,
                        "content" to cleanContent,
                        "category" to category.label,
                        "language" to language.label,
                        "privacyStatus" to privacyStatus.name,
                        "publishStatus" to publishStatus.name,
                        "favorite" to favorite,
                        "wordCount" to words,
                        "charCount" to chars,
                        "authorName" to cleanAuthor,
                        "cardTheme" to cardTheme.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(createPayload).await()
                } else {
                    val updatePayload = mapOf(
                        "title" to cleanTitle,
                        "content" to cleanContent,
                        "category" to category.label,
                        "language" to language.label,
                        "privacyStatus" to privacyStatus.name,
                        "publishStatus" to publishStatus.name,
                        "favorite" to favorite,
                        "wordCount" to words,
                        "charCount" to chars,
                        "authorName" to cleanAuthor,
                        "cardTheme" to cardTheme.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    try {
                        docRef.update(updatePayload).await()
                    } catch (e: FirebaseFirestoreException) {
                        if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                            val createPayload = updatePayload + mapOf(
                                "userId" to uid,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                            docRef.set(createPayload).await()
                        } else {
                            throw e
                        }
                    }
                }
                true
            }
            if (synced == true) {
                localDraftDao.markSynced(draftKey)
                SaveOutcome.SavedCloud(docId)
            } else {
                SaveOutcome.SavedLocalPending(docId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, opType, docRef.path)
            SaveOutcome.SyncFailedLocal(docId, e.localizedMessage ?: "Cloud sync failed")
        }
    }

    suspend fun toggleShayariFavorite(userId: String, shayariId: String, favorite: Boolean): Result<Unit> {
        val path = "users/$userId/shayari/$shayariId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(shayariId.isNotBlank())
            shayariCollection(uid).document(shayariId).update(
                mapOf(
                    "favorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteShayari(userId: String, shayariId: String): Result<Unit> {
        val path = "users/$userId/shayari/$shayariId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(shayariId.isNotBlank())
            localDraftDao.deleteDraft(shayariDraftKey(uid, shayariId))
            shayariCollection(uid).document(shayariId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- BOOKS ---

    fun observeBooks(userId: String): Flow<List<Book>> = flow {
        val uid = requireAuthorizedUserId(userId)
        val path = "users/$uid/books"
        emitAll(
            booksCollection(uid)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Book::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createBook(
        userId: String,
        title: String,
        authorName: String,
        description: String,
        coverStyle: BookCoverStyle,
        language: WritingLanguage,
        genre: BookGenre,
        privacyStatus: PrivacyStatus,
        publishStatus: PublishStatus,
        localCoverUri: String? = null
    ): Result<String> {
        var path = "users/$userId/books"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val cleanTitle = title.trim().take(160)
            require(cleanTitle.isNotEmpty()) { "Book title cannot be empty" }
            val docRef = booksCollection(uid).document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "authorName" to authorName.trim().take(80),
                "description" to description.trim().take(2000),
                "coverStyle" to coverStyle.name,
                "language" to language.label,
                "genre" to genre.label,
                "privacyStatus" to privacyStatus.name,
                "publishStatus" to publishStatus.name,
                "favorite" to false,
                "chapterCount" to 1L,
                "totalWordCount" to 0L,
                "totalCharCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            // Create initial independent Chapter 1 for the new book
            val firstChapterRef = chaptersCollection(uid, docRef.id).document()
            val chapterPayload = mapOf(
                "userId" to uid,
                "bookId" to docRef.id,
                "title" to "Chapter 1",
                "content" to "",
                "orderIndex" to 0L,
                "textAlignment" to ChapterAlignment.START.name,
                "wordCount" to 0L,
                "charCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firstChapterRef.set(chapterPayload).await()

            if (!localCoverUri.isNullOrBlank()) {
                saveLocalBookCover(uid, docRef.id, localCoverUri)
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBook(
        userId: String,
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
        localCoverUri: String? = null,
        updateLocalCover: Boolean = false
    ): Result<Unit> {
        val path = "users/$userId/books/$bookId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val cleanTitle = title.trim().take(160)
            require(bookId.isNotBlank() && cleanTitle.isNotEmpty()) { "Book title cannot be empty" }
            booksCollection(uid).document(bookId).update(
                mapOf(
                    "title" to cleanTitle,
                    "authorName" to authorName.trim().take(80),
                    "description" to description.trim().take(2000),
                    "coverStyle" to coverStyle.name,
                    "language" to language.label,
                    "genre" to genre.label,
                    "privacyStatus" to privacyStatus.name,
                    "publishStatus" to publishStatus.name,
                    "favorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (updateLocalCover) {
                saveLocalBookCover(uid, bookId, localCoverUri)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleBookFavorite(userId: String, bookId: String, favorite: Boolean): Result<Unit> {
        val path = "users/$userId/books/$bookId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(bookId.isNotBlank())
            booksCollection(uid).document(bookId).update(
                mapOf(
                    "favorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBookWithChapters(userId: String, bookId: String): Result<Unit> {
        val path = "users/$userId/books/$bookId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(bookId.isNotBlank())
            localDraftDao.deleteBookChapterDrafts(uid, bookId)
            localDraftDao.deleteBookCover(bookId)
            val chapters = chaptersCollection(uid, bookId).get().await()
            for (chunk in chapters.documents.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            booksCollection(uid).document(bookId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- INDEPENDENT CHAPTERS PER BOOK ---

    fun observeChapters(userId: String, bookId: String): Flow<List<Chapter>> = flow {
        val uid = requireAuthorizedUserId(userId)
        require(bookId.isNotBlank())
        val path = "users/$uid/books/$bookId/chapters"
        emitAll(
            chaptersCollection(uid, bookId)
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Chapter::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getLocalChapterDraft(userId: String, bookId: String, chapterId: String): LocalDraftEntity? =
        localDraftDao.getDraft(chapterDraftKey(userId, bookId, chapterId))

    suspend fun saveChapterLocalOnly(
        userId: String,
        bookId: String,
        chapterId: String,
        title: String,
        content: String,
        alignment: ChapterAlignment,
        orderIndex: Long,
        pendingSync: Boolean = true
    ) {
        localDraftDao.upsertDraft(
            LocalDraftEntity(
                draftKey = chapterDraftKey(userId, bookId, chapterId),
                userId = userId,
                entityType = "CHAPTER",
                bookId = bookId,
                itemId = chapterId,
                title = title.trim().ifBlank { "Untitled Chapter" }.take(160),
                content = content.take(200000),
                categoryOrAlignment = alignment.name,
                language = "",
                privacyStatus = PrivacyStatus.PRIVATE.name,
                publishStatus = PublishStatus.DRAFT.name,
                favorite = false,
                authorName = "",
                cardTheme = "",
                orderIndex = orderIndex,
                pendingSync = pendingSync,
                lastEditedMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun createChapter(
        userId: String,
        bookId: String,
        title: String,
        orderIndex: Long
    ): Result<String> {
        var path = "users/$userId/books/$bookId/chapters"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(bookId.isNotBlank())
            val cleanTitle = title.trim().ifBlank { "Chapter ${orderIndex + 1}" }.take(160)
            val docRef = chaptersCollection(uid, bookId).document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "bookId" to bookId,
                "title" to cleanTitle,
                "content" to "",
                "orderIndex" to orderIndex.coerceAtLeast(0L),
                "textAlignment" to ChapterAlignment.START.name,
                "wordCount" to 0L,
                "charCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            recalculateBookStats(uid, bookId)
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveChapter(
        userId: String,
        bookId: String,
        chapterId: String,
        title: String,
        content: String,
        alignment: ChapterAlignment,
        orderIndex: Long
    ): SaveOutcome {
        val uid = try {
            requireAuthorizedUserId(userId)
        } catch (e: Exception) {
            return SaveOutcome.SyncFailedLocal(chapterId, e.localizedMessage ?: "Not signed in")
        }

        val cleanTitle = title.trim().ifBlank { "Untitled Chapter" }.take(160)
        val cleanContent = content.take(200000)
        val words = TextMetrics.countWords(cleanContent)
        val chars = TextMetrics.countCharacters(cleanContent)
        val draftKey = chapterDraftKey(uid, bookId, chapterId)

        // 1. Always persist local Room draft keyed strictly to (userId, bookId, chapterId)
        saveChapterLocalOnly(
            userId = uid,
            bookId = bookId,
            chapterId = chapterId,
            title = cleanTitle,
            content = cleanContent,
            alignment = alignment,
            orderIndex = orderIndex,
            pendingSync = true
        )

        if (!connectivityObserver.isCurrentlyOnline()) {
            return SaveOutcome.SavedLocalPending(chapterId)
        }

        val docRef = chaptersCollection(uid, bookId).document(chapterId)
        return try {
            val synced = withTimeoutOrNull(CLOUD_WRITE_TIMEOUT_MS) {
                docRef.update(
                    mapOf(
                        "title" to cleanTitle,
                        "content" to cleanContent,
                        "textAlignment" to alignment.name,
                        "wordCount" to words,
                        "charCount" to chars,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                recalculateBookStats(uid, bookId)
                true
            }
            if (synced == true) {
                localDraftDao.markSynced(draftKey)
                SaveOutcome.SavedCloud(chapterId)
            } else {
                SaveOutcome.SavedLocalPending(chapterId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            SaveOutcome.SyncFailedLocal(chapterId, e.localizedMessage ?: "Cloud sync failed")
        }
    }

    suspend fun reorderChapters(userId: String, bookId: String, orderedChapterIds: List<String>): Result<Unit> {
        val path = "users/$userId/books/$bookId/chapters"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(bookId.isNotBlank())
            val batch = db.batch()
            orderedChapterIds.forEachIndexed { idx, chapterId ->
                val ref = chaptersCollection(uid, bookId).document(chapterId)
                batch.update(
                    ref,
                    mapOf(
                        "orderIndex" to idx.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteChapter(userId: String, bookId: String, chapterId: String): Result<Unit> {
        val path = "users/$userId/books/$bookId/chapters/$chapterId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(bookId.isNotBlank() && chapterId.isNotBlank())
            localDraftDao.deleteDraft(chapterDraftKey(uid, bookId, chapterId))
            chaptersCollection(uid, bookId).document(chapterId).delete().await()
            recalculateBookStats(uid, bookId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun recalculateBookStats(uid: String, bookId: String) {
        val path = "users/$uid/books/$bookId"
        try {
            val chaptersSnap = chaptersCollection(uid, bookId).get().await()
            val chapters = chaptersSnap.toObjects(
                Chapter::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val totalWords = chapters.sumOf { it.wordCount }
            val totalChars = chapters.sumOf { it.charCount }
            booksCollection(uid).document(bookId).update(
                mapOf(
                    "chapterCount" to chapters.size.toLong(),
                    "totalWordCount" to totalWords,
                    "totalCharCount" to totalChars,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    // --- SYNC ALL PENDING OFFLINE DRAFTS ---

    suspend fun syncAllPendingDrafts(userId: String): SyncSummary {
        if (!connectivityObserver.isCurrentlyOnline()) {
            val pending = localDraftDao.getPendingDrafts(userId).size
            return SyncSummary(syncedCount = 0, failedCount = pending)
        }
        val drafts = localDraftDao.getPendingDrafts(userId)
        var synced = 0
        var failed = 0
        for (draft in drafts) {
            val outcome = when (draft.entityType) {
                "SHAYARI" -> saveShayari(
                    userId = userId,
                    existingId = draft.itemId,
                    title = draft.title,
                    content = draft.content,
                    category = ShayariCategory.fromRaw(draft.categoryOrAlignment),
                    language = WritingLanguage.fromRaw(draft.language),
                    privacyStatus = PrivacyStatus.fromRaw(draft.privacyStatus),
                    publishStatus = PublishStatus.fromRaw(draft.publishStatus),
                    favorite = draft.favorite,
                    authorName = draft.authorName,
                    cardTheme = ShayariCardTheme.fromRaw(draft.cardTheme),
                    isNewDocument = false
                )
                "CHAPTER" -> {
                    val bId = draft.bookId
                    if (bId.isNullOrBlank()) {
                        SaveOutcome.SyncFailedLocal(draft.itemId, "Missing book reference")
                    } else {
                        saveChapter(
                            userId = userId,
                            bookId = bId,
                            chapterId = draft.itemId,
                            title = draft.title,
                            content = draft.content,
                            alignment = ChapterAlignment.fromRaw(draft.categoryOrAlignment),
                            orderIndex = draft.orderIndex
                        )
                    }
                }
                else -> SaveOutcome.SyncFailedLocal(draft.itemId, "Unknown draft type")
            }
            if (outcome is SaveOutcome.SavedCloud) synced++ else failed++
        }
        return SyncSummary(syncedCount = synced, failedCount = failed)
    }

    private companion object {
        const val CLOUD_WRITE_TIMEOUT_MS = 5000L
    }
}
