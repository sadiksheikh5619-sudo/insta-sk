package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BlockedUserRef
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.PrivateUser
import com.example.data.model.PublicProfile
import com.example.data.model.Story
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ReportDialog
import com.example.ui.components.StarlightAvatar
import com.example.ui.components.StarlightImage
import com.example.ui.components.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    myProfile: PublicProfile?,
    myPostsState: UiState<List<Post>>,
    allPublicPosts: List<Post>,
    savedPostIds: Set<String>,
    stories: List<Story>,
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenHighlight: (String) -> Unit,
    onCreatePost: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Posts" to Icons.Default.GridOn, "Reels" to Icons.Default.Movie, "Saved" to Icons.Default.Bookmark)

    val myPosts = (myPostsState as? UiState.Success)?.data.orEmpty()
    val myReels = remember(myPosts) { myPosts.filter { it.postType == "reel" } }
    val savedPosts = remember(allPublicPosts, myPosts, savedPostIds) {
        (allPublicPosts + myPosts).distinctBy { it.id }.filter { it.id in savedPostIds }
    }
    val highlights = remember(stories, myProfile) {
        stories.filter { it.userId == myProfile?.userId && it.highlight }
    }

    val activePosts = when (selectedTab) {
        1 -> myReels
        2 -> savedPosts
        else -> myPosts.filter { it.postType == "post" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "@${myProfile?.username ?: "starlight"}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (myProfile?.verified == true) {
                            Icon(Icons.Default.Verified, contentDescription = "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("profile_settings_button")) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StarlightAvatar(
                            avatarUrl = myProfile?.avatarUrl.orEmpty(),
                            username = myProfile?.username ?: "S",
                            size = 84.dp,
                            hasStoryRing = stories.any { it.userId == myProfile?.userId }
                        )
                        ProfileStat(count = myProfile?.postsCount ?: myPosts.size.toLong(), label = "Posts")
                        ProfileStat(count = myProfile?.followersCount ?: 0L, label = "Followers")
                        ProfileStat(count = myProfile?.followingCount ?: 0L, label = "Following")
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = myProfile?.displayName ?: "Starlight Creator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!myProfile?.bio.isNullOrBlank()) {
                            Text(
                                text = myProfile.bio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onEditProfile,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_profile_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Profile")
                    }
                }
            }

            if (highlights.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Story Highlights",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(highlights, key = { it.id }) { story ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { onOpenHighlight(story.userId) }
                                ) {
                                    StarlightImage(
                                        source = story.mediaUrl,
                                        contentDescription = story.highlightTitle,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = story.highlightTitle.ifBlank { "Highlight" },
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, (label, icon) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(label) },
                            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }

            if (activePosts.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = when (selectedTab) {
                            1 -> "No Reels Yet"
                            2 -> "No Saved Posts"
                            else -> "Share Your First Post"
                        },
                        subtitle = when (selectedTab) {
                            1 -> "Record or upload a vertical video to showcase in Reels."
                            2 -> "Tap the bookmark icon on any post or reel to save it here."
                            else -> "Your photo and video posts will appear on your profile grid."
                        },
                        actionLabel = "Create Now",
                        onAction = onCreatePost,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                val rows = activePosts.chunked(3)
                items(rows) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { post ->
                            StarlightImage(
                                source = if (post.mediaType == "video") post.thumbnailUrl else post.mediaUrl,
                                contentDescription = post.caption,
                                fallbackText = post.authorUsername,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenPost(post) }
                            )
                        }
                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(count: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    currentUserId: String,
    profile: PublicProfile?,
    userPostsState: UiState<List<Post>>,
    isFollowing: Boolean,
    onBack: () -> Unit,
    onToggleFollow: (PublicProfile) -> Unit,
    onMessageUser: (PublicProfile) -> Unit,
    onOpenPost: (Post) -> Unit,
    onBlockUser: (String, String) -> Unit,
    onReportUser: (String, String) -> Unit
) {
    var showReport by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("@${profile?.username ?: "creator"}") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("user_profile_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (profile != null && profile.userId != currentUserId) {
                        IconButton(onClick = { showReport = true }) {
                            Icon(Icons.Default.Report, contentDescription = "Report user")
                        }
                        IconButton(onClick = {
                            onBlockUser(profile.userId, profile.username)
                            onBack()
                        }) {
                            Icon(Icons.Default.Block, contentDescription = "Block user")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val posts = (userPostsState as? UiState.Success)?.data.orEmpty()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (profile != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StarlightAvatar(
                                avatarUrl = profile.avatarUrl,
                                username = profile.username,
                                size = 80.dp,
                                online = profile.online
                            )
                            ProfileStat(profile.postsCount, "Posts")
                            ProfileStat(profile.followersCount, "Followers")
                            ProfileStat(profile.followingCount, "Following")
                        }
                        Text(profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (profile.bio.isNotBlank()) {
                            Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (profile.userId != currentUserId) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { onToggleFollow(profile) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isFollowing) "Following" else "Follow")
                                }
                                OutlinedButton(
                                    onClick = { onMessageUser(profile) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Message")
                                }
                            }
                        }
                    }
                }
            }

            val rows = posts.chunked(3)
            items(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { post ->
                        StarlightImage(
                            source = if (post.mediaType == "video") post.thumbnailUrl else post.mediaUrl,
                            contentDescription = post.caption,
                            fallbackText = post.authorUsername,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenPost(post) }
                        )
                    }
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }

    if (showReport && profile != null) {
        ReportDialog(
            targetLabel = "@${profile.username}",
            onDismiss = { showReport = false },
            onSubmit = { reason ->
                onReportUser(profile.userId, reason)
                showReport = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notificationsState: UiState<List<NotificationItem>>,
    onBack: () -> Unit,
    onMarkAllRead: (List<NotificationItem>) -> Unit,
    onOpenActorProfile: (String) -> Unit
) {
    val itemsList = (notificationsState as? UiState.Success)?.data.orEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("notifications_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (itemsList.any { !it.read }) {
                        TextButton(onClick = { onMarkAllRead(itemsList) }) {
                            Text("Mark all read")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (itemsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    title = "No New Activity",
                    subtitle = stringResource(R.string.empty_notifications),
                    actionLabel = "Back to Feed",
                    onAction = onBack
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(itemsList, key = { it.id }) { notif ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (notif.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenActorProfile(notif.actorId) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StarlightAvatar(
                                avatarUrl = notif.actorAvatar,
                                username = notif.actorUsername,
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "@${notif.actorUsername} ${notif.previewText}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (notif.read) FontWeight.Normal else FontWeight.SemiBold
                                )
                                Text(
                                    text = "${notif.type.uppercase()} • ${formatRelativeTime(notif.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    privateUser: PrivateUser?,
    darkMode: Boolean,
    notificationsEnabled: Boolean,
    blockedUsers: List<BlockedUserRef>,
    onBack: () -> Unit,
    onUpdateSettings: (privateAccount: Boolean, darkMode: Boolean, pushNotifications: Boolean) -> Unit,
    onUnblockUser: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    var privateAccount by remember(privateUser?.privateAccount) {
        mutableStateOf(privateUser?.privateAccount ?: false)
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onUpdateSettings(privateAccount, darkMode, granted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Account & Appearance", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Dark Cosmic Theme")
                            }
                            Switch(
                                checked = darkMode,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings(privateAccount, enabled, notificationsEnabled)
                                },
                                modifier = Modifier.testTag("settings_dark_mode_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Private Account")
                                    Text("Hide from public Explore", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = privateAccount,
                                onCheckedChange = { enabled ->
                                    privateAccount = enabled
                                    onUpdateSettings(enabled, darkMode, notificationsEnabled)
                                },
                                modifier = Modifier.testTag("settings_privacy_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Push & Activity Alerts")
                            }
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= 33) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onUpdateSettings(privateAccount, darkMode, enabled)
                                    }
                                },
                                modifier = Modifier.testTag("settings_notifications_switch")
                            )
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Security & Sign-In", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Signed in via Google (${privateUser?.email?.ifBlank { "Google Account" } ?: "Google Account"}). Manage password and 2-step verification in your Google Account security settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (blockedUsers.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Blocked Users", style = MaterialTheme.typography.titleMedium)
                            blockedUsers.forEach { blocked ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("@${blocked.targetUsername}")
                                    TextButton(onClick = { onUnblockUser(blocked.targetUserId) }) {
                                        Text("Unblock")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out")
                    }

                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_delete_account_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Account")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete STARLIGHT Account?") },
            text = { Text("This permanently deletes your STARLIGHT profile and signs you out. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
