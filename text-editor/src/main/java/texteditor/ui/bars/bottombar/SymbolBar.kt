package texteditor.ui.bars.bottombar

import android.graphics.Typeface
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import texteditor.SYMBOLS

private const val ReferenceWidthDp = 411f
private const val ReferenceHeightDp = 905f
private const val MinHeightFactor = 0.75f
private val BaseRestingBarHeight = 37.5.dp
private val BaseExpandedBarHeight = 142.dp
private val BaseSymbolWidth = 50.dp
private val BaseSymbolHeight = 35.dp
private val BaseTabStripHeight = 32.dp

val BarColor = Color(0xFF2A2A2A)

@Immutable
private class BarMetrics(widthFactor: Float, heightFactor: Float) {
    private val h = heightFactor.coerceAtLeast(MinHeightFactor)
    val tabStripHeight = BaseTabStripHeight * h
    val restingHeight = BaseRestingBarHeight * h
    val expandedHeight = BaseExpandedBarHeight * h
    val gridHeight = expandedHeight - tabStripHeight
    val travel = expandedHeight - restingHeight
    val symbolHeight = BaseSymbolHeight * h
    val symbolWidth = BaseSymbolWidth * widthFactor
}

@Composable
private fun rememberBarMetrics(): BarMetrics {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    val heightDp = config.screenHeightDp
    return remember(widthDp, heightDp) {
        BarMetrics(
            widthFactor = widthDp / ReferenceWidthDp,
            heightFactor = heightDp / ReferenceHeightDp,
        )
    }
}

private class SymbolEntry(
    val label: String,
    val onClick: (CodeEditor?) -> Unit,
)

private val SymbolEntries: List<SymbolEntry> = buildList {
    add(SymbolEntry("<-") { it?.unindentSelection() })
    add(SymbolEntry("->") { it?.indentLines(false) })
    SYMBOLS.forEach { (label, insert) ->
        add(SymbolEntry(label) { editor -> editor?.insertSymbol(insert) })
    }
}

@Composable
fun SymbolInputBar(
    editor: CodeEditor?,
    typeface: Typeface,
    modifier: Modifier = Modifier,
) {
    val metrics = rememberBarMetrics()
    var expansion by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val travelPx = with(LocalDensity.current) { metrics.travel.toPx() }
    val fontFamily = remember(typeface) { FontFamily(typeface) }
    val gridScroll = rememberScrollState()

    fun settle() {
        settleJob?.cancel()
        settleJob = scope.launch {
            val target = if (expansion >= 0.5f) 1f else 0f
            if (target == 0f) gridScroll.scrollTo(0)
            animate(
                initialValue = expansion,
                targetValue = target,
                animationSpec = tween(durationMillis = 160),
            ) { value, _ -> expansion = value }
        }
    }

    val barHeight = metrics.restingHeight + metrics.travel * expansion

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        color = BarColor,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 0.4.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clipToBounds()
                    .pointerInput(travelPx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                settleJob?.cancel()
                                if (expansion < 1f && gridScroll.value != 0) {
                                    scope.launch { gridScroll.scrollTo(0) }
                                }
                            },
                            onDragEnd = { settle() },
                            onDragCancel = { settle() },
                        ) { change, dragAmount ->
                            val next = (expansion - dragAmount / travelPx).coerceIn(0f, 1f)
                            if (next != expansion) {
                                expansion = next
                                change.consume()
                            }
                        }
                    },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // TODO
                    TabStrip(metrics = metrics, expansion = expansion)
                    SymbolGrid(
                        editor = editor,
                        fontFamily = fontFamily,
                        metrics = metrics,
                        scrollState = gridScroll,
                        scrollEnabled = expansion >= 1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .height(metrics.gridHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabStrip(
    metrics: BarMetrics,
    expansion: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.tabStripHeight * expansion)
            .alpha(expansion)
            .background(Color.Transparent), contentAlignment = Alignment.Center
    ) {
//        Row(
//            modifier = Modifier
//                .width(54.dp)
//                .fillMaxHeight()
//                .background(MaterialTheme.colorScheme.surfaceContainerLow)
//        ) {
//            Text("Main")
//        }
    }
}

@Composable
private fun SymbolGrid(
    editor: CodeEditor?,
    fontFamily: FontFamily,
    metrics: BarMetrics,
    scrollState: ScrollState,
    scrollEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val cellWidth = metrics.symbolWidth
        val cellHeight = metrics.symbolHeight
        val columns = (maxWidth / cellWidth).toInt().coerceAtLeast(1)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState, enabled = scrollEnabled)
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SymbolEntries.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { entry ->
                        SymbolCell(
                            label = entry.label,
                            fontFamily = fontFamily,
                            onClick = { entry.onClick(editor) },
                            modifier = Modifier.size(cellWidth, cellHeight),
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.size(cellWidth, cellHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolCell(
    label: String,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = fontFamily,
            color = LocalContentColor.current.copy(alpha = 0.8f),
            fontSize = 13.sp,
        )
    }
}

private fun CodeEditor.insertSymbol(insert: String) {
    if (!isEditable) return
    val cursor = this.cursor
    text.replace(
        cursor.leftLine,
        cursor.leftColumn,
        cursor.rightLine,
        cursor.rightColumn,
        insert,
    )
}
