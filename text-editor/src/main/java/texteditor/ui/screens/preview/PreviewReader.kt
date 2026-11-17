package texteditor.ui.screens.preview

import java.util.Locale

sealed interface PreviewDocument {
    val source: String

    data class Markdown(override val source: String) : PreviewDocument
    data class Html(override val source: String) : PreviewDocument
}

enum class PreviewKind { MARKDOWN, HTML }

object PreviewReaders {
    private val markdownExtensions = setOf("md", "markdown", "mdown", "mkdn")
    private val htmlExtensions = setOf("htm", "html")

    fun kindFor(fileName: String?): PreviewKind? {
        val name = fileName?.substringAfterLast('/')?.lowercase(Locale.ROOT) ?: return null
        if ('.' !in name) return null
        return when (name.substringAfterLast('.')) {
            in htmlExtensions -> PreviewKind.HTML
            in markdownExtensions -> PreviewKind.MARKDOWN
            else -> null
        }
    }
}
