package com.deku.calculator

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Text that is currently "evaporating" after AC / C. null line = that line is not animated. */
@Immutable
data class Vanish(val id: Long, val result: String?, val expression: String?)

/** UI-facing state holder: engine + display state + the AC evaporation snapshot. */
class CalculatorController {
    private val engine = CalculatorEngine()
    private var nextId = 1L

    var state by mutableStateOf(engine.display())
        private set
    var vanish by mutableStateOf<Vanish?>(null)
        private set

    fun onKey(key: Key) {
        val before = state
        engine.press(key)
        val after = engine.display()
        vanish = if (key == Key.Clear) {
            val r = before.result.takeIf { it != after.result }
            val e = before.expression.takeIf { it.isNotEmpty() && it != after.expression }
            if (r != null || e != null) Vanish(nextId++, r, e) else null
        } else {
            null // any other input cancels the animation instantly
        }
        state = after
    }

    fun onVanishFinished(id: Long) {
        if (vanish?.id == id) vanish = null
    }
}
