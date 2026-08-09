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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import dev.grixo.nomad.domain.model.OnboardingStatus
import dev.grixo.nomad.ui.history.HistoryMapRoute
import dev.grixo.nomad.ui.journal.JournalRoute
import dev.grixo.nomad.ui.main.MainRoute
import dev.grixo.nomad.ui.navigate.NavigateMapRoute
import dev.grixo.nomad.ui.registration.RegistrationRoute
import dev.grixo.nomad.ui.splash.SplashScreen

object NomadRoutes {
    const val REGISTER = "register"
    const val HOME = "home"
    const val JOURNAL = "journal"
    const val HISTORY = "history"
    const val NAVIGATE =
        "navigate/{originLat}/{originLon}/{destLat}/{destLon}/{name}"
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
            val canPop = navController.previousBackStackEntry != null
            val allowBackToHome = canPop || resolvedStatus == OnboardingStatus.SKIPPED
            RegistrationRoute(
                onFinished = {
                    navController.navigate(NomadRoutes.HOME) {
                        popUpTo(NomadRoutes.REGISTER) { inclusive = true }
                    }
                },
                onBack = if (allowBackToHome) {
                    {
                        if (!navController.popBackStack()) {
                            navController.navigate(NomadRoutes.HOME) {
                                popUpTo(NomadRoutes.REGISTER) { inclusive = true }
                            }
                        }
                    }
                } else {
                    null
                }
            )
        }
        composable(NomadRoutes.HOME) {
            MainRoute(
                onOpenJournal = { navController.navigate(NomadRoutes.JOURNAL) },
                onOpenHistory = { navController.navigate(NomadRoutes.HISTORY) },
                onOpenRegister = {
                    navController.navigate(NomadRoutes.REGISTER)
                },
                onNavigateToPlace = { name, originLat, originLon, destLat, destLon ->
                    val encodedName = Uri.encode(name)
                    navController.navigate(
                        "navigate/$originLat/$originLon/$destLat/$destLon/$encodedName"
                    )
                },
                onLoggedOut = {
                    // Logout: land on register; back should not return to a logged-in home.
                    navController.navigate(NomadRoutes.REGISTER) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(NomadRoutes.JOURNAL) {
            JournalRoute(onClose = { navController.popBackStack() })
        }
        composable(NomadRoutes.HISTORY) {
            HistoryMapRoute(onClose = { navController.popBackStack() })
        }
        composable(
            route = NomadRoutes.NAVIGATE,
            arguments = listOf(
                navArgument("originLat") { type = NavType.StringType },
                navArgument("originLon") { type = NavType.StringType },
                navArgument("destLat") { type = NavType.StringType },
                navArgument("destLon") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) {
            NavigateMapRoute(onClose = { navController.popBackStack() })
        }
    }
}
