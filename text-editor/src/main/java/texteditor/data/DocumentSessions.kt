package texteditor.data

import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Job
import texteditor.LoadState
import texteditor.OpenDocument
import texteditor.data.encoding.EncodingCatalog
import texteditor.data.encoding.EncodingScheme
import texteditor.data.encoding.LineEnding

internal class DocumentSession(
    val id: Long,
    var path: String?,
    var name: String?,
    var directory: String?,
    var content: Content = Content(),
    var encoding: EncodingScheme = EncodingCatalog.default,
    var lineEnding: LineEnding = LineEnding.LF,
    var originalLineEnding: LineEnding = LineEnding.LF,
    var mixedLineEndings: Boolean = false,
    var isModified: Boolean = false,
    var isNewFile: Boolean = false,
    var languageOverride: String? = null,
    var loadState: LoadState = LoadState.Success,
) {
    var isSaving: Boolean = false
    var loadJob: Job? = null
    var selectionLeft: Int = -1
    var selectionRight: Int = -1
    var scrollX: Int = 0
    var scrollY: Int = 0

    fun toOpenDocument(): OpenDocument = OpenDocument(id, path, name, directory, isModified)
}

internal object DocumentSessions {
    private val sessions = LinkedHashMap<Long, DocumentSession>()
    private val recentOrder = mutableListOf<Long>()
    private var nextId = 1L

    var activeId: Long = 0L
        private set

    val active: DocumentSession?
        get() = sessions[activeId]

    fun snapshot(): List<OpenDocument> = sessions.values.map { it.toOpenDocument() }

    fun get(id: Long): DocumentSession? = sessions[id]

    fun findByPath(path: String): DocumentSession? = sessions.values.firstOrNull { it.path == path }

    fun create(
        path: String?,
        name: String?,
        directory: String?,
        isNewFile: Boolean,
    ): DocumentSession {
        val session = DocumentSession(
            id = nextId++,
            path = path,
            name = name,
            directory = directory,
            isNewFile = isNewFile,
            loadState = if (path == null) LoadState.Success else LoadState.Loading,
        )
        sessions[session.id] = session
        return session
    }

    fun markUsed(id: Long) {
        if (id !in sessions) return
        recentOrder.remove(id)
        recentOrder += id
        activeId = id
    }

    fun remove(id: Long): Boolean {
        if (sessions.remove(id) == null) return false
        recentOrder.remove(id)
        if (activeId == id) activeId = recentOrder.lastOrNull { it in sessions } ?: 0L
        return true
    }
}
