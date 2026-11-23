package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Comment
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UiState
import com.example.ui.components.StarlightAvatar
import com.example.ui.components.StarlightImage
import com.example.ui.components.StarlightVideoPlayer
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.StarlightBrushes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    post: Post,
    commentsState: UiState<List<Comment>>,
    onBack: () -> Unit,
    onAddComment: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit
) {
    var commentText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comments (${post.commentsCount})") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("comments_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it.take(400) },
                        placeholder = { Text("Add a comment...") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comment_text_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                val text = commentText.trim()
                                commentText = ""
                                onAddComment(text)
                            }
                        },
                        enabled = commentText.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(StarlightBrushes.primaryGradient)
                            .testTag("comment_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post comment", tint = Color.White)
                    }
                }
            }
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
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StarlightAvatar(
                        avatarUrl = post.authorAvatar,
                        username = post.authorUsername,
                        size = 38.dp
                    )
                    Column {
                        Text("@${post.authorUsername}", fontWeight = FontWeight.Bold)
                        if (post.caption.isNotBlank()) {
                            Text(post.caption, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            when (commentsState) {
                is UiState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(commentsState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is UiState.Success -> {
                    if (commentsState.data.isEmpty()) {
                        item {
                            Text(
                                text = "No comments yet. Start the conversation ✨",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    } else {
                        items(commentsState.data, key = { it.id }) { comment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                StarlightAvatar(
                                    avatarUrl = comment.authorAvatar,
                                    username = comment.authorUsername,
                                    size = 34.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "@${comment.authorUsername}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = formatRelativeTime(comment.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = comment.text,
                                        style = MaterialTheme.typography.bodyLarge
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

@Composable
fun StoryViewerScreen(
    currentUserId: String,
    stories: List<Story>,
    onClose: () -> Unit,
    onMarkViewed: (Story) -> Unit,
    onToggleHighlight: (Story, Boolean) -> Unit,
    onDeleteStory: (String) -> Unit
) {
    if (stories.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var index by remember { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, stories.lastIndex)
    val currentStory = stories[safeIndex]

    LaunchedEffect(currentStory.id) {
        onMarkViewed(currentStory)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(safeIndex, stories.size) {
                detectTapGestures { offset ->
                    if (offset.x < size.width / 3f) {
                        if (safeIndex > 0) index = safeIndex - 1
                    } else {
                        if (safeIndex < stories.lastIndex) index = safeIndex + 1 else onClose()
                    }
                }
            }
    ) {
        if (currentStory.mediaType == "video") {
            StarlightVideoPlayer(
                videoUrl = currentStory.mediaUrl,
                thumbnailUrl = "",
                isActive = true,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            StarlightImage(
                source = currentStory.mediaUrl,
                contentDescription = currentStory.caption.ifBlank { "Story" },
                fallbackText = currentStory.authorUsername,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stories.forEachIndexed { idx, _ ->
                    LinearProgressIndicator(
                        progress = { if (idx <= safeIndex) 1f else 0f },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StarlightAvatar(
                    avatarUrl = currentStory.authorAvatar,
                    username = currentStory.authorUsername,
                    size = 38.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "@${currentStory.authorUsername}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatRelativeTime(currentStory.createdAt),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (currentStory.userId == currentUserId) {
                    IconButton(onClick = { onToggleHighlight(currentStory, !currentStory.highlight) }) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Toggle highlight",
                            tint = if (currentStory.highlight) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(onClick = { onDeleteStory(currentStory.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete story", tint = Color.White)
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("story_close_button")) {
                    Icon(Icons.Default.Close, contentDescription = "Close story", tint = Color.White)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (currentStory.caption.isNotBlank()) {
                Text(
                    text = currentStory.caption,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("${currentStory.viewsCount} views", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
