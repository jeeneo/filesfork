/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.viewer.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.exifinterface.media.ExifInterface
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.zhanghai.android.filesfork.R
import me.zhanghai.android.filesfork.file.fileProviderUri
import me.zhanghai.android.filesfork.provider.common.delete
import me.zhanghai.android.filesfork.util.createSendImageIntent
import me.zhanghai.android.filesfork.util.extraPath
import me.zhanghai.android.filesfork.util.withChooser
import java.io.IOException

private class RotationTransformation(private val degrees: Float) : Transformation {
    override val cacheKey = "rotation_$degrees"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (degrees == 0f) return input
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, matrix, true)
    }
}

private fun readExifRotation(uri: Uri, contentResolver: ContentResolver): Float = try {
    contentResolver.openInputStream(uri)?.use { stream ->
        val exif = ExifInterface(stream)
        when (exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
} catch (_: IOException) {
    0f
}

private suspend fun buildImageRequest(
    context: android.content.Context,
    uri: Uri,
): ImageRequest {
    val (degrees) = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        readExifRotation(uri, cr) to (uri)
    }
    return ImageRequest.Builder(context).data(uri).crossfade(true)
        .apply { if (degrees != 0f) transformations(RotationTransformation(degrees)) }.build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    paths: List<Path>,
    initialPosition: Int,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPaths = remember { mutableStateListOf<Path>().also { it.addAll(paths) } }
    if (currentPaths.isEmpty()) {
        LaunchedEffect(Unit) { onNavigateUp() }
        return
    }
    val pagerState = rememberPagerState(
        initialPage = initialPosition.coerceIn(0, currentPaths.lastIndex),
        pageCount = { currentPaths.size },
    )
    var pathPendingDelete by remember { mutableStateOf<Path?>(null) }
    var toolbarVisible by rememberSaveable { mutableStateOf(true) }
    val idx = pagerState.currentPage.coerceIn(0, currentPaths.lastIndex)
    val title = currentPaths[idx].fileName.toString()
    val subtitle = if (currentPaths.size > 1) {
        stringResource(
            R.string.image_viewer_subtitle_format, idx + 1, currentPaths.size
        )
    } else null

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = toolbarVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
            ) {
                TopAppBar(
                    {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
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
                        IconButton(onClick = {
                            pathPendingDelete = currentPaths[pagerState.currentPage]
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                        IconButton(onClick = {
                            val path = currentPaths[pagerState.currentPage]
                            val intent = path.fileProviderUri.createSendImageIntent()
                                .apply { extraPath = path }.withChooser()
                            context.startActivity(intent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.action_share),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
            }
        },
        containerColor = Color.Black,
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            beyondViewportPageCount = 1,
            key = { currentPaths[it].hashCode() },
        ) { page ->
            ImagePage(
                path = currentPaths[page],
                onTap = { toolbarVisible = !toolbarVisible },
            )
        }
    }
    pathPendingDelete?.let { path ->
        AlertDialog(
            onDismissRequest = { pathPendingDelete = null },
            text = {
                Text(stringResource(R.string.image_viewer_delete_message_format, path.fileName))
            },
            confirmButton = {
                TextButton(onClick = {
                    pathPendingDelete = null
                    scope.launch {
                        try {
                            path.delete()
                        } catch (e: IOException) {
                            e.printStackTrace()
                            return@launch
                        }
                        currentPaths.remove(path)
                        if (currentPaths.isEmpty()) {
                            onNavigateUp()
                            return@launch
                        }
                        val newPage = pagerState.currentPage.coerceIn(0, currentPaths.lastIndex)
                        if (pagerState.currentPage != newPage) pagerState.scrollToPage(newPage)
                    }
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pathPendingDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ImagePage(
    path: Path,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(
            maxZoomFactor = 8f,
            overzoomEffect = OverzoomEffect.RubberBanding,
        )
    )
    val imageState = rememberZoomableImageState(zoomableState)

    val isImage by produceState<Boolean>(initialValue = true, path) {
        val uri = path.fileProviderUri
        val mimeType = context.contentResolver.getType(uri)
        value = mimeType?.startsWith("image/") == true
    }

    if (!isImage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.image_viewer_not_an_image, path.fileName),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    val request by produceState<ImageRequest?>(initialValue = null, path) {
        value = buildImageRequest(context, path.fileProviderUri)
    }
    if (request == null) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        ZoomableAsyncImage(
            model = request,
            contentDescription = path.fileName.toString(),
            state = imageState,
            modifier = Modifier.fillMaxSize(),
            onClick = { onTap() },
        )
        AnimatedVisibility(
            visible = !imageState.isImageDisplayed,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            CircularWavyProgressIndicator(color = Color.White)
        }
    }
}
