package texteditor.data.encoding

enum class EncodingPickerMode { REOPEN, SAVE }

class EncodingScheme(
    val id: String,
    val label: String,
    val group: String,
    val charsetName: String,
    val bom: ByteArray?,
    val aliases: List<String> = emptyList(),
) {
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        return q.isEmpty() || label.lowercase().contains(q) || id.lowercase()
            .contains(q) || charsetName.lowercase().contains(q) || aliases.any {
            it.lowercase().contains(q)
        }
    }

    override fun equals(other: Any?): Boolean = other is EncodingScheme && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "EncodingScheme($id, $label)"
}

internal object EncodingBoms {
    val UTF_8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    val UTF_16_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    val UTF_16_BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    val UTF_32_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)
    val UTF_32_BE = byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())
}
