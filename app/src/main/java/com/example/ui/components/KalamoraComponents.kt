package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Book
import com.example.data.model.BookCoverStyle
import com.example.data.model.PrivacyStatus
import com.example.data.model.PublishStatus
import com.example.data.model.SaveSyncState
import com.example.data.model.Shayari
import com.example.data.model.ShayariCardTheme
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.ErrorCrimsonBg
import com.example.ui.theme.LiteraryManuscriptFont
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberBg
import com.example.ui.theme.PlayfairDisplayFontFamily
import com.example.ui.theme.SyncedSage
import com.example.ui.theme.SyncedSageBg
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

fun formatFriendlyDate(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return "Just now"
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
}

@Composable
fun SaveStatusPill(
    state: SaveSyncState,
    onRetryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (bg, fg, label, icon) = when (state) {
        SaveSyncState.Idle -> Quad(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Ready",
            Icons.Default.Edit
        )
        SaveSyncState.UnsavedChanges -> Quad(
            PendingAmberBg,
            PendingAmber,
            "Unsaved changes",
            Icons.Default.Edit
        )
        SaveSyncState.Saving -> Quad(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Saving…",
            Icons.Default.Sync
        )
        SaveSyncState.SavedToCloud -> Quad(
            SyncedSageBg,
            SyncedSage,
            "Saved to Cloud",
            Icons.Default.CloudDone
        )
        SaveSyncState.SavedLocallyPendingSync -> Quad(
            PendingAmberBg,
            PendingAmber,
            "Saved Locally · Pending Sync",
            Icons.Default.CloudOff
        )
        is SaveSyncState.SyncFailedLocally -> Quad(
            ErrorCrimsonBg,
            ErrorCrimson,
            "Sync Failed · Safe Locally",
            Icons.Default.ErrorOutline
        )
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = modifier
            .then(
                if (state is SaveSyncState.SyncFailedLocally || state is SaveSyncState.SavedLocallyPendingSync) {
                    Modifier.clickable { onRetryClick() }
                } else Modifier
            )
            .testTag("save_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (state == SaveSyncState.Saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.8.dp,
                    color = fg
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = fg,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun BookCoverThumbnail(
    book: Book,
    localCoverUri: String?,
    modifier: Modifier = Modifier
) {
    val style = BookCoverStyle.fromRaw(book.coverStyle)
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp, topStart = 4.dp, bottomStart = 4.dp))
            .background(
                Brush.linearGradient(
                    listOf(style.primaryColor, style.secondaryColor)
                )
            )
    ) {
        if (!localCoverUri.isNullOrBlank()) {
            AsyncImage(
                model = localCoverUri,
                contentDescription = "Book cover for ${book.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.72f))
                        )
                    )
            )
        } else if (style == BookCoverStyle.MAHOGANY) {
            Image(
                painter = painterResource(R.drawable.img_book_cover_ornament_1795534286959),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.32f)
            )
        }

        // Subtle book spine crease
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(8.dp)
                .background(Color.Black.copy(alpha = 0.18f))
        )

        // Inner filigree gold frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                .border(1.dp, style.goldColor.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = book.genre.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.9.sp),
                    color = if (!localCoverUri.isNullOrBlank()) Color(0xFFF3DCA9) else style.goldColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = book.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = LiteraryManuscriptFont,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    ),
                    color = if (!localCoverUri.isNullOrBlank()) Color.White else style.textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = book.authorName.ifBlank { "Kalamora" },
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic, fontSize = 9.sp),
                    color = if (!localCoverUri.isNullOrBlank()) Color(0xFFEDE2D1) else style.textColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ShayariPaperCard(
    shayari: Shayari,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("shayari_card_${shayari.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Subtle antique gold manuscript edge ribbon
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(124.dp)
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = shayari.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Text(
                                text = shayari.language,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (shayari.publishStatus == PublishStatus.PUBLISHED.name) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SyncedSageBg
                            ) {
                                Text(
                                    text = "Published",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SyncedSage,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("shayari_fav_${shayari.id}")
                    ) {
                        Icon(
                            imageVector = if (shayari.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (shayari.favorite) "Remove from favorites" else "Mark as favorite",
                            tint = if (shayari.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (shayari.title.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = shayari.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = shayari.content.ifBlank { "Untitled verse draft…" },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = LiteraryManuscriptFont,
                        lineHeight = 25.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (shayari.privacyStatus == PrivacyStatus.PRIVATE.name) {
                                Icons.Default.Lock
                            } else {
                                Icons.Default.Public
                            },
                            contentDescription = shayari.privacyStatus,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${shayari.wordCount} words · ${formatFriendlyDate(shayari.updatedAt ?: shayari.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShayariCardPreviewCanvas(
    shayari: Shayari,
    theme: ShayariCardTheme,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(20.dp))
            .background(theme.backgroundColor)
            .padding(18.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.5.dp, theme.accentColor.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .padding(8.dp)
                .border(0.8.dp, theme.accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = shayari.category.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                        color = theme.accentColor
                    )
                    Text(
                        text = "“",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = PlayfairDisplayFontFamily,
                            fontSize = 44.sp,
                            lineHeight = 40.sp
                        ),
                        color = theme.accentColor.copy(alpha = 0.65f)
                    )
                    if (shayari.title.isNotBlank()) {
                        Text(
                            text = shayari.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = LiteraryManuscriptFont,
                                fontWeight = FontWeight.Bold
                            ),
                            color = theme.accentColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = shayari.content.trim().ifBlank { "आपकी शायरी यहाँ दिखाई देगी…" },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = LiteraryManuscriptFont,
                        fontSize = if (shayari.content.length > 180) 15.sp else 18.sp,
                        lineHeight = 28.sp
                    ),
                    color = theme.textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(1.dp)
                            .background(theme.accentColor.copy(alpha = 0.6f))
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "— ${shayari.authorName.ifBlank { "Kalamora Writer" }}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = LiteraryManuscriptFont,
                            fontStyle = FontStyle.Italic
                        ),
                        color = theme.subtextColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "KALAMORA · कलम की दुनिया",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PlayfairDisplayFontFamily,
                            letterSpacing = 1.4.sp,
                            fontSize = 9.sp
                        ),
                        color = theme.accentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStudioState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
