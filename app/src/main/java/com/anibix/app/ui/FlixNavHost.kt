package com.anibix.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anibix.app.FlixApp
import com.anibix.app.ui.screens.*

@Composable
fun FlixNavHost(app: FlixApp) {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(app))

    NavHost(
        nav, startDestination = "home",
        enterTransition = { fadeIn(tween(260)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(260)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        composable("home") {
            HomeScreen(vm,
                onTitle = { nav.navigate("detail/$it") },
                onPlay = { nav.navigate("player/$it") },
                onGrid = { nav.navigate("grid") },
                onDownloads = { nav.navigate("downloads") })
        }
        composable("grid") {
            AnimeGridScreen(vm,
                onTitle = { nav.navigate("detail/$it") },
                onBack = { nav.popBackStack() })
        }
        composable("detail/{id}") { e ->
            DetailScreen(vm, e.arguments!!.getString("id")!!,
                onPlay = { nav.navigate("player/$it") },
                onBack = { nav.popBackStack() })
        }
        composable("player/{id}") { e ->
            PlayerScreen(vm, e.arguments!!.getString("id")!!, onBack = { nav.popBackStack() })
        }
        composable("downloads") {
            DownloadsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
