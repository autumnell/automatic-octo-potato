package com.mtgscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mtgscanner.ui.collection.CollectionScreen
import com.mtgscanner.ui.detail.CardDetailScreen
import com.mtgscanner.ui.scan.ScanScreen
import com.mtgscanner.ui.search.SearchScreen
import com.mtgscanner.ui.theme.MtgTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MtgTheme { AppRoot() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("scan", "Scan", Icons.Filled.CameraAlt),
    Tab("search", "Search", Icons.Filled.Search),
    Tab("collection", "Collection", Icons.Filled.ViewList),
)

@androidx.compose.runtime.Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "scan", modifier = Modifier.padding(padding)) {
            composable("scan") { ScanScreen(onOpenCard = { nav.navigate("card/$it") }) }
            composable("search") { SearchScreen() }
            composable("collection") { CollectionScreen(onOpenCard = { nav.navigate("card/$it") }) }
            composable("card/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                CardDetailScreen(id = it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
        }
    }
}
