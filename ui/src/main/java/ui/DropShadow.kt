package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PaneShadow(shadowAtEnd: Boolean) {
    val shadow = MaterialTheme.colorScheme.scrim
    val paneShadowSize = 6.dp
    val paneShadowAlpha = 0.4f
    Canvas(Modifier.fillMaxSize()) {
        val band = paneShadowSize.toPx()
        if (band <= 0f) return@Canvas
        val paneWidth = size.width
        val paneHeight = size.height
        fun edge(alpha: Float) = shadow.copy(alpha = alpha)
        drawRect(
            brush = Brush.verticalGradient(
                0f to edge(paneShadowAlpha),
                0.5f to edge(paneShadowAlpha * 0.25f),
                1f to Color.Transparent,
                endY = band,
            ),
            size = Size(paneWidth, band),
        )
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to edge(paneShadowAlpha * 0.25f),
                1f to edge(paneShadowAlpha),
                startY = paneHeight - band,
                endY = paneHeight,
            ),
            topLeft = Offset(0f, paneHeight - band),
            size = Size(paneWidth, band),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                if (shadowAtEnd) 0f to Color.Transparent else 0f to edge(paneShadowAlpha),
                0.5f to edge(paneShadowAlpha * 0.25f),
                if (shadowAtEnd) 1f to edge(paneShadowAlpha) else 1f to Color.Transparent,
                startX = if (shadowAtEnd) paneWidth - band else 0f,
                endX = if (shadowAtEnd) paneWidth else band,
            ),
            topLeft = Offset(if (shadowAtEnd) paneWidth - band else 0f, 0f),
            size = Size(band, paneHeight),
        )
    }
}

@Composable
fun SimpleShadow() {
    val shadow = MaterialTheme.colorScheme.scrim
    val paneShadowSize = 4.dp
    val paneShadowAlpha = 0.4f
    Canvas(Modifier.fillMaxSize()) {
        val band = paneShadowSize.toPx()
        if (band <= 0f) return@Canvas
        val paneWidth = size.width
        fun edge(alpha: Float) = shadow.copy(alpha = alpha)
        drawRect(
            brush = Brush.verticalGradient(
                0f to edge(paneShadowAlpha),
                0.5f to edge(paneShadowAlpha * 0.25f),
                1f to Color.Transparent,
                endY = band,
            ),
            size = Size(paneWidth, band),
        )
    }
}

