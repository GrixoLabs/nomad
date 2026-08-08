package dev.grixo.nomad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import dev.grixo.nomad.ui.navigation.NomadNavHost
import dev.grixo.nomad.ui.theme.NomadTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NomadTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    NomadNavHost()
                }
            }
        }
    }
}
