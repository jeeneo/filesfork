package texteditor.data.encoding

import java.nio.charset.Charset

object EncodingCatalog {
    private const val DEFAULT_ID = "utf8"
    private const val WINDOWS_1252_ID = "windows-1252"

    val schemes: List<EncodingScheme> = buildList {
        addEncoding("utf8", "UTF-8", UNICODE, "UTF-8", aliases = listOf("utf8", "utf-8", "cp65001"))
        addEncoding(
            "utf8bom", "UTF-8 with BOM", UNICODE, "UTF-8", EncodingBoms.UTF_8,
            aliases = listOf("utf8bom", "utf-8 with bom", "utf8 with bom"),
        )
        addEncoding(
            "utf16le",
            "UTF-16 LE",
            UNICODE,
            "UTF-16LE",
            aliases = listOf("utf16le", "utf-16le")
        )
        addEncoding(
            "utf16lebom", "UTF-16 LE with BOM", UNICODE, "UTF-16LE", EncodingBoms.UTF_16_LE,
            aliases = listOf("utf16lebom", "utf-16le with bom"),
        )
        addEncoding(
            "utf16be",
            "UTF-16 BE",
            UNICODE,
            "UTF-16BE",
            aliases = listOf("utf16be", "utf-16be")
        )
        addEncoding(
            "utf16bebom", "UTF-16 BE with BOM", UNICODE, "UTF-16BE", EncodingBoms.UTF_16_BE,
            aliases = listOf("utf16bebom", "utf-16be with bom"),
        )
        addEncoding(
            "utf32le",
            "UTF-32 LE",
            UNICODE,
            "UTF-32LE",
            aliases = listOf("utf32le", "utf-32le")
        )
        addEncoding(
            "utf32lebom", "UTF-32 LE with BOM", UNICODE, "UTF-32LE", EncodingBoms.UTF_32_LE,
            aliases = listOf("utf32lebom", "utf-32le with bom"),
        )
        addEncoding(
            "utf32be",
            "UTF-32 BE",
            UNICODE,
            "UTF-32BE",
            aliases = listOf("utf32be", "utf-32be")
        )
        addEncoding(
            "utf32bebom", "UTF-32 BE with BOM", UNICODE, "UTF-32BE", EncodingBoms.UTF_32_BE,
            aliases = listOf("utf32bebom", "utf-32be with bom"),
        )

        addEncoding(
            "us-ascii",
            "US-ASCII",
            WESTERN,
            "US-ASCII",
            aliases = listOf("ascii", "usascii")
        )

        for (number in 1..16) {
            if (number == 12) continue // ISO-8859-12 was never published.
            val name = "ISO-8859-$number"
            addEncoding(name.lowercase(), name, groupForIso8859(number), name)
        }
        for (number in 1250..1258) {
            val name = "windows-$number"
            addEncoding(
                name,
                "Windows-$number",
                groupForWindows(number),
                name,
                aliases = listOf("cp$number")
            )
        }

        for (number in listOf(
            437,
            850,
            852,
            855,
            857,
            858,
            860,
            861,
            862,
            863,
            865,
            866,
            869,
            720,
            737,
            872
        )) {
            val name = "IBM$number"
            addEncoding(name.lowercase(), name, DOS_OEM, name, aliases = listOf("cp$number"))
        }

        addEncoding("koi8-r", "KOI8-R", CYRILLIC, "KOI8-R", aliases = listOf("koi8r"))
        addEncoding("koi8-u", "KOI8-U", CYRILLIC, "KOI8-U", aliases = listOf("koi8u"))
        addEncoding(
            "x-macroman",
            "Macintosh (MacRoman)",
            MACINTOSH,
            "x-MacRoman",
            aliases = listOf("macroman", "macintosh")
        )

        addEncoding(
            "shift_jis", "Shift JIS", JAPANESE, "Shift_JIS",
            aliases = listOf("sjis", "shift-jis", "shiftjis", "cp932", "windows-31j"),
        )
        addEncoding("euc-jp", "EUC-JP", JAPANESE, "EUC-JP", aliases = listOf("eucjp"))
        addEncoding(
            "iso-2022-jp",
            "ISO-2022-JP",
            JAPANESE,
            "ISO-2022-JP",
            aliases = listOf("iso2022jp")
        )

        addEncoding("gb2312", "GB2312", CHINESE, "GB2312", aliases = listOf("euc-cn", "gb_2312-80"))
        addEncoding("gbk", "GBK", CHINESE, "GBK", aliases = listOf("cp936"))
        addEncoding("gb18030", "GB18030", CHINESE, "GB18030", aliases = listOf("gb-18030"))
        addEncoding("big5", "Big5", CHINESE, "Big5", aliases = listOf("cp950"))
        addEncoding(
            "big5-hkscs",
            "Big5-HKSCS",
            CHINESE,
            "Big5-HKSCS",
            aliases = listOf("big5hkscs")
        )

        addEncoding(
            "euc-kr",
            "EUC-KR",
            KOREAN,
            "EUC-KR",
            aliases = listOf("cp949", "euckr", "ks_c_5601-1987")
        )
        addEncoding(
            "iso-2022-kr",
            "ISO-2022-KR",
            KOREAN,
            "ISO-2022-KR",
            aliases = listOf("iso2022kr")
        )
    }.distinctBy { it.id }

    /** UTF-8 without BOM; also the scheme used for new documents unless the prefs say otherwise. */
    val default: EncodingScheme = schemes.firstOrNull { it.id == DEFAULT_ID } ?: schemes.first()

    /** Lossless for every byte value, used as the last-resort decode fallback. */
    val windows1252: EncodingScheme = schemes.firstOrNull { it.id == WINDOWS_1252_ID } ?: default

    val utf8: EncodingScheme
        get() = default

    fun byId(id: String?): EncodingScheme? =
        if (id == null) null else schemes.firstOrNull { it.id == id }

    /** Finds the scheme for a charset name or alias, preferring the variant without a BOM. */
    fun forCharset(name: String?): EncodingScheme? {
        if (name.isNullOrBlank()) return null
        val canonical = runCatching { Charset.forName(name).name() }.getOrNull()
        val target = canonical ?: name
        return schemes.firstOrNull { it.charsetName.equals(target, ignoreCase = true) }
            ?: schemes.firstOrNull { scheme ->
                scheme.aliases.any { it.equals(name, ignoreCase = true) }
            }
    }

    fun grouped(): List<Pair<String, List<EncodingScheme>>> =
        schemes.groupBy { it.group }.map { (group, entries) -> group to entries }

    private fun MutableList<EncodingScheme>.addEncoding(
        id: String,
        label: String,
        group: String,
        charsetName: String,
        bom: ByteArray? = null,
        aliases: List<String> = emptyList(),
    ) {
        val charset = runCatching { Charset.forName(charsetName) }.getOrNull() ?: return
        add(EncodingScheme(id, label, group, charset.name(), bom, aliases))
    }

    private fun groupForIso8859(number: Int): String = when (number) {
        5, 7 -> CYRILLIC
        else -> WESTERN
    }

    private fun groupForWindows(number: Int): String = when (number) {
        1251 -> CYRILLIC
        else -> WESTERN
    }

    private const val UNICODE = "Unicode"
    private const val WESTERN = "Western European"
    private const val CYRILLIC = "Cyrillic"
    private const val JAPANESE = "Japanese"
    private const val CHINESE = "Chinese"
    private const val KOREAN = "Korean"
    private const val DOS_OEM = "DOS / OEM"
    private const val MACINTOSH = "Macintosh"
}
