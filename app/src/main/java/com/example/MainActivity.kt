package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.KalamoraDatabase
import com.example.data.local.UserPreferencesStore
import com.example.data.model.UiState
import com.example.data.remote.ConnectivityObserver
import com.example.data.remote.KalamoraRepository
import com.example.data.remote.authStateFlow
import com.example.data.remote.signOutUser
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BookStudioScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MyBooksScreen
import com.example.ui.screens.MyShayariScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ShayariStudioScreen
import com.example.ui.theme.KalamoraTheme
import com.example.ui.viewmodel.BookStudioViewModel
import com.example.ui.viewmodel.KalamoraViewModel
import com.example.ui.viewmodel.ShayariStudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KalamoraAppRoot()
        }
    }
}

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    BOOKS("My Books", Icons.Filled.AutoStories, Icons.Outlined.AutoStories, "nav_tab_books"),
    SHAYARI("My Shayari", Icons.Filled.Create, Icons.Outlined.Create, "nav_tab_shayari"),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_tab_profile")
}

private sealed interface StudioDestination {
    data object Tabs : StudioDestination
    data object Library : StudioDestination
    data class ShayariEditor(val shayariId: String?) : StudioDestination
    data class BookEditor(val bookId: String) : StudioDestination
}

@Composable
fun KalamoraAppRoot() {
    val context = LocalContext.current
    val preferencesStore = remember(context) { UserPreferencesStore(context.applicationContext) }
    val isDarkSepiaTheme by preferencesStore.isDarkSepiaTheme.collectAsStateWithLifecycle(initialValue = false)

    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    KalamoraTheme(darkTheme = isDarkSepiaTheme) {
        val user = currentUser
        if (user == null) {
            // Unauthenticated gate: No Firestore listeners or feature ViewModels are created here.
            AuthScreen(onAuthSuccess = { })
        } else {
            // Key by authenticated UID so account switches tear down listeners cleanly.
            key(user.uid) {
                AuthenticatedKalamoraShell(
                    user = user,
                    preferencesStore = preferencesStore
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedKalamoraShell(
    user: FirebaseUser,
    preferencesStore: UserPreferencesStore
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val mainViewModel: KalamoraViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val dao = KalamoraDatabase.getInstance(app).localDraftDao()
                val connectivity = ConnectivityObserver(app)
                val repo = KalamoraRepository(db, dao, connectivity)
                KalamoraViewModel(repo, preferencesStore, user.uid)
            }
        }
    )

    val userProfileState by mainViewModel.userProfileState.collectAsStateWithLifecycle()
    val booksState by mainViewModel.booksState.collectAsStateWithLifecycle()
    val shayariState by mainViewModel.shayariState.collectAsStateWithLifecycle()
    val stats by mainViewModel.writingStats.collectAsStateWithLifecycle()
    val localBookCovers by mainViewModel.localBookCovers.collectAsStateWithLifecycle()
    val isOnline by mainViewModel.isOnline.collectAsStateWithLifecycle()
    val pendingDraftCount by mainViewModel.pendingDraftCount.collectAsStateWithLifecycle()
    val isDarkSepiaTheme by mainViewModel.isDarkSepiaTheme.collectAsStateWithLifecycle()

    val userProfile = (userProfileState as? UiState.Success)?.data
    val books = (booksState as? UiState.Success)?.data.orEmpty()
    val shayariList = (shayariState as? UiState.Success)?.data.orEmpty()
    val defaultAuthorName = userProfile?.effectiveAuthorName
        ?: user.displayName?.ifBlank { null }
        ?: "Kalamora Writer"

    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var destination by remember { mutableStateOf<StudioDestination>(StudioDestination.Tabs) }
    var triggerCreateBookSheet by remember { mutableStateOf(false) }

    LaunchedEffect(mainViewModel) {
        mainViewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    when (val dest = destination) {
        is StudioDestination.Library -> {
            LibraryScreen(
                books = books,
                shayariList = shayariList,
                localBookCovers = localBookCovers,
                onBack = { destination = StudioDestination.Tabs },
                onOpenBook = { bookId -> destination = StudioDestination.BookEditor(bookId) },
                onOpenShayari = { shayariId -> destination = StudioDestination.ShayariEditor(shayariId) },
                onToggleShayariFavorite = { mainViewModel.toggleShayariFavorite(it) }
            )
        }

        is StudioDestination.ShayariEditor -> {
            val existingShayari = remember(dest.shayariId, shayariList) {
                if (dest.shayariId.isNullOrBlank()) null else shayariList.firstOrNull { it.id == dest.shayariId }
            }
            val editorKey = "shayari_${user.uid}_${dest.shayariId ?: "new_${System.identityHashCode(dest)}"}"
            val shayariVm: ShayariStudioViewModel = viewModel(
                key = editorKey,
                factory = viewModelFactory {
                    initializer {
                        ShayariStudioViewModel(
                            repository = mainViewModel.repository,
                            currentUserId = user.uid,
                            defaultAuthorName = defaultAuthorName,
                            initialShayari = existingShayari
                        )
                    }
                }
            )
            ShayariStudioScreen(
                viewModel = shayariVm,
                currentUserId = user.uid,
                onBack = { destination = StudioDestination.Tabs },
                onMessage = { mainViewModel.emitMessage(it) }
            )
        }

        is StudioDestination.BookEditor -> {
            val activeBook = books.firstOrNull { it.id == dest.bookId }
            if (activeBook == null) {
                destination = StudioDestination.Tabs
            } else {
                val bookVm: BookStudioViewModel = viewModel(
                    key = "book_${user.uid}_${dest.bookId}",
                    factory = viewModelFactory {
                        initializer {
                            BookStudioViewModel(
                                repository = mainViewModel.repository,
                                currentUserId = user.uid,
                                bookId = dest.bookId
                            )
                        }
                    }
                )
                BookStudioScreen(
                    book = activeBook,
                    localCoverUri = localBookCovers[activeBook.id],
                    defaultAuthorName = defaultAuthorName,
                    viewModel = bookVm,
                    onUpdateBookMetadata = { title, author, desc, coverStyle, lang, genre, privacy, publish, favorite, coverUri, coverChanged ->
                        mainViewModel.updateBook(
                            bookId = activeBook.id,
                            title = title,
                            authorName = author,
                            description = desc,
                            coverStyle = coverStyle,
                            language = lang,
                            genre = genre,
                            privacyStatus = privacy,
                            publishStatus = publish,
                            favorite = favorite,
                            localCoverUri = coverUri,
                            updateLocalCover = coverChanged
                        )
                    },
                    onBack = { destination = StudioDestination.Tabs },
                    onMessage = { mainViewModel.emitMessage(it) }
                )
            }
        }

        StudioDestination.Tabs -> {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 640.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!useNavigationRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                MainTab.entries.forEach { tab ->
                                    val selected = selectedTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { selectedTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavigationRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                MainTab.entries.forEach { tab ->
                                    val selected = selectedTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { selectedTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (selectedTab) {
                                MainTab.HOME -> HomeScreen(
                                    userProfile = userProfile,
                                    stats = stats,
                                    booksState = booksState,
                                    shayariState = shayariState,
                                    localBookCovers = localBookCovers,
                                    isOnline = isOnline,
                                    pendingDraftCount = pendingDraftCount,
                                    onSyncPendingDrafts = { mainViewModel.syncPendingDraftsNow() },
                                    onWriteShayariClick = {
                                        destination = StudioDestination.ShayariEditor(null)
                                    },
                                    onCreateBookClick = {
                                        selectedTab = MainTab.BOOKS
                                        triggerCreateBookSheet = true
                                    },
                                    onOpenBook = { bookId ->
                                        destination = StudioDestination.BookEditor(bookId)
                                    },
                                    onOpenShayari = { shayariId ->
                                        destination = StudioDestination.ShayariEditor(shayariId)
                                    },
                                    onToggleShayariFavorite = { mainViewModel.toggleShayariFavorite(it) },
                                    onOpenLibrary = { destination = StudioDestination.Library },
                                    onViewAllBooks = { selectedTab = MainTab.BOOKS },
                                    onViewAllShayari = { selectedTab = MainTab.SHAYARI }
                                )

                                MainTab.BOOKS -> MyBooksScreen(
                                    booksState = booksState,
                                    localBookCovers = localBookCovers,
                                    defaultAuthorName = defaultAuthorName,
                                    showCreateDialogInitially = triggerCreateBookSheet,
                                    onCreateDialogHandled = { triggerCreateBookSheet = false },
                                    onOpenBook = { bookId ->
                                        destination = StudioDestination.BookEditor(bookId)
                                    },
                                    onCreateBook = { title, author, desc, coverStyle, lang, genre, privacy, publish, coverUri, onCreated ->
                                        mainViewModel.createBook(
                                            title = title,
                                            authorName = author,
                                            description = desc,
                                            coverStyle = coverStyle,
                                            language = lang,
                                            genre = genre,
                                            privacyStatus = privacy,
                                            publishStatus = publish,
                                            localCoverUri = coverUri,
                                            onCreated = onCreated
                                        )
                                    },
                                    onUpdateBook = { bookId, title, author, desc, coverStyle, lang, genre, privacy, publish, favorite, coverUri, coverChanged ->
                                        mainViewModel.updateBook(
                                            bookId = bookId,
                                            title = title,
                                            authorName = author,
                                            description = desc,
                                            coverStyle = coverStyle,
                                            language = lang,
                                            genre = genre,
                                            privacyStatus = privacy,
                                            publishStatus = publish,
                                            favorite = favorite,
                                            localCoverUri = coverUri,
                                            updateLocalCover = coverChanged
                                        )
                                    },
                                    onToggleFavorite = { mainViewModel.toggleBookFavorite(it) },
                                    onDeleteBook = { mainViewModel.deleteBook(it) }
                                )

                                MainTab.SHAYARI -> MyShayariScreen(
                                    shayariState = shayariState,
                                    onWriteNewShayari = {
                                        destination = StudioDestination.ShayariEditor(null)
                                    },
                                    onOpenShayari = { shayariId ->
                                        destination = StudioDestination.ShayariEditor(shayariId)
                                    },
                                    onToggleFavorite = { mainViewModel.toggleShayariFavorite(it) }
                                )

                                MainTab.PROFILE -> ProfileScreen(
                                    userProfile = userProfile,
                                    authEmail = user.email,
                                    authPhotoUrl = user.photoUrl?.toString(),
                                    stats = stats,
                                    isOnline = isOnline,
                                    pendingDraftCount = pendingDraftCount,
                                    isDarkSepiaTheme = isDarkSepiaTheme,
                                    onToggleTheme = { mainViewModel.setDarkSepiaTheme(it) },
                                    onSaveProfile = { displayName, penName, bio, prefLang, pubEnabled ->
                                        mainViewModel.updateProfile(displayName, penName, bio, prefLang, pubEnabled)
                                    },
                                    onSyncPendingDrafts = { mainViewModel.syncPendingDraftsNow() },
                                    onOpenLibrary = { destination = StudioDestination.Library },
                                    onSignOut = {
                                        signOutUser(
                                            credentialManager = credentialManager,
                                            scope = scope
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
