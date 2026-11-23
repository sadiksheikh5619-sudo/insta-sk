package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class PrivateUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val username: String = "",
    val displayName: String = "",
    val privateAccount: Boolean = false,
    val darkMode: Boolean = true,
    val pushNotifications: Boolean = true,
    val profileSetupComplete: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val visibility: String = "public",
    val online: Boolean = true,
    val verified: Boolean = false,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val postsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Post(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val authorAvatar: String = "",
    val postType: String = "post",
    val mediaType: String = "image",
    val mediaUrl: String = "",
    val thumbnailUrl: String = "",
    val caption: String = "",
    val category: String = "General",
    val hashtags: List<String> = emptyList(),
    val musicTitle: String = "",
    val visibility: String = "public",
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Comment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val postOwnerId: String = "",
    val postVisibility: String = "public",
    val userId: String = "",
    val authorUsername: String = "",
    val authorAvatar: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Story(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val authorAvatar: String = "",
    val mediaType: String = "image",
    val mediaUrl: String = "",
    val caption: String = "",
    val highlight: Boolean = false,
    val highlightTitle: String = "",
    val visibility: String = "public",
    val viewsCount: Long = 0L,
    val expiresAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPostRef(
    @DocumentId val id: String = "",
    val userId: String = "",
    val postId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FollowRef(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetUserId: String = "",
    val targetUsername: String = "",
    val targetAvatar: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockedUserRef(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetUserId: String = "",
    val targetUsername: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val actorId: String = "",
    val actorUsername: String = "",
    val actorAvatar: String = "",
    val type: String = "like",
    val targetId: String = "",
    val previewText: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val participantAId: String = "",
    val participantBId: String = "",
    val participantAName: String = "",
    val participantBName: String = "",
    val participantAAvatar: String = "",
    val participantBAvatar: String = "",
    val lastMessageText: String = "",
    val lastMessageType: String = "text",
    val lastSenderId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val participantUids: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val messageType: String = "text",
    val text: String = "",
    val mediaUrl: String = "",
    val durationMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Report(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "post",
    val targetId: String = "",
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
