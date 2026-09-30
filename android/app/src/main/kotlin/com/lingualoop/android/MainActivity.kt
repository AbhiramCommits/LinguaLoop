package com.lingualoop.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lingualoop.android.ui.LinguaLoopNavHost
import com.lingualoop.android.ui.theme.LinguaLoopTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinguaLoopTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LinguaLoopNavHost()
                }
            }
        }
    }
}
