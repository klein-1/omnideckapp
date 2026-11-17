package com.example.services.gemini

import kotlin.math.ceil

enum class AiToneMode(val label: String) {
    CONCISE("Concise"),
    PROFESSIONAL("Professional"),
    FRIENDLY("Friendly"),
    BULLETS("Bullet Summary"),
    FIX_GRAMMAR("Polish & Fix")
}

data class AiStudioResult(
    val outputText: String,
    val metricsLabel: String,
    val isLiveCloudResponse: Boolean
)

/**
 * Repository abstraction for Omni Deck AI Studio tools (Writer, Prompt Lab, Code Explainer).
 * Currently backed by [LocalAiFallbackRepository] because Firebase AI Logic setup was declined.
 */
interface AiStudioRepository {
    suspend fun rewriteText(input: String, tone: AiToneMode): AiStudioResult
    suspend fun evaluatePrompt(template: String, variables: Map<String, String>): AiStudioResult
    suspend fun explainCodeOrPattern(snippet: String, kind: String): AiStudioResult
}

object AiStudioProvider {
    val repository: AiStudioRepository = LocalAiFallbackRepository()
}

class LocalAiFallbackRepository : AiStudioRepository {

    override suspend fun rewriteText(input: String, tone: AiToneMode): AiStudioResult {
        val clean = input.trim()
        if (clean.isEmpty()) {
            return AiStudioResult("Enter source text to preview ${tone.label.lowercase()} transformation.", "0 tokens est.", false)
        }
        val words = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val sentences = clean.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val tokensEst = ceil(clean.length / 4.0).toInt()
        val readSec = (words.size * 60 / 220).coerceAtLeast(1)

        val sampleDraft = when (tone) {
            AiToneMode.CONCISE -> {
                val lead = sentences.take(2).joinToString(" ").ifBlank { clean.take(180) }
                "$lead"
            }
            AiToneMode.PROFESSIONAL -> {
                "Summary Note:\n" + sentences.joinToString("\n") { s ->
                    "• ${s.trim().replaceFirstChar { it.uppercaseChar() }}"
                }
            }
            AiToneMode.FRIENDLY -> {
                "Hey there — here's a clear rundown:\n\n${sentences.joinToString(" ")}"
            }
            AiToneMode.BULLETS -> {
                sentences.mapIndexed { idx, s -> "${idx + 1}. ${s.trim()}" }.joinToString("\n")
            }
            AiToneMode.FIX_GRAMMAR -> {
                clean.replace(Regex("\\s+"), " ")
                    .replace(Regex("\\s+([,.!?])"), "$1")
                    .replaceFirstChar { it.uppercaseChar() }
            }
        }

        val out = buildString {
            appendLine("[Sample AI Preview · ${tone.label} · Firebase AI Logic Disabled]")
            appendLine()
            appendLine(sampleDraft)
            appendLine()
            appendLine("---")
            append("On-Device Stats: ${words.size} words · ${sentences.size} sentences · ~${readSec}s read · ~$tokensEst tokens")
        }
        return AiStudioResult(out.trim(), "${words.size} words · ~$tokensEst tokens", false)
    }

    override suspend fun evaluatePrompt(template: String, variables: Map<String, String>): AiStudioResult {
        var resolved = template
        variables.forEach { (k, v) ->
            resolved = resolved.replace("{{$k}}", v.ifBlank { "<$k>" })
        }
        val remainingVars = Regex("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}").findAll(resolved).map { it.groupValues[1] }.distinct().toList()
        val tokenEst = ceil(resolved.length / 4.0).toInt()
        val hasRole = resolved.contains("you are", ignoreCase = true) || resolved.contains("role:", ignoreCase = true)
        val hasFormat = resolved.contains("json", ignoreCase = true) || resolved.contains("format", ignoreCase = true) || resolved.contains("bullet", ignoreCase = true)

        val out = buildString {
            appendLine("[Compiled Prompt · Firebase AI Logic Disabled for Live Completion]")
            appendLine()
            appendLine(resolved)
            appendLine()
            appendLine("---")
            appendLine("Readiness Check:")
            appendLine("• Estimated Input Tokens: ~$tokenEst")
            appendLine("• Role / Persona Specified: ${if (hasRole) "Yes" else "Tip: add 'You are a ...'"}")
            appendLine("• Output Format Constrained: ${if (hasFormat) "Yes" else "Tip: specify JSON, table, or word cap"}")
            if (remainingVars.isNotEmpty()) {
                appendLine("• Unfilled Variables: ${remainingVars.joinToString(", ")}")
            }
        }
        return AiStudioResult(out.trim(), "~$tokenEst tokens · ${remainingVars.size} unfilled vars", false)
    }

    override suspend fun explainCodeOrPattern(snippet: String, kind: String): AiStudioResult {
        val clean = snippet.trim()
        if (clean.isEmpty()) {
            return AiStudioResult("Paste code, Regex, or SQL to analyze.", "Empty input", false)
        }
        val findings = mutableListOf<String>()
        when (kind.uppercase()) {
            "REGEX" -> {
                if (clean.contains("\\d")) findings += "• \\d — matches any ASCII digit [0-9]"
                if (clean.contains("\\w")) findings += "• \\w — matches word char [A-Za-z0-9_]"
                if (clean.contains("\\s")) findings += "• \\s — matches whitespace (space, tab, newline)"
                if (clean.contains("+")) findings += "• + — quantifier: 1 or more repetitions"
                if (clean.contains("*")) findings += "• * — quantifier: 0 or more repetitions"
                if (clean.contains("?")) findings += "• ? — optional quantifier (or lazy modifier)"
                if (clean.contains("^") || clean.contains("$")) findings += "• ^ / $ — start / end anchor"
                if (clean.contains("(")) findings += "• (...) — capturing or non-capturing group"
                if (clean.contains("[")) findings += "• [...] — character class set"
                if (findings.isEmpty()) findings += "• Literal sequence match (${clean.length} chars)"
            }
            "SQL" -> {
                val upper = clean.uppercase()
                if ("SELECT" in upper) findings += "• SELECT projection reads specified columns"
                if ("FROM" in upper) findings += "• FROM selects primary source table/view"
                if ("JOIN" in upper) findings += "• JOIN combines rows across related tables"
                if ("WHERE" in upper) findings += "• WHERE filters rows before grouping"
                if ("GROUP BY" in upper) findings += "• GROUP BY aggregates rows by key"
                if ("ORDER BY" in upper) findings += "• ORDER BY sorts final result set"
                if (("DELETE" in upper || "UPDATE" in upper) && "WHERE" !in upper) {
                    findings += "• ⚠ Safety Alert: UPDATE/DELETE without WHERE affects every row!"
                }
                if (findings.isEmpty()) findings += "• SQL statement (${clean.lines().size} lines)"
            }
            else -> {
                val lines = clean.lines()
                findings += "• Line count: ${lines.size} lines (${clean.length} chars)"
                if ("fun " in clean || "function " in clean || "def " in clean) findings += "• Defines reusable function(s)"
                if ("class " in clean || "data class " in clean || "interface " in clean) findings += "• Declares data model / type contract"
                if ("for (" in clean || "while (" in clean || ".forEach" in clean || ".map" in clean) findings += "• Iterates over collection or loop condition"
                if ("try" in clean || "catch" in clean || "runCatching" in clean) findings += "• Includes structured error handling"
                if ("suspend " in clean || "async " in clean || "await " in clean) findings += "• Performs asynchronous/non-blocking execution"
            }
        }

        val out = buildString {
            appendLine("[Structural Analysis · Sample AI Preview · Firebase Required for Live LLM]")
            appendLine("Mode: $kind")
            appendLine()
            findings.forEach { appendLine(it) }
            appendLine()
            append("Estimated Context Footprint: ~${ceil(clean.length / 4.0).toInt()} tokens")
        }
        return AiStudioResult(out.trim(), "${findings.size} insights · $kind", false)
    }
}
