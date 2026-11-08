package texteditor.data

import android.content.Context
import android.widget.Toast

object AppToasts {
    private var toast: Toast? = null

    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        toast?.cancel()
        toast = Toast.makeText(context, message, duration).apply { show() }
    }
}
