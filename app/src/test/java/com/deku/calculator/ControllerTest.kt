package com.deku.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerTest {
    private fun CalculatorController.type(keys: String) {
        for (c in keys) onKey(
            when (c) {
                in '0'..'9' -> Key.Digit(c - '0'); '+' -> Key.Operator(Op.ADD); '*' -> Key.Operator(Op.MUL)
                '=' -> Key.Equals; 'C' -> Key.Clear; '.' -> Key.Dot; else -> error("$c")
            }
        )
    }

    @Test fun acResetsStateAndStartsVanish() {
        val c = CalculatorController()
        c.type("12+34=")
        c.type("C")
        assertEquals("0", c.state.result)
        assertEquals("", c.state.expression)
        assertEquals("AC", c.state.clearLabel)
        assertNotNull(c.vanish); val v = c.vanish!!
        assertEquals("46", v.result)
        assertEquals("12 + 34 =", v.expression)
        c.onVanishFinished(v.id)
        assertNull(c.vanish)
    }

    @Test fun cOnlyVanishesTheEntry() {
        val c = CalculatorController()
        c.type("12+34C")
        assertEquals("34", c.vanish!!.result)
        assertNull(c.vanish!!.expression)          // "12 +" stays
        assertEquals("12 +", c.state.expression)
    }

    @Test fun inputDuringVanishCancelsAndShowsImmediately() {
        val c = CalculatorController()
        c.type("987C")
        assertNotNull(c.vanish)
        c.type("5")
        assertNull(c.vanish)
        assertEquals("5", c.state.result)
    }

    @Test fun acOnEmptyDisplayDoesNotAnimate() {
        val c = CalculatorController()
        c.type("C")
        assertNull(c.vanish)
    }

    @Test fun staleFinishIgnored() {
        val c = CalculatorController()
        c.type("1C")
        val first = c.vanish!!.id
        c.type("2C")
        c.onVanishFinished(first)
        assertNotNull(c.vanish)                    // newer animation still running
    }
}
