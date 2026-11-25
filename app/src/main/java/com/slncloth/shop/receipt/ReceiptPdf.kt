package com.slncloth.shop.receipt

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.slncloth.shop.core.api.BillDto
import com.slncloth.shop.core.api.ShopResponse
import com.slncloth.shop.core.money.Money
import com.slncloth.shop.core.util.Format
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a customer-safe receipt as a PDF.
 *
 * Deliberately excluded: secret item code, buying cost, markup, profit and margin.
 */
object ReceiptPdf {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun render(context: Context, bill: BillDto, shop: ShopResponse): File {
        val document = PdfDocument()
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        var canvas = page.canvas
        var y = MARGIN

        val title = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val normal = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            typeface = Typeface.SANS_SERIF
            isAntiAlias = true
        }
        val bold = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val divider = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        fun newPageIfNeeded(needed: Float) {
            if (y + needed <= PAGE_HEIGHT - MARGIN) return
            document.finishPage(page)
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            canvas = page.canvas
            y = MARGIN
        }

        canvas.drawText(shop.shopName, MARGIN, y, title)
        y += 18f
        canvas.drawText(shop.address, MARGIN, y, normal)
        y += 24f
        canvas.drawText("Bill ${bill.number}", MARGIN, y, bold)
        y += 14f
        canvas.drawText("Date: ${Format.dateTime(bill.createdAt)}", MARGIN, y, normal)
        y += 14f
        canvas.drawText("Customer: ${bill.customerName}  (${bill.customerMobile})", MARGIN, y, normal)
        y += 10f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, divider)
        y += 16f

        val colItem = MARGIN
        val colQty = 340f
        val colPrice = 400f
        val colTotal = 490f

        canvas.drawText("Item", colItem, y, bold)
        canvas.drawText("Qty", colQty, y, bold)
        canvas.drawText("Price", colPrice, y, bold)
        canvas.drawText("Total", colTotal, y, bold)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, divider)
        y += 14f

        bill.items.forEach { item ->
            newPageIfNeeded(16f)
            val label = if (item.name.length > 36) item.name.take(34) + "…" else item.name
            canvas.drawText(label, colItem, y, normal)
            canvas.drawText(item.qty.toString(), colQty, y, normal)
            canvas.drawText(Money.format(item.unitPricePaise), colPrice, y, normal)
            canvas.drawText(Money.format(item.lineTotalPaise), colTotal, y, normal)
            y += 14f
        }

        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, divider)
        y += 16f

        fun row(label: String, amount: String, emphasize: Boolean = false) {
            newPageIfNeeded(16f)
            val paint = if (emphasize) bold else normal
            canvas.drawText(label, MARGIN, y, paint)
            canvas.drawText(amount, colTotal, y, paint)
            y += 14f
        }

        row("Subtotal", Money.format(bill.subtotalPaise))
        if (bill.walletUsedPaise > 0) {
            row("Wallet used", "-" + Money.format(bill.walletUsedPaise))
        }
        row("Net paid", Money.format(bill.netPaidPaise), emphasize = true)
        row("Cashback earned", Money.format(bill.cashbackPaise), emphasize = true)
        row("Payment method (recorded only)", bill.paymentMethod)

        y += 12f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, divider)
        y += 16f
        canvas.drawText("Thank you for your business.", MARGIN, y, normal)
        if (shop.receiptFooter.isNotBlank()) {
            y += 14f
            canvas.drawText(shop.receiptFooter.take(90), MARGIN, y, normal)
        }

        document.finishPage(page)

        val dir = File(context.cacheDir, "receipts")
        dir.mkdirs()
        val safeNumber = bill.number.replace(Regex("[^A-Za-z0-9-]"), "_")
        val file = File(dir, "bill-$safeNumber.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    /** Plain-text summary for WhatsApp/SMS sharing via the Android share sheet. */
    fun text(bill: BillDto, shop: ShopResponse): String = buildString {
        appendLine(shop.shopName)
        appendLine(shop.address)
        appendLine()
        appendLine("Bill ${bill.number} — ${Format.dateTime(bill.createdAt)}")
        appendLine("Customer: ${bill.customerName}")
        appendLine()
        bill.items.forEach {
            appendLine("${it.name}  ${it.qty} x ${Money.format(it.unitPricePaise)} = ${Money.format(it.lineTotalPaise)}")
        }
        appendLine()
        appendLine("Subtotal: ${Money.format(bill.subtotalPaise)}")
        if (bill.walletUsedPaise > 0) appendLine("Wallet used: -${Money.format(bill.walletUsedPaise)}")
        appendLine("Net paid: ${Money.format(bill.netPaidPaise)}")
        appendLine("Cashback earned: ${Money.format(bill.cashbackPaise)}")
        appendLine("Payment: ${bill.paymentMethod} (recorded, not verified)")
        if (shop.receiptFooter.isNotBlank()) appendLine(shop.receiptFooter)
    }
}
