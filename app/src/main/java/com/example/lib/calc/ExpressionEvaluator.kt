package com.example.lib.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atanh
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

sealed interface EvalResult {
    data class Success(val value: Double, val formatted: String) : EvalResult
    data class Error(val message: String) : EvalResult
}

/**
 * Full Scientific Expression Evaluator supporting:
 * - Operators: +, -, *, /, %, ^, postfix factorial !
 * - Constants: pi (π), e, phi (φ), tau (τ), ans
 * - Functions: sin, cos, tan, asin, acos, atan, sinh, cosh, tanh, asinh, acosh, atanh,
 *              sqrt, cbrt, ln, log, log10, log2, exp, abs, floor, ceil, round, fact,
 *              deg, rad, nCr(n,r), nPr(n,r), gcd(a,b), lcm(a,b), min(a,b), max(a,b), pow(a,b)
 */
object ExpressionEvaluator {
    fun evaluate(expression: String, degreesMode: Boolean = true, ansValue: Double = 0.0): EvalResult {
        val cleaned = expression.trim()
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "pi")
            .replace("φ", "phi")
            .replace("τ", "tau")
            .replace("√(", "sqrt(")
            .replace("∛(", "cbrt(")
        if (cleaned.isEmpty()) return EvalResult.Error("Enter an expression")

        return try {
            val parser = Parser(cleaned, degreesMode, ansValue)
            val result = parser.parseExpression()
            if (parser.pos < parser.input.length) {
                return EvalResult.Error("Unexpected character '${parser.input[parser.pos]}'")
            }
            if (result.isNaN() || result.isInfinite()) {
                return EvalResult.Error("Math error (division by zero or domain error)")
            }
            EvalResult.Success(result, formatNumberClean(result))
        } catch (e: IllegalArgumentException) {
            EvalResult.Error(e.message ?: "Invalid syntax")
        }
    }

    fun formatNumberClean(v: Double): String {
        if (abs(v) < 1e-12) return "0"
        val roundedLong = v.roundToLong()
        if (abs(v - roundedLong) < 1e-10 && abs(v) < 1e15) {
            return roundedLong.toString()
        }
        val formatted = String.format(java.util.Locale.US, "%.8f", v)
            .trimEnd('0')
            .trimEnd('.')
        return if (formatted == "-0") "0" else formatted
    }

    private fun factorial(n: Double): Double {
        if (n < 0 || n > 170 || abs(n - n.roundToLong()) > 1e-9) {
            throw IllegalArgumentException("Factorial requires integer 0..170")
        }
        var res = 1.0
        for (i in 2..n.roundToLong().toInt()) res *= i
        return res
    }

    private fun nCr(n: Long, r: Long): Double {
        if (r < 0 || n < 0 || r > n) throw IllegalArgumentException("nCr requires 0 <= r <= n")
        val k = min(r, n - r)
        var res = 1.0
        for (i in 1L..k) {
            res = res * (n - k + i) / i
        }
        return res
    }

    private fun nPr(n: Long, r: Long): Double {
        if (r < 0 || n < 0 || r > n) throw IllegalArgumentException("nPr requires 0 <= r <= n")
        var res = 1.0
        for (i in 0L until r) res *= (n - i)
        return res
    }

    private fun gcdLong(a: Long, b: Long): Long {
        var x = abs(a); var y = abs(b)
        while (y != 0L) { val t = x % y; x = y; y = t }
        return x
    }

    private class Parser(val input: String, val degreesMode: Boolean, val ansValue: Double) {
        var pos: Int = 0

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skipWhitespace()
            return if (pos < input.length) input[pos] else null
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; x += parseTerm() }
                    '-' -> { pos++; x -= parseTerm() }
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parsePower()
            while (true) {
                when (peek()) {
                    '*' -> { pos++; x *= parsePower() }
                    '/' -> {
                        pos++
                        val d = parsePower()
                        if (d == 0.0) throw IllegalArgumentException("Division by zero")
                        x /= d
                    }
                    '%' -> {
                        pos++
                        val d = parsePower()
                        if (d == 0.0) throw IllegalArgumentException("Modulo by zero")
                        x %= d
                    }
                    else -> return x
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            return if (peek() == '^') {
                pos++
                base.pow(parsePower())
            } else {
                base
            }
        }

        private fun parseUnary(): Double {
            return when (peek()) {
                '+' -> { pos++; parseUnary() }
                '-' -> { pos++; -parseUnary() }
                else -> parsePostfix()
            }
        }

        private fun parsePostfix(): Double {
            var v = parsePrimary()
            while (peek() == '!') {
                pos++
                v = factorial(v)
            }
            return v
        }

        private fun parsePrimary(): Double {
            skipWhitespace()
            if (pos >= input.length) throw IllegalArgumentException("Unexpected end of expression")

            if (input[pos] == '(') {
                pos++
                val v = parseExpression()
                skipWhitespace()
                if (pos >= input.length || input[pos] != ')') {
                    throw IllegalArgumentException("Missing closing ')'")
                }
                pos++
                return v
            }

            if (input[pos].isDigit() || input[pos] == '.') {
                val start = pos
                while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
                if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
                    val saved = pos
                    pos++
                    if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
                    if (pos < input.length && input[pos].isDigit()) {
                        while (pos < input.length && input[pos].isDigit()) pos++
                    } else {
                        pos = saved
                    }
                }
                val numStr = input.substring(start, pos)
                return numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number '$numStr'")
            }

            if (input[pos].isLetter()) {
                val start = pos
                while (pos < input.length && input[pos].isLetterOrDigit()) pos++
                val ident = input.substring(start, pos).lowercase()
                when (ident) {
                    "pi" -> return PI
                    "e" -> return Math.E
                    "phi" -> return (1.0 + sqrt(5.0)) / 2.0
                    "tau" -> return 2.0 * PI
                    "ans" -> return ansValue
                }
                skipWhitespace()
                if (pos >= input.length || input[pos] != '(') {
                    throw IllegalArgumentException("Expected '(' after function '$ident'")
                }
                pos++
                val args = mutableListOf<Double>()
                args += parseExpression()
                while (peek() == ',') {
                    pos++
                    args += parseExpression()
                }
                skipWhitespace()
                if (pos >= input.length || input[pos] != ')') {
                    throw IllegalArgumentException("Missing ')' after $ident(...)")
                }
                pos++
                val arg = args[0]
                val arg2 = args.getOrNull(1)
                val angle = if (degreesMode) Math.toRadians(arg) else arg
                return when (ident) {
                    "sqrt" -> { if (arg < 0) throw IllegalArgumentException("sqrt of negative number"); sqrt(arg) }
                    "cbrt" -> cbrt(arg)
                    "abs" -> abs(arg)
                    "floor" -> floor(arg)
                    "ceil" -> ceil(arg)
                    "round" -> round(arg)
                    "exp" -> exp(arg)
                    "fact" -> factorial(arg)
                    "sin" -> sin(angle)
                    "cos" -> cos(angle)
                    "tan" -> tan(angle)
                    "asin" -> { val r = asin(arg); if (degreesMode) Math.toDegrees(r) else r }
                    "acos" -> { val r = acos(arg); if (degreesMode) Math.toDegrees(r) else r }
                    "atan" -> { val r = atan(arg); if (degreesMode) Math.toDegrees(r) else r }
                    "sinh" -> sinh(arg)
                    "cosh" -> cosh(arg)
                    "tanh" -> tanh(arg)
                    "asinh" -> asinh(arg)
                    "acosh" -> acosh(arg)
                    "atanh" -> atanh(arg)
                    "deg" -> Math.toDegrees(arg)
                    "rad" -> Math.toRadians(arg)
                    "log", "log10" -> { if (arg <= 0) throw IllegalArgumentException("log domain must be > 0"); log10(arg) }
                    "log2" -> { if (arg <= 0) throw IllegalArgumentException("log2 domain must be > 0"); log2(arg) }
                    "ln" -> { if (arg <= 0) throw IllegalArgumentException("ln domain must be > 0"); ln(arg) }
                    "ncr" -> nCr(arg.roundToLong(), (arg2 ?: throw IllegalArgumentException("nCr needs 2 args")).roundToLong())
                    "npr" -> nPr(arg.roundToLong(), (arg2 ?: throw IllegalArgumentException("nPr needs 2 args")).roundToLong())
                    "gcd" -> gcdLong(arg.roundToLong(), (arg2 ?: throw IllegalArgumentException("gcd needs 2 args")).roundToLong()).toDouble()
                    "lcm" -> {
                        val b = (arg2 ?: throw IllegalArgumentException("lcm needs 2 args")).roundToLong()
                        val a = arg.roundToLong()
                        if (a == 0L || b == 0L) 0.0 else abs(a / gcdLong(a, b) * b).toDouble()
                    }
                    "min" -> min(arg, arg2 ?: arg)
                    "max" -> max(arg, arg2 ?: arg)
                    "pow" -> arg.pow(arg2 ?: throw IllegalArgumentException("pow needs 2 args"))
                    else -> throw IllegalArgumentException("Unknown function '$ident'")
                }
            }

            throw IllegalArgumentException("Unexpected symbol '${input[pos]}'")
        }
    }
}
