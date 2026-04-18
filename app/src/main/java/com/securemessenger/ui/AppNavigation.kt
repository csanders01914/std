package com.securemessenger.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securemessenger.ui.contactlist.ContactListScreen
import com.securemessenger.ui.chat.ChatScreen
import com.securemessenger.ui.qr.QRScreen

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "contacts") {
        composable("contacts") {
            ContactListScreen(
                onContactClick = { onion -> nav.navigate("chat/$onion") },
                onAddContact   = { nav.navigate("qr") },
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
    }
}
