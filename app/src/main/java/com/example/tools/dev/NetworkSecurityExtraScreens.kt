package com.example.tools.dev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotMatrixReadout
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelChip
import com.example.design.PixelProgressBar
import com.example.design.PixelStamp
import com.example.design.PixelTextField
import com.example.lib.dev.PasswordQrEngine
import com.example.lib.expansions.SubnetTotpEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkSecurityExtraScreens(
    meta: ToolMeta,
    initialText: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.FOREST

    // ==================== 1. IPv4 SUBNET / CIDR LAB ====================
    var cidrInput by remember(initialText) {
        mutableStateOf(initialText.ifBlank { "192.168.10.42/24" })
    }
    val subnet = remember(cidrInput) { SubnetTotpEngine.parseIpv4Cidr(cidrInput) }

    // ==================== 2. RFC 6238 TOTP AUTHENTICATOR ====================
    var totpSecret by remember(initialText) {
        mutableStateOf(initialText.ifBlank { "JBSWY3DPEHPK3PXP" })
    }
    var totpDigits by remember { mutableIntStateOf(6) }
    var totpPeriod by remember { mutableIntStateOf(30) }
    var epochSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }

    LaunchedEffect(meta.id) {
        if (meta.id == "net-totp") {
            while (true) {
                epochSec = System.currentTimeMillis() / 1000L
                delay(500L)
            }
        }
    }

    val totp = remember(totpSecret, epochSec, totpPeriod, totpDigits) {
        SubnetTotpEngine.computeTotp(totpSecret, epochSec, totpPeriod, totpDigits)
    }

    val outputText = if (meta.id == "net-subnet") {
        if (!subnet.valid) subnet.error else """
            CIDR: ${subnet.ip}/${subnet.prefix}
            Network: ${subnet.networkAddress}
            Broadcast: ${subnet.broadcastAddress}
            Subnet Mask: ${subnet.subnetMask} (Wildcard ${subnet.wildcardMask})
            Host Range: ${subnet.firstHost} .. ${subnet.lastHost}
            Usable Hosts: ${subnet.usableHosts} / Total ${subnet.totalAddresses}
            Scope: ${subnet.ipClassAndScope}
            Binary IP: ${subnet.binaryIp}
        """.trimIndent()
    } else {
        if (!totp.valid) totp.error else "TOTP: ${totp.code} (${totp.remainingSec}s left)\nURI: ${totp.otpAuthUri}"
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputText,
        outputFileName = "${meta.id}_${System.currentTimeMillis()}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        helpParagraph = if (meta.id == "net-subnet") {
            "Calculate IPv4 network boundaries, masks, host counts, RFC 1918 scope & binary octets for any CIDR notation."
        } else {
            "Generate standard RFC 6238 2FA TOTP codes from any Base32 secret locally with countdown sync and otpauth QR matrix."
        },
        onStatusToast = onStatusToast
    ) {
        if (meta.id == "net-subnet") {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("IPV4 / CIDR INPUT", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelTextField(
                        value = cidrInput,
                        onValueChange = { cidrInput = it },
                        placeholder = "e.g. 10.0.4.15/20",
                        chapterHue = hue,
                        tag = "subnet_cidr_input"
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("192.168.1.42/24", "10.0.0.1/8", "172.16.40.5/20", "192.168.0.12/28", "8.8.8.8/32").forEach { ex ->
                            PixelChip(label = ex, selected = cidrInput == ex, chapterHue = hue, onClick = { cidrInput = ex })
                        }
                    }
                }
            }
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!subnet.valid) {
                        Text(subnet.error, style = DotkitType.monoData(12), color = palette.err)
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("USABLE HOSTS", style = DotkitType.monoLabel(10), color = palette.ink2)
                                Text("${subnet.usableHosts}", style = DotkitType.readoutDoto(28), color = palette.chapterColor(hue))
                            }
                            PixelStamp(subnet.ipClassAndScope, palette.ok)
                        }
                        listOf(
                            "Network" to subnet.networkAddress,
                            "Broadcast" to subnet.broadcastAddress,
                            "Netmask" to subnet.subnetMask,
                            "Wildcard" to subnet.wildcardMask,
                            "Host Range" to "${subnet.firstHost} – ${subnet.lastHost}",
                            "Binary" to subnet.binaryIp
                        ).forEach { (k, v) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.paper)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(k, style = DotkitType.monoLabel(10), color = palette.ink2)
                                Text(v, style = DotkitType.monoData(12), color = palette.ink)
                            }
                        }
                    }
                }
            }
        } else {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("BASE32 SECRET KEY", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelTextField(
                        value = totpSecret,
                        onValueChange = { totpSecret = it },
                        placeholder = "JBSWY3DPEHPK3PXP",
                        chapterHue = hue,
                        tag = "totp_secret_input"
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(6 to "6 Digits", 8 to "8 Digits").forEach { (d, lbl) ->
                            PixelChip(label = lbl, selected = totpDigits == d, chapterHue = hue, onClick = { totpDigits = d })
                        }
                        listOf(30 to "30s", 60 to "60s").forEach { (p, lbl) ->
                            PixelChip(label = lbl, selected = totpPeriod == p, chapterHue = ChapterHue.SKY, onClick = { totpPeriod = p })
                        }
                    }
                }
            }
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!totp.valid) {
                        Text(totp.error, style = DotkitType.monoData(12), color = palette.err)
                    } else {
                        val spaced = totp.code.chunked(3).joinToString(" ")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DotMatrixReadout(
                                valueText = spaced,
                                sizeSp = 38,
                                accentColor = palette.chapterColor(hue)
                            )
                            PixelStamp("${totp.remainingSec}s", if (totp.remainingSec <= 5) palette.warn else palette.ok)
                        }
                        PixelProgressBar(
                            progress = totp.remainingSec.toFloat() / totpPeriod.toFloat(),
                            chapterHue = hue
                        )
                        val qr = remember(totp.otpAuthUri) { PasswordQrEngine.encodeQrMatrix(totp.otpAuthUri) }
                        if (qr != null) {
                            val inkColor = palette.ink
                            val bgColor = palette.paper
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bgColor)
                                        .border(0.8.dp, palette.hairline, RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    val n = qr.size
                                    val cell = size.width / n
                                    for (r in 0 until n) {
                                        for (c in 0 until n) {
                                            if (qr[r][c]) {
                                                drawRect(
                                                    color = inkColor,
                                                    topLeft = Offset(c * cell, r * cell),
                                                    size = Size(cell + 0.5f, cell + 0.5f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
