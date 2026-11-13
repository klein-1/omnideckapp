package com.example.lib.text

enum class CaseStyle(val id: String, val label: String, val category: String) {
    UPPER("upper", "UPPERCASE", "Standard"),
    LOWER("lower", "lowercase", "Standard"),
    SENTENCE("sentence", "Sentence case", "Standard"),
    TITLE_AP("title_ap", "Title Case (AP)", "Editorial"),
    TITLE_CHICAGO("title_chicago", "Title Case (Chicago)", "Editorial"),
    TITLE_APA("title_apa", "Title Case (APA)", "Editorial"),
    CAMEL("camel", "camelCase", "Developer"),
    PASCAL("pascal", "PascalCase", "Developer"),
    SNAKE("snake", "snake_case", "Developer"),
    KEBAB("kebab", "kebab-case", "Developer"),
    CONSTANT("constant", "CONSTANT_CASE", "Developer"),
    DOT("dot", "dot.case", "Developer"),
    PATH("path", "path/case", "Developer"),
    ALTERNATING("alternating", "aLtErNaTiNg", "Stylised"),
    INVERSE("inverse", "iNVERSE cASE", "Stylised")
}

data class TextMetrics(
    val chars: Int,
    val charsNoSpaces: Int,
    val words: Int,
    val sentences: Int,
    val lines: Int,
    val bytesUtf8: Int,
    val readingSeconds: Int
)

object CaseConverterEngine {
    private val AP_MINOR_WORDS = setOf(
        "a", "an", "and", "at", "but", "by", "for", "in", "nor", "of", "on", "or", "so", "the", "to", "up", "yet"
    )
    private val CHICAGO_MINOR_WORDS = AP_MINOR_WORDS + setOf(
        "as", "if", "into", "from", "off", "onto", "out", "over", "past", "per", "than", "till", "upon", "via", "with"
    )

    fun convert(input: String, style: CaseStyle): String {
        if (input.isEmpty()) return ""
        return when (style) {
            CaseStyle.UPPER -> input.uppercase()
            CaseStyle.LOWER -> input.lowercase()
            CaseStyle.SENTENCE -> toSentenceCase(input)
            CaseStyle.TITLE_AP -> toEditorialTitleCase(input, minCapsLength = 4, minorSet = AP_MINOR_WORDS)
            CaseStyle.TITLE_CHICAGO -> toEditorialTitleCase(input, minCapsLength = 5, minorSet = CHICAGO_MINOR_WORDS)
            CaseStyle.TITLE_APA -> toEditorialTitleCase(input, minCapsLength = 4, minorSet = CHICAGO_MINOR_WORDS)
            CaseStyle.CAMEL -> {
                val words = splitIdentifierWords(input)
                words.mapIndexed { idx, w ->
                    if (idx == 0) w.lowercase() else capitalizeWord(w.lowercase())
                }.joinToString("")
            }
            CaseStyle.PASCAL -> splitIdentifierWords(input).joinToString("") { capitalizeWord(it.lowercase()) }
            CaseStyle.SNAKE -> splitIdentifierWords(input).joinToString("_") { it.lowercase() }
            CaseStyle.KEBAB -> splitIdentifierWords(input).joinToString("-") { it.lowercase() }
            CaseStyle.CONSTANT -> splitIdentifierWords(input).joinToString("_") { it.uppercase() }
            CaseStyle.DOT -> splitIdentifierWords(input).joinToString(".") { it.lowercase() }
            CaseStyle.PATH -> splitIdentifierWords(input).joinToString("/") { it.lowercase() }
            CaseStyle.ALTERNATING -> {
                var upper = false
                buildString(input.length) {
                    for (ch in input) {
                        if (ch.isLetter()) {
                            append(if (upper) ch.uppercaseChar() else ch.lowercaseChar())
                            upper = !upper
                        } else {
                            append(ch)
                        }
                    }
                }
            }
            CaseStyle.INVERSE -> buildString(input.length) {
                for (ch in input) {
                    when {
                        ch.isUpperCase() -> append(ch.lowercaseChar())
                        ch.isLowerCase() -> append(ch.uppercaseChar())
                        else -> append(ch)
                    }
                }
            }
        }
    }

    fun analyze(input: String): TextMetrics {
        if (input.isEmpty()) return TextMetrics(0, 0, 0, 0, 0, 0, 0)
        val chars = input.length
        val noSpaces = input.count { !it.isWhitespace() }
        val words = Regex("\\S+").findAll(input).count()
        val sentences = Regex("[.!?]+(\\s|$)").findAll(input).count().coerceAtLeast(if (words > 0) 1 else 0)
        val lines = input.lines().size
        val bytes = input.encodeToByteArray().size
        val readingSec = ((words / 220.0) * 60.0).toInt().coerceAtLeast(if (words > 0) 1 else 0)
        return TextMetrics(chars, noSpaces, words, sentences, lines, bytes, readingSec)
    }

    private fun toSentenceCase(input: String): String {
        val sb = StringBuilder(input.length)
        var capNext = true
        for (ch in input) {
            if (ch.isLetter()) {
                sb.append(if (capNext) ch.uppercaseChar() else ch.lowercaseChar())
                capNext = false
            } else {
                sb.append(ch)
                if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                    capNext = true
                }
            }
        }
        return sb.toString()
    }

    private fun toEditorialTitleCase(input: String, minCapsLength: Int, minorSet: Set<String>): String {
        return input.lines().joinToString("\n") { line ->
            val tokens = line.split(Regex("(\\s+)"))
            val spaces = Regex("\\s+").findAll(line).map { it.value }.toList()
            val nonBlankIndices = tokens.indices.filter { tokens[it].isNotEmpty() }
            val firstIdx = nonBlankIndices.firstOrNull() ?: -1
            val lastIdx = nonBlankIndices.lastOrNull() ?: -1

            val converted = tokens.mapIndexed { idx, rawWord ->
                val clean = rawWord.trim { !it.isLetterOrDigit() }.lowercase()
                val forceCap = idx == firstIdx || idx == lastIdx ||
                    (idx > 0 && tokens[idx - 1].endsWith(":")) ||
                    clean.length >= minCapsLength ||
                    !minorSet.contains(clean)
                if (forceCap) capitalizeWord(rawWord.lowercase()) else rawWord.lowercase()
            }
            buildString {
                for (i in converted.indices) {
                    append(converted[i])
                    if (i < spaces.size) append(spaces[i])
                }
            }
        }
    }

    private fun capitalizeWord(word: String): String {
        val idx = word.indexOfFirst { it.isLetter() }
        if (idx < 0) return word
        return word.substring(0, idx) + word[idx].uppercaseChar() + word.substring(idx + 1)
    }

    private fun splitIdentifierWords(input: String): List<String> {
        val normalized = input
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
            .replace(Regex("[^A-Za-z0-9]+"), " ")
            .trim()
        if (normalized.isEmpty()) return emptyList()
        return normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
}
