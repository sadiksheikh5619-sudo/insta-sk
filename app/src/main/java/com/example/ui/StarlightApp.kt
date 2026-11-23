package com.example.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.auth.AuthManager
import com.example.data.model.Post
import com.example.data.model.PublicProfile
import com.example.data.model.UiState
import com.example.data.prefs.ThemePreferences
import com.example.data.repository.StarlightRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.CommentsScreen
import com.example.ui.screens.CreateScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.ReelsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StoryViewerScreen
import com.example.ui.screens.UserProfileScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val CREATE = "create/{mode}"
    const val REELS = "reels"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "edit_profile"
    const val CHAT_LIST = "chat_list"
    const val CHAT_DETAIL = "chat/{otherUserId}"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val STORY_VIEWER = "story_viewer/{userId}"
    const val USER_PROFILE = "user_profile/{userId}"
    const val COMMENTS = "comments/{postId}"

    fun create(mode: String = "post") = "create/$mode"
    fun chat(otherUserId: String) = "chat/$otherUserId"
    fun storyViewer(userId: String) = "story_viewer/$userId"
    fun userProfile(userId: String) = "user_profile/$userId"
    fun comments(postId: String) = "comments/$postId"
}

@Composable
fun StarlightApp(
    darkMode: Boolean,
    notificationsEnabled: Boolean,
    themePreferences: ThemePreferences
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedStarlightShell(
                currentUserId = user.uid,
                darkMode = darkMode,
                notificationsEnabled = notificationsEnabled,
                themePreferences = themePreferences,
                onSignedOut = { currentUser = null }
            )
        }
    }
}

@Composable
private fun AuthenticatedStarlightShell(
    currentUserId: String,
    darkMode: Boolean,
    notificationsEnabled: Boolean,
    themePreferences: ThemePreferences,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: StarlightViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StarlightViewModel(
                    repository = StarlightRepository(db),
                    themePreferences = themePreferences,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val privateUserState by viewModel.privateUserState.collectAsStateWithLifecycle()
    val myProfileState by viewModel.myPublicProfileState.collectAsStateWithLifecycle()
    val myPostsState by viewModel.myPostsState.collectAsStateWithLifecycle()
    val publicProfilesState by viewModel.publicProfilesState.collectAsStateWithLifecycle()
    val publicPostsState by viewModel.publicPostsState.collectAsStateWithLifecycle()
    val publicStoriesState by viewModel.publicStoriesState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val likedPostIds by viewModel.likedPostIds.collectAsStateWithLifecycle()
    val savedPostIds by viewModel.savedPostIds.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.statusMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Trigger local Android notification when a new unread notification arrives
    var lastNotifiedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notificationsState, notificationsEnabled) {
        if (!notificationsEnabled) return@LaunchedEffect
        val latest = (notificationsState as? UiState.Success)?.data?.firstOrNull { !it.read } ?: return@LaunchedEffect
        if (lastNotifiedId != null && lastNotifiedId != latest.id) {
            showLocalSocialNotification(
                context = context,
                title = "@${latest.actorUsername}",
                body = latest.previewText
            )
        }
        lastNotifiedId = latest.id
    }

    val privateUser = (privateUserState as? UiState.Success)?.data
    val myProfile = (myProfileState as? UiState.Success)?.data

    // Show loading while initial user record loads, or onboarding if setup incomplete
    if (privateUserState is UiState.Loading && myProfileState is UiState.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (privateUser != null && !privateUser.profileSetupComplete) {
        ProfileSetupScreen(
            initialPrivateUser = privateUser,
            initialPublicProfile = myProfile,
            isBusy = isBusy,
            onSave = { username, displayName, bio, avatarUrl, privateAccount ->
                viewModel.saveProfileSetup(username, displayName, bio, avatarUrl, privateAccount) {}
            }
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in setOf(
        Routes.HOME,
        Routes.EXPLORE,
        Routes.REELS,
        Routes.PROFILE
    )

    val allProfiles = (publicProfilesState as? UiState.Success)?.data.orEmpty()
    val allPosts = (publicPostsState as? UiState.Success)?.data.orEmpty()
    val myPosts = (myPostsState as? UiState.Success)?.data.orEmpty()
    val allStories = (publicStoriesState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    val navItems = listOf(
                        Triple(Routes.HOME, stringResource(R.string.nav_home), Icons.Filled.Home to Icons.Outlined.Home),
                        Triple(Routes.EXPLORE, stringResource(R.string.nav_explore), Icons.Filled.Explore to Icons.Outlined.Explore),
                        Triple(Routes.create("post"), stringResource(R.string.nav_create), Icons.Filled.AddCircle to Icons.Outlined.AddCircleOutline),
                        Triple(Routes.REELS, stringResource(R.string.nav_reels), Icons.Filled.Movie to Icons.Outlined.Movie),
                        Triple(Routes.PROFILE, stringResource(R.string.nav_profile), Icons.Filled.Person to Icons.Outlined.Person)
                    )
                    navItems.forEach { (route, label, icons) ->
                        val selected = currentRoute == route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (route.startsWith("create/")) {
                                    navController.navigate(route)
                                } else {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) icons.first else icons.second,
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("bottom_nav_${label.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    currentUserId = currentUserId,
                    myProfile = myProfile,
                    postsState = publicPostsState,
                    storiesState = publicStoriesState,
                    profiles = allProfiles,
                    likedPostIds = likedPostIds,
                    savedPostIds = savedPostIds,
                    followingIds = followingIds,
                    unreadNotifications = unreadNotifications,
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onOpenChats = { navController.navigate(Routes.CHAT_LIST) },
                    onOpenCreatePost = { navController.navigate(Routes.create("post")) },
                    onOpenCreateStory = { navController.navigate(Routes.create("story")) },
                    onOpenStoryViewer = { uid -> navController.navigate(Routes.storyViewer(uid)) },
                    onOpenComments = { post -> navController.navigate(Routes.comments(post.id)) },
                    onOpenUserProfile = { uid ->
                        if (uid == currentUserId) navController.navigate(Routes.PROFILE)
                        else navController.navigate(Routes.userProfile(uid))
                    },
                    onToggleLike = { post -> viewModel.toggleLike(post) },
                    onToggleSave = { postId -> viewModel.toggleSave(postId) },
                    onToggleFollow = { profile -> viewModel.toggleFollow(profile) },
                    onDeletePost = { post -> viewModel.deletePost(post) },
                    onBlockUser = { uid, uname -> viewModel.blockUser(uid, uname) },
                    onReportPost = { postId, reason -> viewModel.submitReport("post", postId, reason) }
                )
            }

            composable(Routes.EXPLORE) {
                ExploreScreen(
                    currentUserId = currentUserId,
                    profilesState = publicProfilesState,
                    postsState = publicPostsState,
                    followingIds = followingIds,
                    onOpenUserProfile = { uid ->
                        if (uid == currentUserId) navController.navigate(Routes.PROFILE)
                        else navController.navigate(Routes.userProfile(uid))
                    },
                    onOpenComments = { post -> navController.navigate(Routes.comments(post.id)) },
                    onToggleFollow = { profile -> viewModel.toggleFollow(profile) },
                    onOpenCreatePost = { navController.navigate(Routes.create("post")) }
                )
            }

            composable(
                route = Routes.CREATE,
                arguments = listOf(navArgument("mode") { type = NavType.StringType })
            ) { entry ->
                val mode = entry.arguments?.getString("mode") ?: "post"
                CreateScreen(
                    initialMode = mode,
                    isBusy = isBusy,
                    onBack = { navController.popBackStack() },
                    onPublishPost = { postType, mediaType, mediaUrl, thumbUrl, caption, category, tags, music, visibility ->
                        viewModel.createPost(
                            postType, mediaType, mediaUrl, thumbUrl, caption, category, tags, music, visibility
                        ) {
                            navController.popBackStack()
                        }
                    },
                    onPublishStory = { mediaType, mediaUrl, caption, highlight, highlightTitle ->
                        viewModel.createStory(mediaType, mediaUrl, caption, highlight, highlightTitle) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(Routes.REELS) {
                ReelsScreen(
                    currentUserId = currentUserId,
                    postsState = publicPostsState,
                    profiles = allProfiles,
                    likedPostIds = likedPostIds,
                    savedPostIds = savedPostIds,
                    followingIds = followingIds,
                    onCreateReel = { navController.navigate(Routes.create("reel")) },
                    onOpenComments = { post -> navController.navigate(Routes.comments(post.id)) },
                    onOpenUserProfile = { uid ->
                        if (uid == currentUserId) navController.navigate(Routes.PROFILE)
                        else navController.navigate(Routes.userProfile(uid))
                    },
                    onToggleLike = { post -> viewModel.toggleLike(post) },
                    onToggleSave = { postId -> viewModel.toggleSave(postId) },
                    onToggleFollow = { profile -> viewModel.toggleFollow(profile) }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    myProfile = myProfile,
                    myPostsState = myPostsState,
                    allPublicPosts = allPosts,
                    savedPostIds = savedPostIds,
                    stories = allStories,
                    onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenPost = { post -> navController.navigate(Routes.comments(post.id)) },
                    onOpenHighlight = { uid -> navController.navigate(Routes.storyViewer(uid)) },
                    onCreatePost = { navController.navigate(Routes.create("post")) }
                )
            }

            composable(Routes.EDIT_PROFILE) {
                ProfileSetupScreen(
                    initialPrivateUser = privateUser,
                    initialPublicProfile = myProfile,
                    isBusy = isBusy,
                    onSave = { username, displayName, bio, avatarUrl, privateAccount ->
                        viewModel.saveProfileSetup(username, displayName, bio, avatarUrl, privateAccount) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(Routes.CHAT_LIST) {
                ChatListScreen(
                    currentUserId = currentUserId,
                    conversationsState = conversationsState,
                    profiles = allProfiles,
                    onBack = { navController.popBackStack() },
                    onOpenConversation = { other -> navController.navigate(Routes.chat(other.userId)) },
                    onExploreCreators = {
                        navController.navigate(Routes.EXPLORE) {
                            popUpTo(navController.graph.findStartDestination().id)
                        }
                    }
                )
            }

            composable(
                route = Routes.CHAT_DETAIL,
                arguments = listOf(navArgument("otherUserId") { type = NavType.StringType })
            ) { entry ->
                val otherUserId = entry.arguments?.getString("otherUserId") ?: ""
                val otherProfile = allProfiles.firstOrNull { it.userId == otherUserId }
                    ?: PublicProfile(userId = otherUserId, username = "creator", displayName = "Creator")
                val convId = remember(otherUserId) { viewModel.conversationIdFor(otherUserId) }
                val messagesState by viewModel.observeMessages(convId)
                    .collectAsStateWithLifecycle(initialValue = UiState.Loading)

                ChatDetailScreen(
                    currentUserId = currentUserId,
                    otherProfile = otherProfile,
                    messagesState = messagesState,
                    onBack = { navController.popBackStack() },
                    onOpenUserProfile = { uid -> navController.navigate(Routes.userProfile(uid)) },
                    onSendMessage = { type, text, mediaUrl, durationMs ->
                        viewModel.sendMessage(otherProfile, type, text, mediaUrl, durationMs)
                    },
                    onNotify = { msg -> viewModel.emitMessage(msg) }
                )
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    notificationsState = notificationsState,
                    onBack = { navController.popBackStack() },
                    onMarkAllRead = { items -> viewModel.markAllNotificationsRead(items) },
                    onOpenActorProfile = { uid -> navController.navigate(Routes.userProfile(uid)) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    privateUser = privateUser,
                    darkMode = darkMode,
                    notificationsEnabled = notificationsEnabled,
                    blockedUsers = blockedUsers,
                    onBack = { navController.popBackStack() },
                    onUpdateSettings = { priv, dark, push ->
                        viewModel.updateSettings(priv, dark, push)
                    },
                    onUnblockUser = { uid -> viewModel.unblockUser(uid) },
                    onSignOut = {
                        AuthManager.signOut(
                            credentialManager = credentialManager,
                            scope = scope,
                            onBeforeSignOut = { viewModel.prepareForSignOut() },
                            onSignOutComplete = onSignedOut
                        )
                    },
                    onDeleteAccount = {
                        viewModel.deleteAccount {
                            AuthManager.signOut(
                                credentialManager = credentialManager,
                                scope = scope,
                                onSignOutComplete = onSignedOut
                            )
                        }
                    }
                )
            }

            composable(
                route = Routes.STORY_VIEWER,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { entry ->
                val targetUid = entry.arguments?.getString("userId") ?: ""
                val userStories = remember(allStories, targetUid) {
                    allStories.filter { it.userId == targetUid }
                }
                StoryViewerScreen(
                    currentUserId = currentUserId,
                    stories = userStories,
                    onClose = { navController.popBackStack() },
                    onMarkViewed = { story -> viewModel.markStoryViewed(story) },
                    onToggleHighlight = { story, highlight ->
                        viewModel.toggleStoryHighlight(story, highlight, story.highlightTitle.ifBlank { "Highlights" })
                    },
                    onDeleteStory = { storyId ->
                        viewModel.deleteStory(storyId) { navController.popBackStack() }
                    }
                )
            }

            composable(
                route = Routes.USER_PROFILE,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { entry ->
                val targetUid = entry.arguments?.getString("userId") ?: ""
                LaunchedEffect(targetUid) {
                    viewModel.loadTargetProfile(targetUid)
                }
                val cached by viewModel.selectedProfileCache.collectAsStateWithLifecycle()
                val profile = allProfiles.firstOrNull { it.userId == targetUid } ?: cached
                val userPostsState by viewModel.observeUserPosts(targetUid)
                    .collectAsStateWithLifecycle(initialValue = UiState.Loading)

                UserProfileScreen(
                    currentUserId = currentUserId,
                    profile = profile,
                    userPostsState = userPostsState,
                    isFollowing = targetUid in followingIds,
                    onBack = { navController.popBackStack() },
                    onToggleFollow = { p -> viewModel.toggleFollow(p) },
                    onMessageUser = { p -> navController.navigate(Routes.chat(p.userId)) },
                    onOpenPost = { post -> navController.navigate(Routes.comments(post.id)) },
                    onBlockUser = { uid, uname -> viewModel.blockUser(uid, uname) },
                    onReportUser = { uid, reason -> viewModel.submitReport("user", uid, reason) }
                )
            }

            composable(
                route = Routes.COMMENTS,
                arguments = listOf(navArgument("postId") { type = NavType.StringType })
            ) { entry ->
                val postId = entry.arguments?.getString("postId") ?: ""
                val post = (allPosts + myPosts).firstOrNull { it.id == postId }
                if (post == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val commentsState by viewModel.observeComments(post)
                        .collectAsStateWithLifecycle(initialValue = UiState.Loading)
                    CommentsScreen(
                        post = post,
                        commentsState = commentsState,
                        onBack = { navController.popBackStack() },
                        onAddComment = { text -> viewModel.addComment(post, text) },
                        onOpenUserProfile = { uid -> navController.navigate(Routes.userProfile(uid)) }
                    )
                }
            }
        }
    }
}

private fun showLocalSocialNotification(context: Context, title: String, body: String) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val channelId = "starlight_activity"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "STARLIGHT Activity",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
}
