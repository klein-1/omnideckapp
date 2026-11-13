package com.example.lib.format

import com.example.design.RegionFormat
import java.text.DecimalFormat
import kotlin.math.abs

object NumberFormatters {
    /**
     * Formats integers using Indian lakh/crore grouping (e.g., 12,34,567) or standard 3-digit grouping.
     */
    fun formatNumber(value: Long, region: RegionFormat = RegionFormat.EN_IN): String {
        if (region == RegionFormat.EN_US) {
            return DecimalFormat("#,###").format(value)
        }
        val negative = value < 0
        val s = abs(value).toString()
        if (s.length <= 3) return if (negative) "-$s" else s
        val lastThree = s.substring(s.length - 3)
        val rest = s.substring(0, s.length - 3)
        val sb = StringBuilder()
        var count = 0
        for (i in rest.length - 1 downTo 0) {
            sb.append(rest[i])
            count++
            if (count == 2 && i != 0) {
                sb.append(',')
                count = 0
            }
        }
        val groupedRest = sb.reverse().toString()
        val result = "$groupedRest,$lastThree"
        return if (negative) "-$result" else result
    }

    fun formatCurrency(amount: Double, region: RegionFormat = RegionFormat.EN_IN): String {
        val rounded = kotlin.math.round(amount).toLong()
        return "${region.currencySymbol}${formatNumber(rounded, region)}"
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.2f MB", mb)
    }
}
