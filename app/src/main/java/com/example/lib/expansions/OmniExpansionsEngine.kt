package com.example.lib.expansions

import com.example.lib.dev.Base32HexEngine
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

// ==================== 1. JSON DIFF, SCHEMA & TYPE CODEGEN ====================

enum class JsonDiffKind { ADDED, REMOVED, CHANGED, TYPE_CHANGED }

data class JsonDiffEntry(
    val path: String,
    val kind: JsonDiffKind,
    val leftValue: String?,
    val rightValue: String?
)

data class JsonDiffResult(
    val valid: Boolean,
    val error: String = "",
    val entries: List<JsonDiffEntry> = emptyList()
)

private sealed interface JVal {
    data object JNull : JVal
    data class JBool(val v: Boolean) : JVal
    data class JNum(val raw: String, val isIntegral: Boolean) : JVal
    data class JStr(val v: String) : JVal
    data class JArr(val items: List<JVal>) : JVal
    data class JObj(val fields: LinkedHashMap<String, JVal>) : JVal
}

private class PureJsonParser(private val src: String) {
    private var pos = 0

    fun parseRoot(): JVal {
        skipWs()
        val v = parseVal()
        skipWs()
        if (pos < src.length) throw IllegalArgumentException("Unexpected trailing char at $pos")
        if (v !is JVal.JObj && v !is JVal.JArr) {
            throw IllegalArgumentException("Root must be JSON object {...} or array [...]")
        }
        return v
    }

    private fun skipWs() {
        while (pos < src.length && src[pos] in " \t\n\r") pos++
    }

    private fun parseVal(): JVal {
        skipWs()
        if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
        return when (val ch = src[pos]) {
            '{' -> parseObj()
            '[' -> parseArr()
            '"' -> JVal.JStr(parseStr())
            't', 'f' -> parseBool()
            'n' -> parseNull()
            '-', in '0'..'9' -> parseNum()
            else -> throw IllegalArgumentException("Unexpected char '$ch' at $pos")
        }
    }

    private fun parseObj(): JVal.JObj {
        pos++ // '{'
        val map = LinkedHashMap<String, JVal>()
        skipWs()
        if (pos < src.length && src[pos] == '}') {
            pos++
            return JVal.JObj(map)
        }
        while (true) {
            skipWs()
            if (pos >= src.length || src[pos] != '"') throw IllegalArgumentException("Expected key string at $pos")
            val key = parseStr()
            skipWs()
            if (pos >= src.length || src[pos] != ':') throw IllegalArgumentException("Expected ':' at $pos")
            pos++
            map[key] = parseVal()
            skipWs()
            if (pos >= src.length) throw IllegalArgumentException("Unterminated object")
            when (src[pos]) {
                ',' -> pos++
                '}' -> { pos++; return JVal.JObj(map) }
                else -> throw IllegalArgumentException("Expected ',' or '}' at $pos")
            }
        }
    }

    private fun parseArr(): JVal.JArr {
        pos++ // '['
        val list = mutableListOf<JVal>()
        skipWs()
        if (pos < src.length && src[pos] == ']') {
            pos++
            return JVal.JArr(list)
        }
        while (true) {
            list.add(parseVal())
            skipWs()
            if (pos >= src.length) throw IllegalArgumentException("Unterminated array")
            when (src[pos]) {
                ',' -> pos++
                ']' -> { pos++; return JVal.JArr(list) }
                else -> throw IllegalArgumentException("Expected ',' or ']' at $pos")
            }
        }
    }

    private fun parseStr(): String {
        pos++ // '"'
        val sb = StringBuilder()
        while (pos < src.length) {
            val ch = src[pos++]
            when (ch) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= src.length) throw IllegalArgumentException("Unterminated escape")
                    when (val esc = src[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = src.substring(pos, (pos + 4).coerceAtMost(src.length))
                            pos += hex.length
                            val code = hex.toIntOrNull(16)
                            sb.append(if (code != null) Char(code) else '?')
                        }
                        else -> sb.append(esc)
                    }
                }
                else -> sb.append(ch)
            }
        }
        throw IllegalArgumentException("Unterminated string")
    }

    private fun parseBool(): JVal.JBool = when {
        src.startsWith("true", pos) -> { pos += 4; JVal.JBool(true) }
        src.startsWith("false", pos) -> { pos += 5; JVal.JBool(false) }
        else -> throw IllegalArgumentException("Invalid boolean at $pos")
    }

    private fun parseNull(): JVal.JNull {
        if (!src.startsWith("null", pos)) throw IllegalArgumentException("Invalid null at $pos")
        pos += 4
        return JVal.JNull
    }

    private fun parseNum(): JVal.JNum {
        val start = pos
        while (pos < src.length && src[pos] in "-+0123456789.eE") pos++
        val raw = src.substring(start, pos)
        val num = raw.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number '$raw'")
        val isInt = !raw.contains('.') && !raw.contains('e', true) && num.isFinite()
        return JVal.JNum(raw, isInt)
    }
}

object JsonDiffSchemaEngine {

    fun diffJson(leftRaw: String, rightRaw: String): JsonDiffResult {
        val left = runCatching { PureJsonParser(leftRaw.trim()).parseRoot() }.getOrElse {
            return JsonDiffResult(false, "Left JSON error: ${it.message}")
        }
        val right = runCatching { PureJsonParser(rightRaw.trim()).parseRoot() }.getOrElse {
            return JsonDiffResult(false, "Right JSON error: ${it.message}")
        }
        val out = mutableListOf<JsonDiffEntry>()
        diffValues("$", left, right, out)
        return JsonDiffResult(true, entries = out)
    }

    private fun diffValues(path: String, left: JVal?, right: JVal?, out: MutableList<JsonDiffEntry>) {
        if (left == null && right == null) return
        if (left == null) {
            out += JsonDiffEntry(path, JsonDiffKind.ADDED, null, formatVal(right!!))
            return
        }
        if (right == null) {
            out += JsonDiffEntry(path, JsonDiffKind.REMOVED, formatVal(left), null)
            return
        }
        if (left is JVal.JObj && right is JVal.JObj) {
            val allKeys = (left.fields.keys + right.fields.keys).sorted()
            for (k in allKeys) {
                val p = "$path.$k"
                val lv = left.fields[k]
                val rv = right.fields[k]
                when {
                    lv == null -> out += JsonDiffEntry(p, JsonDiffKind.ADDED, null, formatVal(rv!!))
                    rv == null -> out += JsonDiffEntry(p, JsonDiffKind.REMOVED, formatVal(lv), null)
                    else -> diffValues(p, lv, rv, out)
                }
            }
            return
        }
        if (left is JVal.JArr && right is JVal.JArr) {
            val maxLen = maxOf(left.items.size, right.items.size)
            for (i in 0 until maxLen) {
                val p = "$path[$i]"
                when {
                    i >= left.items.size -> out += JsonDiffEntry(p, JsonDiffKind.ADDED, null, formatVal(right.items[i]))
                    i >= right.items.size -> out += JsonDiffEntry(p, JsonDiffKind.REMOVED, formatVal(left.items[i]), null)
                    else -> diffValues(p, left.items[i], right.items[i], out)
                }
            }
            return
        }
        if (left::class != right::class) {
            out += JsonDiffEntry(path, JsonDiffKind.TYPE_CHANGED, formatVal(left), formatVal(right))
        } else if (formatVal(left) != formatVal(right)) {
            out += JsonDiffEntry(path, JsonDiffKind.CHANGED, formatVal(left), formatVal(right))
        }
    }

    private fun formatVal(v: JVal): String = when (v) {
        is JVal.JNull -> "null"
        is JVal.JBool -> v.v.toString()
        is JVal.JNum -> v.raw
        is JVal.JStr -> "\"${v.v}\""
        is JVal.JObj -> "{${v.fields.size} keys}"
        is JVal.JArr -> "[${v.items.size} items]"
    }

    fun generateJsonSchema(rawJson: String): Result<String> = runCatching {
        val root = PureJsonParser(rawJson.trim()).parseRoot()
        buildSchemaBlock(root, isRoot = true, indent = 0)
    }

    private fun buildSchemaBlock(v: JVal, isRoot: Boolean = false, indent: Int = 0): String {
        val pad = "  ".repeat(indent)
        val inner = "  ".repeat(indent + 1)
        val lines = mutableListOf<String>()
        if (isRoot) lines += "${inner}\"\$schema\": \"https://json-schema.org/draft/2020-12/schema\","
        when (v) {
            is JVal.JNull -> lines += "${inner}\"type\": \"null\""
            is JVal.JBool -> lines += "${inner}\"type\": \"boolean\""
            is JVal.JNum -> lines += "${inner}\"type\": \"${if (v.isIntegral) "integer" else "number"}\""
            is JVal.JStr -> lines += "${inner}\"type\": \"string\""
            is JVal.JArr -> {
                lines += "${inner}\"type\": \"array\"${if (v.items.isNotEmpty()) "," else ""}"
                if (v.items.isNotEmpty()) {
                    lines += "${inner}\"items\": ${buildSchemaBlock(v.items.first(), false, indent + 1).trimStart()}"
                }
            }
            is JVal.JObj -> {
                lines += "${inner}\"type\": \"object\","
                val keys = v.fields.keys.toList()
                lines += "${inner}\"properties\": {"
                keys.forEachIndexed { idx, k ->
                    val comma = if (idx < keys.lastIndex) "," else ""
                    val propSchema = buildSchemaBlock(v.fields[k]!!, false, indent + 2).trimStart()
                    lines += "${"  ".repeat(indent + 2)}\"$k\": $propSchema$comma"
                }
                lines += "${inner}},"
                lines += "${inner}\"required\": [${keys.joinToString(", ") { "\"$it\"" }}]"
            }
        }
        return "{\n${lines.joinToString("\n")}\n$pad}"
    }

    fun generateKotlinDataClasses(rawJson: String, rootName: String = "RootPayload"): Result<String> = runCatching {
        val root = PureJsonParser(rawJson.trim()).parseRoot()
        val sb = StringBuilder()
        sb.appendLine("import kotlinx.serialization.Serializable")
        sb.appendLine()
        val classes = mutableListOf<String>()
        when (root) {
            is JVal.JObj -> emitKotlinClass(rootName, root, classes)
            is JVal.JArr -> {
                val firstObj = root.items.firstOrNull { it is JVal.JObj } as? JVal.JObj
                if (firstObj != null) emitKotlinClass(rootName, firstObj, classes)
                else classes += "// Array of primitives: List<${kotlinTypeFor(root.items.firstOrNull() ?: JVal.JStr(""), "Item", classes)}>"
            }
            else -> Unit
        }
        sb.append(classes.joinToString("\n\n"))
        sb.toString().trim()
    }

    private fun emitKotlinClass(name: String, obj: JVal.JObj, classes: MutableList<String>) {
        val fields = mutableListOf<String>()
        obj.fields.forEach { (key, value) ->
            val propName = toCamelCase(key)
            val subName = propName.replaceFirstChar { it.uppercaseChar() }
            val ktType = kotlinTypeFor(value, subName, classes)
            fields += "    val $propName: $ktType"
        }
        val body = if (fields.isEmpty()) "class $name" else "@Serializable\ndata class $name(\n${fields.joinToString(",\n")}\n)"
        classes.add(body)
    }

    private fun kotlinTypeFor(v: JVal, subName: String, classes: MutableList<String>): String = when (v) {
        is JVal.JNull -> "String? = null"
        is JVal.JBool -> "Boolean"
        is JVal.JNum -> if (v.isIntegral) "Int" else "Double"
        is JVal.JStr -> "String"
        is JVal.JObj -> {
            emitKotlinClass(subName, v, classes)
            subName
        }
        is JVal.JArr -> {
            val first = v.items.firstOrNull() ?: JVal.JNull
            "List<${kotlinTypeFor(first, "${subName}Item", classes)}>"
        }
    }

    fun generateTypeScriptInterfaces(rawJson: String, rootName: String = "RootPayload"): Result<String> = runCatching {
        val root = PureJsonParser(rawJson.trim()).parseRoot()
        val interfaces = mutableListOf<String>()
        when (root) {
            is JVal.JObj -> emitTsInterface(rootName, root, interfaces)
            is JVal.JArr -> {
                val firstObj = root.items.firstOrNull { it is JVal.JObj } as? JVal.JObj
                if (firstObj != null) emitTsInterface(rootName, firstObj, interfaces)
                else interfaces += "export type $rootName = ${tsTypeFor(root.items.firstOrNull() ?: JVal.JNull, "Item", interfaces)}[];"
            }
            else -> Unit
        }
        interfaces.joinToString("\n\n")
    }

    private fun emitTsInterface(name: String, obj: JVal.JObj, out: MutableList<String>) {
        val lines = obj.fields.map { (k, v) ->
            val sub = toCamelCase(k).replaceFirstChar { it.uppercaseChar() }
            "  $k: ${tsTypeFor(v, sub, out)};"
        }
        out.add("export interface $name {\n${lines.joinToString("\n")}\n}")
    }

    private fun tsTypeFor(v: JVal, subName: String, out: MutableList<String>): String = when (v) {
        is JVal.JNull -> "null"
        is JVal.JBool -> "boolean"
        is JVal.JNum -> "number"
        is JVal.JStr -> "string"
        is JVal.JObj -> {
            emitTsInterface(subName, v, out)
            subName
        }
        is JVal.JArr -> {
            val first = v.items.firstOrNull() ?: JVal.JNull
            "${tsTypeFor(first, "${subName}Item", out)}[]"
        }
    }

    private fun toCamelCase(raw: String): String {
        val parts = raw.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "field"
        return parts.first().lowercase() + parts.drop(1).joinToString("") { p ->
            p.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
    }
}

// ==================== 2. SVG OPTIMIZER & VECTOR ANALYZER ====================

data class SvgOptimizeReport(
    val optimizedSvg: String,
    val originalBytes: Int,
    val optimizedBytes: Int,
    val savingsPercent: Float,
    val viewBoxWidth: Float,
    val viewBoxHeight: Float,
    val pathDataList: List<String>,
    val composeSnippet: String
)

object SvgVectorEngine {

    fun optimizeAndInspect(rawSvg: String, precisionDecimals: Int = 2): SvgOptimizeReport {
        val origBytes = rawSvg.toByteArray().size
        var s = rawSvg
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<metadata[^>]*>.*?</metadata>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("<title[^>]*>.*?</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("<desc[^>]*>.*?</desc>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("\\s*(?:xmlns:(?:dc|cc|rdf|inkscape|sodipodi)|id=\"layer\\d+\")=\"[^\"]*\""), "")
            .replace(Regex(">\\s+<"), "><")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        // Round floating point numbers cleanly inside d="" attributes
        val fmtPattern = "%.${precisionDecimals.coerceIn(0, 4)}f"
        s = Regex("(\\d+\\.\\d{${precisionDecimals + 1},})").replace(s) { m ->
            val v = m.value.toDoubleOrNull() ?: return@replace m.value
            String.format(Locale.US, fmtPattern, v).trimEnd('0').trimEnd('.')
        }

        val optBytes = s.toByteArray().size
        val savings = if (origBytes > 0) ((origBytes - optBytes).toFloat() / origBytes * 100f).coerceAtLeast(0f) else 0f

        val vbMatch = Regex("viewBox\\s*=\\s*[\"'][\\d.\\-]+\\s+[\\d.\\-]+\\s+([\\d.]+)\\s+([\\d.]+)[\"']").find(s)
        val w = vbMatch?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 24f
        val h = vbMatch?.groupValues?.getOrNull(2)?.toFloatOrNull() ?: 24f

        val paths = Regex("\\sd\\s*=\\s*[\"']([^\"']+)[\"']").findAll(s).map { it.groupValues[1] }.toList()
        val composeCode = buildComposeVectorSnippet(w, h, paths)

        return SvgOptimizeReport(
            optimizedSvg = s,
            originalBytes = origBytes,
            optimizedBytes = optBytes,
            savingsPercent = savings,
            viewBoxWidth = w,
            viewBoxHeight = h,
            pathDataList = paths,
            composeSnippet = composeCode
        )
    }

    private fun buildComposeVectorSnippet(w: Float, h: Float, paths: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("val CustomVector = ImageVector.Builder(")
        sb.appendLine("    defaultWidth = 24.dp, defaultHeight = 24.dp,")
        sb.appendLine("    viewportWidth = ${w}f, viewportHeight = ${h}f")
        sb.appendLine(").apply {")
        if (paths.isEmpty()) {
            sb.appendLine("    // Add <path d=\"...\"/> in SVG to emit pathData builders")
        } else {
            paths.forEachIndexed { idx, d ->
                sb.appendLine("    // Path #${idx + 1}")
                sb.appendLine("    path(fill = SolidColor(Color.Black)) {")
                sb.appendLine("        // d = \"${d.take(72)}${if (d.length > 72) "..." else ""}\"")
                sb.appendLine("    }")
            }
        }
        sb.appendLine("}.build()")
        return sb.toString().trim()
    }
}

// ==================== 3. CSS GRADIENT & CUBIC BEZIER STUDIO ====================

enum class GradientKind(val label: String) { LINEAR("Linear"), RADIAL("Radial"), CONIC("Conic") }

data class GradientStopSpec(
    val colorArgb: Int,
    val positionPct: Int // 0..100
)

object GradientBezierEngine {

    fun toCss(kind: GradientKind, angleDeg: Int, stops: List<GradientStopSpec>): String {
        val sorted = stops.sortedBy { it.positionPct }
        val stopStr = sorted.joinToString(", ") { "${argbToHex(it.colorArgb)} ${it.positionPct}%" }
        return when (kind) {
            GradientKind.LINEAR -> "background: linear-gradient(${angleDeg}deg, $stopStr);"
            GradientKind.RADIAL -> "background: radial-gradient(circle at center, $stopStr);"
            GradientKind.CONIC -> "background: conic-gradient(from ${angleDeg}deg at 50% 50%, $stopStr);"
        }
    }

    fun toComposeSnippet(kind: GradientKind, stops: List<GradientStopSpec>): String {
        val sorted = stops.sortedBy { it.positionPct }
        val args = sorted.joinToString(", ") {
            val frac = String.format(Locale.US, "%.2ff", it.positionPct / 100f)
            val hex = String.format("%08X", it.colorArgb)
            "$frac to Color(0x$hex)"
        }
        return when (kind) {
            GradientKind.LINEAR -> "Brush.linearGradient($args)"
            GradientKind.RADIAL -> "Brush.radialGradient($args)"
            GradientKind.CONIC -> "Brush.sweepGradient($args)"
        }
    }

    fun argbToHex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    /**
     * Samples a cubic-bezier(x1, y1, x2, y2) curve at normalized progress t (0..1)
     * using Newton-Raphson inversion on X(s) and returning Y(s).
     */
    fun sampleCubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, targetX: Float): Float {
        val tTarget = targetX.coerceIn(0f, 1f)
        var u = tTarget
        repeat(6) {
            val x = cubicCoord(u, x1, x2)
            val dx = cubicDeriv(u, x1, x2)
            if (abs(dx) < 1e-5f) return@repeat
            u = (u - (x - tTarget) / dx).coerceIn(0f, 1f)
        }
        return cubicCoord(u, y1, y2)
    }

    private fun cubicCoord(t: Float, p1: Float, p2: Float): Float {
        val inv = 1f - t
        return 3f * inv * inv * t * p1 + 3f * inv * t * t * p2 + t * t * t
    }

    private fun cubicDeriv(t: Float, p1: Float, p2: Float): Float {
        val inv = 1f - t
        return 3f * inv * inv * p1 + 6f * inv * t * (p2 - p1) + 3f * t * t * (1f - p2)
    }
}

// ==================== 4. ASPECT RATIO, RULE-OF-3 & DP/REM SCALER ====================

data class AspectRatioSummary(
    val gcd: Int,
    val ratioLabel: String,
    val decimalRatio: Double,
    val megapixelEstimate: Double
)

data class DensityRow(val bucket: String, val scale: Float, val pxWidth: Int, val pxHeight: Int)

object AspectRatioEngine {

    fun gcd(a: Int, b: Int): Int {
        var x = abs(a)
        var y = abs(b)
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x.coerceAtLeast(1)
    }

    fun analyze(width: Int, height: Int): AspectRatioSummary {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val g = gcd(w, h)
        return AspectRatioSummary(
            gcd = g,
            ratioLabel = "${w / g}:${h / g}",
            decimalRatio = w.toDouble() / h.toDouble(),
            megapixelEstimate = (w.toLong() * h.toLong()) / 1_000_000.0
        )
    }

    fun scaleWidthForRatio(baseW: Int, baseH: Int, targetHeight: Int): Int {
        if (baseH <= 0) return targetHeight
        return ((targetHeight.toDouble() * baseW.toDouble()) / baseH.toDouble()).roundToInt()
    }

    fun scaleHeightForRatio(baseW: Int, baseH: Int, targetWidth: Int): Int {
        if (baseW <= 0) return targetWidth
        return ((targetWidth.toDouble() * baseH.toDouble()) / baseW.toDouble()).roundToInt()
    }

    fun ruleOfThree(a: Double, b: Double, c: Double): Double? {
        if (abs(a) < 1e-12) return null
        return (b * c) / a
    }

    fun densityMatrix(dpW: Int, dpH: Int): List<DensityRow> = listOf(
        DensityRow("mdpi (1x)", 1.0f, dpW, dpH),
        DensityRow("hdpi (1.5x)", 1.5f, (dpW * 1.5f).roundToInt(), (dpH * 1.5f).roundToInt()),
        DensityRow("xhdpi (2x)", 2.0f, dpW * 2, dpH * 2),
        DensityRow("xxhdpi (3x)", 3.0f, dpW * 3, dpH * 3),
        DensityRow("xxxhdpi (4x)", 4.0f, dpW * 4, dpH * 4)
    )
}

// ==================== 5. IPv4 CIDR SUBNET & RFC 6238 TOTP ENGINE ====================

data class SubnetInfo(
    val valid: Boolean,
    val error: String = "",
    val ip: String = "",
    val prefix: Int = 24,
    val networkAddress: String = "",
    val broadcastAddress: String = "",
    val subnetMask: String = "",
    val wildcardMask: String = "",
    val firstHost: String = "",
    val lastHost: String = "",
    val totalAddresses: Long = 0,
    val usableHosts: Long = 0,
    val ipClassAndScope: String = "",
    val binaryIp: String = ""
)

data class TotpResult(
    val valid: Boolean,
    val code: String = "------",
    val remainingSec: Int = 30,
    val otpAuthUri: String = "",
    val error: String = ""
)

object SubnetTotpEngine {

    fun parseIpv4Cidr(input: String): SubnetInfo {
        val parts = input.trim().split("/")
        val ipStr = parts.getOrNull(0)?.trim().orEmpty()
        val prefix = if (parts.size >= 2) parts[1].trim().toIntOrNull() else 24
        if (prefix == null || prefix !in 0..32) {
            return SubnetInfo(false, "CIDR prefix must be 0..32.")
        }
        val octets = ipStr.split(".")
        if (octets.size != 4) return SubnetInfo(false, "Enter a valid IPv4 address like 192.168.1.42/24.")
        val nums = octets.map { it.toIntOrNull() }
        if (nums.any { it == null || it !in 0..255 }) {
            return SubnetInfo(false, "Each IPv4 octet must be 0..255.")
        }
        val ipLong = ((nums[0]!!.toLong() shl 24) or (nums[1]!!.toLong() shl 16) or (nums[2]!!.toLong() shl 8) or nums[3]!!.toLong()) and 0xFFFFFFFFL
        val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val wildcard = mask xor 0xFFFFFFFFL
        val network = ipLong and mask
        val broadcast = network or wildcard
        val total = 1L shl (32 - prefix)
        val usable = when {
            prefix == 32 -> 1L
            prefix == 31 -> 2L
            else -> (total - 2L).coerceAtLeast(0L)
        }
        val firstHost = if (prefix >= 31) network else network + 1L
        val lastHost = if (prefix >= 31) broadcast else broadcast - 1L

        val firstOctet = nums[0]!!
        val secondOctet = nums[1]!!
        val ipClass = when {
            firstOctet < 128 -> "Class A"
            firstOctet < 192 -> "Class B"
            firstOctet < 224 -> "Class C"
            firstOctet < 240 -> "Class D (Multicast)"
            else -> "Class E (Reserved)"
        }
        val scope = when {
            firstOctet == 10 -> "Private (RFC 1918)"
            firstOctet == 172 && secondOctet in 16..31 -> "Private (RFC 1918)"
            firstOctet == 192 && secondOctet == 168 -> "Private (RFC 1918)"
            firstOctet == 127 -> "Loopback"
            firstOctet == 169 && secondOctet == 254 -> "Link-Local"
            firstOctet == 100 && secondOctet in 64..127 -> "CGNAT (RFC 6598)"
            else -> "Public / Routable"
        }

        val bin = nums.joinToString(".") { n ->
            Integer.toBinaryString(n!!).padStart(8, '0')
        }

        return SubnetInfo(
            valid = true,
            ip = formatIpv4(ipLong),
            prefix = prefix,
            networkAddress = formatIpv4(network),
            broadcastAddress = formatIpv4(broadcast),
            subnetMask = formatIpv4(mask),
            wildcardMask = formatIpv4(wildcard),
            firstHost = formatIpv4(firstHost),
            lastHost = formatIpv4(lastHost),
            totalAddresses = total,
            usableHosts = usable,
            ipClassAndScope = "$ipClass · $scope",
            binaryIp = bin
        )
    }

    private fun formatIpv4(ip: Long): String =
        "${(ip ushr 24) and 0xFF}.${(ip ushr 16) and 0xFF}.${(ip ushr 8) and 0xFF}.${ip and 0xFF}"

    /**
     * Generates an RFC 6238 TOTP code from a Base32 secret.
     */
    fun computeTotp(
        secretBase32: String,
        epochSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSec: Int = 30,
        digits: Int = 6,
        algorithm: String = "HmacSHA1",
        issuer: String = "OmniDeck",
        account: String = "user@omnideck.app"
    ): TotpResult {
        val cleanSecret = secretBase32.uppercase(Locale.US).replace(Regex("[\\s-=]+"), "")
        if (cleanSecret.isEmpty()) {
            return TotpResult(false, error = "Enter a Base32 secret (e.g. JBSWY3DPEHPK3PXP).")
        }
        val keyBytes = runCatching { Base32HexEngine.decodeBase32(cleanSecret) }.getOrElse {
            return TotpResult(false, error = "Invalid Base32 characters.")
        }
        if (keyBytes.isEmpty()) {
            return TotpResult(false, error = "Secret decoded to 0 bytes.")
        }
        val safePeriod = periodSec.coerceIn(5, 300)
        val safeDigits = digits.coerceIn(6, 8)
        val counter = epochSeconds / safePeriod
        val rem = safePeriod - (epochSeconds % safePeriod).toInt()

        val msg = ByteArray(8)
        var v = counter
        for (i in 7 downTo 0) {
            msg[i] = (v and 0xFF).toByte()
            v = v ushr 8
        }

        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(keyBytes, algorithm))
        val hash = mac.doFinal(msg)
        val offset = hash.last().toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val mod = 10.0.pow(safeDigits).toInt()
        val otp = (binary % mod).toString().padStart(safeDigits, '0')
        val algShort = algorithm.removePrefix("Hmac")
        val uri = "otpauth://totp/$issuer:$account?secret=$cleanSecret&issuer=$issuer&algorithm=$algShort&digits=$safeDigits&period=$safePeriod"

        return TotpResult(
            valid = true,
            code = otp,
            remainingSec = rem,
            otpAuthUri = uri
        )
    }
}

// ==================== 6. IMAGE PALETTE EXTRACTOR & FAVICON SPEC ====================

data class PaletteSwatch(
    val colorArgb: Int,
    val hex: String,
    val percentage: Float
)

object ImagePaletteEngine {

    /**
     * Quantizes ARGB pixels into 5-bit color buckets and extracts top distinct swatches.
     */
    fun extractTopSwatches(argbPixels: IntArray, maxCount: Int = 6): List<PaletteSwatch> {
        if (argbPixels.isEmpty()) return emptyList()
        val counts = HashMap<Int, Int>()
        var sampled = 0
        val step = (argbPixels.size / 4096).coerceAtLeast(1)
        var i = 0
        while (i < argbPixels.size) {
            val px = argbPixels[i]
            val alpha = (px ushr 24) and 0xFF
            if (alpha >= 128) {
                val r = ((px ushr 16) and 0xF0) + 8
                val g = ((px ushr 8) and 0xF0) + 8
                val b = (px and 0xF0) + 8
                val key = (0xFF shl 24) or (r.coerceAtMost(255) shl 16) or (g.coerceAtMost(255) shl 8) or b.coerceAtMost(255)
                counts[key] = (counts[key] ?: 0) + 1
                sampled++
            }
            i += step
        }
        if (sampled == 0) return emptyList()

        val sorted = counts.entries.sortedByDescending { it.value }
        val chosen = mutableListOf<PaletteSwatch>()
        for (entry in sorted) {
            if (chosen.size >= maxCount) break
            val argb = entry.key
            val tooClose = chosen.any { colorDistSq(it.colorArgb, argb) < 2200 }
            if (!tooClose) {
                chosen += PaletteSwatch(
                    colorArgb = argb,
                    hex = String.format("#%06X", argb and 0xFFFFFF),
                    percentage = (entry.value * 100f) / sampled
                )
            }
        }
        return chosen
    }

    private fun colorDistSq(a: Int, b: Int): Int {
        val dr = ((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)
        val dg = ((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    fun faviconManifestHtml(appName: String, themeHex: String): String {
        val safeName = appName.ifBlank { "OmniDeck" }
        val safeColor = themeHex.ifBlank { "#2563EB" }
        return """
            <!-- Favicon & App Icon Pack -->
            <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16x16.png">
            <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
            <link rel="icon" type="image/png" sizes="48x48" href="/favicon-48x48.png">
            <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">
            <link rel="manifest" href="/site.webmanifest">
            <meta name="theme-color" content="$safeColor">
            
            /* site.webmanifest */
            {
              "name": "$safeName",
              "short_name": "$safeName",
              "theme_color": "$safeColor",
              "background_color": "#070709",
              "display": "standalone",
              "icons": [
                { "src": "/android-chrome-192x192.png", "sizes": "192x192", "type": "image/png" },
                { "src": "/android-chrome-512x512.png", "sizes": "512x512", "type": "image/png" }
              ]
            }
        """.trimIndent()
    }
}
