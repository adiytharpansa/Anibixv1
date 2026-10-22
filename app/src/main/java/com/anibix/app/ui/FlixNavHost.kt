package com.anibix.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anibix.app.ui.screens.AnimeGridScreen
import com.anibix.app.ui.screens.DetailScreen
import com.anibix.app.ui.screens.DownloadsScreen
import com.anibix.app.ui.screens.HomeScreen
import com.anibix.app.ui.screens.PlayerScreen
import com.anibix.app.ui.screens.SettingsScreen

@Composable
fun FlixNavHost() {
    val nav = rememberNavController()
    val vm: MainViewModel = hiltViewModel()

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
                onPlay = { nav.navigate("player/$it/1") },
                onPlayEpisode = { id, ep -> nav.navigate("player/$id/$ep") },
                onGrid = { nav.navigate("grid") },
                onDownloads = { nav.navigate("downloads") },
                onSettings = { nav.navigate("settings") })
        }
        composable("grid") {
            AnimeGridScreen(vm,
                onTitle = { nav.navigate("detail/$it") },
                onBack = { nav.popBackStack() })
        }
        composable(
            "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { e ->
            DetailScreen(vm, e.arguments!!.getString("id")!!,
                onPlay = { id, ep -> nav.navigate("player/$id/$ep") },
                onBack = { nav.popBackStack() })
        }
        composable(
            "player/{id}/{ep}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("ep") { type = NavType.IntType },
            )
        ) { e ->
            PlayerScreen(
                vm,
                e.arguments!!.getString("id")!!,
                e.arguments!!.getInt("ep"),
                onBack = { nav.popBackStack() },
                onNextEpisode = { id, ep ->
                    nav.navigate("player/$id/$ep") {
                        popUpTo("player/{id}/{ep}") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("downloads") {
            DownloadsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
