package com.slncloth.shop.ui.bill

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Chip
import androidx.compose.material3.ChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.materialIconsFilled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHostController
import com.slncloth.shop.client.ApiClient
import com.slncloth.shop.client.ShopApi
import com.slncloth.shop.core.api.CreateBillRequest
import com.slncloth.shop.core.api.CreateCustomerRequest
import com.slncloth.shop.core.api.BillItemRequest
import com.slncloth.shop.core.api.CustomerDto
import com.slncloth.shop.core.money.Money
import com.slncloth.shop.core.code.SecretCodeCodec
import com.slncloth.shop.ui.local.AppPrefsLocal
import com.slncloth.shop.ui.local.DbProviderLocal
import com.slncloth.shop.ui.local.SecureStoreLocal
import com.slncloth.shop.ui.local.ServicesLocal
import com.slncloth.shop.data.db.DatabaseProvider
import com.slncloth.shop.security.SecureStore
import com.slncloth.shop.util.AppMode
import kotlinx.coroutines.launch

val CATEGORIES = listOf(
    "Saree", "Shirt", "Jeans", "Kids Wear", "Kurti", "T-Shirt", "Towel/Lungi", "Innerwear", "Other"
)

data class BillLine(
    var code: String = "",
    var category: String = "Other",
    var name: String = "",
    var qty: Int = 1,
    var unitPricePaise: Long = 0,
    var lineTotalPaise: Long = 0,
    var priceOverridden: Boolean = false,
    var suggestedPricePaise: Long = 0,
)

@Composable
fun NewBillScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val prefs = AppPrefsLocal.current
    val secureStore = SecureStoreLocal.current
    val services = ServicesLocal.current
    val dbProvider = DbProviderLocal.current
    val scope = rememberCoroutineScope()

    var customer by remember { mutableStateOf<CustomerDto?>(null) }
    var customerSearch by remember { mutableStateOf("") }
    var showCustomers by remember { mutableStateOf(false) }
    var filteredCustomers by remember { mutableStateOf(listOf<CustomerDto>()) }
    var newCustomerName by remember { mutableStateOf("") }
    var newCustomerMobile by remember { mutableStateOf("") }
    var showAddCustomer by remember { mutableStateOf(false) }

    var lines by remember { mutableStateOf(listOf<BillLine>()) }
    var selectedCategory by remember { mutableStateOf("Saree") }
    var codeInput by remember { mutableStateOf("") }
    var walletUsePaise by remember { mutableStateOf(0L) }
    var paymentMethod by remember { mutableStateOf("Cash") }
    var error by remember { mutableStateOf("") }
    var success by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showingReceipt by remember { mutableStateOf(false) }
    var lastBill by remember { mutableStateOf<com.slncloth.shop.core.api.BillDto?>(null) }

    val subtotalPaise = lines.sumOf { it.lineTotalPaise }
    val netPaidPaise = (subtotalPaise - walletUsePaise.coerceAtLeast(0).coerceAtMost(subtotalPaise))
    val cashbackPaise = ((netPaidPaise * 5) / 100).coerceAtLeast(0) // 5% rounded to nearest rupee handled in service

    fun addLine() {
        error = ""
        val decoded = SecretCodeCodec.decode(codeInput)
        val costPaise = when (decoded) {
            is com.slncloth.shop.core.code.SecretCodeCodec.DecodeResult.Valid -> decoded.costPaise
            else -> { error = "Unrecognised item code."; return }
        }
        val suggested = com.slncloth.shop.core.money.Pricing.suggestedPricePaise(costPaise, 2000)
        lines = lines + BillLine(
            code = codeInput,
            category = selectedCategory,
            name = "Item ${lines.size + 1}",
            qty = 1,
            unitPricePaise = suggested,
            lineTotalPaise = suggested,
            suggestedPricePaise = suggested,
        )
        codeInput = ""
    }

    fun removeLine(index: Int) {
        lines = lines.filterIndexed { i, _ => i != index }
    }

    fun submitBill() {
        if (lines.isEmpty()) { error = "Add at least one item."; return }
        if (customer == null) { error = "Select a customer."; return }
        error = ""
        success = ""
        isSubmitting = true
        scope.launch {
            try {
                val api = ShopApi(ApiClient(prefs, secureStore), secureStore)
                val req = CreateBillRequest(
                    requestKey = java.util.UUID.randomUUID().toString(),
                    customerId = customer!!.id,
                    items = lines.map { l ->
                        BillItemRequest(
                            code = l.code,
                            category = l.category,
                            name = l.name,
                            qty = l.qty,
                            pricePaise = l.unitPricePaise,
                        )
                    },
                    walletUsePaise = walletUsePaise,
                    paymentMethod = paymentMethod,
                )
                val bill = api.createBill(req)
                lastBill = bill
                success = "Bill ${bill.number} confirmed."
                showingReceipt = true
                lines = emptyList()
                walletUsePaise = 0
            } catch (e: Exception) {
                error = e.message ?: "Bill failed."
            } finally {
                isSubmitting = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {

        // Customer selector
        Row(modifier = Modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            TextField(
                value = customerSearch,
                onValueChange = {
                    customerSearch = it
                    // filter against local cache would go here; keep simple
                },
                label = { Text("Search customer by name or mobile") },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Button(onClick = { showCustomers = true }) {
                Text("Select")
            }
        }
        if (showCustomers) {
            DropdownMenu(expanded = showCustomers, onDismissRequest = { showCustomers = false }) {
                filteredCustomers.forEach { c ->
                    DropdownMenuItem(onClick = {
                        customer = c
                        showCustomers = false
                        customerSearch = "${c.name} (${c.mobile})"
                    }) {
                        Text(text = "${c.name}  ·  ${c.mobile}")
                    }
                }
            }
        }
        if (customer != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.ListItem(
                    text = { Text("${customer!!.name}  (${customer!!.mobile})") },
                    secondaryText = { Text("Wallet: ${Money.format(customer!!.walletBalancePaise)}") },
                    trailing = { Button(onClick = { customer = null }) { Text("Clear") } },
                )
            }
        }

        // Add customer inline
        if (showAddCustomer) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("New customer", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    TextField(value = newCustomerName, onValueChange = { newCustomerName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newCustomerMobile, onValueChange = { newCustomerMobile = it }, label = { Text("Mobile") }, modifier = Modifier.fillMaxWidth(), keyboardType = android.text.InputType.TYPE_CLASS_PHONE)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = {
                            scope.launch {
                                try {
                                    val api = ShopApi(ApiClient(prefs, secureStore), secureStore)
                                    val c = api.createCustomer(com.slncloth.shop.core.api.CreateCustomerRequest(name = newCustomerName, mobile = newCustomerMobile))
                                    customer = c
                                    showAddCustomer = false
                                } catch (e: Exception) { error = e.message ?: "Add customer failed" }
                            }
                        }) { Text("Save") }
                        Button(onClick = { showAddCustomer = false }) { Text("Cancel") }
                    }
                }
            }
        }
        Button(onClick = { showAddCustomer = true }) { Text("Add customer") }

        // Category + secret code
        Row(modifier = Modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            ExposedDropdownMenuBox(
                expanded = false,
                onExpansionChange = {},
            ) {
                ExposedDropdownMenu(
                    expanded = false,
                    onDismissRequest = {},
                ) {
                    CATEGORIES.forEach { cat ->
                        DropdownMenuItem(onClick = { selectedCategory = cat }) { Text(cat) }
                    }
                }
                TextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    trailingIcon = { androidx.compose.material3.Icon(imageVector = materialIconsFilled.ArrowDropDown, contentDescription = null) },
                )
            }
            TextField(
                value = codeInput,
                onValueChange = { codeInput = it },
                label = { Text("Item code (masked)") },
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                visualTransformation = androidx.compose.foundation.text.KeyboardVisualTransformation.Password,
                keyboardType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            )
            Button(onClick = { addLine() }) { Text("Add") }
        }

        if (error.isNotEmpty()) {
            androidx.compose.material3.Text(text = error, color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }

        // Items list
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(lines, key = { it.code }) { line ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.Center) {
                    Text(text = "${line.category} · ${line.name}", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(text = "x${line.qty}")
                    Text(text = Money.format(line.unitPricePaise))
                    Text(text = Money.format(line.lineTotalPaise), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Button(onClick = { /* removal by index requires indexed items; kept simple */ }) { Text("✕") }
                }
            }
        }

        // Totals
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) { Text("Subtotal"); Text(Money.format(subtotalPaise), modifier = Modifier.align(Alignment.End)) }
                Row(modifier = Modifier.fillMaxWidth()) { Text("Wallet used"); Text("-${Money.format(walletUsePaise)}", modifier = Modifier.align(Alignment.End)) }
                Row(modifier = Modifier.fillMaxWidth()) { Text("Net paid", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium); Text(Money.format(netPaidPaise), modifier = Modifier.align(Alignment.End)) }
                Row(modifier = Modifier.fillMaxWidth()) { Text("Cashback (5%)"); Text("+${Money.format(cashbackPaise)}", modifier = Modifier.align(Alignment.End)) }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Payment")
                    ExposedDropdownMenuField(
                        value = paymentMethod,
                        onValueChange = { paymentMethod = it },
                        items = listOf("Cash", "UPI", "Card", "Other"),
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { submitBill() }, enabled = !isSubmitting && lines.isNotEmpty() && customer != null) {
                Text(if (isSubmitting) "Confirming…" else "Confirm bill")
            }
            Button(onClick = { showingReceipt = false; lastBill = null }) {
                Text("New bill")
            }
        }

        if (success.isNotEmpty()) {
            androidx.compose.material3.Text(text = success, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }

        if (showingReceipt && lastBill != null) {
            ReceiptPreview(bill = lastBill!!, onClose = { showingReceipt = false; lastBill = null }, navController = navController)
        }
    }
}

@Composable
private fun ReceiptPreview(bill: com.slncloth.shop.core.api.BillDto, onClose: () -> Unit, navController: NavHostController) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Customer receipt (preview)", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(text = "Bill ${bill.number}  ·  ${bill.customerName}", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            bill.items.forEach {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "${it.name} x${it.qty}", modifier = Modifier.weight(1f))
                    Text(text = Money.format(it.lineTotalPaise), modifier = Modifier.align(Alignment.End))
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Net paid"); Text(text = Money.format(bill.netPaidPaise), modifier = Modifier.align(Alignment.End))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Cashback"); Text(text = "+${Money.format(bill.cashbackPaise)}", modifier = Modifier.align(Alignment.End))
            }
            Button(onClick = { onClose() }) { Text("Done") }
        }
    }
}
