package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Book
import com.example.data.model.BookCoverStyle
import com.example.data.model.BookGenre
import com.example.data.model.PrivacyStatus
import com.example.data.model.PublishStatus
import com.example.data.model.UiState
import com.example.data.model.WritingLanguage
import com.example.ui.components.BookCoverThumbnail
import com.example.ui.components.EmptyStudioState
import com.example.ui.components.formatFriendlyDate
import com.example.ui.theme.SyncedSage
import com.example.ui.theme.SyncedSageBg

private enum class BookFilterTab(val label: String) {
    ALL("All Books"),
    DRAFTS("Drafts"),
    PUBLISHED("Published"),
    FAVORITES("Favorites")
}

private enum class BookSortMode(val label: String) {
    RECENT("Recently Edited"),
    TITLE("Title A–Z"),
    WORDS("Most Words")
}

@Composable
fun MyBooksScreen(
    booksState: UiState<List<Book>>,
    localBookCovers: Map<String, String>,
    defaultAuthorName: String,
    showCreateDialogInitially: Boolean = false,
    onCreateDialogHandled: () -> Unit = {},
    onOpenBook: (String) -> Unit,
    onCreateBook: (
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
    ) -> Unit,
    onUpdateBook: (
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
        updateLocalCover: Boolean
    ) -> Unit,
    onToggleFavorite: (Book) -> Unit,
    onDeleteBook: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(BookFilterTab.ALL) }
    var selectedSort by remember { mutableStateOf(BookSortMode.RECENT) }
    var showCreateSheet by remember { mutableStateOf(showCreateDialogInitially) }
    var editingBook by remember { mutableStateOf<Book?>(null) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    if (showCreateDialogInitially && !showCreateSheet) {
        showCreateSheet = true
        onCreateDialogHandled()
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Create book") },
                text = { Text("New Book") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_create_book")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "My Books",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Your private manuscripts & multi-chapter works",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        onClick = {
                            val values = BookSortMode.entries
                            selectedSort = values[(values.indexOf(selectedSort) + 1) % values.size]
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.testTag("books_sort_button")
                    ) {
                        Text(
                            text = "Sort: ${selectedSort.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search books by title, genre, or synopsis…") },
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
                        .testTag("books_search_input")
                )

                Spacer(Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BookFilterTab.entries) { tab ->
                        FilterChip(
                            selected = selectedFilter == tab,
                            onClick = { selectedFilter = tab },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("book_filter_${tab.name.lowercase()}")
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                when (booksState) {
                    is UiState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    is UiState.Error -> Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = booksState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    is UiState.Success -> {
                        val filtered = booksState.data.filter { book ->
                            val matchesTab = when (selectedFilter) {
                                BookFilterTab.ALL -> true
                                BookFilterTab.DRAFTS -> book.publishStatus == PublishStatus.DRAFT.name
                                BookFilterTab.PUBLISHED -> book.publishStatus == PublishStatus.PUBLISHED.name
                                BookFilterTab.FAVORITES -> book.favorite
                            }
                            val q = searchQuery.trim()
                            val matchesSearch = q.isEmpty() ||
                                book.title.contains(q, ignoreCase = true) ||
                                book.description.contains(q, ignoreCase = true) ||
                                book.genre.contains(q, ignoreCase = true) ||
                                book.authorName.contains(q, ignoreCase = true)
                            matchesTab && matchesSearch
                        }.let { list ->
                            when (selectedSort) {
                                BookSortMode.RECENT -> list.sortedByDescending { it.updatedAt?.seconds ?: 0L }
                                BookSortMode.TITLE -> list.sortedBy { it.title.lowercase() }
                                BookSortMode.WORDS -> list.sortedByDescending { it.totalWordCount }
                            }
                        }

                        if (filtered.isEmpty()) {
                            EmptyStudioState(
                                icon = Icons.Default.AutoStories,
                                title = if (booksState.data.isEmpty()) "Your bookshelf is waiting" else "No matching books",
                                subtitle = if (booksState.data.isEmpty()) {
                                    "Create your first book and organize unlimited independent chapters with auto-save."
                                } else {
                                    "Try clearing your search or switching filter tabs."
                                },
                                actionLabel = if (booksState.data.isEmpty()) "Create Book" else null,
                                onAction = if (booksState.data.isEmpty()) ({ showCreateSheet = true }) else null
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 88.dp)
                            ) {
                                items(filtered, key = { it.id }) { book ->
                                    BookLibraryCard(
                                        book = book,
                                        localCoverUri = localBookCovers[book.id],
                                        onOpen = { onOpenBook(book.id) },
                                        onEditDetails = { editingBook = book },
                                        onToggleFavorite = { onToggleFavorite(book) },
                                        onRequestDelete = { bookToDelete = book }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        BookMetadataModalSheet(
            initialBook = null,
            initialCoverUri = null,
            defaultAuthorName = defaultAuthorName,
            onDismiss = {
                showCreateSheet = false
                onCreateDialogHandled()
            },
            onConfirm = { title, author, desc, coverStyle, lang, genre, privacy, publish, coverUri, _ ->
                showCreateSheet = false
                onCreateDialogHandled()
                onCreateBook(title, author, desc, coverStyle, lang, genre, privacy, publish, coverUri) { newId ->
                    onOpenBook(newId)
                }
            }
        )
    }

    editingBook?.let { target ->
        BookMetadataModalSheet(
            initialBook = target,
            initialCoverUri = localBookCovers[target.id],
            defaultAuthorName = defaultAuthorName,
            onDismiss = { editingBook = null },
            onConfirm = { title, author, desc, coverStyle, lang, genre, privacy, publish, coverUri, coverChanged ->
                editingBook = null
                onUpdateBook(
                    target.id, title, author, desc, coverStyle, lang, genre,
                    privacy, publish, target.favorite, coverUri, coverChanged
                )
            }
        )
    }

    bookToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("Delete \"${target.title}\"?") },
            text = {
                Text("This permanently deletes the book and all ${target.chapterCount} chapter(s) inside it. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        bookToDelete = null
                        onDeleteBook(id)
                    },
                    modifier = Modifier.testTag("confirm_delete_book_button")
                ) {
                    Text("Delete Book")
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BookLibraryCard(
    book: Book,
    localCoverUri: String?,
    onOpen: () -> Unit,
    onEditDetails: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("book_card_${book.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BookCoverThumbnail(
                book = book,
                localCoverUri = localCoverUri,
                modifier = Modifier.width(86.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "by ${book.authorName.ifBlank { "Kalamora Writer" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite book",
                            tint = if (book.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (book.description.isNotBlank()) {
                    Text(
                        text = book.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = book.genre,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (book.publishStatus == PublishStatus.PUBLISHED.name) SyncedSageBg else MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = PublishStatus.fromRaw(book.publishStatus).label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (book.publishStatus == PublishStatus.PUBLISHED.name) SyncedSage else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Icon(
                        imageVector = if (book.privacyStatus == PrivacyStatus.PRIVATE.name) Icons.Default.Lock else Icons.Default.Public,
                        contentDescription = book.privacyStatus,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${book.chapterCount} ch · ${book.totalWordCount} words · ${formatFriendlyDate(book.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        IconButton(onClick = onEditDetails, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit book details",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onRequestDelete, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete book",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookMetadataModalSheet(
    initialBook: Book?,
    initialCoverUri: String?,
    defaultAuthorName: String,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        authorName: String,
        description: String,
        coverStyle: BookCoverStyle,
        language: WritingLanguage,
        genre: BookGenre,
        privacyStatus: PrivacyStatus,
        publishStatus: PublishStatus,
        localCoverUri: String?,
        coverChanged: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(initialBook?.title.orEmpty()) }
    var authorName by remember { mutableStateOf(initialBook?.authorName?.ifBlank { defaultAuthorName } ?: defaultAuthorName) }
    var description by remember { mutableStateOf(initialBook?.description.orEmpty()) }
    var coverStyle by remember { mutableStateOf(BookCoverStyle.fromRaw(initialBook?.coverStyle)) }
    var language by remember { mutableStateOf(WritingLanguage.fromRaw(initialBook?.language)) }
    var genre by remember { mutableStateOf(BookGenre.fromRaw(initialBook?.genre)) }
    var privacyStatus by remember { mutableStateOf(PrivacyStatus.fromRaw(initialBook?.privacyStatus)) }
    var publishStatus by remember { mutableStateOf(PublishStatus.fromRaw(initialBook?.publishStatus)) }
    var coverUri by remember { mutableStateOf(initialCoverUri) }
    var coverChanged by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coverUri = uri.toString()
            coverChanged = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (initialBook == null) "Create New Book" else "Edit Book Details",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it.take(160)
                    titleError = false
                },
                label = { Text("Book Title *") },
                placeholder = { Text("e.g., काग़ज़ के परिंदे or Letters at Dusk") },
                isError = titleError,
                supportingText = if (titleError) ({ Text("Please enter a book title") }) else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("book_title_input")
            )

            OutlinedTextField(
                value = authorName,
                onValueChange = { authorName = it.take(80) },
                label = { Text("Author Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("book_author_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(2000) },
                label = { Text("Synopsis / Description") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("book_description_input")
            )

            // Cover Style Picker + Zero-Permission Photo Picker
            Text("Book Cover Style", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCoverStyle.entries.forEach { style ->
                    val selected = coverStyle == style && coverUri.isNullOrBlank()
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(style.primaryColor)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else style.goldColor,
                                shape = CircleShape
                            )
                            .clickable {
                                coverStyle = style
                                if (!coverUri.isNullOrBlank()) {
                                    coverUri = null
                                    coverChanged = true
                                }
                            }
                    )
                }
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (coverUri.isNullOrBlank()) "Photo" else "Change", style = MaterialTheme.typography.labelMedium)
                }
            }

            Text("Genre", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BookGenre.entries) { g ->
                    FilterChip(
                        selected = genre == g,
                        onClick = { genre = g },
                        label = { Text(g.label) }
                    )
                }
            }

            Text("Language", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WritingLanguage.entries) { lang ->
                    FilterChip(
                        selected = language == lang,
                        onClick = { language = lang },
                        label = { Text(lang.label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Privacy", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrivacyStatus.entries.forEach { p ->
                            FilterChip(
                                selected = privacyStatus == p,
                                onClick = { privacyStatus = p },
                                label = { Text(p.label) }
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PublishStatus.entries.forEach { st ->
                            FilterChip(
                                selected = publishStatus == st,
                                onClick = { publishStatus = st },
                                label = { Text(st.label) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (title.trim().isEmpty()) {
                        titleError = true
                    } else {
                        onConfirm(
                            title.trim(),
                            authorName.trim(),
                            description.trim(),
                            coverStyle,
                            language,
                            genre,
                            privacyStatus,
                            publishStatus,
                            coverUri,
                            coverChanged
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_book_metadata_button")
            ) {
                Text(
                    text = if (initialBook == null) "Create Book & Open Studio" else "Save Book Details",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
