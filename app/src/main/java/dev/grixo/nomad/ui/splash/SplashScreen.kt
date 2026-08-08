package dev.grixo.nomad.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.grixo.nomad.R
import kotlinx.coroutines.delay

private const val SPLASH_MS = 6_000L

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(900),
        label = "splash_alpha"
    )
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        visible = true
        delay(SPLASH_MS)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        colors.primary.copy(alpha = 0.18f),
                        colors.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.logo_nomad),
                contentDescription = "Nomad",
                modifier = Modifier
                    .alpha(alpha)
                    .padding(32.dp)
                    .height(112.dp)
                    .widthIn(max = 300.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "NOMAD",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(alpha)
            )
        }
    }
}
