package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Book
import com.example.data.model.PublishStatus
import com.example.data.model.Shayari
import com.example.ui.components.BookCoverThumbnail
import com.example.ui.components.EmptyStudioState
import com.example.ui.components.ShayariPaperCard
import com.example.ui.components.formatFriendlyDate

enum class LibraryShelf(val label: String) {
    RECENTLY_EDITED("Recently Edited"),
    ALL_BOOKS("All Books"),
    ALL_SHAYARI("All Shayari"),
    DRAFTS("Drafts"),
    PUBLISHED("Published"),
    FAVORITES("Favorites")
}

private sealed interface LibraryEntry {
    val updatedMillis: Long
    val title: String

    data class BookEntry(val book: Book) : LibraryEntry {
        override val updatedMillis: Long get() = book.updatedAt?.toDate()?.time ?: 0L
        override val title: String get() = book.title
    }

    data class ShayariEntry(val shayari: Shayari) : LibraryEntry {
        override val updatedMillis: Long get() = shayari.updatedAt?.toDate()?.time ?: 0L
        override val title: String get() = shayari.title.ifBlank { shayari.content }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    books: List<Book>,
    shayariList: List<Shayari>,
    localBookCovers: Map<String, String>,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenShayari: (String) -> Unit,
    onToggleShayariFavorite: (Shayari) -> Unit
) {
    BackHandler { onBack() }

    var selectedShelf by remember { mutableStateOf(LibraryShelf.RECENTLY_EDITED) }
    var searchQuery by remember { mutableStateOf("") }
    var sortAscending by remember { mutableStateOf(false) }

    val entries = remember(books, shayariList, selectedShelf, searchQuery, sortAscending) {
        val raw: List<LibraryEntry> = when (selectedShelf) {
            LibraryShelf.ALL_BOOKS -> books.map { LibraryEntry.BookEntry(it) }
            LibraryShelf.ALL_SHAYARI -> shayariList.map { LibraryEntry.ShayariEntry(it) }
            LibraryShelf.DRAFTS -> books.filter { it.publishStatus == PublishStatus.DRAFT.name }
                .map { LibraryEntry.BookEntry(it) } +
                shayariList.filter { it.publishStatus == PublishStatus.DRAFT.name }
                    .map { LibraryEntry.ShayariEntry(it) }
            LibraryShelf.PUBLISHED -> books.filter { it.publishStatus == PublishStatus.PUBLISHED.name }
                .map { LibraryEntry.BookEntry(it) } +
                shayariList.filter { it.publishStatus == PublishStatus.PUBLISHED.name }
                    .map { LibraryEntry.ShayariEntry(it) }
            LibraryShelf.FAVORITES -> books.filter { it.favorite }.map { LibraryEntry.BookEntry(it) } +
                shayariList.filter { it.favorite }.map { LibraryEntry.ShayariEntry(it) }
            LibraryShelf.RECENTLY_EDITED -> books.map { LibraryEntry.BookEntry(it) } +
                shayariList.map { LibraryEntry.ShayariEntry(it) }
        }

        val q = searchQuery.trim()
        val filtered = if (q.isEmpty()) raw else raw.filter { entry ->
            when (entry) {
                is LibraryEntry.BookEntry -> entry.book.title.contains(q, true) ||
                    entry.book.genre.contains(q, true) ||
                    entry.book.description.contains(q, true)
                is LibraryEntry.ShayariEntry -> entry.shayari.title.contains(q, true) ||
                    entry.shayari.content.contains(q, true) ||
                    entry.shayari.category.contains(q, true)
            }
        }

        if (sortAscending) {
            filtered.sortedBy { it.title.lowercase() }
        } else {
            filtered.sortedByDescending { it.updatedMillis }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Library") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("library_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        onClick = { sortAscending = !sortAscending },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (sortAscending) "A–Z" else "Recent",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 20.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search across all books & Shayari…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input")
                )

                Spacer(Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LibraryShelf.entries) { shelf ->
                        FilterChip(
                            selected = selectedShelf == shelf,
                            onClick = { selectedShelf = shelf },
                            label = { Text(shelf.label) },
                            modifier = Modifier.testTag("library_shelf_${shelf.name.lowercase()}")
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (entries.isEmpty()) {
                    EmptyStudioState(
                        icon = Icons.Default.FolderCopy,
                        title = "Nothing in ${selectedShelf.label}",
                        subtitle = "Your books and Shayari matching this shelf will appear here automatically."
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        items(
                            items = entries,
                            key = {
                                when (it) {
                                    is LibraryEntry.BookEntry -> "book_${it.book.id}"
                                    is LibraryEntry.ShayariEntry -> "shayari_${it.shayari.id}"
                                }
                            }
                        ) { entry ->
                            when (entry) {
                                is LibraryEntry.BookEntry -> {
                                    val book = entry.book
                                    Card(
                                        onClick = { onOpenBook(book.id) },
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BookCoverThumbnail(
                                                book = book,
                                                localCoverUri = localBookCovers[book.id],
                                                modifier = Modifier.width(56.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "BOOK · ${book.genre}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Text(
                                                    text = book.title,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${book.chapterCount} ch · ${book.totalWordCount} words · ${formatFriendlyDate(book.updatedAt)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                is LibraryEntry.ShayariEntry -> {
                                    ShayariPaperCard(
                                        shayari = entry.shayari,
                                        onClick = { onOpenShayari(entry.shayari.id) },
                                        onToggleFavorite = { onToggleShayariFavorite(entry.shayari) }
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
