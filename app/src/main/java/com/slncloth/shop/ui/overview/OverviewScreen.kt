package com.slncloth.shop.ui.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHostController
import com.slncloth.shop.client.ApiClient
import com.slncloth.shop.client.ShopApi
import com.slncloth.shop.ui.local.AppPrefsLocal
import com.slncloth.shop.ui.local.SecureStoreLocal
import com.slncloth.shop.core.money.Money

@Composable
fun OverviewScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val prefs = AppPrefsLocal.current
    val secureStore = SecureStoreLocal.current
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {

        Text("Overview", style = MaterialTheme.typography.titleLarge)
        Text("Owner-only: sales, buying cost, profit and margin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(onClick = { /* load owner data */ }) {
            Text("Refresh")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Sales"); Text("—", modifier = androidx.compose.foundation.layout.Alignment.End)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Buying cost"); Text("—", modifier = androidx.compose.foundation.layout.Alignment.End)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Profit"); Text("—", modifier = androidx.compose.foundation.layout.Alignment.End)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Margin"); Text("—", modifier = androidx.compose.foundation.layout.Alignment.End)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Markup"); Text("—", modifier = androidx.compose.foundation.layout.Alignment.End)
                }
                Text("Markup = profit ÷ cost. Margin = profit ÷ selling price.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (error.isNotEmpty()) {
            androidx.compose.material3.Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
