package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PublishStatus
import com.example.data.model.Shayari
import com.example.data.model.ShayariCategory
import com.example.data.model.UiState
import com.example.ui.components.EmptyStudioState
import com.example.ui.components.ShayariPaperCard

private enum class ShayariStatusFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    DRAFTS("Drafts"),
    PUBLISHED("Published")
}

@Composable
fun MyShayariScreen(
    shayariState: UiState<List<Shayari>>,
    onWriteNewShayari: () -> Unit,
    onOpenShayari: (String) -> Unit,
    onToggleFavorite: (Shayari) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ShayariCategory?>(null) }
    var selectedStatus by remember { mutableStateOf(ShayariStatusFilter.ALL) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onWriteNewShayari,
                icon = { Icon(Icons.Default.Create, contentDescription = "Write Shayari") },
                text = { Text("Write Shayari") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_write_shayari")
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
                            text = "My Shayari",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Verses, ghazals & couplets in Hindi, English & Unicode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Shayari by title, lines, or language…") },
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
                        .testTag("shayari_search_input")
                )

                Spacer(Modifier.height(10.dp))

                // Status Filter Row
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ShayariStatusFilter.entries) { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text(status.label) },
                            modifier = Modifier.testTag("shayari_status_${status.name.lowercase()}")
                        )
                    }
                }

                // Category Filter Row
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All Categories") }
                        )
                    }
                    items(ShayariCategory.entries) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = if (selectedCategory == category) null else category
                            },
                            label = { Text(category.label) },
                            modifier = Modifier.testTag("shayari_cat_${category.name.lowercase()}")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                when (shayariState) {
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
                            text = shayariState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    is UiState.Success -> {
                        val filtered = shayariState.data.filter { item ->
                            val matchesCat = selectedCategory == null ||
                                item.category.equals(selectedCategory!!.label, ignoreCase = true)
                            val matchesStatus = when (selectedStatus) {
                                ShayariStatusFilter.ALL -> true
                                ShayariStatusFilter.FAVORITES -> item.favorite
                                ShayariStatusFilter.DRAFTS -> item.publishStatus == PublishStatus.DRAFT.name
                                ShayariStatusFilter.PUBLISHED -> item.publishStatus == PublishStatus.PUBLISHED.name
                            }
                            val q = searchQuery.trim()
                            val matchesSearch = q.isEmpty() ||
                                item.title.contains(q, ignoreCase = true) ||
                                item.content.contains(q, ignoreCase = true) ||
                                item.category.contains(q, ignoreCase = true) ||
                                item.language.contains(q, ignoreCase = true)
                            matchesCat && matchesStatus && matchesSearch
                        }

                        if (filtered.isEmpty()) {
                            EmptyStudioState(
                                icon = Icons.Default.Create,
                                title = if (shayariState.data.isEmpty()) "No Shayari written yet" else "No matching Shayari",
                                subtitle = if (shayariState.data.isEmpty()) {
                                    "Capture your first verse in Hindi or English and craft shareable Shayari cards."
                                } else {
                                    "Try choosing a different category or clearing your search."
                                },
                                actionLabel = if (shayariState.data.isEmpty()) "Write Shayari" else null,
                                onAction = if (shayariState.data.isEmpty()) onWriteNewShayari else null
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 88.dp)
                            ) {
                                items(filtered, key = { it.id }) { shayari ->
                                    ShayariPaperCard(
                                        shayari = shayari,
                                        onClick = { onOpenShayari(shayari.id) },
                                        onToggleFavorite = { onToggleFavorite(shayari) }
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
