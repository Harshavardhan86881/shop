package com.slncloth.shop.core.api

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------------------
// Shared API contract. Every @Serializable class here is used by both the Ktor server
// and the Android client. Money is always integer paise.
// ---------------------------------------------------------------------------------------

@Serializable
data class HealthResponse(
    val ok: Boolean = true,
    val shopName: String,
    val fingerprint: String,
    val pairingEnabled: Boolean,
    val serverVersion: String,
    val dbVersion: Int,
)

@Serializable
data class PairRequest(
    val pairingToken: String,
    val deviceName: String,
    val clientVersion: String,
)

@Serializable
data class PairResponse(
    val deviceToken: String,
    val shopName: String,
    val serverVersion: String,
)

@Serializable
data class LoginRequest(val pin: String)

@Serializable
data class LoginResponse(
    val sessionToken: String,
    val role: String,
    val userName: String,
    val expiresAt: Long,
    val deviceId: String,
    val deviceName: String,
)

@Serializable
data class SessionResponse(
    val role: String,
    val userName: String,
    val expiresAt: Long,
    val deviceId: String,
    val deviceName: String,
)

@Serializable
data class ShopResponse(
    val shopName: String,
    val address: String,
    val cashbackBp: Int,
    val allowStaffPriceOverride: Boolean,
    val receiptFooter: String,
)

@Serializable
data class CustomerDto(
    val id: String,
    val name: String,
    val mobile: String,
    val walletBalancePaise: Long,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val totalBills: Int = 0,
    val lastBillAt: Long? = null,
)

@Serializable
data class CustomerHistoryDto(
    val billId: Long,
    val billNumber: String,
    val createdAt: Long,
    val subtotalPaise: Long,
    val netPaidPaise: Long,
    val cashbackPaise: Long,
    val status: String,
)

@Serializable
data class CustomerDetailDto(
    val customer: CustomerDto,
    val history: List<CustomerHistoryDto>,
    val ledger: List<WalletEntryDto>,
)

@Serializable
data class CreateCustomerRequest(
    val name: String,
    val mobile: String,
    val note: String = "",
)

@Serializable
data class UpdateCustomerRequest(
    val name: String,
    val mobile: String,
    val note: String = "",
)

@Serializable
data class MergeCustomerRequest(val targetId: String)

@Serializable
data class ResolveItemRequest(
    val code: String,
    val category: String,
    val name: String? = null,
    val qty: Int,
    /** Optional manual selling price. Ignored unless the role/setting allows it. */
    val pricePaise: Long? = null,
)

@Serializable
data class ResolveItemResponse(
    val category: String,
    val name: String,
    val qty: Int,
    val suggestedPricePaise: Long,
    val unitPricePaise: Long,
    val lineTotalPaise: Long,
    val priceOverridden: Boolean,
)

@Serializable
data class BillItemRequest(
    val code: String,
    val category: String,
    val name: String? = null,
    val qty: Int,
    val pricePaise: Long? = null,
)

@Serializable
data class CreateBillRequest(
    val requestKey: String,
    val customerId: String,
    val items: List<BillItemRequest>,
    val walletUsePaise: Long,
    val paymentMethod: String,
    /** Owner may allow the wallet to go negative on a reversal only; kept for clarity. */
    val allowNegativeWallet: Boolean = false,
)

@Serializable
data class BillItemDto(
    val lineNo: Int,
    val name: String,
    val category: String,
    val qty: Int,
    val unitPricePaise: Long,
    val lineTotalPaise: Long,
    val priceOverridden: Boolean,
)

@Serializable
data class BillDto(
    val id: Long,
    val number: String,
    val customerId: String,
    val customerName: String,
    val customerMobile: String,
    val createdAt: Long,
    val subtotalPaise: Long,
    val walletUsedPaise: Long,
    val netPaidPaise: Long,
    val cashbackPaise: Long,
    val paymentMethod: String,
    val status: String,
    val createdBy: String,
    val reversalReason: String? = null,
    val items: List<BillItemDto>,
)

@Serializable
data class BillSummaryDto(
    val id: Long,
    val number: String,
    val customerName: String,
    val customerMobile: String,
    val createdAt: Long,
    val subtotalPaise: Long,
    val netPaidPaise: Long,
    val cashbackPaise: Long,
    val paymentMethod: String,
    val status: String,
)

@Serializable
data class RequestStatusResponse(
    /** NOT_FOUND or COMPLETED */
    val state: String,
    val bill: BillDto? = null,
)

@Serializable
data class ReverseBillRequest(
    val reason: String,
    val allowNegativeWallet: Boolean = false,
)

@Serializable
data class WalletEntryDto(
    val id: Long,
    val customerId: String,
    val customerName: String,
    val billId: Long? = null,
    val billNumber: String? = null,
    val type: String,
    val direction: String,
    val amountPaise: Long,
    val balanceAfterPaise: Long,
    val reason: String,
    val actorName: String,
    val createdAt: Long,
)

@Serializable
data class WalletSummaryDto(
    val outstandingPaise: Long,
    val totalCreditsPaise: Long,
    val totalDebitsPaise: Long,
    val cashbackIssuedPaise: Long,
    val cashbackRedeemedPaise: Long,
    val entryCount: Int,
)

@Serializable
data class WalletAdjustRequest(
    val customerId: String,
    /** CREDIT or DEBIT */
    val direction: String,
    val amountPaise: Long,
    val reason: String,
)

@Serializable
data class WalletMismatch(
    val customerId: String,
    val customerName: String,
    val balancePaise: Long,
    val ledgerPaise: Long,
)

@Serializable
data class WalletVerifyResponse(
    val ok: Boolean,
    val mismatches: List<WalletMismatch> = emptyList(),
)

@Serializable
data class DailyPointDto(
    val day: String,
    val salesPaise: Long,
    val costPaise: Long,
    val profitPaise: Long,
    val cashbackPaise: Long,
)

@Serializable
data class CategoryBreakdownDto(
    val category: String,
    val quantity: Int,
    val salesPaise: Long,
    val costPaise: Long,
    val profitPaise: Long,
)

@Serializable
data class OverviewResponse(
    val from: Long,
    val to: Long,
    val salesPaise: Long,
    val costPaise: Long,
    val profitPaise: Long,
    val markupPercent: Double,
    val marginPercent: Double,
    val cashbackIssuedPaise: Long,
    val walletRedeemedPaise: Long,
    val billCount: Int,
    val itemCount: Int,
    val daily: List<DailyPointDto>,
    val categories: List<CategoryBreakdownDto>,
    val recentBills: List<BillSummaryDto>,
)

@Serializable
data class SettingsDto(
    val shopName: String,
    val address: String,
    val ownerName: String,
    val markupBp: Int,
    val cashbackBp: Int,
    val allowStaffPriceOverride: Boolean,
    val receiptFooter: String,
    val serverPort: Int,
    val bindAddress: String,
)

@Serializable
data class UpdateSettingsRequest(
    val shopName: String,
    val address: String,
    val ownerName: String,
    val markupBp: Int,
    val cashbackBp: Int,
    val allowStaffPriceOverride: Boolean,
    val receiptFooter: String,
    val serverPort: Int,
    val bindAddress: String,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val role: String,
    val active: Boolean,
    val createdAt: Long,
    val lastLoginAt: Long? = null,
)

@Serializable
data class CreateUserRequest(
    val name: String,
    val pin: String,
    val role: String = "STAFF",
)

@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val active: Boolean? = null,
    val pin: String? = null,
)

@Serializable
data class DeviceDto(
    val id: String,
    val name: String,
    val pairedAt: Long,
    val lastSeenAt: Long,
    val revokedAt: Long? = null,
)

@Serializable
data class PairingTokenResponse(
    val pairingToken: String,
    val url: String,
    val fingerprint: String,
    val expiresAt: Long,
    val shopName: String,
)

@Serializable
data class AuditEventDto(
    val id: Long,
    val createdAt: Long,
    val actorName: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val details: String,
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)

@Serializable
data class OkResponse(val ok: Boolean = true)
