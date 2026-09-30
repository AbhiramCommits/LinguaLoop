package com.lingualoop.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lingualoop.android.data.auth.TokenStore
import com.lingualoop.android.di.AppEntryPoint
import com.lingualoop.android.ui.home.HomeScreen
import com.lingualoop.android.ui.home.HomeViewModel
import com.lingualoop.android.ui.lesson.LessonScreen
import com.lingualoop.android.ui.lesson.LessonViewModel
import com.lingualoop.android.ui.login.LoginScreen
import com.lingualoop.android.ui.login.LoginViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val LESSON = "lesson/{lessonId}"

    fun lesson(lessonId: Long) = "lesson/$lessonId"
}

@Composable
fun LinguaLoopNavHost() {
    val context = LocalContext.current
    // EncryptedSharedPreferences + MasterKey touch the Android Keystore, which
    // can block; initialize the token store off the main thread so startup
    // never ANRs on slow devices or emulators.
    val tokenStoreState = produceState<TokenStore?>(initialValue = null) {
        value = withContext(Dispatchers.Default) {
            EntryPointAccessors.fromApplication(context, AppEntryPoint::class.java).tokenStore()
        }
    }
    val tokenStore = tokenStoreState.value
    if (tokenStore == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val navController = rememberNavController()
    val loggedIn by tokenStore.token.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (loggedIn != null) Routes.HOME else Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            val viewModel = hiltViewModel<LoginViewModel>()
            LoginScreen(
                viewModel = viewModel,
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            val viewModel = hiltViewModel<HomeViewModel>()
            HomeScreen(
                viewModel = viewModel,
                onLogout = {
                    tokenStore.clear()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenLesson = { lessonId ->
                    navController.navigate(Routes.lesson(lessonId))
                },
            )
        }
        composable(
            route = Routes.LESSON,
            arguments = listOf(navArgument("lessonId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val lessonId = backStackEntry.arguments?.getLong("lessonId") ?: return@composable
            val viewModel = hiltViewModel<LessonViewModel>()
            LessonScreen(
                lessonId = lessonId,
                viewModel = viewModel,
                onBackHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
            )
        }
    }
}
