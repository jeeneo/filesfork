package texteditor.ui.dialogs.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf

/**
 * The stack of dialogs currently open; the last request is the one on top.
 *
 * [show] pushes, so a dialog can open another one without nesting the two composables, and
 * [dismissTop] returns to the one below.
 */
@Stable
class DialogController {
    val requests: List<DialogRequest>
        field = mutableStateListOf<DialogRequest>()

    fun show(request: DialogRequest) {
        requests.add(request)
    }

    fun dismissTop() {
        if (requests.isNotEmpty()) {
            requests.removeAt(requests.lastIndex)
        }
    }
}
