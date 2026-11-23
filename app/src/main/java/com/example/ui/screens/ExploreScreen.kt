package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Post
import com.example.data.model.PublicProfile
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StarlightAvatar
import com.example.ui.components.StarlightImage

@Composable
fun ExploreScreen(
    currentUserId: String,
    profilesState: UiState<List<PublicProfile>>,
    postsState: UiState<List<Post>>,
    followingIds: Set<String>,
    onOpenUserProfile: (String) -> Unit,
    onOpenComments: (Post) -> Unit,
    onToggleFollow: (PublicProfile) -> Unit,
    onOpenCreatePost: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedHashtag by remember { mutableStateOf<String?>(null) }
    val categories = listOf("All", "Photography", "Music", "Art", "Tech", "Travel", "Fashion", "Fitness")

    val allProfiles = (profilesState as? UiState.Success)?.data.orEmpty()
    val allPosts = (postsState as? UiState.Success)?.data.orEmpty()

    val trendingHashtags = remember(allPosts) {
        allPosts.flatMap { it.hashtags }
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }
    }

    val filteredCreators = remember(allProfiles, searchQuery, currentUserId) {
        val q = searchQuery.trim().lowercase()
        allProfiles.filter { profile ->
            profile.userId != currentUserId &&
                (q.isBlank() ||
                    profile.username.lowercase().contains(q) ||
                    profile.displayName.lowercase().contains(q))
        }
    }

    val filteredPosts = remember(allPosts, searchQuery, selectedCategory, selectedHashtag) {
        val q = searchQuery.trim().lowercase()
        allPosts.filter { post ->
            val matchesCategory = selectedCategory == "All" || post.category.equals(selectedCategory, ignoreCase = true)
            val matchesTag = selectedHashtag == null || post.hashtags.any { it.equals(selectedHashtag, ignoreCase = true) }
            val matchesQuery = q.isBlank() ||
                post.caption.lowercase().contains(q) ||
                post.authorUsername.lowercase().contains(q) ||
                post.hashtags.any { it.lowercase().contains(q) }
            matchesCategory && matchesTag && matchesQuery
        }.sortedByDescending { it.likesCount + it.commentsCount * 2 }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Explore",
                    style = MaterialTheme.typography.headlineMedium
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search creators, captions, or #hashtags") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explore_search_input")
                )
            }
        }

        // Categories
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        modifier = Modifier.testTag("explore_category_${category.lowercase()}")
                    )
                }
            }
        }

        // Trending Hashtags
        if (trendingHashtags.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Trending Hashtags", style = MaterialTheme.typography.titleMedium)
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trendingHashtags) { tag ->
                            FilterChip(
                                selected = selectedHashtag == tag,
                                onClick = { selectedHashtag = if (selectedHashtag == tag) null else tag },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
        }

        // Discover Creators
        if (filteredCreators.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Discover Creators",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredCreators, key = { it.userId }) { creator ->
                            val isFollowing = creator.userId in followingIds
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .width(158.dp)
                                    .clickable { onOpenUserProfile(creator.userId) }
                                    .testTag("discover_creator_${creator.userId}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StarlightAvatar(
                                        avatarUrl = creator.avatarUrl,
                                        username = creator.username,
                                        size = 58.dp,
                                        online = creator.online
                                    )
                                    Text(
                                        text = creator.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "@${creator.username}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isFollowing) {
                                        OutlinedButton(
                                            onClick = { onToggleFollow(creator) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Following", maxLines = 1)
                                        }
                                    } else {
                                        Button(
                                            onClick = { onToggleFollow(creator) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Follow", maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Trending Posts Grid
        item {
            Text(
                text = "Trending Posts",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (filteredPosts.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No matching posts yet",
                    subtitle = "Try another hashtag or category, or publish a post to kickstart this topic.",
                    actionLabel = "Create Post",
                    onAction = onOpenCreatePost,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            val rows = filteredPosts.chunked(3)
            items(rows) { rowPosts ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowPosts.forEach { post ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenComments(post) }
                                .testTag("explore_post_${post.id}")
                        ) {
                            StarlightImage(
                                source = if (post.mediaType == "video") post.thumbnailUrl else post.mediaUrl,
                                contentDescription = post.caption.ifBlank { "Explore post" },
                                fallbackText = post.authorUsername,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (post.mediaType == "video" || post.postType == "reel") {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Video",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(2.dp)
                                    )
                                }
                            }
                        }
                    }
                    repeat(3 - rowPosts.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
