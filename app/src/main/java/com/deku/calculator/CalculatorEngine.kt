package com.deku.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Keys understood by the engine. */
sealed interface Key {
    data class Digit(val d: Int) : Key
    data class Operator(val op: Op) : Key
    data object Dot : Key
    data object Equals : Key
    data object Clear : Key
    data object Backspace : Key
    data object Negate : Key
    data object Percent : Key
}

enum class Op(val symbol: String, val precedence: Int) {
    ADD("+", 1), SUB("−", 1), MUL("×", 2), DIV("÷", 2)
}

/** What the UI renders. */
data class DisplayState(
    val expression: String,
    val result: String,
    val isError: Boolean,
    val clearLabel: String,
    val activeOp: Op?,
)

/**
 * Immediate-entry calculator with correct operator precedence.
 * Numbers are kept as plain strings while typing and as BigDecimal in the token list.
 */
class CalculatorEngine {
    private val numbers = mutableListOf<BigDecimal>()
    private val ops = mutableListOf<Op>()
    /** Number being typed (plain format, '.' decimal). null = waiting for operand after an operator. */
    private var entry: String? = "0"
    private var lastExpression: String = ""
    private var justEvaluated = false
    private var error = false

    fun press(key: Key) {
        if (error && key !is Key.Digit && key !is Key.Dot && key !is Key.Clear) return
        if (error) resetAll()
        when (key) {
            is Key.Digit -> digit(key.d)
            Key.Dot -> dot()
            is Key.Operator -> operator(key.op)
            Key.Equals -> equals()
            Key.Clear -> clear()
            Key.Backspace -> backspace()
            Key.Negate -> negate()
            Key.Percent -> percent()
        }
    }

    fun pressAll(keys: String) {
        for (c in keys) press(
            when (c) {
                in '0'..'9' -> Key.Digit(c - '0')
                '.', ',' -> Key.Dot
                '+' -> Key.Operator(Op.ADD)
                '-' -> Key.Operator(Op.SUB)
                '*' -> Key.Operator(Op.MUL)
                '/' -> Key.Operator(Op.DIV)
                '=' -> Key.Equals
                'C' -> Key.Clear
                '<' -> Key.Backspace
                '~' -> Key.Negate
                '%' -> Key.Percent
                else -> error("bad key $c")
            }
        )
    }

    private fun resetAll() {
        numbers.clear(); ops.clear(); entry = "0"; lastExpression = ""; justEvaluated = false; error = false
    }

    private fun startFreshIfEvaluated() {
        if (justEvaluated) { resetAll() }
    }

    private fun digitCount(s: String) = s.count { it.isDigit() }

    private fun digit(d: Int) {
        startFreshIfEvaluated()
        val e = entry
        entry = when {
            e == null -> d.toString()
            e == "0" -> d.toString()
            e == "-0" -> "-$d"
            digitCount(e) >= MAX_DIGITS -> e
            else -> e + d
        }
    }

    private fun dot() {
        startFreshIfEvaluated()
        val e = entry ?: "0"
        entry = if ('.' in e) e else "$e."
    }

    private fun operator(op: Op) {
        if (justEvaluated) {
            justEvaluated = false
            lastExpression = ""
        }
        val e = entry
        if (e == null) {
            // Replace the pending operator.
            if (ops.isNotEmpty() && ops.size == numbers.size) ops[ops.lastIndex] = op
            return
        }
        numbers += BigDecimal(e.trimEnd('.').ifEmpty { "0" })
        ops += op
        entry = null
    }

    private fun equals() {
        if (justEvaluated) return
        val e = entry
        if (e == null) {
            if (ops.isEmpty()) return
            ops.removeAt(ops.lastIndex) // ignore dangling operator
        } else {
            numbers += BigDecimal(e.trimEnd('.').ifEmpty { "0" })
        }
        if (ops.isEmpty()) {
            // Nothing to compute; just normalise the number.
            val n = numbers.single()
            numbers.clear()
            entry = toPlain(n)
            return
        }
        val expr = buildExpression(numbers, ops)
        val result = evaluate(numbers, ops)
        numbers.clear(); ops.clear()
        lastExpression = "$expr ="
        justEvaluated = true
        if (result == null) {
            error = true
            entry = "0"
        } else {
            entry = toPlain(result)
        }
    }

    private fun clear() {
        val e = entry
        if (!justEvaluated && e != null && e != "0" && numbers.isNotEmpty()) {
            entry = "0"           // C: clear current entry only
        } else {
            resetAll()            // AC
        }
    }

    private fun backspace() {
        if (justEvaluated) { lastExpression = ""; justEvaluated = false; return }
        val e = entry
        if (e == null) {
            if (ops.isNotEmpty()) {
                ops.removeAt(ops.lastIndex)
                entry = toPlain(numbers.removeAt(numbers.lastIndex))
            }
            return
        }
        val next = e.dropLast(1)
        entry = if (next.isEmpty() || next == "-") "0" else next
    }

    private fun negate() {
        if (justEvaluated) { justEvaluated = false; lastExpression = "" }
        val e = entry ?: "0"
        entry = if (e.startsWith("-")) e.drop(1) else "-$e"
    }

    private fun percent() {
        if (justEvaluated) { justEvaluated = false; lastExpression = "" }
        val e = entry ?: return
        val x = BigDecimal(e.trimEnd('.').ifEmpty { "0" })
        val lastOp = ops.lastOrNull()
        val value = if (lastOp == Op.ADD || lastOp == Op.SUB) {
            // a + b%  ->  a + a*b/100 (percentage of the running value)
            val base = evaluate(numbers, ops.dropLast(1)) ?: BigDecimal.ZERO
            base.multiply(x).divide(HUNDRED, MC)
        } else {
            x.divide(HUNDRED, MC)
        }
        entry = toPlain(value)
    }

    fun display(): DisplayState {
        val e = entry
        val shownExpression = when {
            error || justEvaluated -> lastExpression
            else -> buildString {
                for (i in numbers.indices) {
                    if (i > 0) append(' ')
                    append(Formatter.number(toPlain(numbers[i])))
                    if (i < ops.size) append(' ').append(ops[i].symbol)
                }
            }
        }
        val result = when {
            error -> ERROR_TEXT
            e != null -> Formatter.number(e)
            numbers.isNotEmpty() -> Formatter.number(toPlain(numbers.last()))
            else -> "0"
        }
        val showC = !justEvaluated && e != null && e != "0" && numbers.isNotEmpty()
        return DisplayState(
            expression = shownExpression,
            result = result,
            isError = error,
            clearLabel = if (showC) "C" else "AC",
            activeOp = if (e == null && !justEvaluated) ops.lastOrNull() else null,
        )
    }

    companion object {
        const val MAX_DIGITS = 15
        const val ERROR_TEXT = "Ошибка"
        private val MC = MathContext(34, RoundingMode.HALF_EVEN)
        private val HUNDRED = BigDecimal(100)

        private fun buildExpression(nums: List<BigDecimal>, ops: List<Op>): String = buildString {
            for (i in nums.indices) {
                if (i > 0) append(' ').append(ops[i - 1].symbol).append(' ')
                append(Formatter.number(toPlain(nums[i])))
            }
        }

        /** Evaluates with precedence. Returns null on division by zero. */
        fun evaluate(nums: List<BigDecimal>, ops: List<Op>): BigDecimal? {
            if (nums.isEmpty()) return BigDecimal.ZERO
            require(ops.size == nums.size - 1 || ops.size == nums.size)
            // First pass: × and ÷
            val terms = mutableListOf(nums[0])
            val addOps = mutableListOf<Op>()
            for (i in 0 until nums.size - 1) {
                val op = ops[i]
                val rhs = nums[i + 1]
                when (op) {
                    Op.MUL -> terms[terms.lastIndex] = terms.last().multiply(rhs, MC)
                    Op.DIV -> {
                        if (rhs.signum() == 0) return null
                        terms[terms.lastIndex] = terms.last().divide(rhs, MC)
                    }
                    else -> { addOps += op; terms += rhs }
                }
            }
            var acc = terms[0]
            for (i in addOps.indices) {
                acc = if (addOps[i] == Op.ADD) acc.add(terms[i + 1], MC) else acc.subtract(terms[i + 1], MC)
            }
            return acc
        }

        /** Round to display precision and strip floating junk. */
        fun toPlain(v: BigDecimal): String {
            val r = v.round(MathContext(MAX_DIGITS, RoundingMode.HALF_EVEN))
            if (r.signum() == 0) return "0"
            val s = r.stripTrailingZeros()
            val abs = s.abs()
            return if (abs >= BigDecimal("1e15") || abs < BigDecimal("1e-9")) {
                // scientific: mantissa e exponent
                val exp = s.precision() - s.scale() - 1
                val mantissa = s.movePointLeft(exp).stripTrailingZeros().toPlainString()
                "${mantissa}e$exp"
            } else s.toPlainString()
        }
    }
}

object Formatter {
    private const val GROUP = '\u202F' // narrow no-break space
    private const val DECIMAL = ','

    /** Formats a plain number string ("-1234.50", "12e20") for display. */
    fun number(plain: String): String {
        if (plain == CalculatorEngine.ERROR_TEXT) return plain
        val neg = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val ePos = body.indexOf('e')
        val mant = if (ePos >= 0) body.substring(0, ePos) else body
        val exp = if (ePos >= 0) body.substring(ePos + 1) else null
        val dot = mant.indexOf('.')
        val intPart = if (dot >= 0) mant.substring(0, dot) else mant
        val frac = if (dot >= 0) mant.substring(dot + 1) else null
        val grouped = if (exp != null) intPart else group(intPart)
        return buildString {
            if (neg) append('−')
            append(grouped)
            if (frac != null) append(DECIMAL).append(frac)
            if (exp != null) append("e").append(exp.replace("-", "−"))
        }
    }

    private fun group(s: String): String {
        if (s.length <= 4) return s // 1234 stays compact, 12 345 grouped
        val sb = StringBuilder()
        val first = s.length % 3
        if (first > 0) sb.append(s, 0, first)
        var i = first
        while (i < s.length) {
            if (sb.isNotEmpty()) sb.append(GROUP)
            sb.append(s, i, i + 3)
            i += 3
        }
        return sb.toString()
    }
}
