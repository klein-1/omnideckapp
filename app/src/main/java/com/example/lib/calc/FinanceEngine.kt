package com.example.lib.calc

import kotlin.math.pow

data class EmiBreakdown(
    val monthlyEmi: Double,
    val totalInterest: Double,
    val totalPayable: Double,
    val principalRatio: Float
)

data class TipSplitResult(
    val tipAmount: Double,
    val totalBill: Double,
    val perPerson: Double
)

data class InvestmentResult(
    val investedAmount: Double,
    val estimatedReturns: Double,
    val maturityValue: Double
)

data class GstResult(
    val baseAmount: Double,
    val gstAmount: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val totalAmount: Double
)

data class InvoiceLineItem(
    val description: String,
    val qty: Double,
    val unitPrice: Double
)

object FinanceEngine {
    fun calculateEmi(principal: Double, annualRatePercent: Double, tenureMonths: Int): EmiBreakdown {
        if (principal <= 0 || tenureMonths <= 0) {
            return EmiBreakdown(0.0, 0.0, 0.0, 1f)
        }
        if (annualRatePercent <= 0.0) {
            val emi = principal / tenureMonths
            return EmiBreakdown(emi, 0.0, principal, 1f)
        }
        val r = annualRatePercent / 12.0 / 100.0
        val factor = (1.0 + r).pow(tenureMonths.toDouble())
        val emi = principal * r * factor / (factor - 1.0)
        val total = emi * tenureMonths
        val interest = (total - principal).coerceAtLeast(0.0)
        val ratio = (principal / total).toFloat().coerceIn(0f, 1f)
        return EmiBreakdown(emi, interest, total, ratio)
    }

    fun calculateTipSplit(billAmount: Double, tipPercent: Double, peopleCount: Int): TipSplitResult {
        val safeBill = billAmount.coerceAtLeast(0.0)
        val safeTip = tipPercent.coerceAtLeast(0.0)
        val people = peopleCount.coerceAtLeast(1)
        val tip = safeBill * (safeTip / 100.0)
        val total = safeBill + tip
        return TipSplitResult(tip, total, total / people)
    }

    fun calculateSimpleInterest(principal: Double, ratePercent: Double, years: Double): InvestmentResult {
        val p = principal.coerceAtLeast(0.0)
        val r = ratePercent.coerceAtLeast(0.0)
        val t = years.coerceAtLeast(0.0)
        val interest = p * r * t / 100.0
        return InvestmentResult(p, interest, p + interest)
    }

    fun calculateCompoundInterest(
        principal: Double,
        ratePercent: Double,
        years: Double,
        compoundsPerYear: Int = 4
    ): InvestmentResult {
        val p = principal.coerceAtLeast(0.0)
        val r = (ratePercent / 100.0).coerceAtLeast(0.0)
        val n = compoundsPerYear.coerceAtLeast(1)
        val t = years.coerceAtLeast(0.0)
        val total = p * (1.0 + r / n).pow(n * t)
        return InvestmentResult(p, (total - p).coerceAtLeast(0.0), total)
    }

    fun calculateSip(monthlyInvestment: Double, annualReturnPct: Double, years: Int): InvestmentResult {
        val p = monthlyInvestment.coerceAtLeast(0.0)
        val months = (years * 12).coerceAtLeast(1)
        val invested = p * months
        if (annualReturnPct <= 0.0) return InvestmentResult(invested, 0.0, invested)
        val i = annualReturnPct / 12.0 / 100.0
        val fv = p * (((1.0 + i).pow(months) - 1.0) / i) * (1.0 + i)
        return InvestmentResult(invested, (fv - invested).coerceAtLeast(0.0), fv)
    }

    fun calculateRd(monthlyDeposit: Double, annualRatePct: Double, months: Int): InvestmentResult {
        val p = monthlyDeposit.coerceAtLeast(0.0)
        val m = months.coerceAtLeast(1)
        val invested = p * m
        if (annualRatePct <= 0.0) return InvestmentResult(invested, 0.0, invested)
        val r = annualRatePct / 400.0 // quarterly compounding standard
        var maturity = 0.0
        for (k in 1..m) {
            val quarters = (m - k + 1) / 3.0
            maturity += p * (1.0 + r).pow(quarters)
        }
        return InvestmentResult(invested, (maturity - invested).coerceAtLeast(0.0), maturity)
    }

    fun calculateGst(amount: Double, ratePercent: Double, inclusive: Boolean): GstResult {
        val safeAmt = amount.coerceAtLeast(0.0)
        val r = ratePercent.coerceAtLeast(0.0)
        return if (inclusive) {
            val base = safeAmt * 100.0 / (100.0 + r)
            val gst = safeAmt - base
            GstResult(base, gst, gst / 2.0, gst / 2.0, safeAmt)
        } else {
            val gst = safeAmt * r / 100.0
            GstResult(safeAmt, gst, gst / 2.0, gst / 2.0, safeAmt + gst)
        }
    }
}
