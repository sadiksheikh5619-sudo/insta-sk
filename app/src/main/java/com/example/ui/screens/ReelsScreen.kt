package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Post
import com.example.data.model.PublicProfile
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HeartBurstOverlay
import com.example.ui.components.StarlightAvatar
import com.example.ui.components.StarlightImage
import com.example.ui.components.StarlightVideoPlayer
import com.example.ui.components.sharePostText
import com.example.ui.theme.StarlightBrushes

@Composable
fun ReelsScreen(
    currentUserId: String,
    postsState: UiState<List<Post>>,
    profiles: List<PublicProfile>,
    likedPostIds: Set<String>,
    savedPostIds: Set<String>,
    followingIds: Set<String>,
    onCreateReel: () -> Unit,
    onOpenComments: (Post) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onToggleLike: (Post) -> Unit,
    onToggleSave: (String) -> Unit,
    onToggleFollow: (PublicProfile) -> Unit
) {
    val context = LocalContext.current
    val profileById = remember(profiles) { profiles.associateBy { it.userId } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (postsState) {
            is UiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is UiState.Error -> {
                EmptyStateCard(
                    title = "Reels unavailable",
                    subtitle = postsState.message,
                    actionLabel = "Post a Reel",
                    onAction = onCreateReel,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
            is UiState.Success -> {
                val reels = postsState.data.filter { it.postType == "reel" }
                if (reels.isEmpty()) {
                    EmptyStateCard(
                        title = stringResource(R.string.empty_reels_title),
                        subtitle = stringResource(R.string.empty_reels_subtitle),
                        actionLabel = "Create First Reel 🎬",
                        onAction = onCreateReel,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                } else {
                    val pagerState = rememberPagerState(pageCount = { reels.size })
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val reel = reels[page]
                        val isActive = pagerState.currentPage == page
                        val isLiked = reel.id in likedPostIds
                        val isSaved = reel.id in savedPostIds
                        val isFollowing = reel.userId in followingIds
                        val authorProfile = profileById[reel.userId]
                        var showHeart by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(reel.id, isLiked) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            showHeart = true
                                            if (!isLiked) onToggleLike(reel)
                                        }
                                    )
                                }
                        ) {
                            if (reel.mediaType == "video") {
                                StarlightVideoPlayer(
                                    videoUrl = reel.mediaUrl,
                                    thumbnailUrl = reel.thumbnailUrl,
                                    isActive = isActive,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                StarlightImage(
                                    source = reel.mediaUrl,
                                    contentDescription = reel.caption.ifBlank { "Reel" },
                                    fallbackText = reel.authorUsername,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.35f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.72f)
                                            )
                                        )
                                    )
                            )

                            Box(modifier = Modifier.align(Alignment.Center)) {
                                HeartBurstOverlay(visible = showHeart)
                            }

                            // Top overlay bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Reels",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = onCreateReel,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(StarlightBrushes.primaryGradient)
                                        .testTag("reels_create_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Create Reel", tint = Color.White)
                                }
                            }

                            // Right action rail
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 12.dp, bottom = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = { onToggleLike(reel) },
                                        modifier = Modifier.testTag("reel_like_${reel.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Like reel",
                                            tint = if (isLiked) MaterialTheme.colorScheme.tertiary else Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Text(reel.likesCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = { onOpenComments(reel) },
                                        modifier = Modifier.testTag("reel_comment_${reel.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = "Comments",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Text(reel.commentsCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(
                                    onClick = { sharePostText(context, reel.authorUsername, reel.caption) },
                                    modifier = Modifier.testTag("reel_share_${reel.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Share reel",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleSave(reel.id) },
                                    modifier = Modifier.testTag("reel_save_${reel.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Save reel",
                                        tint = if (isSaved) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Bottom creator & music info
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth(0.78f)
                                    .padding(start = 16.dp, bottom = 28.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    StarlightAvatar(
                                        avatarUrl = reel.authorAvatar,
                                        username = reel.authorUsername,
                                        size = 40.dp,
                                        modifier = Modifier.clickable { onOpenUserProfile(reel.userId) }
                                    )
                                    Text(
                                        text = "@${reel.authorUsername}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { onOpenUserProfile(reel.userId) }
                                    )
                                    if (reel.userId != currentUserId) {
                                        OutlinedButton(
                                            onClick = {
                                                val target = authorProfile ?: PublicProfile(
                                                    userId = reel.userId,
                                                    username = reel.authorUsername,
                                                    displayName = reel.authorUsername,
                                                    avatarUrl = reel.authorAvatar
                                                )
                                                onToggleFollow(target)
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(
                                                text = if (isFollowing) "Following" else "Follow",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }

                                if (reel.caption.isNotBlank()) {
                                    Text(
                                        text = reel.caption,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Surface(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = reel.musicTitle.ifBlank { "Original Audio • @${reel.authorUsername}" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
}
