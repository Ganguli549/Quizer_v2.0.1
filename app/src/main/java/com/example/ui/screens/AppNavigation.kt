package com.example.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
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
    val showAiAssistant by viewModel.showAiAssistant.collectAsState()

    // Steps back exactly ONE screen on the navigation stack (with RESUMED guard to prevent double-tap multi-pop)
    val navigateBackOneStep: () -> Unit = {
        val currentEntry = navController.currentBackStackEntry
        val isResumed = currentEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
        if (isResumed) {
            if (!navController.popBackStack()) {
                navController.navigate(HomeRoute) {
                    popUpTo(HomeRoute) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    // Explicitly returns all the way to HomeScreen (used by the "Return Home" button on ResultScreen)
    val navigateToHomeRoot: () -> Unit = {
        if (!navController.popBackStack(HomeRoute, inclusive = false)) {
            navController.navigate(HomeRoute) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(showAiAssistant) {
        if (showAiAssistant) {
            val targetMode = viewModel.targetAiMode
            val targetRoute: Any = if (targetMode != null && targetMode.endsWith("_read")) {
                ReadBookRoute
            } else {
                EditBookRoute
            }

            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != targetRoute::class.qualifiedName) {
                if (targetRoute is ReadBookRoute) {
                    navController.navigate(ReadBookRoute) { launchSingleTop = true }
                } else {
                    navController.navigate(EditBookRoute) { launchSingleTop = true }
                }
            }
        }
    }

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
                onStartQuiz = { navController.navigate(QuizRoute) { launchSingleTop = true } },
                onNavigateStats = { navController.navigate(StatsRoute) { launchSingleTop = true } },
                onNavigateHistory = { navController.navigate(HistoryRoute) { launchSingleTop = true } },
                onNavigateSearch = { navController.navigate(SearchRoute) { launchSingleTop = true } },
                onNavigateBookmarks = { navController.navigate(BookmarksRoute) { launchSingleTop = true } },
                onNavigateLearn = { navController.navigate(LearnRoute) { launchSingleTop = true } },
                onNavigateAnalysis = { navController.navigate(AnalysisRoute) { launchSingleTop = true } },
                onNavigateSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                onNavigateReadBook = { navController.navigate(ReadBookRoute) { launchSingleTop = true } }
            )
        }
        composable<QuizRoute> {
            QuizScreen(
                viewModel = viewModel,
                onFinish = {
                    navController.navigate(ResultsRoute) {
                        popUpTo(QuizRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onCancel = navigateBackOneStep
            )
        }
        composable<ResultsRoute> {
            ResultScreen(
                viewModel = viewModel,
                onHome = navigateToHomeRoot,
                onBack = navigateBackOneStep,
                onReview = { navController.navigate(ReviewRoute) { launchSingleTop = true } },
                onPracticeWrong = {
                    navController.navigate(QuizRoute) {
                        popUpTo(ResultsRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(
                viewModel = viewModel,
                onHome = navigateBackOneStep,
                onReview = { navController.navigate(ReviewRoute) { launchSingleTop = true } },
                onStartQuiz = { navController.navigate(QuizRoute) { launchSingleTop = true } }
            )
        }
        composable<StatsRoute> {
            StatsScreen(
                viewModel = viewModel,
                onHome = navigateBackOneStep,
                onNavigateAnalysis = {
                    val prevRoute = navController.previousBackStackEntry?.destination?.route
                    if (prevRoute == AnalysisRoute::class.qualifiedName) {
                        navigateBackOneStep()
                    } else {
                        navController.navigate(AnalysisRoute) { launchSingleTop = true }
                    }
                },
                onReviewQuiz = { navController.navigate(ReviewRoute) { launchSingleTop = true } }
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                viewModel = viewModel,
                onHome = navigateBackOneStep,
                onStartQuiz = { navController.navigate(QuizRoute) { launchSingleTop = true } }
            )
        }
        composable<BookmarksRoute> {
            BookmarksScreen(
                viewModel = viewModel,
                onHome = navigateBackOneStep,
                onStartQuiz = { navController.navigate(QuizRoute) { launchSingleTop = true } }
            )
        }
        composable<LearnRoute> {
            LearnScreen(
                viewModel = viewModel,
                onHome = navigateBackOneStep,
                onStartQuiz = { navController.navigate(QuizRoute) { launchSingleTop = true } }
            )
        }
        composable<AnalysisRoute> {
            AnalysisScreen(
                viewModel = viewModel,
                onHome = navigateBackOneStep,
                onStartQuiz = { navController.navigate(QuizRoute) { launchSingleTop = true } },
                onNavigateStats = {
                    val prevRoute = navController.previousBackStackEntry?.destination?.route
                    if (prevRoute == StatsRoute::class.qualifiedName) {
                        navigateBackOneStep()
                    } else {
                        navController.navigate(StatsRoute) { launchSingleTop = true }
                    }
                }
            )
        }
        composable<ReviewRoute> {
            ReviewScreen(
                viewModel = viewModel,
                onHome = navigateBackOneStep
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = viewModel,
                onBack = navigateBackOneStep,
                onNavigateToAiSettings = { navController.navigate(AiSettingsRoute) { launchSingleTop = true } }
            )
        }
        composable<AiSettingsRoute> {
            AiSettingsScreen(
                viewModel = viewModel,
                onBack = navigateBackOneStep,
                onNavigateToUsage = { navController.navigate(UsageRoute) { launchSingleTop = true } }
            )
        }
        composable<UsageRoute> {
            UsageScreen(onNavigateBack = navigateBackOneStep)
        }
        composable<ReadBookRoute> {
            ReadBookScreen(
                viewModel = viewModel,
                onHome = navigateBackOneStep,
                onEditBook = { navController.navigate(EditBookRoute) { launchSingleTop = true } },
                onNavigateToAiSettings = { navController.navigate(AiSettingsRoute) { launchSingleTop = true } },
                onNavigateToUsage = { navController.navigate(UsageRoute) { launchSingleTop = true } }
            )
        }
        composable<EditBookRoute> {
            EditBookScreen(
                viewModel = viewModel,
                onBack = navigateBackOneStep,
                onNavigateToAiSettings = { navController.navigate(AiSettingsRoute) { launchSingleTop = true } },
                onNavigateToUsage = { navController.navigate(UsageRoute) { launchSingleTop = true } }
            )
        }
    }
}
