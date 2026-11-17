package texteditor.server.http

// SPDX-License-Identifier: GPL-3.0-or-later

fun String.escapeJson(): String =
    buildString {
        this@escapeJson.forEach { character ->
            when (character) {
                '\\' -> {
                    append("\\\\")
                }

                '"' -> {
                    append("\\\"")
                }

                '\b' -> {
                    append("\\b")
                }

                '\u000C' -> {
                    append("\\f")
                }

                '\n' -> {
                    append("\\n")
                }

                '\r' -> {
                    append("\\r")
                }

                '\t' -> {
                    append("\\t")
                }

                '<' -> {
                    append("\\u003c")
                }

                '>' -> {
                    append("\\u003e")
                }

                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
    }

fun jsonString(value: String): String = "\"${value.escapeJson()}\""

fun jsonObject(vararg fields: Pair<String, String>): String =
    fields.joinToString(separator = ",", prefix = "{", postfix = "}") {
        "${jsonString(it.first)}:${it.second}"
    }

fun jsonArray(values: List<String>): String =
    values.joinToString(separator = ",", prefix = "[", postfix = "]")
