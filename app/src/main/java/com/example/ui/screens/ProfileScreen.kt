package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.data.model.WritingLanguage
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.SyncedSage
import com.example.ui.viewmodel.WritingStats

@Composable
fun ProfileScreen(
    userProfile: UserProfile?,
    authEmail: String?,
    authPhotoUrl: String?,
    stats: WritingStats,
    isOnline: Boolean,
    pendingDraftCount: Int,
    isDarkSepiaTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onSaveProfile: (
        displayName: String,
        penName: String,
        bio: String,
        preferredLanguage: WritingLanguage,
        publicProfileEnabled: Boolean
    ) -> Unit,
    onSyncPendingDrafts: () -> Unit,
    onOpenLibrary: () -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember { mutableStateOf(userProfile?.displayName.orEmpty()) }
    var penName by remember { mutableStateOf(userProfile?.penName.orEmpty()) }
    var bio by remember { mutableStateOf(userProfile?.bio.orEmpty()) }
    var preferredLanguage by remember { mutableStateOf(WritingLanguage.fromRaw(userProfile?.preferredLanguage)) }
    var publicProfileEnabled by remember { mutableStateOf(userProfile?.publicProfileEnabled ?: false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        if (userProfile != null) {
            displayName = userProfile.displayName
            penName = userProfile.penName
            bio = userProfile.bio
            preferredLanguage = WritingLanguage.fromRaw(userProfile.preferredLanguage)
            publicProfileEnabled = userProfile.publicProfileEnabled
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Text(
                    text = "Author Profile",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Author Identity Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val photo = userProfile?.photoUrl?.ifBlank { authPhotoUrl } ?: authPhotoUrl
                        if (!photo.isNullOrBlank()) {
                            AsyncImage(
                                model = photo,
                                contentDescription = "Author profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (displayName.firstOrNull() ?: 'K').uppercaseChar().toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.effectiveAuthorName ?: displayName.ifBlank { "Kalamora Writer" },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val emailDisplay = userProfile?.email?.ifBlank { authEmail } ?: authEmail
                            if (!emailDisplay.isNullOrBlank()) {
                                Text(
                                    text = emailDisplay,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${stats.totalBooks} books · ${stats.totalShayari} Shayari · ${stats.totalWords} words",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Edit Author Details Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Writer Identity", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(80) },
                            label = { Text("Display Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_display_name_input")
                        )

                        OutlinedTextField(
                            value = penName,
                            onValueChange = { penName = it.take(80) },
                            label = { Text("Pen Name / Takhallus (printed on exports)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_pen_name_input")
                        )

                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it.take(300) },
                            label = { Text("Author Bio") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_bio_input")
                        )

                        Text("Preferred Writing Language", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(WritingLanguage.entries) { lang ->
                                FilterChip(
                                    selected = preferredLanguage == lang,
                                    onClick = { preferredLanguage = lang },
                                    label = { Text(lang.label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Community Author Profile Readiness", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Opt-in flag reserved for future Kalamora public reader community. All private works stay strictly private.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = publicProfileEnabled,
                                onCheckedChange = { publicProfileEnabled = it }
                            )
                        }

                        Button(
                            onClick = {
                                onSaveProfile(
                                    displayName,
                                    penName,
                                    bio,
                                    preferredLanguage,
                                    publicProfileEnabled
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_profile_button")
                        ) {
                            Text("Save Author Profile", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Studio Theme & Offline Safety Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Writing Studio & Sync", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDarkSepiaTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = "Theme mode",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = if (isDarkSepiaTheme) "Sepia Night Ink" else "Warm Cream Paper",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Comfortable reading & late-night writing theme",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isDarkSepiaTheme,
                                onCheckedChange = onToggleTheme,
                                modifier = Modifier.testTag("theme_toggle_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isOnline && pendingDraftCount == 0) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = "Sync status",
                                    tint = if (isOnline && pendingDraftCount == 0) SyncedSage else PendingAmber
                                )
                                Column {
                                    Text(
                                        text = if (isOnline) "Connected to Kalamora Cloud" else "Offline Mode",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (pendingDraftCount == 0) {
                                            "All manuscripts & local drafts are synced."
                                        } else {
                                            "$pendingDraftCount local draft(s) pending cloud sync."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (pendingDraftCount > 0) {
                                OutlinedButton(onClick = onSyncPendingDrafts) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Sync")
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenLibrary,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open Complete Personal Library")
                        }
                    }
                }
            }

            // Sign Out
            item {
                OutlinedButton(
                    onClick = { showSignOutConfirm = true },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out of Kalamora?") },
            text = { Text("Your cloud manuscripts remain safely stored under your Google account.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
