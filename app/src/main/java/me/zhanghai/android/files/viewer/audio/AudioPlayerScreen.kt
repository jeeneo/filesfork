package me.zhanghai.android.filesfork.viewer.audio

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.MoreExecutors
import java8.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.zhanghai.android.filesfork.R
import me.zhanghai.android.filesfork.file.MimeType
import me.zhanghai.android.filesfork.file.fileProviderUri
import me.zhanghai.android.filesfork.util.createSendStreamIntent
import me.zhanghai.android.filesfork.util.extraPath
import me.zhanghai.android.filesfork.util.withChooser

private fun formatDuration(milliseconds: Long): String {
    if (milliseconds <= 0) return "-:--"
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    paths: List<Path>,
    initialPosition: Int,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentPaths = remember { mutableStateListOf<Path>().also { it.addAll(paths) } }
    if (currentPaths.isEmpty()) {
        LaunchedEffect(Unit) { onNavigateUp() }
        return
    }
    var currentIndex by rememberSaveable {
        mutableIntStateOf(initialPosition.coerceIn(0, currentPaths.lastIndex))
    }
    var isPlaying by rememberSaveable { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }

    var isLoadingMetadata by remember { mutableStateOf(true) }
    val metadata = remember { mutableStateOf<MediaMetadata?>(null) }

    var isShuffleEnabled by rememberSaveable { mutableStateOf(false) }
    var isRepeatEnabled by rememberSaveable { mutableStateOf(false) }

    val playOrder = remember { mutableStateListOf<Int>() }
    LaunchedEffect(isShuffleEnabled, currentPaths.size) {
        val indices = (0 until currentPaths.size).toMutableList()
        if (isShuffleEnabled) {
            indices.shuffle()
            if (indices.remove(currentIndex)) {
                indices.add(0, currentIndex)
            }
        }
        playOrder.clear()
        playOrder.addAll(indices)
    }

    val fileName = currentPaths[currentIndex].fileName.toString()
    val displayTitle = metadata.value?.title?.toString() ?: fileName

    val artist = metadata.value?.artist?.toString()
    val album = metadata.value?.albumTitle?.toString()

    var player by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(Unit) {
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            { player = controllerFuture.get() },
            MoreExecutors.directExecutor(),
        )
        onDispose {
            MediaController.releaseFuture(controllerFuture)
            player = null
        }
    }

    val connectedPlayer = player
    if (connectedPlayer == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.audio_player_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    DisposableEffect(connectedPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = connectedPlayer.duration.coerceAtLeast(0)
                    isLoadingMetadata = false
                }
                if (playbackState == Player.STATE_ENDED) {
                    if (isRepeatEnabled) {
                        connectedPlayer.seekTo(0)
                        connectedPlayer.play()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIndex = connectedPlayer.currentMediaItemIndex
                if (newIndex != currentIndex && newIndex in currentPaths.indices) {
                    currentIndex = newIndex
                }
                durationMs = 0
                positionMs = 0
                isLoadingMetadata = true
                metadata.value = null
            }

            override fun onMediaMetadataChanged(newMetadata: MediaMetadata) {
                if (newMetadata.title != null || newMetadata.artist != null || newMetadata.albumTitle != null || newMetadata.artworkData != null || newMetadata.artworkUri != null) {
                    metadata.value = newMetadata
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                isShuffleEnabled = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                isRepeatEnabled = repeatMode == Player.REPEAT_MODE_ONE
            }
        }
        connectedPlayer.addListener(listener)
        metadata.value = connectedPlayer.mediaMetadata
        onDispose { connectedPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentPaths.toList(), connectedPlayer) {
        val mediaItems = currentPaths.map { MediaItem.fromUri(it.fileProviderUri) }
        connectedPlayer.setMediaItems(
            mediaItems, currentIndex, 0
        )
        connectedPlayer.prepare()
        connectedPlayer.playWhenReady = true
    }

    LaunchedEffect(currentIndex, connectedPlayer) {
        if (connectedPlayer.currentMediaItemIndex == currentIndex) return@LaunchedEffect
        connectedPlayer.seekToDefaultPosition(currentIndex)
        connectedPlayer.prepare()
        connectedPlayer.playWhenReady = true
    }

    LaunchedEffect(isShuffleEnabled, connectedPlayer) {
        connectedPlayer.shuffleModeEnabled = isShuffleEnabled
    }
    LaunchedEffect(isRepeatEnabled, connectedPlayer) {
        connectedPlayer.repeatMode =
            if (isRepeatEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(connectedPlayer) {
        while (isActive) {
            if (!isUserSeeking) {
                positionMs = connectedPlayer.currentPosition.coerceAtLeast(0)
            }
            delay(200)
        }
    }

    fun playPrevious() {
        if (connectedPlayer.hasPreviousMediaItem()) {
            connectedPlayer.seekToPreviousMediaItem()
        }
    }

    fun playNext() {
        if (connectedPlayer.hasNextMediaItem()) {
            connectedPlayer.seekToNextMediaItem()
        }
    }

    val hasQueue = currentPaths.size > 1
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = hasQueue,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.audio_player_queue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
                val listState = rememberLazyListState()
                LaunchedEffect(currentIndex) {
                    coroutineScope.launch { listState.animateScrollToItem(currentIndex) }
                }
                LazyColumn(state = listState) {
                    itemsIndexed(
                        currentPaths, key = { _, path -> path.hashCode() }) { index, path ->
                        val selected = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable {
                                    currentIndex = index
                                    coroutineScope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = path.fileName.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            if (selected) {
                                Text(
                                    text = formatDuration(durationMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(android.R.string.cancel),
                            )
                        }
                    },
                    actions = {
                        if (hasQueue) {
                            IconButton(onClick = {
                                coroutineScope.launch { drawerState.open() }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = stringResource(R.string.audio_player_queue),
                                )
                            }
                        }
                        IconButton(onClick = {
                            val path = currentPaths[currentIndex]
                            val intent = path.fileProviderUri.createSendStreamIntent(
                                MimeType.AUDIO_ANY
                            ).apply { extraPath = path }.withChooser()
                            context.startActivity(intent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.action_share),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    val artworkData = metadata.value?.artworkData
                    val artworkUri = metadata.value?.artworkUri
                    if (artworkData != null || artworkUri != null) {
                        AsyncImage(
                            model = artworkData ?: artworkUri,
                            contentDescription = album,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = if (isLoadingMetadata) stringResource(R.string.audio_player_loading) else displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    InfoRow(
                        icon = Icons.Default.Person,
                        text = artist,
                        isLoading = isLoadingMetadata,
                        placeholder = stringResource(R.string.unknown_info),
                    )
                    InfoRow(
                        icon = Icons.Default.Album,
                        text = album,
                        isLoading = isLoadingMetadata,
                        placeholder = stringResource(R.string.unknown_info),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val sliderValue = if (isUserSeeking) seekPositionMs else positionMs.toFloat()
                    val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                    Slider(
                        value = sliderValue.coerceIn(0f, sliderMax),
                        onValueChange = {
                            isUserSeeking = true
                            seekPositionMs = it
                        },
                        onValueChangeFinished = {
                            connectedPlayer.seekTo(seekPositionMs.toLong())
                            positionMs = seekPositionMs.toLong()
                            isUserSeeking = false
                        },
                        valueRange = 0f..sliderMax,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDuration(
                                if (isUserSeeking) seekPositionMs.toLong() else positionMs
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatDuration(durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledIconToggleButton(
                            checked = isShuffleEnabled,
                            onCheckedChange = { isShuffleEnabled = it },
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = stringResource(R.string.audio_player_shuffle),
                            )
                        }


                        IconButton(
                            onClick = { playPrevious() },
                            enabled = hasQueue,
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = stringResource(R.string.audio_player_previous),
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                if (connectedPlayer.isPlaying) {
                                    connectedPlayer.pause()
                                } else {
                                    connectedPlayer.play()
                                }
                            },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.audio_player_pause
                                    else R.string.audio_player_play
                                ),
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        IconButton(
                            onClick = { playNext() },
                            enabled = hasQueue,
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = stringResource(R.string.audio_player_next),
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        FilledIconToggleButton(
                            checked = isRepeatEnabled,
                            onCheckedChange = { isRepeatEnabled = it },
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = stringResource(R.string.audio_player_repeat),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String?,
    isLoading: Boolean,
    placeholder: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = when {
                text != null -> text
                isLoading -> stringResource(R.string.audio_player_loading)
                else -> placeholder
            },
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (text == null) FontStyle.Italic else FontStyle.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
