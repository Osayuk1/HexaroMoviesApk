package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.MovieViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request POST_NOTIFICATIONS permission at runtime on Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 1001)
            }
        }

        enableEdgeToEdge()
        setContent {
            // Retrieve the shared ViewModel in the activity context
            val movieViewModel: MovieViewModel = viewModel(
                factory = MovieViewModel.provideFactory(application)
            )
            val appTheme by movieViewModel.appTheme.collectAsState()

            MyApplicationTheme(themeSelection = appTheme) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = movieViewModel,
                            onMovieClick = { movieId, mediaType ->
                                if (mediaType == "offline_play") {
                                    navController.navigate("player")
                                } else {
                                    navController.navigate("detail/$movieId/$mediaType")
                                }
                            },
                            onPlayInAppClick = { url, title ->
                                movieViewModel.currentPlaybackUrl = url
                                movieViewModel.currentPlaybackTitle = title
                                navController.navigate("player")
                            }
                        )
                    }

                    composable(
                        route = "detail/{movieId}/{mediaType}",
                        arguments = listOf(
                            navArgument("movieId") { type = NavType.IntType },
                            navArgument("mediaType") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val movieId = backStackEntry.arguments?.getInt("movieId") ?: 0
                        val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"
                        DetailScreen(
                            movieId = movieId,
                            mediaType = mediaType,
                            viewModel = movieViewModel,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onPlayInAppClick = { id, title, poster, url ->
                                movieViewModel.currentPlaybackUrl = url
                                movieViewModel.currentPlaybackTitle = title
                                movieViewModel.currentPlaybackMovieId = id
                                movieViewModel.currentPlaybackPosterPath = poster
                                navController.navigate("player")
                            }
                        )
                    }

                    composable("player") {
                        PlayerScreen(
                            streamUrl = movieViewModel.currentPlaybackUrl,
                            movieTitle = movieViewModel.currentPlaybackTitle,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            viewModel = movieViewModel
                        )
                    }
                }
            }
        }
    }
}
