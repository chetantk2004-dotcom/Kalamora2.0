package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LocalDraftEntity::class, LocalBookCoverEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KalamoraDatabase : RoomDatabase() {
    abstract fun localDraftDao(): LocalDraftDao

    companion object {
        @Volatile
        private var INSTANCE: KalamoraDatabase? = null

        fun getInstance(context: Context): KalamoraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalamoraDatabase::class.java,
                    "kalamora_local_drafts.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
