package com.morningsteps.kids

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.morningsteps.kids.ui.AppRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Leave the launch theme as soon as the first frame can be drawn. No artificial delay.
        setTheme(R.style.Theme_MorningSteps)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MorningStepsApp).container
        setContent { AppRoot(container) }
    }

    override fun onStart() {
        super.onStart()
        // Recompute timers after background / restart. Silent: no delayed sound is ever played here.
        val container = (application as MorningStepsApp).container
        lifecycleScope.launch { container.routines.recoverTimers() }
    }
}
