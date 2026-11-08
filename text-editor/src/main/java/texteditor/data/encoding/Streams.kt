package texteditor.data.encoding

import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

fun EncodingScheme.bomLengthIn(sample: ByteArray): Int {
    val mark = bom ?: return 0
    return if (sample.startsWithBytes(mark)) mark.size else 0
}

fun InputStream.readerFor(
    scheme: EncodingScheme,
    bomLength: Int,
    strict: Boolean = true,
): Reader {
    repeat(bomLength) { read() }
    val decoder = Charset.forName(scheme.charsetName).newDecoder()
    if (strict) {
        decoder.onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
    }
    return InputStreamReader(this, decoder)
}

internal class RecordingReader(
    private val delegate: Reader,
    private val limit: Int,
) : Reader() {
    private val builder = StringBuilder()

    val recorded: String
        get() = builder.toString()

    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        val count = delegate.read(buffer, offset, length)
        if (count > 0 && builder.length < limit) {
            builder.appendRange(buffer, offset, offset + minOf(count, limit - builder.length))
        }
        return count
    }

    override fun close() {
        delegate.close()
    }
}

internal fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (index in prefix.indices) {
        if (this[index] != prefix[index]) return false
    }
    return true
}
