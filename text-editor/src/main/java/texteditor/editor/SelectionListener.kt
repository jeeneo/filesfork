package texteditor.editor

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.rosemoe.sora.util.IntPair
import io.github.rosemoe.sora.widget.CodeEditor

internal class SelectionListener(
    private val editor: CodeEditor,
    private val isImeVisible: () -> Boolean,
) : View.OnTouchListener {
    private var selectionTap = false
    private var forwardingToEditor = false
    private var pendingDown: MotionEvent? = null
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(editor.context).scaledTouchSlop

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, e: MotionEvent): Boolean {
        return !isImeVisible() && when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(e)
            MotionEvent.ACTION_MOVE -> onMove(view, e)
            MotionEvent.ACTION_UP -> onUp(view, e)
            MotionEvent.ACTION_CANCEL -> onCancel(view, e)
            else -> forwardingToEditor
        }
    }

    private fun onDown(e: MotionEvent): Boolean {
        val cur = editor.cursor
        val onHandle = editor.leftHandleDescriptor.position.contains(
            e.x,
            e.y
        ) || editor.rightHandleDescriptor.position.contains(
            e.x,
            e.y
        ) || editor.insertHandleDescriptor.position.contains(e.x, e.y)
        forwardingToEditor = false
        selectionTap = false
        if (!onHandle && cur.isSelected && editor.isScreenPointOnText(e.x, e.y)) {
            val pos = editor.getPointPositionOnScreen(e.x, e.y)
            val line = IntPair.getFirst(pos)
            val column = IntPair.getSecond(pos)
            val tapIndex = editor.text.getCharIndex(line, column)
            val withinLines = line in cur.leftLine..cur.rightLine
            if (withinLines && tapIndex in cur.left..cur.right) {
                selectionTap = true
                downX = e.x
                downY = e.y
                pendingDown = MotionEvent.obtain(e)
                return true
            }
        }
        return false
    }

    private fun onMove(view: View, e: MotionEvent): Boolean {
        if (selectionTap && !forwardingToEditor) {
            val dx = e.x - downX
            val dy = e.y - downY
            if (dx * dx + dy * dy > touchSlop * touchSlop) {
                forwardingToEditor = true
                selectionTap = false
                pendingDown?.let { view.onTouchEvent(it) }
                pendingDown?.recycle()
                pendingDown = null
            } else {
                return true
            }
        }
        if (forwardingToEditor) {
            view.onTouchEvent(e)
            return true
        }
        return false
    }

    private fun onUp(view: View, e: MotionEvent): Boolean {
        val consumed = when {
            forwardingToEditor -> {
                view.onTouchEvent(e)
                true
            }

            selectionTap -> {
                selectionTap = false
                view.performClick()
                editor.showSoftInput()
                true
            }

            else -> false
        }
        forwardingToEditor = false
        pendingDown?.recycle()
        pendingDown = null
        return consumed
    }

    private fun onCancel(view: View, e: MotionEvent): Boolean {
        if (forwardingToEditor) view.onTouchEvent(e)
        selectionTap = false
        forwardingToEditor = false
        pendingDown?.recycle()
        pendingDown = null
        return false
    }
}
