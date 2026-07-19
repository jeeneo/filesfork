package me.zhanghai.android.filesfork.viewer.audio

import android.content.ComponentName
import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
import java.net.URI
import kotlin.math.roundToInt

@Composable
fun AudioPlayerBar() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var mediaItemCount by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf<MediaMetadata?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            { controller = controllerFuture.get() },
            MoreExecutors.directExecutor(),
        )
        onDispose {
            MediaController.releaseFuture(controllerFuture)
            controller = null
        }
    }

    val player = controller

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}

        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                mediaItemCount = timeline.windowCount
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaMetadataChanged(newMetadata: MediaMetadata) {
                if (newMetadata.title != null) {
                    metadata = newMetadata
                }
            }
        }
        player.addListener(listener)
        mediaItemCount = player.mediaItemCount
        isPlaying = player.isPlaying
        metadata = player.mediaMetadata
        onDispose { player.removeListener(listener) }
    }

    if (player == null || mediaItemCount == 0) return

    val whenDismiss = with(density) { 40.dp.toPx() }

    val title = metadata?.title?.toString() ?: if (player.mediaItemCount > 0) {
        URI.create(
            player.getMediaItemAt(player.currentMediaItemIndex).mediaId
        ).path?.substringAfterLast('/') ?: ""
    } else {
        ""
    }

    val displayText = remember(metadata, title) {
        val artist = metadata?.artist?.toString()
        if (!artist.isNullOrBlank()) "$artist - $title" else title
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var isDrag = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val delta = change.position.y - change.previousPosition.y
                            if (delta != 0f) {
                                isDrag = true
                                dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                                change.consume()
                            }
                        }
                    } while (change.pressed)
                    if (isDrag && dragOffset > 0f) {
                        val currentOffset = dragOffset
                        if (currentOffset > whenDismiss) {
                            scope.launch {
                                animate(
                                    currentOffset, currentOffset + 400f, animationSpec = tween(200)
                                ) { value, _ -> dragOffset = value }
                                player.stop()
                                player.clearMediaItems()
                                dragOffset = 0f
                            }
                        } else {
                            scope.launch {
                                animate(
                                    currentOffset,
                                    0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessHigh)
                                ) { value, _ -> dragOffset = value }
                            }
                        }
                    } else if (!isDrag) {
                        context.startActivity(Intent(context, AudioPlayerActivity::class.java))
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { player.seekBack() }) {
                        Icon(
                            imageVector = Icons.Filled.FastRewind,
                            contentDescription = "Rewind",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                    }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    IconButton(onClick = { player.seekForward() }) {
                        Icon(
                            imageVector = Icons.Filled.FastForward,
                            contentDescription = "Fast forward",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(onClick = { player.stop(); player.clearMediaItems() }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close music player",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
//                Spacer(
//                    modifier = Modifier.height(82.dp)
//                )
                Spacer(
                    modifier = Modifier.height(
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
        }
    }
}
