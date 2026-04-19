package com.securemessenger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securemessenger.SecureMessengerApp
import com.securemessenger.ui.contactlist.ContactListScreen
import com.securemessenger.ui.chat.ChatScreen
import com.securemessenger.ui.group.GroupChatScreen
import com.securemessenger.ui.group.GroupCreateScreen
import com.securemessenger.ui.qr.QRScreen
import java.util.UUID

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val context = LocalContext.current.applicationContext as SecureMessengerApp
    val torStatus by context.messagingService.torStatus.collectAsState()

    NavHost(navController = nav, startDestination = "splash") {
        composable("splash") {
            SplashScreen(
                status = torStatus,
                onReady = {
                    if (nav.currentDestination?.route == "splash") {
                        nav.navigate("contacts") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            )
        }
        composable("contacts") {
            ContactListScreen(
                onContactClick = { onion -> nav.navigate("chat/$onion") },
                onGroupClick   = { groupId -> nav.navigate("group/$groupId") },
                onAddContact   = { nav.navigate("qr") },
                onNewGroup     = { nav.navigate("group_create") },
            )
        }
        composable("qr") {
            QRScreen(onDone = { nav.popBackStack() })
        }
        composable(
            route = "chat/{onion}",
            arguments = listOf(navArgument("onion") { type = NavType.StringType }),
        ) { backStack ->
            ChatScreen(
                onionAddress = backStack.arguments!!.getString("onion")!!,
                onBack = { nav.popBackStack() },
            )
        }
        composable("group_create") {
            GroupCreateScreen(
                onGroupCreated = { groupId ->
                    nav.navigate("group/$groupId") {
                        popUpTo("group_create") { inclusive = true }
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "group/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStack ->
            GroupChatScreen(
                groupId = UUID.fromString(backStack.arguments!!.getString("groupId")!!),
                onBack  = { nav.popBackStack() },
            )
        }
    }
}
