package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Post
import com.example.data.model.PublicProfile
import com.example.data.model.Story
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HeartBurstOverlay
import com.example.ui.components.ReportDialog
import com.example.ui.components.StarlightAvatar
import com.example.ui.components.StarlightImage
import com.example.ui.components.StarlightVideoPlayer
import com.example.ui.components.formatRelativeTime
import com.example.ui.components.sharePostText
import com.example.ui.theme.StarlightBrushes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentUserId: String,
    myProfile: PublicProfile?,
    postsState: UiState<List<Post>>,
    storiesState: UiState<List<Story>>,
    profiles: List<PublicProfile>,
    likedPostIds: Set<String>,
    savedPostIds: Set<String>,
    followingIds: Set<String>,
    unreadNotifications: Int,
    onOpenNotifications: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenCreatePost: () -> Unit,
    onOpenCreateStory: () -> Unit,
    onOpenStoryViewer: (String) -> Unit,
    onOpenComments: (Post) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onToggleLike: (Post) -> Unit,
    onToggleSave: (String) -> Unit,
    onToggleFollow: (PublicProfile) -> Unit,
    onDeletePost: (Post) -> Unit,
    onBlockUser: (String, String) -> Unit,
    onReportPost: (String, String) -> Unit
) {
    val context = LocalContext.current
    var reportTargetPost by remember { mutableStateOf<Post?>(null) }

    val profileById = remember(profiles) { profiles.associateBy { it.userId } }
    val storiesList = (storiesState as? UiState.Success)?.data.orEmpty()
    val storyAuthors = remember(storiesList) {
        storiesList.groupBy { it.userId }.entries.map { (uid, items) ->
            uid to items.first()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("home_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge { Text(unreadNotifications.coerceAtMost(99).toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications"
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenChats,
                        modifier = Modifier.testTag("home_messages_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Messages"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Stories Tray
            item(key = "stories_tray") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "add_your_story") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(onClick = onOpenCreateStory)
                                .testTag("add_story_button")
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                StarlightAvatar(
                                    avatarUrl = myProfile?.avatarUrl.orEmpty(),
                                    username = myProfile?.username ?: "You",
                                    size = 62.dp,
                                    hasStoryRing = storiesList.any { it.userId == currentUserId }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(StarlightBrushes.primaryGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add story",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.your_story),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    items(storyAuthors, key = { it.first }) { (uid, sampleStory) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onOpenStoryViewer(uid) }
                                .testTag("story_ring_$uid")
                        ) {
                            StarlightAvatar(
                                avatarUrl = sampleStory.authorAvatar,
                                username = sampleStory.authorUsername,
                                size = 62.dp,
                                hasStoryRing = true
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (uid == currentUserId) "You" else "@${sampleStory.authorUsername}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            when (postsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        EmptyStateCard(
                            title = "Could not load feed",
                            subtitle = postsState.message,
                            actionLabel = "Create Post",
                            onAction = onOpenCreatePost,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    val feedPosts = postsState.data.filter { it.postType == "post" }
                    if (feedPosts.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = stringResource(R.string.empty_feed_title),
                                subtitle = stringResource(R.string.empty_feed_subtitle),
                                actionLabel = "Share First Post ✨",
                                onAction = onOpenCreatePost,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        items(feedPosts, key = { it.id }) { post ->
                            val authorProfile = profileById[post.userId]
                            FeedPostCard(
                                post = post,
                                isOwnPost = post.userId == currentUserId,
                                isLiked = post.id in likedPostIds,
                                isSaved = post.id in savedPostIds,
                                isFollowing = post.userId in followingIds,
                                authorOnline = authorProfile?.online ?: false,
                                onAuthorClick = { onOpenUserProfile(post.userId) },
                                onLike = { onToggleLike(post) },
                                onComment = { onOpenComments(post) },
                                onShare = { sharePostText(context, post.authorUsername, post.caption) },
                                onSave = { onToggleSave(post.id) },
                                onFollow = {
                                    val target = authorProfile ?: PublicProfile(
                                        userId = post.userId,
                                        username = post.authorUsername,
                                        displayName = post.authorUsername,
                                        avatarUrl = post.authorAvatar
                                    )
                                    onToggleFollow(target)
                                },
                                onDelete = { onDeletePost(post) },
                                onBlock = { onBlockUser(post.userId, post.authorUsername) },
                                onReport = { reportTargetPost = post }
                            )
                        }
                    }
                }
            }
        }
    }

    reportTargetPost?.let { target ->
        ReportDialog(
            targetLabel = "@${target.authorUsername}'s post",
            onDismiss = { reportTargetPost = null },
            onSubmit = { reason ->
                onReportPost(target.id, reason)
                reportTargetPost = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedPostCard(
    post: Post,
    isOwnPost: Boolean,
    isLiked: Boolean,
    isSaved: Boolean,
    isFollowing: Boolean,
    authorOnline: Boolean,
    onAuthorClick: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onFollow: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showHeartBurst by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Post Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onAuthorClick)
                ) {
                    StarlightAvatar(
                        avatarUrl = post.authorAvatar,
                        username = post.authorUsername,
                        size = 40.dp,
                        online = authorOnline
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "@${post.authorUsername}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${post.category} • ${formatRelativeTime(post.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isOwnPost) {
                    TextButton(
                        onClick = onFollow,
                        modifier = Modifier.testTag("follow_button_${post.userId}")
                    ) {
                        Text(
                            text = if (isFollowing) "Following" else "Follow",
                            color = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Post options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (isOwnPost) {
                            DropdownMenuItem(
                                text = { Text("Delete Post") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Report Post") },
                                onClick = {
                                    menuExpanded = false
                                    onReport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block @${post.authorUsername}") },
                                onClick = {
                                    menuExpanded = false
                                    onBlock()
                                }
                            )
                        }
                    }
                }
            }

            // Media Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(post.id, isLiked) {
                        detectTapGestures(
                            onDoubleTap = {
                                showHeartBurst = true
                                if (!isLiked) onLike()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (post.mediaType == "video") {
                    StarlightVideoPlayer(
                        videoUrl = post.mediaUrl,
                        thumbnailUrl = post.thumbnailUrl,
                        isActive = true,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    StarlightImage(
                        source = post.mediaUrl,
                        contentDescription = post.caption.ifBlank { "Post image" },
                        fallbackText = post.authorUsername,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                HeartBurstOverlay(visible = showHeartBurst)
            }

            // Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onLike,
                    modifier = Modifier.testTag("like_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = post.likesCount.toString(),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(10.dp))
                IconButton(
                    onClick = onComment,
                    modifier = Modifier.testTag("comment_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comment"
                    )
                }
                Text(
                    text = post.commentsCount.toString(),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.testTag("share_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Share"
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onSave,
                    modifier = Modifier.testTag("save_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Caption & Hashtags
            if (post.caption.isNotBlank() || post.hashtags.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (post.caption.isNotBlank()) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("@${post.authorUsername} ")
                                }
                                append(post.caption)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    if (post.hashtags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            post.hashtags.forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
