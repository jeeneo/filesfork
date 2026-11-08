package texteditor.data.encoding

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class UnencodableCharacterException(
    val encodingLabel: String,
    cause: Throwable? = null,
) : Exception("Some characters cannot be saved as $encodingLabel", cause)

object TextEncoder {
    fun encode(
        text: String,
        scheme: EncodingScheme,
        lineEnding: LineEnding? = null,
    ): ByteArray {
        val body = if (lineEnding != null) LineEndings.convert(text, lineEnding) else text
        val encoder = Charset.forName(scheme.charsetName).newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val encoded = try {
            encoder.encode(CharBuffer.wrap(body))
        } catch (e: CharacterCodingException) {
            throw UnencodableCharacterException(scheme.label, e)
        }
        val bytes = ByteArray(encoded.remaining())
        encoded.get(bytes)
        val bom = scheme.bom
        return if (bom == null) bytes else bom + bytes
    }

    fun decode(bytes: ByteArray, scheme: EncodingScheme, bomLength: Int = 0): String {
        val decoder = Charset.forName(scheme.charsetName).newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes, bomLength, bytes.size - bomLength)).toString()
    }

    fun canDecode(bytes: ByteArray, scheme: EncodingScheme): Boolean {
        val bomLength = scheme.bomLengthIn(bytes)
        return runCatching { decode(bytes, scheme, bomLength) }.isSuccess
    }
}
