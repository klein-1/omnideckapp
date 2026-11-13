package com.example.lib.dev

import com.example.lib.calc.EvalResult
import com.example.lib.calc.ExpressionEvaluator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class RegexTestResult(
    val valid: Boolean,
    val error: String = "",
    val matchCount: Int = 0,
    val matches: List<String> = emptyList(),
    val replacedText: String = ""
)

object DevSuiteEngines {

    fun testRegex(pattern: String, input: String, replacement: String, ignoreCase: Boolean, multiline: Boolean): RegexTestResult {
        if (pattern.isEmpty()) return RegexTestResult(true, matchCount = 0, replacedText = input)
        return try {
            val opts = buildSet {
                if (ignoreCase) add(RegexOption.IGNORE_CASE)
                if (multiline) add(RegexOption.MULTILINE)
            }
            val rx = Regex(pattern, opts)
            val all = rx.findAll(input).toList()
            val items = all.take(50).mapIndexed { i, m ->
                val groups = m.groupValues.drop(1)
                if (groups.isEmpty()) "#${i + 1} [${m.range.first}..${m.range.last}]: ${m.value}"
                else "#${i + 1} [${m.range.first}..${m.range.last}]: ${m.value}  groups=${groups}"
            }
            RegexTestResult(
                valid = true,
                matchCount = all.size,
                matches = items,
                replacedText = rx.replace(input, replacement)
            )
        } catch (e: Exception) {
            RegexTestResult(valid = false, error = e.message ?: "Invalid regex")
        }
    }

    fun evaluateScriptSandbox(script: String, degreesMode: Boolean = true): List<String> {
        val env = mutableMapOf<String, Double>()
        val output = mutableListOf<String>()
        script.lines().forEachIndexed { idx, raw ->
            val line = raw.substringBefore("//").trim()
            if (line.isEmpty()) return@forEachIndexed
            val assignMatch = Regex("^([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.+)$").find(line)
            val exprRaw = assignMatch?.groupValues?.get(2) ?: line
            var substituted = exprRaw
            // Replace known variables longest-first
            env.keys.sortedByDescending { it.length }.forEach { k ->
                substituted = Regex("\\b$k\\b").replace(substituted, env[k].toString())
            }
            when (val res = ExpressionEvaluator.evaluate(substituted, degreesMode)) {
                is EvalResult.Success -> {
                    if (assignMatch != null) {
                        val varName = assignMatch.groupValues[1]
                        env[varName] = res.value
                        output += "$varName = ${res.formatted}"
                    } else {
                        output += "Line ${idx + 1} → ${res.formatted}"
                    }
                }
                is EvalResult.Error -> output += "Line ${idx + 1} Error: ${res.message}"
            }
        }
        return output
    }

    fun describeAndPreviewCron(expr: String): Pair<String, List<String>> {
        val parts = expr.trim().split(Regex("\\s+"))
        if (parts.size != 5) return "Invalid cron (need 5 fields: min hour dom month dow)" to emptyList()
        val (minF, hourF, domF, monF, dowF) = parts
        val summary = "Minute=$minF · Hour=$hourF · Day=$domF · Month=$monF · Weekday=$dowF"

        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1)
        }
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm", Locale.US)
        val nextRuns = mutableListOf<String>()
        var iterations = 0
        while (nextRuns.size < 5 && iterations < 50_000) {
            iterations++
            val m = cal.get(Calendar.MINUTE)
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val dom = cal.get(Calendar.DAY_OF_MONTH)
            val mon = cal.get(Calendar.MONTH) + 1
            val dow = (cal.get(Calendar.DAY_OF_WEEK) - 1) // 0=Sun..6=Sat
            if (matchesCronField(minF, m, 0..59) &&
                matchesCronField(hourF, h, 0..23) &&
                matchesCronField(domF, dom, 1..31) &&
                matchesCronField(monF, mon, 1..12) &&
                matchesCronField(dowF, dow, 0..6)
            ) {
                nextRuns += fmt.format(cal.time)
            }
            cal.add(Calendar.MINUTE, 1)
        }
        return summary to nextRuns
    }

    private fun matchesCronField(field: String, value: Int, range: IntRange): Boolean {
        if (field == "*") return true
        return field.split(",").any { token ->
            when {
                token.startsWith("*/") -> {
                    val step = token.removePrefix("*/").toIntOrNull()?.coerceAtLeast(1) ?: return@any false
                    (value - range.first) % step == 0
                }
                "-" in token -> {
                    val a = token.substringBefore("-").toIntOrNull() ?: return@any false
                    val b = token.substringAfter("-").toIntOrNull() ?: return@any false
                    value in a..b
                }
                else -> token.toIntOrNull() == value
            }
        }
    }
}
