package com.example.tools.finance

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelNumberField
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.lib.calc.ExtendedMathEngines
import com.example.lib.calc.FinanceEngine
import com.example.lib.format.NumberFormatters
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.storage.ExpenseEntity
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import java.io.File
import java.io.FileOutputStream
import kotlin.math.pow

import com.example.lib.calc.InvoiceLineItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinanceExtendedScreen(
    meta: ToolMeta,
    expenses: List<ExpenseEntity>,
    onAddExpense: (title: String, amount: Double, category: String, kind: String, note: String) -> Unit,
    onDeleteExpense: (Long) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val look = LocalDotkitLook.current
    val palette = look.palette
    val region = look.regionFormat
    val hue = ChapterHue.LIME

    // Invoice Builder state
    var bizName by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf("INV-001") }
    var taxPctStr by remember { mutableStateOf("0") }
    val invItems = remember { mutableStateListOf<InvoiceLineItem>() }
    var newItemDesc by remember { mutableStateOf("") }
    var newItemQty by remember { mutableStateOf("0") }
    var newItemPrice by remember { mutableStateOf("0") }

    // Interest / MF / FD-RD state (default 0)
    var principalStr by remember { mutableStateOf("0") }
    var rateStr by remember { mutableStateOf("0") }
    var yearsVal by remember { mutableIntStateOf(5) }
    var compoundFreq by remember { mutableIntStateOf(4) } // 1, 2, 4, 12
    var sipMode by remember { mutableStateOf(true) }
    var rdMode by remember { mutableStateOf(false) }

    // GST state (default 0)
    var gstAmountStr by remember { mutableStateOf("0") }
    var gstSlab by remember { mutableIntStateOf(18) }
    var gstInclusive by remember { mutableStateOf(false) }

    // Expense Tracker state
    var expTitle by remember { mutableStateOf("") }
    var expAmount by remember { mutableStateOf("0") }
    var expCat by remember { mutableStateOf("General") }
    var expKind by remember { mutableStateOf("EXPENSE") }

    fun writeInvoicePdf(out: java.io.OutputStream) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c: Canvas = page.canvas
        c.drawColor(Color.WHITE)
        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 22f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val mPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 12f; typeface = Typeface.MONOSPACE }
        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 13f }

        c.drawText("INVOICE $invoiceNo", 42f, 58f, hPaint)
        c.drawText("From: ${bizName.ifBlank { "My Business" }}", 42f, 92f, bPaint)
        c.drawText("Bill To: ${clientName.ifBlank { "Client" }}", 42f, 114f, bPaint)
        c.drawLine(42f, 134f, 553f, 134f, mPaint)

        var y = 160f
        c.drawText("ITEM", 42f, y, mPaint)
        c.drawText("QTY", 330f, y, mPaint)
        c.drawText("RATE", 400f, y, mPaint)
        c.drawText("AMOUNT", 485f, y, mPaint)
        y += 22f

        var sub = 0.0
        invItems.forEach { item ->
            val amt = item.qty * item.unitPrice
            sub += amt
            c.drawText(item.description.take(36), 42f, y, bPaint)
            c.drawText("${item.qty}", 330f, y, bPaint)
            c.drawText("%.2f".format(item.unitPrice), 400f, y, bPaint)
            c.drawText("%.2f".format(amt), 485f, y, bPaint)
            y += 20f
        }
        val taxPct = taxPctStr.toDoubleOrNull() ?: 0.0
        val taxAmt = sub * taxPct / 100.0
        y += 16f
        c.drawLine(42f, y, 553f, y, mPaint)
        y += 24f
        c.drawText("Subtotal: %.2f".format(sub), 370f, y, bPaint)
        y += 20f
        c.drawText("Tax (%.1f%%): %.2f".format(taxPct, taxAmt), 370f, y, bPaint)
        y += 24f
        c.drawText("TOTAL: %.2f".format(sub + taxAmt), 370f, y, hPaint)

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    val saveInvoiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { writeInvoicePdf(it) }
            onStatusToast("Invoice PDF saved.")
        }
    }

    val summaryText = when (meta.id) {
        "fin-invoice" -> {
            val sub = invItems.sumOf { it.qty * it.unitPrice }
            val tax = sub * ((taxPctStr.toDoubleOrNull() ?: 0.0) / 100.0)
            "Invoice $invoiceNo | To: $clientName | Subtotal: ${"%.2f".format(sub)} | Tax: ${"%.2f".format(tax)} | Total: ${"%.2f".format(sub + tax)}"
        }
        "fin-interest" -> {
            val p = principalStr.toDoubleOrNull() ?: 0.0
            val r = rateStr.toDoubleOrNull() ?: 0.0
            val si = FinanceEngine.calculateSimpleInterest(p, r, yearsVal.toDouble())
            val ci = FinanceEngine.calculateCompoundInterest(p, r, yearsVal.toDouble(), compoundFreq)
            "SI Maturity=${NumberFormatters.formatCurrency(si.maturityValue, region)} | CI Maturity=${NumberFormatters.formatCurrency(ci.maturityValue, region)}"
        }
        "fin-mf" -> {
            val p = principalStr.toDoubleOrNull() ?: 0.0
            val r = rateStr.toDoubleOrNull() ?: 0.0
            val res = if (sipMode) FinanceEngine.calculateSip(p, r, yearsVal) else FinanceEngine.calculateCompoundInterest(p, r, yearsVal.toDouble(), 1)
            "Invested=${NumberFormatters.formatCurrency(res.investedAmount, region)} | Wealth=${NumberFormatters.formatCurrency(res.maturityValue, region)}"
        }
        "fin-gst" -> {
            val res = FinanceEngine.calculateGst(gstAmountStr.toDoubleOrNull() ?: 0.0, gstSlab.toDouble(), gstInclusive)
            "Base=${NumberFormatters.formatCurrency(res.baseAmount, region)} | GST=${NumberFormatters.formatCurrency(res.gstAmount, region)} (CGST=${"%.2f".format(res.cgstAmount)}, SGST=${"%.2f".format(res.sgstAmount)}) | Total=${NumberFormatters.formatCurrency(res.totalAmount, region)}"
        }
        "fin-fdrd" -> {
            val p = principalStr.toDoubleOrNull() ?: 0.0
            val r = rateStr.toDoubleOrNull() ?: 0.0
            val res = if (rdMode) FinanceEngine.calculateRd(p, r, yearsVal * 12) else FinanceEngine.calculateCompoundInterest(p, r, yearsVal.toDouble(), 4)
            "Deposited=${NumberFormatters.formatCurrency(res.investedAmount, region)} | Gain=${NumberFormatters.formatCurrency(res.estimatedReturns, region)} | Maturity=${NumberFormatters.formatCurrency(res.maturityValue, region)}"
        }
        "fin-expense" -> buildString {
            appendLine("id,kind,category,title,amount,created_at")
            expenses.forEach { appendLine("${it.id},${it.kind},${it.category},${it.title.replace(",", " ")},${it.amount},${it.createdAt}") }
        }.trim()
        else -> ""
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = summaryText,
        outputFileName = if (meta.id == "fin-expense") "expenses.csv" else "${meta.id}.txt",
        outputKind = if (meta.id == "fin-expense") DataKind.CSV else DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = meta.tagline
    ) {
        when (meta.id) {
            "fin-invoice" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("INVOICE DETAILS", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(value = invoiceNo, onValueChange = { invoiceNo = it }, placeholder = "INV-001", chapterHue = hue, modifier = Modifier.weight(0.7f))
                            PixelNumberField(value = taxPctStr, onValueChange = { taxPctStr = it }, chapterHue = hue, modifier = Modifier.weight(0.5f))
                        }
                        PixelTextField(value = bizName, onValueChange = { bizName = it }, placeholder = "Your Business / Studio Name", chapterHue = hue)
                        PixelTextField(value = clientName, onValueChange = { clientName = it }, placeholder = "Client / Bill To Name", chapterHue = hue)
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("LINE ITEMS (${invItems.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelTextField(value = newItemDesc, onValueChange = { newItemDesc = it }, placeholder = "Service / Item", chapterHue = hue, modifier = Modifier.weight(1.4f))
                            PixelNumberField(value = newItemQty, onValueChange = { newItemQty = it }, chapterHue = hue, modifier = Modifier.weight(0.5f))
                            PixelNumberField(value = newItemPrice, onValueChange = { newItemPrice = it }, chapterHue = hue, modifier = Modifier.weight(0.7f))
                        }
                        PixelButton(text = "+ Add Line Item", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, enabled = newItemDesc.isNotBlank() && (newItemQty.toDoubleOrNull() ?: 0.0) > 0.0, onClick = {
                            invItems.add(InvoiceLineItem(newItemDesc.trim(), newItemQty.toDoubleOrNull() ?: 0.0, newItemPrice.toDoubleOrNull() ?: 0.0))
                            newItemDesc = ""; newItemQty = "0"; newItemPrice = "0"
                        })
                        invItems.forEachIndexed { idx, item ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.description} (${item.qty} × ${item.unitPrice})", style = DotkitType.bodyUi(13), color = palette.ink, modifier = Modifier.weight(1f))
                                PixelChip(label = "✕", onClick = { invItems.removeAt(idx) })
                            }
                        }
                        Text(summaryText, style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(text = "Save PDF", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { saveInvoiceLauncher.launch("${invoiceNo}.pdf") })
                            PixelButton(text = "Share PDF", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                val f = File(context.cacheDir, "${invoiceNo}_${System.currentTimeMillis()}.pdf")
                                FileOutputStream(f).use { writeInvoicePdf(it) }
                                ShareMediaUtils.shareFile(context, f, "application/pdf", "Share Invoice")
                            })
                        }
                    }
                }
            }

            "fin-interest", "fin-mf", "fin-fdrd" -> {
                var intSubTab by remember { mutableStateOf("CALC") } // CALC, FIRE
                if (meta.id == "fin-interest") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelChip("SI / CI Interest", selected = intSubTab == "CALC", chapterHue = hue, onClick = { intSubTab = "CALC" })
                        PixelChip("FIRE · Inflation · CAGR · Salary", selected = intSubTab == "FIRE", chapterHue = hue, onClick = { intSubTab = "FIRE" })
                    }
                }
                if (meta.id == "fin-interest" && intSubTab == "FIRE") {
                    var fireExpStr by remember { mutableStateOf("0") }
                    var fireSwrStr by remember { mutableStateOf("4") }
                    var cagrStart by remember { mutableStateOf("0") }
                    var cagrEnd by remember { mutableStateOf("0") }
                    var cagrYrs by remember { mutableStateOf("0") }
                    var inflRate by remember { mutableStateOf("0") }
                    var hourlyWage by remember { mutableStateOf("0") }

                    val fireTarget = ExtendedMathEngines.computeFireNumber(fireExpStr.toDoubleOrNull() ?: 0.0, fireSwrStr.toDoubleOrNull() ?: 4.0)
                    val cagrVal = ExtendedMathEngines.computeCagr(cagrStart.toDoubleOrNull() ?: 0.0, cagrEnd.toDoubleOrNull() ?: 0.0, cagrYrs.toDoubleOrNull() ?: 0.0)
                    val inflPow = (cagrStart.toDoubleOrNull() ?: 0.0) / (1.0 + ((inflRate.toDoubleOrNull() ?: 0.0) / 100.0)).pow(cagrYrs.toDoubleOrNull() ?: 0.0)
                    val hr = hourlyWage.toDoubleOrNull() ?: 0.0
                    PixelBox {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("FIRE INDEPENDENCE TARGET", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelNumberField(fireExpStr, { fireExpStr = it }, "Annual Expenses", chapterHue = hue, modifier = Modifier.weight(1.2f))
                                PixelNumberField(fireSwrStr, { fireSwrStr = it }, "SWR %", chapterHue = hue, modifier = Modifier.weight(0.8f))
                            }
                            Text("FIRE Portfolio Target: ${NumberFormatters.formatCurrency(fireTarget, region)}", style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                            Text("CAGR & INFLATION PURCHASING POWER", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelNumberField(cagrStart, { cagrStart = it }, "Start", chapterHue = hue, modifier = Modifier.weight(1f))
                                PixelNumberField(cagrEnd, { cagrEnd = it }, "End", chapterHue = hue, modifier = Modifier.weight(1f))
                                PixelNumberField(cagrYrs, { cagrYrs = it }, "Yrs", chapterHue = hue, modifier = Modifier.weight(0.7f))
                                PixelNumberField(inflRate, { inflRate = it }, "Infl%", chapterHue = hue, modifier = Modifier.weight(0.7f))
                            }
                            Text("CAGR: ${"%.2f".format(cagrVal)}% · Inflation-Adjusted Power of Start: ${NumberFormatters.formatCurrency(inflPow, region)}", style = DotkitType.monoData(12), color = palette.ink)
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                            Text("HOURLY ↔ ANNUAL SALARY (2,080 hrs/yr)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelNumberField(hourlyWage, { hourlyWage = it }, "Hourly Rate", chapterHue = hue)
                            Text("Weekly (40h): ${NumberFormatters.formatCurrency(hr * 40.0, region)} · Monthly: ${NumberFormatters.formatCurrency(hr * 2080.0 / 12.0, region)} · Annual: ${NumberFormatters.formatCurrency(hr * 2080.0, region)}", style = DotkitType.monoData(12), color = palette.ok)
                        }
                    }
                } else {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (meta.id == "fin-mf") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelChip(label = "Monthly SIP", selected = sipMode, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { sipMode = true })
                                PixelChip(label = "Lumpsum CAGR", selected = !sipMode, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { sipMode = false })
                            }
                        } else if (meta.id == "fin-fdrd") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelChip(label = "Fixed Deposit (FD)", selected = !rdMode, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { rdMode = false })
                                PixelChip(label = "Recurring Deposit (RD)", selected = rdMode, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { rdMode = true })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if ((meta.id == "fin-mf" && sipMode) || (meta.id == "fin-fdrd" && rdMode)) "MONTHLY DEPOSIT" else "PRINCIPAL", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(value = principalStr, onValueChange = { principalStr = it }, chapterHue = hue)
                            }
                            Column(Modifier.weight(0.7f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("ANNUAL %", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(value = rateStr, onValueChange = { rateStr = it }, chapterHue = hue)
                            }
                        }
                        PixelSteppedSlider("Duration", yearsVal, 1..30, 1, " yr", hue, { yearsVal = it })
                        if (meta.id == "fin-interest") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1 to "Annual", 2 to "Half-Yearly", 4 to "Quarterly", 12 to "Monthly").forEach { (f, lbl) ->
                                    PixelChip(label = lbl, selected = compoundFreq == f, chapterHue = hue, onClick = { compoundFreq = f })
                                }
                            }
                        }
                        Text(summaryText, style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                    }
                }
                }
            }

            "fin-gst" -> {
                val res = FinanceEngine.calculateGst(gstAmountStr.toDoubleOrNull() ?: 0.0, gstSlab.toDouble(), gstInclusive)
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("AMOUNT (${region.currencySymbol})", style = DotkitType.monoLabel(9), color = palette.ink3)
                        PixelNumberField(value = gstAmountStr, onValueChange = { gstAmountStr = it }, chapterHue = hue)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 5, 12, 18, 28).forEach { s ->
                                PixelChip(label = "$s%", selected = gstSlab == s, chapterHue = hue, onClick = { gstSlab = s })
                            }
                        }
                        PixelSwitch("Amount already includes GST (Inclusive)", gstInclusive, { gstInclusive = it }, hue)
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("Base Price: ${NumberFormatters.formatCurrency(res.baseAmount, region)}", style = DotkitType.monoData(13), color = palette.ink)
                        Text("GST ($gstSlab%): ${NumberFormatters.formatCurrency(res.gstAmount, region)} (CGST ${"%.2f".format(res.cgstAmount)} + SGST ${"%.2f".format(res.sgstAmount)})", style = DotkitType.monoData(12), color = palette.ink2)
                        Text("Grand Total: ${NumberFormatters.formatCurrency(res.totalAmount, region)}", style = DotkitType.readoutDoto(26), color = palette.chapterColor(hue))
                    }
                }
            }

            "fin-expense" -> {
                val totalIn = expenses.filter { it.kind == "INCOME" }.sumOf { it.amount }
                val totalOut = expenses.filter { it.kind == "EXPENSE" }.sumOf { it.amount }
                var budgetIncomeStr by remember { mutableStateOf("0") }
                val refIncome = (budgetIncomeStr.toDoubleOrNull() ?: 0.0).let { if (it > 0.0) it else totalIn }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("NET BALANCE", style = DotkitType.monoLabel(10), color = palette.ink2); Text(NumberFormatters.formatCurrency(totalIn - totalOut, region), style = DotkitType.readoutDoto(24), color = palette.ink) }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("+${NumberFormatters.formatCurrency(totalIn, region)}", style = DotkitType.monoData(12), color = palette.ok)
                                Text("-${NumberFormatters.formatCurrency(totalOut, region)}", style = DotkitType.monoData(12), color = palette.err)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip(label = "Expense", selected = expKind == "EXPENSE", chapterHue = ChapterHue.ROSE, modifier = Modifier.weight(1f), onClick = { expKind = "EXPENSE" })
                            PixelChip(label = "Income", selected = expKind == "INCOME", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { expKind = "INCOME" })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(value = expTitle, onValueChange = { expTitle = it }, placeholder = "Description…", chapterHue = hue, modifier = Modifier.weight(1.3f))
                            PixelNumberField(value = expAmount, onValueChange = { expAmount = it }, chapterHue = hue, modifier = Modifier.weight(0.7f))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("General", "Food", "Travel", "Bills", "Subs", "Salary").forEach { c ->
                                PixelChip(label = c, selected = expCat == c, chapterHue = hue, onClick = { expCat = c })
                            }
                        }
                        PixelButton(text = "Log Entry", chapterHue = hue, enabled = expTitle.isNotBlank() && (expAmount.toDoubleOrNull() ?: 0.0) > 0.0, onClick = {
                            onAddExpense(expTitle.trim(), expAmount.toDouble(), expCat, expKind, "")
                            expTitle = ""; expAmount = "0"
                        })

                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("50 / 30 / 20 BUDGET TARGETS", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelNumberField(budgetIncomeStr, { budgetIncomeStr = it }, "Monthly Income (0 = use ledger income)", chapterHue = hue)
                        Text(
                            "Needs 50%: ${NumberFormatters.formatCurrency(refIncome * 0.5, region)} · Wants 30%: ${NumberFormatters.formatCurrency(refIncome * 0.3, region)} · Save 20%: ${NumberFormatters.formatCurrency(refIncome * 0.2, region)}",
                            style = DotkitType.monoData(11),
                            color = palette.chapterColor(hue)
                        )
                        val byCat = expenses.filter { it.kind == "EXPENSE" }.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
                        if (byCat.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                byCat.forEach { (cat, amt) ->
                                    PixelStamp("$cat: ${NumberFormatters.formatCurrency(amt, region)}", palette.ink)
                                }
                            }
                        }

                        if (expenses.isEmpty()) {
                            Text("No transactions logged yet.", style = DotkitType.bodyUi(12), color = palette.ink3)
                        } else {
                            expenses.take(30).forEach { e ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(e.title, style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                                        Text("${e.category} · ${e.kind}", style = DotkitType.monoLabel(10), color = palette.ink2)
                                    }
                                    Text("${if (e.kind == "INCOME") "+" else "-"}${NumberFormatters.formatCurrency(e.amount, region)}", style = DotkitType.monoData(13), color = if (e.kind == "INCOME") palette.ok else palette.err)
                                    PixelChip(label = "✕", onClick = { onDeleteExpense(e.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
