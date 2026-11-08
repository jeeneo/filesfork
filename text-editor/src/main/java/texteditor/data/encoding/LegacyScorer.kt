package texteditor.data.encoding

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object LegacyScorer {
    private const val MIN_SCORE = 50

    private val candidates = listOf(
        "windows-1252",
        "windows-1251",
        "windows-1250",
        "ISO-8859-1",
        "ISO-8859-2",
        "KOI8-R",
        "Shift_JIS",
        "GB18030",
        "Big5",
        "EUC-KR",
    )

    private val cjkCandidates = setOf("Shift_JIS", "GB18030", "Big5", "EUC-KR")
    private val cyrillicCandidates = setOf("windows-1251", "KOI8-R")

    fun detect(sample: ByteArray): LegacyMatch? {
        if (sample.isEmpty()) return null
        var bestName: String? = null
        var bestScore = Int.MIN_VALUE
        for (name in candidates) {
            val charset = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            val text = decodeOrNull(sample, charset) ?: continue
            val score = score(name, text, sample)
            if (score > bestScore) {
                bestName = name
                bestScore = score
            }
        }
        return bestName?.takeIf { bestScore >= MIN_SCORE }?.let(::LegacyMatch)
    }

    private fun score(name: String, text: CharSequence, sample: ByteArray): Int {
        var score = 50
        var controls = 0
        var highControls = 0
        var letters = 0
        var cjk = 0
        var cyrillic = 0
        for (char in text) {
            when {
                char == '\n' || char == '\r' || char == '\t' -> Unit
                char.code < 0x20 -> controls++
                char.code in 0x7F..0x9F -> highControls++
            }
            if (char.isLetter()) letters++
            if (isCjk(char)) cjk++
            if (char in '\u0400'..'\u04FF') cyrillic++
        }

        score -= minOf(40, controls * 10)
        score -= minOf(30, highControls * 5)

        if (name in cjkCandidates) {
            score += when {
                cjk >= 2 -> minOf(25, 10 + cjk * 2)
                cjk == 1 -> -20
                else -> -45
            }
        } else if (cjk > 0) {
            score -= 10
        }

        if (name in cyrillicCandidates) {
            score = when {
                cyrillic == 0 -> score - 35
                cyrillic * 2 < letters -> score - 15
                else -> score + minOf(20, cyrillic * 40 / maxOf(1, letters))
            }
        } else if (cyrillic > 0 && cyrillic * 2 > letters) {
            score -= 20
        }

        if (name == "windows-1252" || name == "ISO-8859-1") {
            val special = sample.count { (it.toInt() and 0xFF) in 0x80..0x9F }
            score += minOf(15, special * 2)
            if (cyrillic > 0) score -= 10
        }

        score += minOf(10, letters / 20)
        return score.coerceIn(0, 100)
    }

    private fun isCjk(char: Char): Boolean =
        char in '\u3040'..'\u30FF' ||
                char in '\u3400'..'\u4DBF' ||
                char in '\u4E00'..'\u9FFF' ||
                char in '\uAC00'..'\uD7AF' ||
                char in '\uF900'..'\uFAFF'

    private fun decodeOrNull(sample: ByteArray, charset: Charset): String? = try {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(sample)).toString()
    } catch (e: Exception) {
        null
    }
}
