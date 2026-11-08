package texteditor.ui.bars.topbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import texteditor.OpenDocument
import texteditor.fileNameOf
import texteditor.ui.bars.bottombar.BarColor

private val TabFlare = 6.dp

@Composable
fun DocumentTabs(
    documents: List<OpenDocument>,
    activeDocumentId: Long,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    val activeIndex = documents.indexOfFirst { it.id == activeDocumentId }
    LaunchedEffect(activeIndex, documents.size) {
        if (activeIndex < 0) return@LaunchedEffect
        val activeVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == activeIndex }
        if (!activeVisible) listState.animateScrollToItem(activeIndex)
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .background(BarColor)
            .padding(top = 4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(-TabFlare),
    ) {
        items(documents, key = { it.id }) { document ->
            DocumentTab(
                document = document,
                selected = document.id == activeDocumentId,
                onSelect = { onSelect(document.id) },
                onClose = { onClose(document.id) },
            )
        }
    }
}

private class InvertedBottomTabShape(
    private val topRadius: Dp,
    private val bottomRadius: Dp,
) : Shape {
    override fun createOutline(
        size: Size, layoutDirection: LayoutDirection, density: Density
    ): Outline {
        val tr = with(density) { topRadius.toPx() }
        val br = with(density) { bottomRadius.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h)
            arcTo(Rect(-br, h - 2 * br, br, h), 90f, -90f, false)
            lineTo(br, tr)
            arcTo(Rect(br, 0f, br + 2 * tr, 2 * tr), 180f, 90f, false)
            lineTo(w - br - tr, 0f)
            arcTo(Rect(w - br - 2 * tr, 0f, w - br, 2 * tr), 270f, 90f, false)
            lineTo(w - br, h - br)
            arcTo(Rect(w - br, h - 2 * br, w + br, h), 180f, -90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun DocumentTab(
    document: OpenDocument,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val name = document.name ?: document.path?.let(::fileNameOf) ?: "New file"
    val title = if (document.isModified) "*$name" else name
    val tabColor = MaterialTheme.colorScheme.surfaceContainer
    val tabShape = remember { InvertedBottomTabShape(topRadius = 6.dp, bottomRadius = TabFlare) }
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) tabColor else tabColor.copy(alpha = 0f),
        animationSpec = tween(150),
        label = "tabBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(150),
        label = "tabContent",
    )
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(tabShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close $name",
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
