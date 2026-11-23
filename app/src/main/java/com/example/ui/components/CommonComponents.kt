package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.text.format.DateUtils
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.media.MediaHelper
import com.example.ui.theme.StarlightBrushes
import com.google.firebase.Timestamp

@Composable
fun StarlightImage(
    source: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackText: String = "S"
) {
    val context = LocalContext.current
    val dataBytes = remember(source) { MediaHelper.decodeDataUriBytes(source) }

    if (source.isBlank()) {
        Box(
            modifier = modifier.background(StarlightBrushes.primaryGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackText.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondary,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        val modelData: Any = dataBytes ?: source
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(modelData)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

@Composable
fun StarlightAvatar(
    avatarUrl: String,
    username: String,
    size: Dp = 44.dp,
    hasStoryRing: Boolean = false,
    online: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(if (hasStoryRing) size + 6.dp else size),
        contentAlignment = Alignment.Center
    ) {
        if (hasStoryRing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(2.5.dp, StarlightBrushes.cosmicStoryRing, CircleShape)
                    .padding(3.dp)
            ) {
                StarlightImage(
                    source = avatarUrl,
                    contentDescription = "$username avatar",
                    fallbackText = username,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        } else {
            StarlightImage(
                source = avatarUrl,
                contentDescription = "$username avatar",
                fallbackText = username,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        }
        if (online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2CEAA3))
            )
        }
    }
}

@Composable
fun StarlightVideoPlayer(
    videoUrl: String,
    thumbnailUrl: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    showControls: Boolean = true
) {
    var isMuted by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var hasError by remember(videoUrl) { mutableStateOf(false) }

    LaunchedEffect(isActive, isPaused) {
        val vv = videoViewRef ?: return@LaunchedEffect
        if (isActive && !isPaused && !hasError) {
            if (!vv.isPlaying) vv.start()
        } else {
            if (vv.isPlaying) vv.pause()
        }
    }

    LaunchedEffect(isMuted) {
        val vol = if (isMuted) 0f else 1f
        try {
            mediaPlayerRef?.setVolume(vol, vol)
        } catch (_: Exception) {
        }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {
            }
            mediaPlayerRef = null
            videoViewRef = null
        }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .clickable { isPaused = !isPaused }
    ) {
        if (videoUrl.isNotBlank() && !hasError) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.parse(videoUrl))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            val vol = if (isMuted) 0f else 1f
                            mp.setVolume(vol, vol)
                            mediaPlayerRef = mp
                            if (isActive && !isPaused) start()
                        }
                        setOnErrorListener { _, _, _ ->
                            hasError = true
                            true
                        }
                        videoViewRef = this
                    }
                },
                update = { view ->
                    videoViewRef = view
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            StarlightImage(
                source = thumbnailUrl,
                contentDescription = "Video preview",
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isPaused && showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (showControls) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { isPaused = !isPaused },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Play" else "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HeartBurstOverlay(visible: Boolean) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            scale.snapTo(0.4f)
            scale.animateTo(1.25f, animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f))
            scale.animateTo(0f, animationSpec = spring())
        }
    }
    if (visible) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier
                .size(88.dp)
                .scale(scale.value)
        )
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .height(50.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled) StarlightBrushes.primaryGradient else Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                    ),
                    shape = MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(StarlightBrushes.primaryGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            GradientButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(0.75f),
                testTag = "empty_state_action_button"
            )
        }
    }
}

@Composable
fun ReportDialog(
    targetLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val quickReasons = listOf("Spam or misleading", "Harassment or hate", "Inappropriate content")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $targetLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Help keep STARLIGHT safe and welcoming.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                quickReasons.forEach { preset ->
                    OutlinedButton(
                        onClick = { reason = preset },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(preset)
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(300) },
                    label = { Text("Reason") },
                    placeholder = { Text("Describe the issue...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (reason.trim().length >= 3) onSubmit(reason.trim()) },
                enabled = reason.trim().length >= 3
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatRelativeTime(timestamp: Timestamp?): String {
    val timeMs = timestamp?.toDate()?.time ?: return "just now"
    return DateUtils.getRelativeTimeSpanString(
        timeMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

fun sharePostText(context: Context, username: String, caption: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "STARLIGHT Post by @$username")
        putExtra(Intent.EXTRA_TEXT, "@$username on STARLIGHT:\n$caption\n\nConnect • Share • Shine ✨")
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share via"))
}
