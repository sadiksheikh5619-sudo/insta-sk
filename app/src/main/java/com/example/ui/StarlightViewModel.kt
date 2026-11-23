package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BlockedUserRef
import com.example.data.model.Comment
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.PrivateUser
import com.example.data.model.PublicProfile
import com.example.data.model.Story
import com.example.data.model.UiState
import com.example.data.prefs.ThemePreferences
import com.example.data.repository.StarlightRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StarlightViewModel(
    private val repository: StarlightRepository,
    private val themePreferences: ThemePreferences,
    val currentUserId: String
) : ViewModel() {

    private val _statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val statusMessage = _statusMessage.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _selectedProfileCache = MutableStateFlow<PublicProfile?>(null)
    val selectedProfileCache: StateFlow<PublicProfile?> = _selectedProfileCache.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureInitialUserDocs()
            repository.setOnlineStatus(true)
        }
    }

    private fun <T> Flow<T>.toUiState(tagLabel: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $tagLabel", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $tagLabel"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val privateUserState: StateFlow<UiState<PrivateUser?>> =
        repository.observePrivateUser(currentUserId).toUiState("account")

    val myPublicProfileState: StateFlow<UiState<PublicProfile?>> =
        repository.observeMyPublicProfile(currentUserId).toUiState("profile")

    val myPostsState: StateFlow<UiState<List<Post>>> =
        repository.observeUserPosts(currentUserId).toUiState("my posts")

    val likedPostIds: StateFlow<Set<String>> =
        repository.observeLikedPostIds(currentUserId)
            .catch { emit(emptySet()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    val savedPostIds: StateFlow<Set<String>> =
        repository.observeSavedPostIds(currentUserId)
            .catch { emit(emptySet()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    val followingIds: StateFlow<Set<String>> =
        repository.observeFollowingIds(currentUserId)
            .catch { emit(emptySet()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    val blockedUsers: StateFlow<List<BlockedUserRef>> =
        repository.observeBlockedUsers(currentUserId)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val blockedUserIds: StateFlow<Set<String>> =
        blockedUsers.map { list -> list.map { it.targetUserId }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    val publicProfilesState: StateFlow<UiState<List<PublicProfile>>> =
        combine(repository.observePublicProfiles(), blockedUserIds) { profiles, blocked ->
            profiles.filter { it.userId !in blocked }
        }.toUiState("creators")

    val publicPostsState: StateFlow<UiState<List<Post>>> =
        combine(repository.observePublicPosts(), blockedUserIds) { posts, blocked ->
            posts.filter { it.userId !in blocked }
        }.toUiState("posts")

    val publicStoriesState: StateFlow<UiState<List<Story>>> =
        combine(repository.observePublicStories(), blockedUserIds) { stories, blocked ->
            val nowMs = System.currentTimeMillis()
            stories.filter { it.userId !in blocked && (it.expiresAtEpochMs > nowMs || it.highlight) }
        }.toUiState("stories")

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        combine(repository.observeConversations(currentUserId), blockedUserIds) { convs, blocked ->
            convs.filter { conv ->
                val other = if (conv.participantAId == currentUserId) conv.participantBId else conv.participantAId
                other !in blocked
            }
        }.toUiState("conversations")

    val notificationsState: StateFlow<UiState<List<NotificationItem>>> =
        repository.observeNotifications(currentUserId).toUiState("notifications")

    val unreadNotificationsCount: StateFlow<Int> =
        notificationsState.map { state ->
            when (state) {
                is UiState.Success -> state.data.count { !it.read }
                else -> 0
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    private fun currentPublicProfileOrFallback(): PublicProfile {
        return (myPublicProfileState.value as? UiState.Success)?.data
            ?: PublicProfile(
                userId = currentUserId,
                username = "starlight_user",
                displayName = "Starlight Creator"
            )
    }

    fun saveProfileSetup(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String,
        privateAccount: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveProfileSetup(username, displayName, bio, avatarUrl, privateAccount)
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.tryEmit("Profile saved ✨")
                onSuccess()
            }.onFailure {
                _statusMessage.tryEmit(it.localizedMessage ?: "Failed to save profile")
            }
        }
    }

    fun createPost(
        postType: String,
        mediaType: String,
        mediaUrl: String,
        thumbnailUrl: String,
        caption: String,
        category: String,
        hashtagsRaw: String,
        musicTitle: String,
        visibility: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val tags = hashtagsRaw.split(" ", ",", "\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { if (it.startsWith("#")) it else "#$it" }
            val result = repository.createPost(
                authorProfile = currentPublicProfileOrFallback(),
                postType = postType,
                mediaType = mediaType,
                mediaUrl = mediaUrl,
                thumbnailUrl = thumbnailUrl,
                caption = caption,
                category = category,
                hashtags = tags,
                musicTitle = musicTitle,
                visibility = visibility
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.tryEmit(if (postType == "reel") "Reel published ✨" else "Post shared ✨")
                onSuccess()
            }.onFailure {
                _statusMessage.tryEmit(it.localizedMessage ?: "Failed to publish")
            }
        }
    }

    fun createStory(
        mediaType: String,
        mediaUrl: String,
        caption: String,
        highlight: Boolean,
        highlightTitle: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createStory(
                authorProfile = currentPublicProfileOrFallback(),
                mediaType = mediaType,
                mediaUrl = mediaUrl,
                caption = caption,
                highlight = highlight,
                highlightTitle = highlightTitle
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.tryEmit("Story added for 24h ✨")
                onSuccess()
            }.onFailure {
                _statusMessage.tryEmit(it.localizedMessage ?: "Failed to post story")
            }
        }
    }

    fun toggleLike(post: Post) {
        viewModelScope.launch {
            val liked = post.id in likedPostIds.value
            repository.toggleLike(post, liked, currentPublicProfileOrFallback())
                .onFailure { _statusMessage.tryEmit("Could not update like") }
        }
    }

    fun toggleSave(postId: String) {
        viewModelScope.launch {
            val saved = postId in savedPostIds.value
            repository.toggleSave(postId, saved)
                .onSuccess {
                    _statusMessage.tryEmit(if (saved) "Removed from Saved" else "Saved to your collection")
                }
                .onFailure { _statusMessage.tryEmit("Could not save post") }
        }
    }

    fun deletePost(post: Post) {
        viewModelScope.launch {
            repository.deletePost(post)
                .onSuccess { _statusMessage.tryEmit("Post deleted") }
                .onFailure { _statusMessage.tryEmit("Failed to delete post") }
        }
    }

    fun observeComments(post: Post): Flow<UiState<List<Comment>>> =
        repository.observeComments(post.id, post.visibility, post.userId)
            .map<List<Comment>, UiState<List<Comment>>> { UiState.Success(it) }
            .catch { error ->
                val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fs == null && error is CancellationException) throw error
                emit(UiState.Error((fs ?: error).message ?: "Failed to load comments"))
            }

    fun addComment(post: Post, text: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addComment(post, text, currentPublicProfileOrFallback())
                .onSuccess { onDone() }
                .onFailure { _statusMessage.tryEmit(it.localizedMessage ?: "Failed to comment") }
        }
    }

    fun toggleFollow(targetProfile: PublicProfile) {
        viewModelScope.launch {
            val following = targetProfile.userId in followingIds.value
            repository.toggleFollow(targetProfile, following, currentPublicProfileOrFallback())
                .onFailure { _statusMessage.tryEmit("Failed to update follow") }
        }
    }

    fun toggleStoryHighlight(story: Story, highlight: Boolean, title: String) {
        viewModelScope.launch {
            repository.toggleStoryHighlight(story, highlight, title)
                .onSuccess {
                    _statusMessage.tryEmit(if (highlight) "Added to Highlights" else "Removed from Highlights")
                }
                .onFailure { _statusMessage.tryEmit("Could not update highlight") }
        }
    }

    fun markStoryViewed(story: Story) {
        viewModelScope.launch {
            repository.markStoryViewed(story, currentPublicProfileOrFallback())
        }
    }

    fun deleteStory(storyId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteStory(storyId)
                .onSuccess {
                    _statusMessage.tryEmit("Story deleted")
                    onDeleted()
                }
                .onFailure { _statusMessage.tryEmit("Failed to delete story") }
        }
    }

    fun observeUserPosts(userId: String): Flow<UiState<List<Post>>> =
        repository.observeUserPosts(userId)
            .map<List<Post>, UiState<List<Post>>> { UiState.Success(it) }
            .catch { error ->
                val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fs == null && error is CancellationException) throw error
                emit(UiState.Error((fs ?: error).message ?: "Failed to load posts"))
            }

    fun loadTargetProfile(userId: String) {
        viewModelScope.launch {
            _selectedProfileCache.value = null
            repository.getPublicProfile(userId)
                .onSuccess { _selectedProfileCache.value = it }
                .onFailure { _statusMessage.tryEmit("Profile unavailable") }
        }
    }

    fun observeMessages(conversationId: String): Flow<UiState<List<Message>>> =
        repository.observeMessages(conversationId, currentUserId)
            .map<List<Message>, UiState<List<Message>>> { UiState.Success(it) }
            .catch { error ->
                val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fs == null && error is CancellationException) throw error
                emit(UiState.Error((fs ?: error).message ?: "Failed to load messages"))
            }

    fun conversationIdFor(otherUserId: String): String =
        repository.conversationIdFor(currentUserId, otherUserId)

    fun sendMessage(
        otherProfile: PublicProfile,
        messageType: String,
        text: String,
        mediaUrl: String = "",
        durationMs: Long = 0L,
        onSent: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.sendMessage(
                myProfile = currentPublicProfileOrFallback(),
                otherProfile = otherProfile,
                messageType = messageType,
                text = text,
                mediaUrl = mediaUrl,
                durationMs = durationMs
            ).onSuccess {
                onSent()
            }.onFailure {
                _statusMessage.tryEmit(it.localizedMessage ?: "Failed to send message")
            }
        }
    }

    fun markAllNotificationsRead(items: List<NotificationItem>) {
        viewModelScope.launch {
            repository.markAllNotificationsRead(items)
        }
    }

    fun blockUser(targetUserId: String, targetUsername: String) {
        viewModelScope.launch {
            repository.blockUser(targetUserId, targetUsername)
                .onSuccess { _statusMessage.tryEmit("Blocked @$targetUsername") }
                .onFailure { _statusMessage.tryEmit("Failed to block user") }
        }
    }

    fun unblockUser(targetUserId: String) {
        viewModelScope.launch {
            repository.unblockUser(targetUserId)
                .onSuccess { _statusMessage.tryEmit("User unblocked") }
        }
    }

    fun submitReport(targetType: String, targetId: String, reason: String) {
        viewModelScope.launch {
            repository.submitReport(targetType, targetId, reason)
                .onSuccess { _statusMessage.tryEmit("Report submitted. Thank you.") }
                .onFailure { _statusMessage.tryEmit(it.localizedMessage ?: "Failed to submit report") }
        }
    }

    fun updateSettings(privateAccount: Boolean, darkMode: Boolean, pushNotifications: Boolean) {
        viewModelScope.launch {
            themePreferences.setDarkMode(darkMode)
            themePreferences.setNotificationsEnabled(pushNotifications)
            repository.updateSettings(privateAccount, darkMode, pushNotifications)
                .onFailure { _statusMessage.tryEmit("Failed to sync settings") }
        }
    }

    suspend fun prepareForSignOut() {
        repository.setOnlineStatus(false)
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteAccount()
            _isBusy.value = false
            res.onSuccess {
                _statusMessage.tryEmit("Account deleted")
                onDeleted()
            }.onFailure {
                _statusMessage.tryEmit(
                    it.localizedMessage ?: "Please sign out and sign back in before deleting your account."
                )
            }
        }
    }

    fun emitMessage(msg: String) {
        _statusMessage.tryEmit(msg)
    }

    private companion object {
        const val TAG = "StarlightVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
