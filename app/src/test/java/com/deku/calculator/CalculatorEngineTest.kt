package com.deku.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {
    private fun run(keys: String): DisplayState = CalculatorEngine().apply { pressAll(keys) }.display()
    private fun result(keys: String) = run(keys).result

    @Test fun precedence() {
        assertEquals("14", result("2+3*4="))
        assertEquals("10", result("2*3+4="))
        assertEquals("7", result("1+12/2="))
        assertEquals("−4", result("2-3*2="))
    }

    @Test fun chainedOperations() {
        assertEquals("6", result("1+2+3="))
        assertEquals("12", result("1+2=*4="))   // continue from result
        assertEquals("5", result("2+*3-1="))    // operator replaced: 2*3-1
    }

    @Test fun noFloatingJunk() {
        assertEquals("0,3", result("0.1+0.2="))
        assertEquals("0,333333333333333", result("1/3="))
        assertEquals("1", result("1/3*3="))
        assertEquals("2,5", result("5/2="))
        assertEquals("0,06", result("0.2*0.3="))
    }

    @Test fun divisionByZero() {
        val s = run("5/0=")
        assertTrue(s.isError)
        assertEquals("Ошибка", s.result)
        assertEquals("7", result("5/0=7"))     // typing recovers
    }

    @Test fun thousandsSeparators() {
        assertEquals("1234", result("1234"))
        assertEquals("12\u202F345", result("12345"))
        assertEquals("1\u202F000\u202F000", result("1000*1000="))
        assertEquals("1\u202F234\u202F567,89", result("1234567.89"))
    }

    @Test fun editing() {
        assertEquals("12", result("123<"))
        assertEquals("0", result("5<"))
        assertEquals("−5", result("5~"))
        assertEquals("5", result("5~~"))
        assertEquals("0,", result("."))
        assertEquals("1,5", result("1..5"))
    }

    @Test fun percent() {
        assertEquals("0,5", result("50%"))
        assertEquals("110", result("100+10%="))   // 100 + 10% of 100
        assertEquals("20", result("200*10%="))
    }

    @Test fun clear() {
        val e = CalculatorEngine()
        e.pressAll("12+34")
        assertEquals("C", e.display().clearLabel)
        e.pressAll("C")
        assertEquals("0", e.display().result)
        assertEquals("12 +", e.display().expression)
        e.pressAll("5=")
        assertEquals("17", e.display().result)
        assertEquals("AC", e.display().clearLabel)
        e.pressAll("C")
        assertEquals("0", e.display().result)
        assertEquals("", e.display().expression)
    }

    @Test fun expressionLine() {
        assertEquals("2 + 3 ×", run("2+3*").expression)
        assertEquals("2 + 3 × 4 =", run("2+3*4=").expression)
    }

    @Test fun largeNumbersUseScientific() {
        assertEquals("1e20", result("10000000000*10000000000="))
        assertEquals("1e−10", result("1/10000000000="))
    }
}
