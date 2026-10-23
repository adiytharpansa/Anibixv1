package com.anibix.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anibix.app.ui.FlixNavHost
import com.anibix.app.ui.theme.FlixTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Content draws behind transparent system bars: hero images reach the top of the screen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        // Kait uji otomatis (dipakai workflow emulator-test): extra test_id/test_ep
        // membuka pemutar langsung. Tidak berpengaruh pada pemakaian normal.
        val testId = intent?.getStringExtra("test_id")
        val testEp = intent?.getIntExtra("test_ep", 1) ?: 1
        val startRoute = testId?.let { "player/$it/$testEp" }
        setContent { FlixTheme { FlixNavHost(startRoute = startRoute) } }
    }
}
