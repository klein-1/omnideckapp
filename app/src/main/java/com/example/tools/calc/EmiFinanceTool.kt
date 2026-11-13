package com.example.tools.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelChip
import com.example.design.PixelNumberField
import com.example.design.PixelSteppedSlider
import com.example.design.PixelTextField
import com.example.design.SwissControlShape
import com.example.lib.calc.DebtAccount
import com.example.lib.calc.ExtendedMathEngines
import com.example.lib.calc.FinanceEngine
import com.example.lib.format.NumberFormatters
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome

private enum class FinanceTab(val label: String) {
    EMI("Loan EMI"),
    DEBT("Debt Planner"),
    PERCENT("Percentage"),
    SPLIT("Tip & Split")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmiFinanceTool(
    meta: ToolMeta,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val hue = ChapterHue.LIME
    val region = look.regionFormat

    var tab by remember { mutableStateOf(FinanceTab.EMI) }

    // EMI state (default 0)
    var principalStr by remember { mutableStateOf("0") }
    var rateStr by remember { mutableStateOf("0") }
    var tenureYears by remember { mutableIntStateOf(5) }

    val principal = principalStr.toDoubleOrNull() ?: 0.0
    val rate = rateStr.toDoubleOrNull() ?: 0.0
    val emiResult = remember(principal, rate, tenureYears) {
        FinanceEngine.calculateEmi(principal, rate, tenureYears * 12)
    }

    // Percentage state (default 0)
    var pctStr by remember { mutableStateOf("0") }
    var baseStr by remember { mutableStateOf("0") }
    val pctVal = pctStr.toDoubleOrNull() ?: 0.0
    val baseVal = baseStr.toDoubleOrNull() ?: 0.0
    val pctOfBase = (pctVal / 100.0) * baseVal
    val whatPct = if (baseVal != 0.0) (pctVal / baseVal) * 100.0 else 0.0
    val pctIncrease = baseVal + pctOfBase
    val pctDecrease = baseVal - pctOfBase

    // Tip Split state (default 0)
    var billStr by remember { mutableStateOf("0") }
    var tipPct by remember { mutableIntStateOf(0) }
    var peopleCount by remember { mutableIntStateOf(1) }
    val splitRes = remember(billStr, tipPct, peopleCount) {
        FinanceEngine.calculateTipSplit(billStr.toDoubleOrNull() ?: 0.0, tipPct.toDouble(), peopleCount)
    }

    // Debt Snowball vs Avalanche state (default 0)
    val debts = remember { mutableStateListOf<DebtAccount>() }
    var debtName by remember { mutableStateOf("") }
    var debtBalance by remember { mutableStateOf("0") }
    var debtApr by remember { mutableStateOf("0") }
    var debtMin by remember { mutableStateOf("0") }
    var extraPayStr by remember { mutableStateOf("0") }
    val snowball = remember(debts.toList(), extraPayStr) {
        ExtendedMathEngines.simulateDebtPayoff(debts, extraPayStr.toDoubleOrNull() ?: 0.0, avalanche = false)
    }
    val avalanche = remember(debts.toList(), extraPayStr) {
        ExtendedMathEngines.simulateDebtPayoff(debts, extraPayStr.toDoubleOrNull() ?: 0.0, avalanche = true)
    }

    val exportText = when (tab) {
        FinanceTab.EMI -> "Monthly EMI: ${NumberFormatters.formatCurrency(emiResult.monthlyEmi, region)}\nPrincipal: ${NumberFormatters.formatCurrency(principal, region)}\nTotal Interest: ${NumberFormatters.formatCurrency(emiResult.totalInterest, region)}\nTotal Payable: ${NumberFormatters.formatCurrency(emiResult.totalPayable, region)}"
        FinanceTab.DEBT -> "Snowball: ${snowball.monthsToPayoff} mo, Interest ${NumberFormatters.formatCurrency(snowball.totalInterestPaid, region)}\nAvalanche: ${avalanche.monthsToPayoff} mo, Interest ${NumberFormatters.formatCurrency(avalanche.totalInterestPaid, region)}"
        FinanceTab.PERCENT -> "$pctStr% of $baseStr = ${String.format(java.util.Locale.US, "%.2f", pctOfBase)}"
        FinanceTab.SPLIT -> "Total Bill: ${NumberFormatters.formatCurrency(splitRes.totalBill, region)} · Per Person ($peopleCount): ${NumberFormatters.formatCurrency(splitRes.perPerson, region)}"
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = exportText,
        outputFileName = "finance_${tab.name.lowercase()}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = "Compute reducing-balance monthly loan EMI with principal-vs-interest ratio bar, quick percentage & discount calculations, and group bill/tip splitting. Number formatting follows your Region setting."
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FinanceTab.entries.forEach { t ->
                PixelChip(label = t.label, selected = tab == t, chapterHue = hue, onClick = { tab = t })
            }
        }

        when (tab) {
            FinanceTab.EMI -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("LOAN PRINCIPAL (${region.currencySymbol})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelNumberField(
                            value = principalStr,
                            onValueChange = { principalStr = it },
                            chapterHue = hue
                        )
                        Text("ANNUAL INTEREST RATE (%)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelNumberField(
                            value = rateStr,
                            onValueChange = { rateStr = it },
                            chapterHue = hue
                        )
                        PixelSteppedSlider(
                            label = "Loan Tenure",
                            value = tenureYears,
                            valueRange = 1..30,
                            step = 1,
                            unitSuffix = " yrs (${tenureYears * 12} mo)",
                            chapterHue = hue,
                            onValueChange = { tenureYears = it }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SwissControlShape)
                                .background(palette.paper)
                                .border(1.dp, palette.hairline, SwissControlShape)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("MONTHLY EMI", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Text(
                                text = NumberFormatters.formatCurrency(emiResult.monthlyEmi, region),
                                style = DotkitType.readoutDoto(32),
                                color = palette.ink
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(SwissControlShape)
                                    .border(1.dp, palette.hairline, SwissControlShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(emiResult.principalRatio.coerceIn(0.05f, 0.95f))
                                        .height(12.dp)
                                        .background(palette.chapterColor(hue))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight((1f - emiResult.principalRatio).coerceIn(0.05f, 0.95f))
                                        .height(12.dp)
                                        .background(palette.warn)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Principal: ${NumberFormatters.formatCurrency(principal, region)}", style = DotkitType.monoData(12), color = palette.ink)
                                Text("Interest: ${NumberFormatters.formatCurrency(emiResult.totalInterest, region)}", style = DotkitType.monoData(12), color = palette.ink2)
                            }
                            Text("Total Payable: ${NumberFormatters.formatCurrency(emiResult.totalPayable, region)}", style = DotkitType.bodyUi(14, bold = true), color = palette.ink)
                        }
                    }
                }
            }

            FinanceTab.DEBT -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("DEBT SNOWBALL VS AVALANCHE PLANNER", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(debtName, { debtName = it }, "Account name (e.g. Card A, Auto)", chapterHue = hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("BALANCE", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(debtBalance, { debtBalance = it }, chapterHue = hue)
                            }
                            Column(Modifier.weight(0.8f)) {
                                Text("APR %", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(debtApr, { debtApr = it }, chapterHue = hue)
                            }
                            Column(Modifier.weight(0.9f)) {
                                Text("MIN PAY", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(debtMin, { debtMin = it }, chapterHue = hue)
                            }
                        }
                        PixelButton(
                            text = "+ Add Debt Account",
                            chapterHue = hue,
                            enabled = (debtBalance.toDoubleOrNull() ?: 0.0) > 0.0 && (debtMin.toDoubleOrNull() ?: 0.0) > 0.0,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                debts.add(
                                    DebtAccount(
                                        name = debtName.trim().ifBlank { "Debt #${debts.size + 1}" },
                                        balance = debtBalance.toDoubleOrNull() ?: 0.0,
                                        aprPercent = debtApr.toDoubleOrNull() ?: 0.0,
                                        minPayment = debtMin.toDoubleOrNull() ?: 0.0
                                    )
                                )
                                debtName = ""; debtBalance = "0"; debtApr = "0"; debtMin = "0"
                            }
                        )
                        Text("EXTRA MONTHLY SURPLUS (${region.currencySymbol})", style = DotkitType.monoLabel(9), color = palette.ink3)
                        PixelNumberField(extraPayStr, { extraPayStr = it }, chapterHue = hue)

                        if (debts.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                debts.forEachIndexed { idx, d ->
                                    PixelChip("${d.name}: ${NumberFormatters.formatCurrency(d.balance, region)} @${d.aprPercent}% ✕", chapterHue = hue, onClick = { debts.removeAt(idx) })
                                }
                            }
                            Text(
                                "Avalanche: ${avalanche.monthsToPayoff} mo · Interest ${NumberFormatters.formatCurrency(avalanche.totalInterestPaid, region)}\nSnowball: ${snowball.monthsToPayoff} mo · Interest ${NumberFormatters.formatCurrency(snowball.totalInterestPaid, region)}\nInterest Saved by Avalanche: ${NumberFormatters.formatCurrency((snowball.totalInterestPaid - avalanche.totalInterestPaid).coerceAtLeast(0.0), region)}",
                                style = DotkitType.monoData(12),
                                color = palette.chapterColor(hue)
                            )
                        } else {
                            Text("Add 1+ debt accounts to compare payoff timelines and total interest.", style = DotkitType.bodyUi(12), color = palette.ink2)
                        }
                    }
                }
            }

            FinanceTab.PERCENT -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("VALUE A (%)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelNumberField(
                            value = pctStr,
                            onValueChange = { pctStr = it },
                            chapterHue = hue
                        )
                        Text("VALUE B (BASE)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelNumberField(
                            value = baseStr,
                            onValueChange = { baseStr = it },
                            chapterHue = hue
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SwissControlShape)
                                .background(palette.paper)
                                .border(1.dp, palette.hairline, SwissControlShape)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("$pctStr% of $baseStr = ${String.format(java.util.Locale.US, "%.2f", pctOfBase)}", style = DotkitType.monoData(14), color = palette.ink)
                            Text("$pctStr is ${String.format(java.util.Locale.US, "%.2f", whatPct)}% of $baseStr", style = DotkitType.monoData(13), color = palette.ink2)
                            Text("$baseStr + $pctStr% = ${String.format(java.util.Locale.US, "%.2f", pctIncrease)}", style = DotkitType.monoData(13), color = palette.ok)
                            Text("$baseStr − $pctStr% = ${String.format(java.util.Locale.US, "%.2f", pctDecrease)}", style = DotkitType.monoData(13), color = palette.err)
                        }
                    }
                }
            }

            FinanceTab.SPLIT -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("BILL AMOUNT (${region.currencySymbol})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelNumberField(
                            value = billStr,
                            onValueChange = { billStr = it },
                            chapterHue = hue
                        )
                        PixelSteppedSlider(
                            label = "Tip %",
                            value = tipPct,
                            valueRange = 0..30,
                            step = 2,
                            unitSuffix = "%",
                            chapterHue = hue,
                            onValueChange = { tipPct = it }
                        )
                        PixelSteppedSlider(
                            label = "People",
                            value = peopleCount,
                            valueRange = 1..20,
                            step = 1,
                            unitSuffix = " people",
                            chapterHue = hue,
                            onValueChange = { peopleCount = it }
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SwissControlShape)
                                .background(palette.paper)
                                .border(1.dp, palette.hairline, SwissControlShape)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("EACH PERSON PAYS", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Text(NumberFormatters.formatCurrency(splitRes.perPerson, region), style = DotkitType.readoutDoto(32), color = palette.ink)
                            Text("Tip: ${NumberFormatters.formatCurrency(splitRes.tipAmount, region)} · Total: ${NumberFormatters.formatCurrency(splitRes.totalBill, region)}", style = DotkitType.monoData(12), color = palette.ink2)
                        }
                    }
                }
            }
        }
    }
}
