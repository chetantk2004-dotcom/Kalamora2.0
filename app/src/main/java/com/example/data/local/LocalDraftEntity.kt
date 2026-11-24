package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_drafts")
data class LocalDraftEntity(
    @PrimaryKey val draftKey: String,
    val userId: String,
    val entityType: String, // "SHAYARI" or "CHAPTER"
    val bookId: String?,
    val itemId: String,
    val title: String,
    val content: String,
    val categoryOrAlignment: String,
    val language: String,
    val privacyStatus: String,
    val publishStatus: String,
    val favorite: Boolean,
    val authorName: String,
    val cardTheme: String,
    val orderIndex: Long,
    val pendingSync: Boolean,
    val lastEditedMillis: Long
)

@Entity(tableName = "local_book_covers")
data class LocalBookCoverEntity(
    @PrimaryKey val bookId: String,
    val userId: String,
    val imageUri: String,
    val updatedAtMillis: Long
)
