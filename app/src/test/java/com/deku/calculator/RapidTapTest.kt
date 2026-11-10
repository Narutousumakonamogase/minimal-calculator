package com.deku.calculator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-xxhdpi")
class RapidTapTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var engine: CalculatorEngine
    private var presses = 0

    @Before fun setUp() {
        engine = CalculatorEngine()
        compose.setContent {
            var state by remember { mutableStateOf(engine.display()) }
            CalculatorScreen(state, onKey = { k -> presses++; engine.press(k); state = engine.display() })
        }
    }

    private fun center(label: String): Offset =
        compose.onNodeWithTag("key_$label").fetchSemanticsNode().boundsInRoot.center

    /** Tap = down+up in one go, no frame wait between taps. */
    private fun rapid(labels: List<String>) {
        val points = labels.map(::center)
        compose.onRoot().performTouchInput {
            points.forEach { p -> down(p); up() }
        }
        compose.waitForIdle()
    }

    @Test fun twentyRapidTapsAllRegister() {
        // 1+1+...+1 = : 10 ones, 9 pluses, 1 equals = 20 taps
        val seq = (List(10) { "1" }.flatMap { listOf(it, "+") }.dropLast(1)) + "="
        assertEquals(20, seq.size)
        rapid(seq)
        assertEquals(20, presses)
        assertEquals("10", engine.display().result)
    }

    @Test fun rapidDifferentDigits() {
        rapid("123456789012345".map { it.toString() })
        assertEquals("123\u202F456\u202F789\u202F012\u202F345", engine.display().result)
    }

    @Test fun sameKeyHammered() {
        rapid(List(12) { "7" })
        assertEquals(12, presses)
        assertEquals("777\u202F777\u202F777\u202F777", engine.display().result)
    }

    @Test fun overlappingMultiFingerTaps() {
        val a = center("1"); val b = center("2"); val c = center("3")
        compose.onRoot().performTouchInput {
            down(0, a)        // finger 1 on "1"
            down(1, b)        // finger 2 on "2" while finger 1 still down
            up(0)
            down(2, c)        // finger 3 on "3" while finger 2 still down
            up(1); up(2)
        }
        compose.waitForIdle()
        assertEquals("123", engine.display().result)
    }

    @Test fun tapInCellCornerOutsideCircleRegisters() {
        val bounds = compose.onNodeWithTag("key_5").fetchSemanticsNode().boundsInRoot
        val corner = Offset(bounds.left + 2f, bounds.top + 2f)
        compose.onRoot().performTouchInput { down(corner); up() }
        compose.waitForIdle()
        assertEquals("5", engine.display().result)
    }
}
