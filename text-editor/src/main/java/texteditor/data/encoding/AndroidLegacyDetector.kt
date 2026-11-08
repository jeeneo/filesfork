package texteditor.data.encoding

import android.annotation.SuppressLint

@SuppressLint("PrivateApi")
internal object AndroidLegacyDetector : LegacyDetector {
    private const val MIN_CONFIDENCE = 50

    private val detectorClass: Class<*>? by lazy {
        runCatching { Class.forName("android.icu.text.CharsetDetector") }.getOrNull()
    }

    val isAvailable: Boolean
        get() = detectorClass != null

    override fun detect(sample: ByteArray): LegacyMatch? {
        val type = detectorClass ?: return null
        return runCatching {
            val detector = type.getDeclaredConstructor().newInstance()
            type.getMethod("setText", ByteArray::class.java).invoke(detector, sample)
            val match = type.getMethod("detect").invoke(detector) ?: return null
            val matchType = match.javaClass
            val name = matchType.getMethod("getName").invoke(match) as? String ?: return null
            val confidence =
                matchType.getMethod("getConfidence").invoke(match) as? Int ?: return null
            if (confidence < MIN_CONFIDENCE) return null
            LegacyMatch(name)
        }.getOrNull()
    }
}

