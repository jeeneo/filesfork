package texteditor.data.encoding

enum class LineEnding(val text: String) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r"),
}

data class LineEndingInfo(
    val lineEnding: LineEnding,
    val mixed: Boolean,
    val lfCount: Int = 0,
    val crlfCount: Int = 0,
    val crCount: Int = 0,
) {
    val label: String
        get() = lineEnding.name
}

object LineEndings {
    fun detect(text: CharSequence, default: LineEnding = LineEnding.LF): LineEndingInfo {
        var lf = 0
        var crlf = 0
        var cr = 0
        val order = ArrayList<LineEnding>(3)
        fun note(style: LineEnding) {
            if (style !in order) order += style
        }

        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') {
                        crlf++
                        note(LineEnding.CRLF)
                        index++
                    } else {
                        cr++
                        note(LineEnding.CR)
                    }
                }

                '\n' -> {
                    lf++
                    note(LineEnding.LF)
                }
            }
            index++
        }

        val counts = mapOf(LineEnding.LF to lf, LineEnding.CRLF to crlf, LineEnding.CR to cr)
        val dominant = if (counts.values.all { it == 0 }) {
            default
        } else {
            order.maxByOrNull { counts.getValue(it) } ?: default
        }
        return LineEndingInfo(
            lineEnding = dominant,
            mixed = order.size > 1,
            lfCount = lf,
            crlfCount = crlf,
            crCount = cr,
        )
    }

    fun convert(text: CharSequence, lineEnding: LineEnding): String {
        if (text.isEmpty()) return ""
        val builder = StringBuilder(text.length + 16)
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') index++
                    builder.append(lineEnding.text)
                }

                '\n' -> builder.append(lineEnding.text)

                else -> builder.append(text[index])
            }
            index++
        }
        return builder.toString()
    }
}
