package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.media.MediaHelper
import com.example.data.media.VoiceMessageController
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.PublicProfile
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StarlightAvatar
import com.example.ui.components.StarlightImage
import com.example.ui.components.StarlightVideoPlayer
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.StarlightBrushes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    currentUserId: String,
    conversationsState: UiState<List<Conversation>>,
    profiles: List<PublicProfile>,
    onBack: () -> Unit,
    onOpenConversation: (PublicProfile) -> Unit,
    onExploreCreators: () -> Unit
) {
    val profileById = remember(profiles) { profiles.associateBy { it.userId } }
    val otherCreators = remember(profiles, currentUserId) {
        profiles.filter { it.userId != currentUserId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_list_back")) {
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
            if (otherCreators.isNotEmpty()) {
                item {
                    Text("Creators Online", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(otherCreators, key = { it.userId }) { creator ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable { onOpenConversation(creator) }
                                    .testTag("start_chat_${creator.userId}")
                            ) {
                                StarlightAvatar(
                                    avatarUrl = creator.avatarUrl,
                                    username = creator.username,
                                    size = 54.dp,
                                    online = creator.online
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = creator.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            when (conversationsState) {
                is UiState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        EmptyStateCard(
                            title = "Messages unavailable",
                            subtitle = conversationsState.message,
                            actionLabel = "Explore Creators",
                            onAction = onExploreCreators
                        )
                    }
                }
                is UiState.Success -> {
                    if (conversationsState.data.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "Direct Messages",
                                subtitle = stringResource(R.string.empty_chats),
                                actionLabel = "Find Creators",
                                onAction = onExploreCreators
                            )
                        }
                    } else {
                        items(conversationsState.data, key = { it.id }) { conv ->
                            val isA = conv.participantAId == currentUserId
                            val otherId = if (isA) conv.participantBId else conv.participantAId
                            val otherName = if (isA) conv.participantBName else conv.participantAName
                            val otherAvatar = if (isA) conv.participantBAvatar else conv.participantAAvatar
                            val liveProfile = profileById[otherId] ?: PublicProfile(
                                userId = otherId,
                                username = otherName,
                                displayName = otherName,
                                avatarUrl = otherAvatar
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenConversation(liveProfile) }
                                    .testTag("conversation_${conv.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StarlightAvatar(
                                        avatarUrl = liveProfile.avatarUrl,
                                        username = liveProfile.username,
                                        size = 48.dp,
                                        online = liveProfile.online
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = liveProfile.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = conv.lastMessageText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = formatRelativeTime(conv.updatedAt),
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    currentUserId: String,
    otherProfile: PublicProfile,
    messagesState: UiState<List<Message>>,
    onBack: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onSendMessage: (messageType: String, text: String, mediaUrl: String, durationMs: Long) -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var playingMsgId by remember { mutableStateOf<String?>(null) }
    val voiceController = remember(context) { VoiceMessageController(context) }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose {
            voiceController.cancelRecording()
            voiceController.stopPlayback()
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    MediaHelper.preparePickedMedia(context, uri)
                }.onSuccess { prepared ->
                    onSendMessage(prepared.mediaType, "", prepared.mediaUrl, 0L)
                }.onFailure {
                    onNotify("Could not attach selected media")
                }
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val started = voiceController.startRecording()
            isRecording = started
            if (!started) onNotify("Could not start microphone recording")
        } else {
            onNotify("Microphone permission is needed for voice messages")
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenUserProfile(otherProfile.userId) }
                    ) {
                        StarlightAvatar(
                            avatarUrl = otherProfile.avatarUrl,
                            username = otherProfile.username,
                            size = 38.dp,
                            online = otherProfile.online
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = otherProfile.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (otherProfile.online) "Online now" else "@${otherProfile.username}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (otherProfile.online) Color(0xFF2CEAA3) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier.testTag("chat_attach_media_button")
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach photo or video")
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it.take(1000) },
                        placeholder = { Text(if (isRecording) "Recording voice..." else "Message...") },
                        enabled = !isRecording,
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    if (messageText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val textToSend = messageText.trim()
                                messageText = ""
                                onSendMessage("text", textToSend, "", 0L)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(StarlightBrushes.primaryGradient)
                                .testTag("chat_send_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (isRecording) {
                                    isRecording = false
                                    val result = voiceController.stopRecording()
                                    if (result != null) {
                                        onSendMessage("audio", "", result.first, result.second)
                                    } else {
                                        onNotify("Voice recording too short")
                                    }
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        isRecording = voiceController.startRecording()
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRecording) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary
                                )
                                .testTag("chat_voice_button")
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) "Stop recording" else "Record voice message",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == currentUserId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isMine) 18.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 18.dp
                        ),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            when (msg.messageType) {
                                "image" -> {
                                    StarlightImage(
                                        source = msg.mediaUrl,
                                        contentDescription = "Photo message",
                                        modifier = Modifier
                                            .size(210.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                "video" -> {
                                    StarlightVideoPlayer(
                                        videoUrl = msg.mediaUrl,
                                        thumbnailUrl = "",
                                        isActive = false,
                                        modifier = Modifier
                                            .size(220.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                "audio" -> {
                                    val isPlaying = playingMsgId == msg.id
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .clickable {
                                                if (isPlaying) {
                                                    voiceController.stopPlayback()
                                                    playingMsgId = null
                                                } else {
                                                    playingMsgId = msg.id
                                                    voiceController.playAudio(msg.mediaUrl) {
                                                        playingMsgId = null
                                                    }
                                                }
                                            }
                                            .padding(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = "Play voice message"
                                        )
                                        Icon(Icons.Default.GraphicEq, contentDescription = null)
                                        Text(
                                            text = "${(msg.durationMs / 1000L).coerceAtLeast(1L)}s voice",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = formatRelativeTime(msg.createdAt),
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
