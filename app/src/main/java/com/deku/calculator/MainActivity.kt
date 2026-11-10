package com.deku.calculator

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class CalculatorViewModel : ViewModel() {
    val controller = CalculatorController()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AColor.BLACK),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val vm: CalculatorViewModel = viewModel()
            val c = vm.controller
            val onKey = remember(c) { { k: Key -> c.onKey(k) } }
            val onVanishDone = remember(c) { { id: Long -> c.onVanishFinished(id) } }
            CalculatorScreen(state = c.state, onKey = onKey, vanish = c.vanish, onVanishDone = onVanishDone)
        }
    }
}
