package com.slncloth.shop.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.bottomNavigation.BottomNavigation
import androidx.compose.material3.bottomNavigation.BottomNavigationItem
import androidx.compose.material3.internal.ExperimentalMaterialApi
import androidx.compose.material3.materialIconsFilled
import androidx.compose.material3.materialIconsOutlined
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navController
import androidx.navigation.compose.rememberNavController
import com.slncloth.shop.ui.bill.NewBillScreen
import com.slncloth.shop.ui.overview.OverviewScreen
import com.slncloth.shop.ui.records.RecordsScreen
import com.slncloth.shop.ui.settings.SettingsScreen
import com.slncloth.shop.ui.setup.ModeSelectScreen
import com.slncloth.shop.ui.setup.SetupWizardScreen
import com.slncloth.shop.ui.setup.ServerStartScreen
import com.slncloth.shop.ui.setup.PairingScreen
import com.slncloth.shop.ui.setup.ScanScreen
import com.slncloth.shop.ui.setup.LoginScreen
import com.slncloth.shop.ui.setup.QrDisplayScreen
import com.slncloth.shop.ui.local.AppPrefsLocal
import com.slncloth.shop.ui.local.SecureStoreLocal
import com.slncloth.shop.ui.local.TlsLocal
import com.slncloth.shop.ui.local.DbProviderLocal
import com.slncloth.shop.ui.local.ServicesLocal

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppNavigator() {
    val prefs = AppPrefsLocal.current
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            if (prefs.configured && prefs.onboardingDone) {
                BottomNavigation(modifier = Modifier.fillMaxWidth()) {
                    val navBackStack by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStack?.destination?.route
                    BottomNavigationItem(
                        icon = { Icon(imageVector = materialIconsFilled.Receipt, contentDescription = null) },
                        label = { Text("Bill") },
                        selected = currentRoute == "bill",
                        onClick = { navController.navigate("bill") { popUpTo(id = navController.navController.id) { inclusive = true } } },
                    )
                    BottomNavigationItem(
                        icon = { Icon(imageVector = materialIconsOutlined.People, contentDescription = null) },
                        label = { Text("Records") },
                        selected = currentRoute == "records",
                        onClick = { navController.navigate("records") { popUpTo(id = navController.navController.id) { inclusive = true } } },
                    )
                    BottomNavigationItem(
                        icon = { Icon(imageVector = materialIconsOutlined.InsertChart, contentDescription = null) },
                        label = { Text("Overview") },
                        selected = currentRoute == "overview",
                        onClick = { navController.navigate("overview") { popUpTo(id = navController.navController.id) { inclusive = true } } },
                    )
                    BottomNavigationItem(
                        icon = { Icon(imageVector = materialIconsOutlined.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                        selected = currentRoute == "settings",
                        onClick = { navController.navigate("settings") { popUpTo(id = navController.navController.id) { inclusive = true } } },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = "setup") {
            composable("setup") { ModeSelectScreen(navController) }
            composable("wizard") { SetupWizardScreen(navController) }
            composable("server") { ServerStartScreen(navController) }
            composable("login") { LoginScreen(navController) }
            composable("pair") { PairingScreen(navController) }
            composable("scan") { ScanScreen(navController) }
            composable("qr") { QrDisplayScreen() }
            composable("bill") { NewBillScreen(navController, modifier = Modifier.padding(innerPadding)) }
            composable("records") { RecordsScreen(navController, modifier = Modifier.padding(innerPadding)) }
            composable("overview") { OverviewScreen(navController, modifier = Modifier.padding(innerPadding)) }
            composable("settings") { SettingsScreen(navController, modifier = Modifier.padding(innerPadding)) }
        }
    }
}
