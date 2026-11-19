package app.transmissionremote.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val pending by vm.pending.collectAsState()
    LaunchedEffect(pending) {
        if (pending != null) nav.navigate("add") { launchSingleTop = true }
    }
    NavHost(nav, startDestination = "list") {
        composable("list") {
            ListScreen(
                vm = vm,
                onOpen = { t -> nav.navigate("detail/${t.serverId}/${t.id}") },
                onAdd = { nav.navigate("add") { launchSingleTop = true } },
                onServers = { nav.navigate("servers") },
                onSession = { sid -> nav.navigate("session/$sid") },
                onStats = { nav.navigate("stats") },
            )
        }
        composable(
            "detail/{sid}/{tid}",
            arguments = listOf(navArgument("tid") { type = NavType.IntType }),
        ) { e ->
            DetailScreen(
                vm = vm,
                sid = e.arguments?.getString("sid").orEmpty(),
                tid = e.arguments?.getInt("tid") ?: 0,
                onBack = { nav.popBackStack() },
            )
        }
        composable("add") { AddScreen(vm, onBack = { nav.popBackStack() }) }
        composable("servers") { ServersScreen(vm, onBack = { nav.popBackStack() }) }
        composable("session/{sid}") { e ->
            SessionScreen(vm, e.arguments?.getString("sid").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable("stats") { StatsScreen(vm, onBack = { nav.popBackStack() }) }
    }
}
