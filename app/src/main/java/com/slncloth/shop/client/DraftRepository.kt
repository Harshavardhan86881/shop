package com.slncloth.shop.client

import com.slncloth.shop.core.api.AppJson
import com.slncloth.shop.data.db.DraftDao
import com.slncloth.shop.data.db.entity.DraftEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class DraftItem(
    val code: String,
    val category: String,
    val name: String,
    val qty: Int,
    val unitPricePaise: Long,
    val lineTotalPaise: Long,
    val priceOverridden: Boolean,
    val suggestedPricePaise: Long,
)

/** Persists unsent/uncertain bills locally. Nothing here is considered complete until the server confirms. */
class DraftRepository(
    private val dao: DraftDao,
    private val json: Json = AppJson.json,
) {

    private val itemListSerializer = ListSerializer(DraftItem.serializer())

    suspend fun save(
        id: String,
        requestKey: String,
        customerId: String,
        customerName: String,
        customerMobile: String,
        items: List<DraftItem>,
        walletUsePaise: Long,
        paymentMethod: String,
        status: String,
        lastError: String? = null,
        billId: Long? = null,
        billNumber: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val existing = dao.byId(id)
        dao.upsert(
            DraftEntity(
                id = id,
                requestKey = requestKey,
                customerId = customerId,
                customerName = customerName,
                customerMobile = customerMobile,
                itemsJson = json.encodeToString(itemListSerializer, items),
                walletUsePaise = walletUsePaise,
                paymentMethod = paymentMethod,
                status = status,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                lastError = lastError,
                billId = billId,
                billNumber = billNumber,
            )
        )
    }

    suspend fun byId(id: String): DraftEntity? = dao.byId(id)

    suspend fun all(): List<DraftEntity> = dao.all()

    suspend fun needingReconcile(): List<DraftEntity> = dao.needingReconcile()

    suspend fun editable(): List<DraftEntity> = dao.editable()

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun countNeedingReconcile(): Int = dao.countNeedingReconcile()

    suspend fun deleteOldDone(before: Long): Int = dao.deleteOldDone(before)

    fun itemsOf(draft: DraftEntity): List<DraftItem> =
        runCatching { json.decodeFromString(itemListSerializer, draft.itemsJson) }.getOrDefault(emptyList())

    companion object {
        const val STATUS_LOCAL = "LOCAL"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_UNCERTAIN = "UNCERTAIN"
        const val STATUS_REJECTED = "REJECTED"
        const val STATUS_DONE = "DONE"
    }
}
