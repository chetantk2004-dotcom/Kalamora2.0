package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Book
import com.example.data.model.Shayari
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.BookCoverThumbnail
import com.example.ui.components.EmptyStudioState
import com.example.ui.components.ShayariPaperCard
import com.example.ui.components.formatFriendlyDate
import com.example.ui.theme.LiteraryManuscriptFont
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberBg
import com.example.ui.viewmodel.WritingStats

@Composable
fun HomeScreen(
    userProfile: UserProfile?,
    stats: WritingStats,
    booksState: UiState<List<Book>>,
    shayariState: UiState<List<Shayari>>,
    localBookCovers: Map<String, String>,
    isOnline: Boolean,
    pendingDraftCount: Int,
    onSyncPendingDrafts: () -> Unit,
    onWriteShayariClick: () -> Unit,
    onCreateBookClick: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenShayari: (String) -> Unit,
    onToggleShayariFavorite: (Shayari) -> Unit,
    onOpenLibrary: () -> Unit,
    onViewAllBooks: () -> Unit,
    onViewAllShayari: () -> Unit
) {
    val books = (booksState as? UiState.Success)?.data.orEmpty()
    val shayariList = (shayariState as? UiState.Success)?.data.orEmpty()

    val latestBook = books.firstOrNull()
    val latestShayari = shayariList.firstOrNull()
    val continueBook = when {
        latestBook != null && latestShayari != null -> {
            val bTime = latestBook.updatedAt?.toDate()?.time ?: 0L
            val sTime = latestShayari.updatedAt?.toDate()?.time ?: 0L
            if (bTime >= sTime) latestBook else null
        }
        latestBook != null -> latestBook
        else -> null
    }
    val continueShayari = if (continueBook == null) latestShayari else null

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Welcome Masthead
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "KALAMORA · कलम की दुनिया",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Welcome back, ${userProfile?.effectiveAuthorName ?: "Writer"}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Offline / Pending Sync Banner
            if (!isOnline || pendingDraftCount > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PendingAmberBg,
                        border = BorderStroke(1.dp, PendingAmber.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (!isOnline) Icons.Default.CloudOff else Icons.Default.Sync,
                                    contentDescription = "Offline status",
                                    tint = PendingAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (!isOnline) {
                                        "Offline mode · Your drafts are protected locally ($pendingDraftCount pending)"
                                    } else {
                                        "$pendingDraftCount local draft(s) ready to sync to Cloud"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PendingAmber
                                )
                            }
                            if (isOnline && pendingDraftCount > 0) {
                                TextButton(onClick = onSyncPendingDrafts) {
                                    Text("Sync Now", color = PendingAmber, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // Writing Statistics Strip (sourced 100% from Firestore)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatTile(
                        label = stringResource(R.string.stat_books),
                        value = stats.totalBooks.toString(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_books_card")
                    )
                    StatTile(
                        label = stringResource(R.string.stat_shayari),
                        value = stats.totalShayari.toString(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_shayari_card")
                    )
                    StatTile(
                        label = stringResource(R.string.stat_words),
                        value = formatWordNumber(stats.totalWords),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_words_card")
                    )
                }
            }

            // Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onWriteShayariClick,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("quick_write_shayari_button")
                    ) {
                        Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_quick_write_shayari), maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onCreateBookClick,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("quick_create_book_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_quick_create_book), maxLines = 1)
                    }
                }
            }

            // Continue Writing Spotlight
            if (continueBook != null || continueShayari != null) {
                item {
                    ContinueWritingSpotlight(
                        book = continueBook,
                        shayari = continueShayari,
                        onContinue = {
                            if (continueBook != null) onOpenBook(continueBook.id)
                            else if (continueShayari != null) onOpenShayari(continueShayari.id)
                        }
                    )
                }
            }

            // Personal Library Organizer Entry Card
            item {
                Surface(
                    onClick = onOpenLibrary,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_personal_library_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderCopy,
                                    contentDescription = "My Library",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.home_open_library),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${stats.totalDrafts} drafts · ${stats.totalPublished} published · ${stats.totalFavorites} favorites",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open library",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Recently Edited Books Section
            item {
                SectionHeader(
                    title = stringResource(R.string.home_recent_books),
                    actionText = if (books.isNotEmpty()) "See All" else null,
                    onAction = onViewAllBooks
                )
            }

            item {
                when (booksState) {
                    is UiState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
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
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    is UiState.Success -> {
                        if (books.isEmpty()) {
                            EmptyStudioState(
                                icon = Icons.Default.AutoStories,
                                title = "Start your first book",
                                subtitle = "Draft multi-chapter novels, poetry collections, or memoirs with automatic chapter saving and PDF publishing.",
                                actionLabel = "Create Book",
                                onAction = onCreateBookClick
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(books.take(6), key = { it.id }) { book ->
                                    Column(
                                        modifier = Modifier
                                            .width(128.dp)
                                            .clickable { onOpenBook(book.id) }
                                            .testTag("home_book_${book.id}")
                                    ) {
                                        BookCoverThumbnail(
                                            book = book,
                                            localCoverUri = localBookCovers[book.id],
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = book.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${book.chapterCount} ch · ${book.totalWordCount}w",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recently Written Shayari Section
            item {
                SectionHeader(
                    title = stringResource(R.string.home_recent_shayari),
                    actionText = if (shayariList.isNotEmpty()) "See All" else null,
                    onAction = onViewAllShayari
                )
            }

            when (shayariState) {
                is UiState.Loading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                is UiState.Error -> item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = shayariState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    if (shayariList.isEmpty()) {
                        item {
                            EmptyStudioState(
                                icon = Icons.Default.Create,
                                title = "Pen your first Shayari",
                                subtitle = "Write verses in Hindi, English, or Urdu and export them as art cards or clean PDFs.",
                                actionLabel = "Write Shayari",
                                onAction = onWriteShayariClick
                            )
                        }
                    } else {
                        items(shayariList.take(5), key = { it.id }) { shayari ->
                            ShayariPaperCard(
                                shayari = shayari,
                                onClick = { onOpenShayari(shayari.id) },
                                onToggleFavorite = { onToggleShayariFavorite(shayari) }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContinueWritingSpotlight(
    book: Book?,
    shayari: Shayari?,
    onContinue: () -> Unit
) {
    Card(
        onClick = onContinue,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("continue_writing_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_continue_writing).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = book?.title
                            ?: shayari?.title?.ifBlank { shayari.content.lines().firstOrNull().orEmpty() }
                            ?: "",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = LiteraryManuscriptFont),
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (book != null) {
                            "${book.chapterCount} chapters · ${book.totalWordCount} words · Edited ${formatFriendlyDate(book.updatedAt)}"
                        } else {
                            "${shayari?.category} · ${shayari?.wordCount} words · Edited ${formatFriendlyDate(shayari?.updatedAt)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = if (book != null) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Create,
                        contentDescription = "Continue writing",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionText: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionText != null) {
            TextButton(onClick = onAction) {
                Text(actionText, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun formatWordNumber(words: Long): String {
    return when {
        words >= 1_000_000 -> String.format("%.1fM", words / 1_000_000.0)
        words >= 10_000 -> String.format("%.1fk", words / 1_000.0)
        else -> words.toString()
    }
}
