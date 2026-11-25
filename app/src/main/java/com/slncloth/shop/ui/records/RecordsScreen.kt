package com.slncloth.shop.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalTabs
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.materialIconsFilled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHostController
import com.slncloth.shop.client.ApiClient
import com.slncloth.shop.client.ShopApi
import com.slncloth.shop.core.api.CustomerDto
import com.slncloth.shop.core.money.Money
import com.slncloth.shop.ui.local.AppPrefsLocal
import com.slncloth.shop.ui.local.SecureStoreLocal
import com.slncloth.shop.ui.components.ErrorText

@Composable
fun RecordsScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val prefs = AppPrefsLocal.current
    val secureStore = SecureStoreLocal.current
    var tab by remember { mutableStateOf(0) } // 0 customers, 1 bills, 2 wallet
    var search by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {

        TextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search customers, bills or mobile") },
            modifier = Modifier.fillMaxWidth(),
        )

        ScrollableTabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth()) {
            Tab(
                text = { Text("Customers") },
                selected = tab == 0,
                onClick = { tab = 0 },
                icon = { androidx.compose.material3.Icon(imageVector = materialIconsFilled.People, contentDescription = null) },
            )
            Tab(
                text = { Text("Bills") },
                selected = tab == 1,
                onClick = { tab = 1 },
                icon = { androidx.compose.material3.Icon(imageVector = materialIconsFilled.Receipt, contentDescription = null) },
            )
            Tab(
                text = { Text("Wallet") },
                selected = tab == 2,
                onClick = { tab = 2 },
                icon = { androidx.compose.material3.Icon(imageVector = materialIconsFilled.AccountBalanceWallet, contentDescription = null) },
            )
        }

        when (tab) {
            0 -> CustomersTab(search, navController)
            1 -> BillsTab(search, navController)
            2 -> WalletTab(navController)
        }
    }
}

@Composable
fun CustomersTab(search: String, navController: NavHostController) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Customers", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { /* open add customer */ }) { Text("Add customer") }
            LazyCustomerList(items = emptyList(), search = search)
        }
    }
}

@Composable
fun BillsTab(search: String, navController: NavHostController) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Bills", style = MaterialTheme.typography.titleLarge)
            LazyBillList(items = emptyList(), search = search)
        }
    }
}

@Composable
fun WalletTab(navController: NavHostController) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Wallet ledger", style = MaterialTheme.typography.titleLarge)
            Text("Owner-only adjustments require the owner PIN.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun LazyCustomerList(items: List<CustomerDto>, search: String) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items) { c ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.Center) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = c.name, style = MaterialTheme.typography.bodyMedium)
                    Text(text = c.mobile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(text = Money.format(c.walletBalancePaise), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

private fun LazyBillList(items: List<Any>, search: String) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items) { _ ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.Center) {
                Text(text = "Bill —", style = MaterialTheme.typography.bodyMedium)
                Text(text = "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
