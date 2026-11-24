package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PrivacyStatus
import com.example.data.model.PublishStatus
import com.example.data.model.SaveSyncState
import com.example.data.model.ShayariCardTheme
import com.example.data.model.ShayariCategory
import com.example.data.model.WritingLanguage
import com.example.export.ExportedFile
import com.example.export.KalamoraExporter
import com.example.ui.components.SaveStatusPill
import com.example.ui.components.ShayariCardPreviewCanvas
import com.example.ui.components.formatFriendlyDate
import com.example.ui.theme.LiteraryManuscriptFont
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberBg
import com.example.ui.viewmodel.ShayariStudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShayariStudioScreen(
    viewModel: ShayariStudioViewModel,
    currentUserId: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPreviewSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var lastExportedFile by remember { mutableStateOf<ExportedFile?>(null) }

    val handleExit = {
        viewModel.flushBeforeExit()
        onBack()
    }

    BackHandler { handleExit() }

    DisposableEffect(Unit) {
        onDispose { viewModel.flushBeforeExit() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SaveStatusPill(
                        state = state.saveSyncState,
                        onRetryClick = { viewModel.saveNow() }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = handleExit,
                        modifier = Modifier.testTag("shayari_editor_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onFavoriteToggle() },
                        modifier = Modifier.testTag("shayari_editor_favorite")
                    ) {
                        Icon(
                            imageVector = if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle favorite",
                            tint = if (state.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.saveNow { sync ->
                                val msg = when (sync) {
                                    SaveSyncState.SavedToCloud -> "Shayari saved to Cloud"
                                    SaveSyncState.SavedLocallyPendingSync -> "Saved locally · Will sync when online"
                                    is SaveSyncState.SyncFailedLocally -> "Cloud sync failed · Draft safe locally"
                                    else -> "Write something before saving"
                                }
                                onMessage(msg)
                            }
                        },
                        modifier = Modifier.testTag("shayari_editor_save")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save Shayari")
                    }
                    if (!state.shayariId.isNullOrBlank()) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("shayari_editor_delete")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Shayari",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showPreviewSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shayari_preview_card_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Card & Share", maxLines = 1)
                    }

                    Button(
                        onClick = {
                            val currentShayari = state.toShayari(currentUserId)
                            if (currentShayari.content.isBlank() && currentShayari.title.isBlank()) {
                                onMessage("Write your Shayari before exporting PDF")
                                return@Button
                            }
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    KalamoraExporter.exportShayariPdf(context, currentShayari)
                                }
                                result.onSuccess { exported ->
                                    lastExportedFile = exported
                                    onMessage("PDF saved to ${exported.savedDisplayLocation}")
                                }.onFailure { err ->
                                    onMessage(err.localizedMessage ?: "PDF export failed")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shayari_export_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export PDF", maxLines = 1)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.restoredLocalDraftBanner) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PendingAmberBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Restored your unsynced local draft.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PendingAmber,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.dismissDraftBanner() }) {
                                Text("Dismiss", color = PendingAmber)
                            }
                        }
                    }
                }

                // Warm Paper Writing Canvas
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Title Input
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (state.title.isEmpty()) {
                                Text(
                                    text = "Shayari Title (optional) · शीर्षक",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = LiteraryManuscriptFont
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )
                            }
                            BasicTextField(
                                value = state.title,
                                onValueChange = { viewModel.onTitleChange(it) },
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = LiteraryManuscriptFont,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("shayari_title_input")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Multiline Hindi / Unicode Content Editor
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        ) {
                            if (state.content.isEmpty()) {
                                Text(
                                    text = "दिल के अल्फ़ाज़ यहाँ लिखें…\nWrite your Shayari, ghazal, or poem here…",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = LiteraryManuscriptFont,
                                        fontSize = 18.sp,
                                        lineHeight = 30.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            BasicTextField(
                                value = state.content,
                                onValueChange = { viewModel.onContentChange(it) },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = LiteraryManuscriptFont,
                                    fontSize = 18.sp,
                                    lineHeight = 30.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("shayari_content_input")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Word count, Character count, Dates
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${state.wordCount} words · ${state.charCount} chars",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("shayari_metrics_text")
                            )
                            Text(
                                text = if (state.createdAt != null) {
                                    "Updated ${formatFriendlyDate(state.updatedAt ?: state.createdAt)}"
                                } else {
                                    "New draft"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Category Selector
                Text("Category", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ShayariCategory.entries) { cat ->
                        FilterChip(
                            selected = state.category == cat,
                            onClick = { viewModel.onCategoryChange(cat) },
                            label = { Text("${cat.label} · ${cat.hindiLabel}") },
                            modifier = Modifier.testTag("editor_cat_${cat.name.lowercase()}")
                        )
                    }
                }

                // Language Selector
                Text("Language", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(WritingLanguage.entries) { lang ->
                        FilterChip(
                            selected = state.language == lang,
                            onClick = { viewModel.onLanguageChange(lang) },
                            label = { Text(lang.label) }
                        )
                    }
                }

                // Privacy & Publish Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Privacy", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrivacyStatus.entries.forEach { privacy ->
                                FilterChip(
                                    selected = state.privacyStatus == privacy,
                                    onClick = { viewModel.onPrivacyChange(privacy) },
                                    label = { Text(privacy.label) }
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Status", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PublishStatus.entries.forEach { pub ->
                                FilterChip(
                                    selected = state.publishStatus == pub,
                                    onClick = { viewModel.onPublishStatusChange(pub) },
                                    label = { Text(pub.label) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // Shayari Visual Card Studio & Share Sheet
    if (showPreviewSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val previewShayari = state.toShayari(currentUserId)
        ModalBottomSheet(
            onDismissRequest = { showPreviewSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Shayari Card Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ShayariCardPreviewCanvas(
                    shayari = previewShayari,
                    theme = state.cardTheme
                )

                Text("Card Theme", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ShayariCardTheme.entries) { theme ->
                        val selected = state.cardTheme == theme
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.clickable { viewModel.onCardThemeChange(theme) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(theme.backgroundColor)
                                        .border(1.dp, theme.accentColor, CircleShape)
                                )
                                Text(theme.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            KalamoraExporter.sharePlainShayariText(context, previewShayari)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share Text")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    KalamoraExporter.exportShayariImage(context, previewShayari, state.cardTheme)
                                }
                                result.onSuccess { exported ->
                                    showPreviewSheet = false
                                    lastExportedFile = exported
                                    onMessage("Image saved to ${exported.savedDisplayLocation}")
                                }.onFailure { err ->
                                    onMessage(err.localizedMessage ?: "Failed to export image")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_shayari_image_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Image", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Exported File Ready Dialog (with immediate Android Share Sheet)
    lastExportedFile?.let { exported ->
        AlertDialog(
            onDismissRequest = { lastExportedFile = null },
            title = { Text("Export Complete") },
            text = {
                Text("Saved \"${exported.fileName}\" to ${exported.savedDisplayLocation}. Would you like to share it now?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = exported
                        lastExportedFile = null
                        KalamoraExporter.shareExportedFile(context, file)
                    },
                    modifier = Modifier.testTag("share_exported_file_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            },
            dismissButton = {
                TextButton(onClick = { lastExportedFile = null }) { Text("Done") }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this Shayari?") },
            text = { Text("This permanently removes the Shayari from your library and cloud backup.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteCurrentShayari {
                            onMessage("Shayari deleted")
                            onBack()
                        }
                    },
                    modifier = Modifier.testTag("confirm_delete_shayari_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
