package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.BlockedUserRef
import com.example.data.model.Comment
import com.example.data.model.Conversation
import com.example.data.model.FollowRef
import com.example.data.model.Message
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.PrivateUser
import com.example.data.model.PublicProfile
import com.example.data.model.Story
import com.example.data.model.UserPostRef
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StarlightRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> Flow<T>.logAndRethrowFirestoreErrors(
        uid: String,
        operationType: OperationType,
        path: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // --- USER & PROFILE ---

    fun observePrivateUser(uid: String): Flow<PrivateUser?> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(PrivateUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logAndRethrowFirestoreErrors(uid, OperationType.GET, path)
        )
    }

    fun observeMyPublicProfile(uid: String): Flow<PublicProfile?> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "public_profiles/$uid"
        emitAll(
            db.collection("public_profiles").document(uid)
                .snapshots()
                .map { it.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logAndRethrowFirestoreErrors(uid, OperationType.GET, path)
        )
    }

    fun observePublicProfiles(): Flow<List<PublicProfile>> = flow {
        val uid = requireUserId()
        val path = "public_profiles"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snap ->
                    snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.followersCount }
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun getPublicProfile(targetUid: String): Result<PublicProfile?> {
        val path = "public_profiles/$targetUid"
        return try {
            requireUserId()
            require(targetUid.isNotBlank())
            val snap = db.collection("public_profiles").document(targetUid).get().await()
            Result.success(
                snap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun ensureInitialUserDocs(): Result<Boolean> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = user.uid
        val privatePath = "users/$uid"
        return try {
            val privateDoc = db.collection("users").document(uid).get().await()
            if (privateDoc.exists()) {
                val complete = privateDoc.getBoolean("profileSetupComplete") ?: false
                return Result.success(complete)
            }
            val rawName = user.displayName?.takeIf { it.isNotBlank() } ?: "Starlight Creator"
            val sanitizedHandle = rawName.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
                .ifBlank { "star_${uid.take(6).lowercase()}" }
                .take(24)
                .let { if (it.length < 3) "${it}_star" else it }

            val privatePayload = mapOf(
                "userId" to uid,
                "email" to (user.email ?: "").take(254),
                "username" to sanitizedHandle,
                "displayName" to rawName.take(50),
                "privateAccount" to false,
                "darkMode" to true,
                "pushNotifications" to true,
                "profileSetupComplete" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val publicPayload = mapOf(
                "userId" to uid,
                "username" to sanitizedHandle,
                "displayName" to rawName.take(50),
                "bio" to "Shining on STARLIGHT ✨",
                "avatarUrl" to (user.photoUrl?.toString() ?: "").take(250_000),
                "visibility" to "public",
                "online" to true,
                "verified" to false,
                "followersCount" to 0L,
                "followingCount" to 0L,
                "postsCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.batch().apply {
                set(db.collection("users").document(uid), privatePayload)
                set(db.collection("public_profiles").document(uid), publicPayload)
            }.commit().await()
            Result.success(false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, privatePath)
            Result.failure(e)
        }
    }

    suspend fun saveProfileSetup(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String,
        privateAccount: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanHandle = username.trim().lowercase().replace(Regex("[^a-z0-9._]"), "").take(30)
        val cleanName = displayName.trim().take(50)
        if (cleanHandle.length < 3) {
            return Result.failure(IllegalArgumentException("Username must be at least 3 characters"))
        }
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Display name is required"))
        }
        val cleanBio = bio.trim().take(240)
        val cleanAvatar = avatarUrl.take(250_000)
        val visibility = if (privateAccount) "private" else "public"
        return try {
            db.batch().apply {
                update(
                    db.collection("users").document(uid),
                    mapOf(
                        "username" to cleanHandle,
                        "displayName" to cleanName,
                        "privateAccount" to privateAccount,
                        "profileSetupComplete" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                update(
                    db.collection("public_profiles").document(uid),
                    mapOf(
                        "username" to cleanHandle,
                        "displayName" to cleanName,
                        "bio" to cleanBio,
                        "avatarUrl" to cleanAvatar,
                        "visibility" to visibility,
                        "online" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateSettings(
        privateAccount: Boolean,
        darkMode: Boolean,
        pushNotifications: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val visibility = if (privateAccount) "private" else "public"
        return try {
            db.batch().apply {
                update(
                    db.collection("users").document(uid),
                    mapOf(
                        "privateAccount" to privateAccount,
                        "darkMode" to darkMode,
                        "pushNotifications" to pushNotifications,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                update(
                    db.collection("public_profiles").document(uid),
                    mapOf(
                        "visibility" to visibility,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun setOnlineStatus(online: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            db.collection("public_profiles").document(uid)
                .update(
                    mapOf(
                        "online" to online,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "public_profiles/$uid")
            Result.failure(e)
        }
    }

    // --- POSTS & REELS ---

    fun observePublicPosts(): Flow<List<Post>> = flow {
        val uid = requireUserId()
        val path = "posts"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeUserPosts(targetUserId: String): Flow<List<Post>> = flow {
        val uid = requireUserId()
        val path = "posts"
        val query = if (uid == targetUserId) {
            db.collection(path).whereEqualTo("userId", targetUserId)
        } else {
            db.collection(path)
                .whereEqualTo("userId", targetUserId)
                .whereEqualTo("visibility", "public")
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeLikedPostIds(uid: String): Flow<Set<String>> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "users/$uid/liked_posts"
        emitAll(
            db.collection("users").document(uid).collection("liked_posts")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserPostRef::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .map { it.postId }
                        .toSet()
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeSavedPostIds(uid: String): Flow<Set<String>> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "users/$uid/saved_posts"
        emitAll(
            db.collection("users").document(uid).collection("saved_posts")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserPostRef::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .map { it.postId }
                        .toSet()
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun createPost(
        authorProfile: PublicProfile,
        postType: String,
        mediaType: String,
        mediaUrl: String,
        thumbnailUrl: String,
        caption: String,
        category: String,
        hashtags: List<String>,
        musicTitle: String,
        visibility: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("posts").document()
        val cleanTags = hashtags
            .map { it.trim().take(30) }
            .filter { it.isNotBlank() }
            .take(10)
        return try {
            val payload = mapOf(
                "userId" to uid,
                "authorUsername" to authorProfile.username.ifBlank { "starlight" }.take(30),
                "authorAvatar" to authorProfile.avatarUrl.take(250_000),
                "postType" to if (postType == "reel") "reel" else "post",
                "mediaType" to if (mediaType == "video") "video" else "image",
                "mediaUrl" to mediaUrl.take(350_000),
                "thumbnailUrl" to thumbnailUrl.take(250_000),
                "caption" to caption.trim().take(500),
                "category" to category.trim().ifBlank { "General" }.take(40),
                "hashtags" to cleanTags,
                "musicTitle" to musicTitle.trim().take(80),
                "visibility" to if (visibility == "private") "private" else "public",
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.batch().apply {
                set(docRef, payload)
                update(
                    db.collection("public_profiles").document(uid),
                    mapOf(
                        "postsCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.commit().await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleLike(
        post: Post,
        currentlyLiked: Boolean,
        actorProfile: PublicProfile?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val postRef = db.collection("posts").document(post.id)
        val likeRef = db.collection("users").document(uid).collection("liked_posts").document(post.id)
        return try {
            val delta = if (currentlyLiked) {
                if (post.likesCount > 0L) -1L else 0L
            } else {
                1L
            }
            db.batch().apply {
                if (currentlyLiked) {
                    delete(likeRef)
                } else {
                    set(
                        likeRef,
                        mapOf(
                            "userId" to uid,
                            "postId" to post.id,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (delta != 0L) {
                    update(
                        postRef,
                        mapOf(
                            "likesCount" to FieldValue.increment(delta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.commit().await()

            if (!currentlyLiked && post.userId != uid && actorProfile != null) {
                sendNotification(
                    recipientId = post.userId,
                    actor = actorProfile,
                    type = "like",
                    targetId = post.id,
                    previewText = "liked your ${if (post.postType == "reel") "reel" else "post"}"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleSave(postId: String, currentlySaved: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val saveRef = db.collection("users").document(uid).collection("saved_posts").document(postId)
        return try {
            if (currentlySaved) {
                saveRef.delete().await()
            } else {
                saveRef.set(
                    mapOf(
                        "userId" to uid,
                        "postId" to postId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, saveRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePost(post: Post): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (post.userId != uid) return Result.failure(IllegalAccessException("Cannot delete another user's post"))
        val postRef = db.collection("posts").document(post.id)
        return try {
            postRef.delete().await()
            db.collection("public_profiles").document(uid)
                .update(
                    mapOf(
                        "postsCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, postRef.path)
            Result.failure(e)
        }
    }

    // --- COMMENTS ---

    fun observeComments(postId: String, postVisibility: String, postOwnerId: String): Flow<List<Comment>> = flow {
        val uid = requireUserId()
        val path = "posts/$postId/comments"
        val query = if (uid == postOwnerId && postVisibility == "private") {
            db.collection("posts").document(postId).collection("comments")
                .whereEqualTo("postOwnerId", postOwnerId)
        } else {
            db.collection("posts").document(postId).collection("comments")
                .whereEqualTo("postVisibility", "public")
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Comment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun addComment(post: Post, text: String, actorProfile: PublicProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanText = text.trim().take(400)
        if (cleanText.isBlank()) return Result.failure(IllegalArgumentException("Comment cannot be empty"))
        val commentRef = db.collection("posts").document(post.id).collection("comments").document()
        return try {
            db.batch().apply {
                set(
                    commentRef,
                    mapOf(
                        "postId" to post.id,
                        "postOwnerId" to post.userId,
                        "postVisibility" to post.visibility,
                        "userId" to uid,
                        "authorUsername" to actorProfile.username.ifBlank { "starlight" }.take(30),
                        "authorAvatar" to actorProfile.avatarUrl.take(250_000),
                        "text" to cleanText,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                update(
                    db.collection("posts").document(post.id),
                    mapOf(
                        "commentsCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.commit().await()

            if (post.userId != uid) {
                sendNotification(
                    recipientId = post.userId,
                    actor = actorProfile,
                    type = "comment",
                    targetId = post.id,
                    previewText = "commented: ${cleanText.take(60)}"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            Result.failure(e)
        }
    }

    // --- STORIES & HIGHLIGHTS ---

    fun observePublicStories(): Flow<List<Story>> = flow {
        val uid = requireUserId()
        val path = "stories"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Story::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun createStory(
        authorProfile: PublicProfile,
        mediaType: String,
        mediaUrl: String,
        caption: String,
        highlight: Boolean,
        highlightTitle: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val storyRef = db.collection("stories").document()
        val expiresAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
        return try {
            storyRef.set(
                mapOf(
                    "userId" to uid,
                    "authorUsername" to authorProfile.username.ifBlank { "starlight" }.take(30),
                    "authorAvatar" to authorProfile.avatarUrl.take(250_000),
                    "mediaType" to if (mediaType == "video") "video" else "image",
                    "mediaUrl" to mediaUrl.take(350_000),
                    "caption" to caption.trim().take(200),
                    "highlight" to highlight,
                    "highlightTitle" to highlightTitle.trim().take(30),
                    "visibility" to authorProfile.visibility,
                    "viewsCount" to 0L,
                    "expiresAtEpochMs" to expiresAt,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(storyRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, storyRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleStoryHighlight(story: Story, highlight: Boolean, title: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (story.userId != uid) return Result.failure(IllegalAccessException("Not owner"))
        val ref = db.collection("stories").document(story.id)
        return try {
            ref.update(
                mapOf(
                    "highlight" to highlight,
                    "highlightTitle" to title.trim().ifBlank { "Highlights" }.take(30),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun markStoryViewed(story: Story, viewerProfile: PublicProfile?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (story.userId == uid) return Result.success(Unit)
        val ref = db.collection("stories").document(story.id)
        return try {
            ref.update(
                mapOf(
                    "viewsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (viewerProfile != null) {
                sendNotification(
                    recipientId = story.userId,
                    actor = viewerProfile,
                    type = "story",
                    targetId = story.id,
                    previewText = "viewed your Starlight story"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        val ref = db.collection("stories").document(storyId)
        return try {
            requireUserId()
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    // --- FOLLOWING, BLOCKING, REPORTING ---

    fun observeFollowingIds(uid: String): Flow<Set<String>> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "users/$uid/following"
        emitAll(
            db.collection("users").document(uid).collection("following")
                .snapshots()
                .map { snap ->
                    snap.toObjects(FollowRef::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .map { it.targetUserId }
                        .toSet()
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeBlockedUsers(uid: String): Flow<List<BlockedUserRef>> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "users/$uid/blocked_users"
        emitAll(
            db.collection("users").document(uid).collection("blocked_users")
                .snapshots()
                .map { snap ->
                    snap.toObjects(BlockedUserRef::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun toggleFollow(
        targetProfile: PublicProfile,
        currentlyFollowing: Boolean,
        actorProfile: PublicProfile?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetProfile.userId == uid) return Result.success(Unit)
        val followRef = db.collection("users").document(uid).collection("following").document(targetProfile.userId)
        val targetRef = db.collection("public_profiles").document(targetProfile.userId)
        val myRef = db.collection("public_profiles").document(uid)
        return try {
            val followerDelta = if (currentlyFollowing) {
                if (targetProfile.followersCount > 0L) -1L else 0L
            } else {
                1L
            }
            val followingDelta = if (currentlyFollowing) {
                if ((actorProfile?.followingCount ?: 0L) > 0L) -1L else 0L
            } else {
                1L
            }
            db.batch().apply {
                if (currentlyFollowing) {
                    delete(followRef)
                } else {
                    set(
                        followRef,
                        mapOf(
                            "userId" to uid,
                            "targetUserId" to targetProfile.userId,
                            "targetUsername" to targetProfile.username.ifBlank { "creator" }.take(30),
                            "targetAvatar" to targetProfile.avatarUrl.take(250_000),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (followerDelta != 0L) {
                    update(
                        targetRef,
                        mapOf(
                            "followersCount" to FieldValue.increment(followerDelta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (followingDelta != 0L) {
                    update(
                        myRef,
                        mapOf(
                            "followingCount" to FieldValue.increment(followingDelta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.commit().await()

            if (!currentlyFollowing && actorProfile != null) {
                sendNotification(
                    recipientId = targetProfile.userId,
                    actor = actorProfile,
                    type = "follow",
                    targetId = uid,
                    previewText = "started following you"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, followRef.path)
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUserId: String, targetUsername: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val ref = db.collection("users").document(uid).collection("blocked_users").document(targetUserId)
        return try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "targetUserId" to targetUserId,
                    "targetUsername" to targetUsername.ifBlank { "user" }.take(30),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val ref = db.collection("users").document(uid).collection("blocked_users").document(targetUserId)
        return try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(targetType: String, targetId: String, reason: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanReason = reason.trim().take(300)
        if (cleanReason.length < 3) return Result.failure(IllegalArgumentException("Please describe the reason"))
        val ref = db.collection("reports").document()
        return try {
            ref.set(
                mapOf(
                    "reporterId" to uid,
                    "targetType" to if (targetType == "user") "user" else "post",
                    "targetId" to targetId,
                    "reason" to cleanReason,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    // --- CHAT & MESSAGING ---

    fun conversationIdFor(uidA: String, uidB: String): String {
        val sorted = listOf(uidA, uidB).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "conversations"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeMessages(conversationId: String, uid: String): Flow<List<Message>> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations").document(conversationId).collection("messages")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun sendMessage(
        myProfile: PublicProfile,
        otherProfile: PublicProfile,
        messageType: String,
        text: String,
        mediaUrl: String = "",
        durationMs: Long = 0L
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val sorted = listOf(myProfile, otherProfile).sortedBy { it.userId }
        val convId = conversationIdFor(myProfile.userId, otherProfile.userId)
        val convRef = db.collection("conversations").document(convId)
        val msgRef = convRef.collection("messages").document()
        val cleanType = when (messageType) {
            "image", "video", "audio" -> messageType
            else -> "text"
        }
        val preview = when (cleanType) {
            "image" -> "Sent a photo"
            "video" -> "Sent a video"
            "audio" -> "Voice message"
            else -> text.trim().take(400)
        }
        return try {
            val existingConv = convRef.get().await()
            val participantUids = listOf(sorted[0].userId, sorted[1].userId)
            val convData = mapOf(
                "participantUids" to participantUids,
                "participantAId" to sorted[0].userId,
                "participantBId" to sorted[1].userId,
                "participantAName" to sorted[0].displayName.ifBlank { sorted[0].username }.take(50),
                "participantBName" to sorted[1].displayName.ifBlank { sorted[1].username }.take(50),
                "participantAAvatar" to sorted[0].avatarUrl.take(250_000),
                "participantBAvatar" to sorted[1].avatarUrl.take(250_000),
                "lastMessageText" to preview,
                "lastMessageType" to cleanType,
                "lastSenderId" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val msgData = mapOf(
                "conversationId" to convId,
                "participantUids" to participantUids,
                "senderId" to uid,
                "senderName" to myProfile.displayName.ifBlank { myProfile.username }.take(50),
                "messageType" to cleanType,
                "text" to text.trim().take(1000),
                "mediaUrl" to mediaUrl.take(350_000),
                "durationMs" to durationMs.coerceAtLeast(0L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.batch().apply {
                if (!existingConv.exists()) {
                    set(convRef, convData + ("createdAt" to FieldValue.serverTimestamp()))
                } else {
                    update(
                        convRef,
                        mapOf(
                            "participantAName" to convData["participantAName"],
                            "participantBName" to convData["participantBName"],
                            "participantAAvatar" to convData["participantAAvatar"],
                            "participantBAvatar" to convData["participantBAvatar"],
                            "lastMessageText" to preview,
                            "lastMessageType" to cleanType,
                            "lastSenderId" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                set(msgRef, msgData)
            }.commit().await()

            sendNotification(
                recipientId = otherProfile.userId,
                actor = myProfile,
                type = "message",
                targetId = convId,
                previewText = preview.take(120)
            )
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    // --- NOTIFICATIONS ---

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "users/$uid/notifications"
        emitAll(
            db.collection("users").document(uid).collection("notifications")
                .snapshots()
                .map { snap ->
                    snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrowFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    private suspend fun sendNotification(
        recipientId: String,
        actor: PublicProfile,
        type: String,
        targetId: String,
        previewText: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        if (recipientId == uid || recipientId.isBlank()) return
        val ref = db.collection("users").document(recipientId).collection("notifications").document()
        try {
            ref.set(
                mapOf(
                    "recipientId" to recipientId,
                    "actorId" to uid,
                    "actorUsername" to actor.username.ifBlank { "starlight" }.take(30),
                    "actorAvatar" to actor.avatarUrl.take(250_000),
                    "type" to type,
                    "targetId" to targetId.take(128),
                    "previewText" to previewText.take(200),
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
        }
    }

    suspend fun markAllNotificationsRead(notifications: List<NotificationItem>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val unread = notifications.filter { !it.read && it.id.isNotBlank() }
        if (unread.isEmpty()) return Result.success(Unit)
        return try {
            val batch = db.batch()
            unread.take(400).forEach { notif ->
                batch.update(
                    db.collection("users").document(uid).collection("notifications").document(notif.id),
                    mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/notifications")
            Result.failure(e)
        }
    }

    // --- ACCOUNT DELETION ---

    suspend fun deleteAccount(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = user.uid
        return try {
            db.batch().apply {
                delete(db.collection("public_profiles").document(uid))
                delete(db.collection("users").document(uid))
            }.commit().await()
            user.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }
}
