package com.altron.alertbuddy.ui.theme.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.altron.alertbuddy.data.AlertRepository
import com.altron.alertbuddy.ui.theme.screens.*
import com.altron.alertbuddy.ui.theme.screens.*

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object ChannelList : Screen("channels")
    object MessageList : Screen("messages/{channelId}/{channelName}") {
        fun createRoute(channelId: String, channelName: String) =
            "messages/$channelId/${java.net.URLEncoder.encode(channelName, "UTF-8")}"
    }
    object MessageDetail : Screen("message/{messageId}") {
        fun createRoute(messageId: String) = "message/$messageId"
    }
    object Settings : Screen("settings")
}

@Composable
fun AlertBuddyNavigation(
    repository: AlertRepository,
    isLoggedIn: Boolean,
    onLoginSuccess: () -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Screen.ChannelList.route else Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                repository = repository,
                onLoginSuccess = {
                    onLoginSuccess()
                    navController.navigate(Screen.ChannelList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ChannelList.route) {
            ChannelListScreen(
                repository = repository,
                onChannelClick = { channelId, channelName ->
                    navController.navigate(Screen.MessageList.createRoute(channelId, channelName))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.MessageList.route,
            arguments = listOf(
                navArgument("channelId") { type = NavType.StringType },
                navArgument("channelName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
            val channelName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("channelName") ?: "",
                "UTF-8"
            )
            MessageListScreen(
                repository = repository,
                channelId = channelId,
                channelName = channelName,
                onMessageClick = { messageId ->
                    navController.navigate(Screen.MessageDetail.createRoute(messageId))
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MessageDetail.route,
            arguments = listOf(
                navArgument("messageId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val messageId = backStackEntry.arguments?.getString("messageId") ?: ""
            MessageDetailScreen(
                repository = repository,
                messageId = messageId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                repository = repository,
                onBackClick = { navController.popBackStack() },
                onLogout = {
                    onLogout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
