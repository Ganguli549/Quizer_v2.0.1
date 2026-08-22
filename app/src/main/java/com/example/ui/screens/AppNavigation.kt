package com.example.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object QuizRoute

@Serializable
object ResultsRoute

@Serializable
object HistoryRoute

@Serializable
object StatsRoute

@Serializable
object SearchRoute

@Serializable
object BookmarksRoute

@Serializable
object LearnRoute

@Serializable
object AnalysisRoute

@Serializable
object ReviewRoute

@Serializable
object SettingsRoute
@Serializable
object AiSettingsRoute

@Serializable
object UsageRoute

@Serializable
object ReadBookRoute

@Serializable
object EditBookRoute

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController, 
        startDestination = HomeRoute,
        enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) }
    ) {
        composable<HomeRoute> {
            HomeScreen(
                viewModel = viewModel, 
                onStartQuiz = { navController.navigate(QuizRoute) },
                onNavigateStats = { navController.navigate(StatsRoute) },
                onNavigateHistory = { navController.navigate(HistoryRoute) },
                onNavigateSearch = { navController.navigate(SearchRoute) },
                onNavigateBookmarks = { navController.navigate(BookmarksRoute) },
                onNavigateLearn = { navController.navigate(LearnRoute) },
                onNavigateAnalysis = { navController.navigate(AnalysisRoute) },
                onNavigateSettings = { navController.navigate(SettingsRoute) },
                onNavigateReadBook = { navController.navigate(ReadBookRoute) }
            )
        }
        composable<QuizRoute> {
            QuizScreen(viewModel = viewModel, onFinish = { 
                navController.navigate(ResultsRoute) {
                    popUpTo(QuizRoute) { inclusive = true }
                } 
            }, onCancel = { navController.popBackStack() })
        }
        composable<ResultsRoute> {
            ResultScreen(
                viewModel = viewModel, 
                onHome = { navController.popBackStack(HomeRoute, false) }, 
                onReview = { navController.navigate(ReviewRoute) },
                onPracticeWrong = { 
                    navController.navigate(QuizRoute) {
                        popUpTo(ResultsRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(
                viewModel = viewModel, 
                onHome = { navController.popBackStack(HomeRoute, false) }, 
                onReview = { navController.navigate(ReviewRoute) },
                onStartQuiz = { navController.navigate(QuizRoute) }
            )
        }
        composable<StatsRoute> {
            StatsScreen(viewModel = viewModel, onHome = { navController.popBackStack(HomeRoute, false) })
        }
        composable<SearchRoute> {
            SearchScreen(
                viewModel = viewModel, 
                onHome = { navController.popBackStack(HomeRoute, false) },
                onStartQuiz = { navController.navigate(QuizRoute) }
            )
        }
        composable<BookmarksRoute> {
            BookmarksScreen(
                viewModel = viewModel, 
                onHome = { navController.popBackStack(HomeRoute, false) },
                onStartQuiz = { navController.navigate(QuizRoute) }
            )
        }
        composable<LearnRoute> {
            LearnScreen(
                viewModel = viewModel, 
                onHome = { navController.popBackStack(HomeRoute, false) },
                onStartQuiz = { navController.navigate(QuizRoute) }
            )
        }
        composable<AnalysisRoute> {
            AnalysisScreen(
                viewModel = viewModel, 
                onHome = { navController.popBackStack(HomeRoute, false) },
                onStartQuiz = { navController.navigate(QuizRoute) }
            )
        }
        composable<ReviewRoute> {
            ReviewScreen(viewModel = viewModel, onHome = { navController.popBackStack(HomeRoute, false) })
        }
        composable<SettingsRoute> {
            SettingsScreen(viewModel = viewModel, 
                           onBack = { navController.popBackStack() },
                           onNavigateToAiSettings = { navController.navigate(AiSettingsRoute) })
        }
        composable<AiSettingsRoute> {
            AiSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() }, onNavigateToUsage = { navController.navigate(UsageRoute) })
        }
        composable<UsageRoute> {
            UsageScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<ReadBookRoute> {
            ReadBookScreen(
                viewModel = viewModel, 
                onHome = { navController.popBackStack(HomeRoute, false) },
                onEditBook = { navController.navigate(EditBookRoute) },
                onNavigateToAiSettings = { navController.navigate(AiSettingsRoute) },
                onNavigateToUsage = { navController.navigate(UsageRoute) }
            )
        }
        composable<EditBookRoute> {
            EditBookScreen(
                viewModel = viewModel, 
                onBack = { navController.popBackStack() },
                onNavigateToAiSettings = { navController.navigate(AiSettingsRoute) },
                onNavigateToUsage = { navController.navigate(UsageRoute) }
            )
        }
    }
}
