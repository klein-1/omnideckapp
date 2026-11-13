package com.example.tools.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.design.PixelChip
import com.example.design.PixelNumberField
import com.example.design.SwissControlShape
import com.example.lib.calc.UnitConverterEngine
import com.example.lib.calc.UnitDomain
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitConverterTool(
    meta: ToolMeta,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.SAND

    var domain by remember { mutableStateOf(UnitDomain.LENGTH) }
    val units = remember(domain) { UnitConverterEngine.UNITS_BY_DOMAIN[domain].orEmpty() }
    var fromId by remember(domain) { mutableStateOf(units.getOrNull(1)?.id ?: units.first().id) }
    var toId by remember(domain) { mutableStateOf(units.firstOrNull()?.id ?: "m") }
    var inputStr by remember { mutableStateOf("0") }

    val inputVal = inputStr.toDoubleOrNull() ?: 0.0
    val converted = remember(domain, inputVal, fromId, toId) {
        UnitConverterEngine.convert(domain, inputVal, fromId, toId)
    }
    val toUnit = units.firstOrNull { it.id == toId }
    val fromUnit = units.firstOrNull { it.id == fromId }

    val formattedOut = remember(converted) { UnitConverterEngine.formatValue(converted) }
    val allConversions = remember(domain, inputVal, fromId) {
        units.map { u ->
            u to UnitConverterEngine.formatValue(UnitConverterEngine.convert(domain, inputVal, fromId, u.id))
        }
    }

    val exportSummary = remember(inputStr, fromUnit, formattedOut, toUnit, allConversions) {
        buildString {
            appendLine("$inputStr ${fromUnit?.symbol.orEmpty()} = $formattedOut ${toUnit?.symbol.orEmpty()}")
            allConversions.forEach { (u, v) -> appendLine("${u.label}: $v ${u.symbol}") }
        }.trim()
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = "$formattedOut ${toUnit?.symbol.orEmpty()}",
        outputFileName = "unit_conversion_${domain.name.lowercase()}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = "Convert instantaneously across Length, Mass, Temperature, Area & Regional Land units (bigha, kanal, marla, gaj), Data (SI base-10 and IEC base-2), Speed, and Time. Tap Swap to reverse direction."
    ) {
        // Domain Tabs
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            UnitDomain.entries.forEach { d ->
                PixelChip(
                    label = d.label,
                    selected = domain == d,
                    chapterHue = hue,
                    onClick = { domain = d }
                )
            }
        }

        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("INPUT VALUE", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelChip(
                        label = "⇄ Swap",
                        selected = true,
                        chapterHue = hue,
                        onClick = {
                            val tmp = fromId
                            fromId = toId
                            toId = tmp
                        }
                    )
                }

                PixelNumberField(
                    value = inputStr,
                    onValueChange = { inputStr = it },
                    allowNegative = domain == UnitDomain.TEMPERATURE,
                    chapterHue = hue
                )

                Text("FROM UNIT", style = DotkitType.monoLabel(10), color = palette.ink2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    units.forEach { u ->
                        PixelChip(
                            label = "${u.label} (${u.symbol})",
                            selected = fromId == u.id,
                            chapterHue = hue,
                            onClick = { fromId = u.id }
                        )
                    }
                }

                Text("TO UNIT", style = DotkitType.monoLabel(10), color = palette.ink2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    units.forEach { u ->
                        PixelChip(
                            label = "${u.label} (${u.symbol})",
                            selected = toId == u.id,
                            chapterHue = hue,
                            onClick = { toId = u.id }
                        )
                    }
                }

                // Result Plate
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SwissControlShape)
                        .background(palette.paper)
                        .border(1.dp, palette.hairline, SwissControlShape)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "$inputStr ${fromUnit?.symbol.orEmpty()} =",
                        style = DotkitType.monoLabel(11),
                        color = palette.ink2
                    )
                    Text(
                        text = "$formattedOut ${toUnit?.symbol.orEmpty()}",
                        style = DotkitType.readoutDoto(32),
                        color = palette.ink
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                Text("ALL ${domain.label.uppercase()} EQUIVALENTS", style = DotkitType.monoLabel(10), color = palette.ink2)
                allConversions.forEach { (u, valStr) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SwissControlShape)
                            .background(palette.paper)
                            .border(1.dp, palette.hairline, SwissControlShape)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(u.label, style = DotkitType.bodyUi(13), color = palette.ink2)
                        Text("$valStr ${u.symbol}", style = DotkitType.monoData(13), color = palette.ink)
                    }
                }
            }
        }
    }
}
