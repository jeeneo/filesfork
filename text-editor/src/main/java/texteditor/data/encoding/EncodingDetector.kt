package texteditor.data.encoding

import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class LegacyMatch(
    val charsetName: String,
)

fun interface LegacyDetector {
    fun detect(sample: ByteArray): LegacyMatch?
}

data class EncodingDetection(
    val scheme: EncodingScheme,
    val bomLength: Int,
)

object EncodingDetector {
    const val SAMPLE_SIZE = 64 * 1024

    private const val MIN_UTF16_PAIRS = 4
    private const val MAX_UTF16_PAIRS = 2048

    private val bomOrder = listOf(
        "utf32lebom" to EncodingBoms.UTF_32_LE,
        "utf32bebom" to EncodingBoms.UTF_32_BE,
        "utf8bom" to EncodingBoms.UTF_8,
        "utf16lebom" to EncodingBoms.UTF_16_LE,
        "utf16bebom" to EncodingBoms.UTF_16_BE,
    )

    fun detect(
        sample: ByteArray,
        default: EncodingScheme = EncodingCatalog.default,
        detectLegacy: Boolean = true,
        legacyDetector: LegacyDetector? = null,
    ): EncodingDetection {
        detectByteOrderMark(sample)?.let { return it }
        detectUtf16WithoutBom(sample)?.let { return it }

        if (isValidUtf8(sample)) {
            return EncodingDetection(
                scheme = EncodingCatalog.utf8,
                bomLength = 0,
            )
        }

        if (detectLegacy && sample.any { it < 0 }) {
            val match = legacyDetector?.detect(sample)
            if (match != null) {
                val scheme = EncodingCatalog.forCharset(match.charsetName)
                if (scheme != null && TextEncoder.canDecode(sample, scheme)) {
                    return EncodingDetection(
                        scheme = scheme,
                        bomLength = 0,
                    )
                }
            }

            val scored = LegacyScorer.detect(sample)
            if (scored != null) {
                val scheme = EncodingCatalog.forCharset(scored.charsetName)
                if (scheme != null && TextEncoder.canDecode(sample, scheme)) {
                    return EncodingDetection(
                        scheme = scheme,
                        bomLength = 0,
                    )
                }
            }
        }

        return EncodingDetection(
            scheme = default,
            bomLength = 0,
        )
    }

    fun detect(
        input: BufferedInputStream,
        default: EncodingScheme = EncodingCatalog.default,
        detectLegacy: Boolean = true,
        legacyDetector: LegacyDetector? = null,
    ): EncodingDetection = detect(readSample(input), default, detectLegacy, legacyDetector)

    /** Reads up to [SAMPLE_SIZE] bytes and rewinds [input] for the decoder. */
    fun readSample(input: BufferedInputStream): ByteArray {
        input.mark(SAMPLE_SIZE + 1)
        try {
            val sample = ByteArray(SAMPLE_SIZE)
            var length = 0
            while (length < SAMPLE_SIZE) {
                val read = input.read(sample, length, SAMPLE_SIZE - length)
                if (read < 0) break
                length += read
            }
            return if (length == SAMPLE_SIZE) sample else sample.copyOf(length)
        } finally {
            input.reset()
        }
    }

    private fun detectByteOrderMark(sample: ByteArray): EncodingDetection? {
        for ((id, mark) in bomOrder) {
            if (!sample.startsWithBytes(mark)) continue
            val scheme = EncodingCatalog.byId(id) ?: continue
            return EncodingDetection(
                scheme = scheme,
                bomLength = mark.size,
            )
        }
        return null
    }

    private fun detectUtf16WithoutBom(sample: ByteArray): EncodingDetection? {
        val pairs = sample.size / 2
        if (pairs < MIN_UTF16_PAIRS) return null
        val examined = minOf(pairs, MAX_UTF16_PAIRS)
        var evenZeros = 0
        var oddZeros = 0
        for (pair in 0 until examined) {
            if (sample[pair * 2] == 0.toByte()) evenZeros++
            if (sample[pair * 2 + 1] == 0.toByte()) oddZeros++
        }
        return when {
            oddZeros >= examined * 3 / 4 && evenZeros <= examined / 16 -> {
                val scheme = EncodingCatalog.byId("utf16le") ?: return null
                EncodingDetection(scheme, 0)
            }

            evenZeros >= examined * 3 / 4 && oddZeros <= examined / 16 -> {
                val scheme = EncodingCatalog.byId("utf16be") ?: return null
                EncodingDetection(scheme, 0)
            }

            else -> null
        }
    }

    private fun isValidUtf8(sample: ByteArray): Boolean {
        val decoder = Charset.forName("UTF-8").newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(sample)
        val output = CharBuffer.allocate(4096)
        while (true) {
            val result = decoder.decode(input, output, false)
            when {
                result.isUnderflow -> return true
                result.isError -> return false
                else -> output.clear()
            }
        }
    }
}
