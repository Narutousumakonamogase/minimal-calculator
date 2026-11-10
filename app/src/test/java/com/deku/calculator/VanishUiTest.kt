package com.deku.calculator

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-xxhdpi")
class VanishUiTest {
    @get:Rule val compose = createComposeRule()
    private val c = CalculatorController()
    private val dir = System.getProperty("screenshot.dir") ?: "."

    @Before fun setUp() {
        compose.setContent {
            CalculatorScreen(c.state, c::onKey, c.vanish, c::onVanishFinished)
        }
    }

    private fun tap(vararg labels: String) {
        val pts = labels.map { compose.onNodeWithTag("key_$it").fetchSemanticsNode().boundsInRoot.center }
        compose.onRoot().performTouchInput { pts.forEach { down(it); up() } }
        compose.waitForIdle() // flush snapshot changes; with autoAdvance=false this does not advance the clock
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun acEvaporatesThenShowsZero() {
        tap("1", "2", "3", "4", "5", "+", "6", "7", "=")
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        tap("AC")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("vanish_result").assertExists()
        compose.onNodeWithTag("vanish_expression").assertExists()
        compose.onRoot().captureRoboImage("$dir/screenshot_vanish.png")
        compose.mainClock.advanceTimeBy(1800)
        compose.onNodeWithTag("vanish_result").assertDoesNotExist()
        compose.onNodeWithTag("result").assertTextEquals("0")
        compose.onNodeWithTag("expression").assertTextEquals("")
    }

    @Test fun inputDuringAnimationShowsImmediately() {
        tap("9", "8", "7", "6", "5", "4", "3")
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        tap("AC")
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("vanish_result").assertExists()
        tap("4", "2")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("vanish_result").assertDoesNotExist()
        compose.onNodeWithTag("result").assertTextEquals("42")
    }
}
