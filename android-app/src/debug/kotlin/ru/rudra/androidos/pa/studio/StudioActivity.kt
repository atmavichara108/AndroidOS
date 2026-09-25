package ru.rudra.androidos.pa.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class StudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StudioScreen() }
    }
}
