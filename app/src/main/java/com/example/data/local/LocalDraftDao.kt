package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: LocalDraftEntity)

    @Query("SELECT * FROM local_drafts WHERE draftKey = :draftKey LIMIT 1")
    suspend fun getDraft(draftKey: String): LocalDraftEntity?

    @Query("SELECT * FROM local_drafts WHERE userId = :userId AND pendingSync = 1 ORDER BY lastEditedMillis DESC")
    suspend fun getPendingDrafts(userId: String): List<LocalDraftEntity>

    @Query("SELECT COUNT(*) FROM local_drafts WHERE userId = :userId AND pendingSync = 1")
    fun observePendingDraftCount(userId: String): Flow<Int>

    @Query("UPDATE local_drafts SET pendingSync = 0 WHERE draftKey = :draftKey")
    suspend fun markSynced(draftKey: String)

    @Query("DELETE FROM local_drafts WHERE draftKey = :draftKey")
    suspend fun deleteDraft(draftKey: String)

    @Query("DELETE FROM local_drafts WHERE userId = :userId AND bookId = :bookId")
    suspend fun deleteBookChapterDrafts(userId: String, bookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookCover(cover: LocalBookCoverEntity)

    @Query("SELECT * FROM local_book_covers WHERE userId = :userId")
    fun observeBookCovers(userId: String): Flow<List<LocalBookCoverEntity>>

    @Query("DELETE FROM local_book_covers WHERE bookId = :bookId")
    suspend fun deleteBookCover(bookId: String)
}
