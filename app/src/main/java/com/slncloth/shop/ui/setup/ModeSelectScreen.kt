package com.slncloth.shop.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.materialIconsFilled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHostController
import com.slncloth.shop.R
import com.slncloth.shop.ui.components.ShopBackground
import com.slncloth.shop.ui.components.PrimaryButton
import com.slncloth.shop.ui.local.AppPrefsLocal
import com.slncloth.shop.util.AppMode

@Composable
fun ModeSelectScreen(navController: NavHostController) {
    val prefs = AppPrefsLocal.current
    var selected by remember { mutableStateOf(AppMode.MAIN) }

    ShopBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = materialIconsFilled.Storefront,
                contentDescription = "Shop",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Sri Lakshmi Narasimha Cloth and Readymade",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Bustand Road, Penagalur, 516127",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(modifier = Modifier.padding(top = 32.dp)) {
                modeCard(
                    title = "Main phone",
                    subtitle = "Runs the shop server and database",
                    icon = materialIconsFilled.DeviceHub,
                    selected = selected == AppMode.MAIN,
                    onClick = { selected = AppMode.MAIN },
                )
                modeCard(
                    title = "Other phone",
                    subtitle = "Connects to the main phone over hotspot",
                    icon = materialIconsFilled.Smartphone,
                    selected = selected == AppMode.CLIENT,
                    onClick = { selected = AppMode.CLIENT },
                )
            }

                PrimaryButton(
                onClick = {
                    prefs.mode = selected
                    navController.navigate(if (selected == com.slncloth.shop.util.AppMode.MAIN) "wizard" else "pair")
                },
                content = { Text("Continue") },
            )
        }
    }
}

@Composable
private fun modeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
    ) {
        androidx.compose.material3.ListItem(
            leading = { androidx.compose.material3.Icon(imageVector = icon, contentDescription = null) },
            text = { Text(title, style = MaterialTheme.typography.titleMedium) },
            secondaryText = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
            trailing = { androidx.compose.material3.Icon(imageVector = materialIconsFilled.Check, contentDescription = null) },
        )
    }
}
