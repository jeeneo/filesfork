package texteditor.ui.bars.topbar

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults.contentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.github.rosemoe.sora.widget.CodeEditor
import texteditor.R
import texteditor.data.AppToasts

@Composable
fun EditMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    editorRef: CodeEditor?,
    context: Context,
) {
    val itemTextColor = contentColor.copy(alpha = 0.8f)
    val itemIconTint = contentColor.copy(alpha = 0.5f)

    DropdownMenu(
        expanded = expanded,
        containerColor = MaterialTheme.colorScheme.onSecondary,
        properties = PopupProperties(
            focusable = false
        ),
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.CopyAll,
                    contentDescription = null,
                    tint = itemIconTint,
                )
            },
            text = { Text("Copy line", color = itemTextColor) },
            onClick = copyLine@{
                onDismiss()
                val editor = editorRef ?: return@copyLine
                val line = editor.cursor.leftLine
                context.copyToClipboard(
                    "Line ${line + 1}", editor.text.getLineString(line)
                )
            })
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.ContentCut,
                    contentDescription = null,
                    tint = itemIconTint,
                )
            },
            text = { Text("Cut line", color = itemTextColor) },
            onClick = cutLine@{
                onDismiss()
                val editor = editorRef ?: return@cutLine
                val line = editor.cursor.leftLine
                context.copyToClipboard(
                    "Line ${line + 1}", editor.text.getLineString(line)
                )
                editor.deleteLine(line)
            })
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = itemIconTint,
                )
            },
            text = { Text("Delete line", color = itemTextColor) },
            onClick = deleteLine@{
                onDismiss()
                val editor = editorRef ?: return@deleteLine
                editor.deleteLine(editor.cursor.leftLine)
            })
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    tint = itemIconTint,
                )
            },
            text = { Text("Clear line", color = itemTextColor) },
            onClick = clearLine@{
                onDismiss()
                val editor = editorRef ?: return@clearLine
                editor.clearLine(editor.cursor.leftLine)
            })
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.Filled.ContentPaste,
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Replace line (paste)", color = itemTextColor
            )
        }, onClick = replaceLine@{
            onDismiss()
            val editor = editorRef ?: return@replaceLine
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@replaceLine
            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) return@replaceLine
            val replacement =
                clip.getItemAt(0).coerceToText(context).toString().removeSuffix("\r\n")
                    .removeSuffix("\n")
            editor.replaceLine(editor.cursor.leftLine, replacement)
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.Filled.TextIncrease,
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Duplicate line", color = itemTextColor
            )
        }, onClick = duplicateLine@{
            onDismiss()
            val editor = editorRef ?: return@duplicateLine
            editor.duplicateLine(editor.cursor.leftLine)
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.text_icon_uppercase),
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Convert to UPPERCASE", color = itemTextColor
            )
        }, onClick = {
            onDismiss()
            editorRef?.convertCase { it.uppercase() }
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.text_icon_lowercase),
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "convert to lowercase", color = itemTextColor
            )
        }, onClick = {
            onDismiss()
            editorRef?.convertCase { it.lowercase() }
        })
        LongClickMenuItem(leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.text_icon_altercase),
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Convert to aLtErNaTiNg", color = itemTextColor
            )
        }, onClick = {
            onDismiss()
            editorRef?.convertCase { it.toAlternatingCase() }
        }, onLongClick = {
            onDismiss()
            AppToasts.show(context, "it has been done")
            editorRef?.convertCase { it.uppercase().replace("I", "i") }
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.text_icon_caseinvert),
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Invert case", color = itemTextColor
            )
        }, onClick = {
            onDismiss()
            editorRef?.convertCase { it.toInvertedCase() }
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Title,
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Convert To Title Case", color = itemTextColor
            )
        }, onClick = {
            onDismiss()
            editorRef?.convertCase { it.toTitleCase() }
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.Filled.TextFormat,
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Convert to sentence case", color = itemTextColor
            )
        }, onClick = sentenceCase@{
            onDismiss()
            editorRef?.convertCase { it.toSentenceCase() }
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Numbers,
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Numbers to words", color = itemTextColor
            )
        }, onClick = {
            onDismiss()
            editorRef?.convertCase { it.numbersToWords() }
        })
    }
}

private fun CodeEditor.convertCase(transform: (String) -> String) {
    val content = text
    val cursor = cursor
    val hasSelection = cursor.left != cursor.right
    val start = if (hasSelection) {
        minOf(cursor.left, cursor.right)
    } else {
        content.getCharIndex(cursor.leftLine, 0)
    }
    val end = if (hasSelection) {
        maxOf(cursor.left, cursor.right)
    } else {
        start + content.getLineString(cursor.leftLine).length
    }
    if (start >= end) return
    content.replace(start, end, transform(content.subSequence(start, end).toString()))
}

private fun String.toTitleCase(): String {
    val builder = StringBuilder(length)
    var atWordStart = true
    for (char in this) {
        when {
            char.isLetter() -> {
                builder.append(if (atWordStart) char.uppercaseChar() else char.lowercaseChar())
                atWordStart = false
            }

            // An apostrophe stays inside the word, so "don't" becomes "Don't".
            char == '\'' -> builder.append(char)

            else -> {
                builder.append(char)
                atWordStart = !char.isDigit()
            }
        }
    }
    return builder.toString()
}

private fun String.toSentenceCase(): String {
    val builder = StringBuilder(length)
    var atSentenceStart = true
    for (char in this) {
        when {
            char.isLetter() -> {
                builder.append(
                    if (atSentenceStart) char.uppercaseChar() else char.lowercaseChar()
                )
                atSentenceStart = false
            }

            char.isDigit() -> {
                builder.append(char)
                atSentenceStart = false
            }

            else -> {
                builder.append(char)
                if (char == '.' || char == '!' || char == '?' || char == '\n') {
                    atSentenceStart = true
                }
            }
        }
    }
    return builder.toString()
}

private fun String.toInvertedCase(): String {
    val builder = StringBuilder(length)
    for (char in this) {
        builder.append(
            when {
                char.isUpperCase() -> char.lowercaseChar()
                char.isLowerCase() -> char.uppercaseChar()
                else -> char
            }
        )
    }
    return builder.toString()
}

private fun String.toAlternatingCase(): String =
    if (this == alternate(startUpper = false)) alternate(startUpper = true)
    else alternate(startUpper = false)

private fun String.alternate(startUpper: Boolean): String {
    val builder = StringBuilder(length)
    var upper = startUpper
    for (char in this) {
        builder.append(
            when {
                !char.isLetter() -> char
                upper -> char.uppercaseChar()
                else -> char.lowercaseChar()
            }
        )
        if (char.isLetter()) upper = !upper
    }
    return builder.toString()
}

private fun CodeEditor.replaceLine(line: Int, replacement: String) {
    val content = text
    val start = content.getCharIndex(line, 0)
    content.replace(start, start + content.getLineString(line).length, replacement)
}

private fun CodeEditor.clearLine(line: Int) {
    val content = text
    val start = content.getCharIndex(line, 0)
    content.replace(start, start + content.getLineString(line).length, "")
}

private fun CodeEditor.duplicateLine(line: Int) {
    val content = text
    val lineText = content.getLineString(line)
    val end = content.getCharIndex(line, 0) + lineText.length
    content.replace(end, end, "\n$lineText")
}

private fun CodeEditor.deleteLine(line: Int) {
    val content = text
    val hasNext = line + 1 < content.lineCount
    val start = when {
        hasNext -> content.getCharIndex(line, 0)
        line > 0 -> content.getCharIndex(line - 1, 0) + content.getLineString(line - 1).length
        else -> 0
    }
    val end = if (hasNext) content.getCharIndex(line + 1, 0) else content.length
    content.replace(start, end, "")
}

@Composable
private fun LongClickMenuItem(
    text: @Composable () -> Unit,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .defaultMinSize(minWidth = 112.dp, minHeight = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leadingIcon()
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            text()
        }
    }
}

private val ONES = arrayOf(
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
    "seventeen", "eighteen", "nineteen",
)
private val TENS = arrayOf(
    "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
)
private val SCALES = arrayOf(
    "", "thousand", "million", "billion", "trillion", "quadrillion", "quintillion",
    "sextillion", "septillion", "octillion", "nonillion", "decillion",
)

private val NUMBER_REGEX = Regex("""\d+(?:\.\d+)?""")

private fun String.numbersToWords(): String = NUMBER_REGEX.replace(this) { match ->
    val intPart = match.value.substringBefore('.')
    val fracPart = match.value.substringAfter('.', "")
    val words = digitsToWords(intPart)
    if (fracPart.isEmpty()) words
    else "$words point " + fracPart.map { ONES[it - '0'] }.joinToString(" ")
}

private fun digitsToWords(digits: String): String {
    val trimmed = digits.trimStart('0')
    if (trimmed.isEmpty()) return "zero"
    if (trimmed.length > SCALES.size * 3) {
        return trimmed.map { ONES[it - '0'] }.joinToString(" ")
    }
    val groups = trimmed.reversed().chunked(3).map { it.reversed().toInt() }
    val parts = mutableListOf<String>()
    for (i in groups.indices.reversed()) {
        if (groups[i] == 0) continue
        val words = threeDigitsToWords(groups[i])
        parts += if (SCALES[i].isEmpty()) words else "$words ${SCALES[i]}"
    }
    return parts.joinToString(" ")
}

private fun threeDigitsToWords(n: Int): String {
    val parts = mutableListOf<String>()
    val hundreds = n / 100
    val rest = n % 100
    if (hundreds > 0) parts += "${ONES[hundreds]} hundred"
    if (rest > 0) {
        parts += if (rest < 20) {
            ONES[rest]
        } else {
            val tens = TENS[rest / 10]
            val ones = rest % 10
            if (ones == 0) tens else "$tens-${ONES[ones]}"
        }
    }
    return parts.joinToString(" ")
}
