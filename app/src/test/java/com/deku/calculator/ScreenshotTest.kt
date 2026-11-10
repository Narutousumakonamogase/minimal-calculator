package com.deku.calculator

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val dir = System.getProperty("screenshot.dir") ?: "."

    private fun shoot(keys: String, name: String) {
        val engine = CalculatorEngine().apply { pressAll(keys) }
        compose.setContent { CalculatorScreen(engine.display(), onKey = {}) }
        compose.onRoot().captureRoboImage("$dir/$name")
    }

    @Test fun main() = shoot("1234.5*12+", "screenshot.png")
    @Test fun result() = shoot("1234.5*12+300=", "screenshot_result.png")
    @Test fun error() = shoot("7/0=", "screenshot_error.png")
    @Test fun long() = shoot("123456789*987654=", "screenshot_long.png")
}
