package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.media.MediaHelper
import com.example.data.media.PreparedMedia
import com.example.ui.components.GradientButton
import com.example.ui.components.StarlightImage
import com.example.ui.components.StarlightVideoPlayer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    initialMode: String = "post",
    isBusy: Boolean,
    onBack: () -> Unit,
    onPublishPost: (
        postType: String,
        mediaType: String,
        mediaUrl: String,
        thumbnailUrl: String,
        caption: String,
        category: String,
        hashtagsRaw: String,
        musicTitle: String,
        visibility: String
    ) -> Unit,
    onPublishStory: (
        mediaType: String,
        mediaUrl: String,
        caption: String,
        highlight: Boolean,
        highlightTitle: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modes = listOf("post", "reel", "story")
    var selectedMode by remember(initialMode) {
        mutableStateOf(if (initialMode in modes) initialMode else "post")
    }
    var preparedMedia by remember { mutableStateOf<PreparedMedia?>(null) }
    var isProcessingMedia by remember { mutableStateOf(false) }
    var mediaError by remember { mutableStateOf<String?>(null) }

    var caption by remember { mutableStateOf("") }
    var hashtagsRaw by remember { mutableStateOf("#starlight") }
    var category by remember { mutableStateOf("Photography") }
    var musicTitle by remember { mutableStateOf("Nebula Echoes — Starlight Sounds") }
    var isPrivate by remember { mutableStateOf(false) }
    var addToHighlight by remember { mutableStateOf(false) }
    var highlightTitle by remember { mutableStateOf("Highlights") }

    val categories = listOf("Photography", "Music", "Art", "Tech", "Travel", "Fashion", "Fitness")

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessingMedia = true
                mediaError = null
                runCatching {
                    MediaHelper.preparePickedMedia(context, uri)
                }.onSuccess { prepared ->
                    preparedMedia = prepared
                }.onFailure { err ->
                    mediaError = err.localizedMessage ?: "Failed to load selected media"
                }
                isProcessingMedia = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedMode) {
                            "reel" -> "Create Reel"
                            "story" -> "Add 24h Story"
                            else -> "New Post"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("create_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .widthIn(max = 580.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        modifier = Modifier.testTag("create_mode_$mode")
                    ) {
                        Text(
                            when (mode) {
                                "reel" -> "Reel"
                                "story" -> "24h Story"
                                else -> "Post"
                            }
                        )
                    }
                }
            }

            // Media Picker Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (selectedMode == "post") 1.15f else 0.85f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable {
                        val requestType = if (selectedMode == "reel") {
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        } else {
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        }
                        mediaPicker.launch(PickVisualMediaRequest(requestType))
                    }
                    .testTag("create_media_picker"),
                contentAlignment = Alignment.Center
            ) {
                val media = preparedMedia
                when {
                    isProcessingMedia -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Preparing media...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    media != null -> {
                        if (media.mediaType == "video") {
                            StarlightVideoPlayer(
                                videoUrl = media.mediaUrl,
                                thumbnailUrl = media.thumbnailUrl,
                                isActive = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            StarlightImage(
                                source = media.mediaUrl,
                                contentDescription = "Selected media preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedMode == "reel") Icons.Default.VideoLibrary else Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (selectedMode == "reel") "Tap to pick a Video or Photo for your Reel" else "Tap to pick a Photo or Video",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Uses Android Photo Picker with zero extra storage permissions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (mediaError != null) {
                Text(
                    text = mediaError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it.take(if (selectedMode == "story") 200 else 500) },
                label = { Text("Caption") },
                placeholder = { Text("Write something radiant...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_caption_input")
            )

            if (selectedMode != "story") {
                OutlinedTextField(
                    value = hashtagsRaw,
                    onValueChange = { hashtagsRaw = it.take(180) },
                    label = { Text("Hashtags") },
                    placeholder = { Text("#starlight #cosmos #vibes") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_hashtags_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.take(4).forEach { item ->
                            FilterChip(
                                selected = category == item,
                                onClick = { category = item },
                                label = { Text(item) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.drop(4).forEach { item ->
                            FilterChip(
                                selected = category == item,
                                onClick = { category = item },
                                label = { Text(item) }
                            )
                        }
                    }
                }

                if (selectedMode == "reel") {
                    OutlinedTextField(
                        value = musicTitle,
                        onValueChange = { musicTitle = it.take(80) },
                        label = { Text("Soundtrack / Music Title") },
                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_music_input")
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Only visible on my profile (Private)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Save to Profile Highlights", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(checked = addToHighlight, onCheckedChange = { addToHighlight = it })
                        }
                        if (addToHighlight) {
                            OutlinedTextField(
                                value = highlightTitle,
                                onValueChange = { highlightTitle = it.take(30) },
                                label = { Text("Highlight Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            GradientButton(
                text = when {
                    isBusy -> "Publishing..."
                    selectedMode == "reel" -> "Publish Reel ✨"
                    selectedMode == "story" -> "Share 24h Story ✨"
                    else -> "Share Post ✨"
                },
                enabled = !isBusy && !isProcessingMedia && (preparedMedia != null || caption.isNotBlank()),
                onClick = {
                    val media = preparedMedia
                    if (selectedMode == "story") {
                        onPublishStory(
                            media?.mediaType ?: "image",
                            media?.mediaUrl ?: "",
                            caption,
                            addToHighlight,
                            highlightTitle
                        )
                    } else {
                        onPublishPost(
                            selectedMode,
                            media?.mediaType ?: "image",
                            media?.mediaUrl ?: "",
                            media?.thumbnailUrl ?: "",
                            caption,
                            category,
                            hashtagsRaw,
                            if (selectedMode == "reel") musicTitle else "",
                            if (isPrivate) "private" else "public"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "create_publish_button"
            )
        }
    }
}
