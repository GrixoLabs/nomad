package dev.grixo.nomad.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.grixo.nomad.domain.model.OnboardingStatus
import dev.grixo.nomad.ui.main.MainRoute
import dev.grixo.nomad.ui.registration.RegistrationRoute
import dev.grixo.nomad.ui.splash.SplashScreen

object NomadRoutes {
    const val SPLASH = "splash"
    const val REGISTER = "register"
    const val HOME = "home"
}

@Composable
fun NomadNavHost(
    viewModel: OnboardingGateViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var splashDone by rememberSaveable { mutableStateOf(false) }

    val resolvedStatus = status
    if (resolvedStatus == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (!splashDone) {
        SplashScreen(onFinished = { splashDone = true })
        return
    }

    val start = when (resolvedStatus) {
        OnboardingStatus.PENDING -> NomadRoutes.REGISTER
        OnboardingStatus.REGISTERED,
        OnboardingStatus.SKIPPED -> NomadRoutes.HOME
    }

    NavHost(navController = navController, startDestination = start) {
        composable(NomadRoutes.REGISTER) {
            RegistrationRoute(
                onFinished = {
                    navController.navigate(NomadRoutes.HOME) {
                        popUpTo(NomadRoutes.REGISTER) { inclusive = true }
                    }
                }
            )
        }
        composable(NomadRoutes.HOME) {
            MainRoute()
        }
    }
}
