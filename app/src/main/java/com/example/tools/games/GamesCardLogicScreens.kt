package com.example.tools.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotMatrixReadout
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelNumberField
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.SwissControlShape
import com.example.lib.games.BlackjackEngine
import com.example.lib.games.FifteenPuzzleEngine
import com.example.lib.games.HanoiEngine
import com.example.lib.games.PlayingCard
import com.example.lib.games.ReversiEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamesCardLogicScreen(
    meta: ToolMeta,
    onRecordScore: (testId: String, label: String, numericScore: Double, higherIsBetter: Boolean) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.ROSE

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = "",
        outputFileName = "${meta.id}_score.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        showOutputBar = false,
        helpParagraph = "${meta.name}: ${meta.tagline}",
        onStatusToast = onStatusToast
    ) {
        when (meta.id) {
            "game-solitaire" -> {
                data class SolitaireCard(val card: PlayingCard, val faceUp: Boolean)
                fun dealKlondike(): Triple<List<List<SolitaireCard>>, List<PlayingCard>, List<List<PlayingCard>>> {
                    val deck = BlackjackEngine.newShuffledDeck().toMutableList()
                    val cols = (0 until 7).map { col ->
                        (0..col).map { row ->
                            SolitaireCard(deck.removeAt(0), faceUp = (row == col))
                        }
                    }
                    val foundations = List(4) { emptyList<PlayingCard>() }
                    return Triple(cols, deck, foundations)
                }

                var deal by remember { mutableStateOf(dealKlondike()) }
                var waste by remember { mutableStateOf<List<PlayingCard>>(emptyList()) }
                var selectedCol by remember { mutableIntStateOf(-2) } // -1 = waste, 0..6 = tableau
                var moves by remember { mutableIntStateOf(0) }
                val cols = deal.first
                val stock = deal.second
                val foundations = deal.third
                val won = foundations.sumOf { it.size } == 52

                fun tryMoveToFoundation(card: PlayingCard): Int? {
                    val f = foundations[card.suit]
                    val topRank = f.lastOrNull()?.rank ?: 0
                    return if (card.rank == topRank + 1) card.suit else null
                }

                fun flipTop(tab: List<List<SolitaireCard>>): List<List<SolitaireCard>> =
                    tab.map { col ->
                        if (col.isNotEmpty() && !col.last().faceUp) {
                            col.dropLast(1) + col.last().copy(faceUp = true)
                        } else col
                    }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("KLONDIKE · MOVES: $moves · FOUND: ${foundations.sumOf { it.size }}/52", style = DotkitType.monoLabel(10), color = palette.ink2)
                            if (won) PixelStamp("SOLVED!", palette.ok)
                        }
                        // Stock, Waste & 4 Foundations
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PixelChip("Draw (${stock.size})", selected = false, chapterHue = hue, onClick = {
                                if (stock.isNotEmpty()) {
                                    waste = waste + stock.first()
                                    deal = Triple(cols, stock.drop(1), foundations)
                                } else if (waste.isNotEmpty()) {
                                    deal = Triple(cols, waste, foundations)
                                    waste = emptyList()
                                }
                                selectedCol = -2
                                moves++
                            })
                            val wTop = waste.lastOrNull()
                            PixelChip(
                                label = wTop?.label() ?: "—",
                                selected = selectedCol == -1,
                                chapterHue = ChapterHue.COBALT,
                                onClick = {
                                    if (wTop != null) {
                                        val fIdx = tryMoveToFoundation(wTop)
                                        if (fIdx != null && selectedCol == -1) {
                                            val nf = foundations.mapIndexed { i, lst -> if (i == fIdx) lst + wTop else lst }
                                            waste = waste.dropLast(1)
                                            deal = Triple(cols, stock, nf)
                                            selectedCol = -2
                                            moves++
                                        } else {
                                            selectedCol = if (selectedCol == -1) -2 else -1
                                        }
                                    }
                                }
                            )
                            val suits = listOf("♠", "♥", "♦", "♣")
                            for (s in 0..3) {
                                val top = foundations[s].lastOrNull()
                                PixelChip(
                                    label = top?.label() ?: suits[s],
                                    selected = top != null,
                                    chapterHue = if (s == 1 || s == 2) ChapterHue.ROSE else ChapterHue.FOREST,
                                    onClick = {
                                        val srcCard = when (selectedCol) {
                                            -1 -> waste.lastOrNull()
                                            in 0..6 -> cols[selectedCol].lastOrNull()?.takeIf { it.faceUp }?.card
                                            else -> null
                                        }
                                        if (srcCard != null && tryMoveToFoundation(srcCard) == s) {
                                            val nf = foundations.mapIndexed { i, lst -> if (i == s) lst + srcCard else lst }
                                            if (selectedCol == -1) {
                                                waste = waste.dropLast(1)
                                                deal = Triple(cols, stock, nf)
                                            } else {
                                                val nc = flipTop(cols.mapIndexed { i, c -> if (i == selectedCol) c.dropLast(1) else c })
                                                deal = Triple(nc, stock, nf)
                                            }
                                            selectedCol = -2
                                            moves++
                                            if (nf.sumOf { it.size } == 52) onRecordScore("game-solitaire", "Won in $moves moves", moves.toDouble(), false)
                                        }
                                    }
                                )
                            }
                        }
                        // 7 Tableau columns
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            cols.forEachIndexed { colIdx, column ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(SwissControlShape)
                                        .background(if (selectedCol == colIdx) palette.chapterColor(hue).copy(alpha = 0.12f) else palette.paper)
                                        .border(1.dp, if (selectedCol == colIdx) palette.chapterColor(hue) else palette.hairline, SwissControlShape)
                                        .clickable {
                                            if (selectedCol == -2) {
                                                if (column.isNotEmpty()) selectedCol = colIdx
                                            } else if (selectedCol == colIdx) {
                                                // Double tap: auto move top card to foundation if legal
                                                val top = column.lastOrNull()?.card
                                                val fIdx = top?.let { tryMoveToFoundation(it) }
                                                if (top != null && fIdx != null) {
                                                    val nf = foundations.mapIndexed { i, lst -> if (i == fIdx) lst + top else lst }
                                                    val nc = flipTop(cols.mapIndexed { i, c -> if (i == colIdx) c.dropLast(1) else c })
                                                    deal = Triple(nc, stock, nf)
                                                    moves++
                                                    if (nf.sumOf { it.size } == 52) onRecordScore("game-solitaire", "Won in $moves moves", moves.toDouble(), false)
                                                }
                                                selectedCol = -2
                                            } else if (selectedCol == -1) {
                                                val card = waste.lastOrNull()
                                                val dst = column.lastOrNull()?.card
                                                val valid = card != null && ((dst == null && card.rank == 13) || (dst != null && dst.isRed != card.isRed && dst.rank == card.rank + 1))
                                                if (valid && card != null) {
                                                    val nc = cols.mapIndexed { i, c -> if (i == colIdx) c + SolitaireCard(card, true) else c }
                                                    waste = waste.dropLast(1)
                                                    deal = Triple(nc, stock, foundations)
                                                    moves++
                                                }
                                                selectedCol = -2
                                            } else {
                                                // Move highest legal faceUp run from selectedCol -> colIdx
                                                val srcCol = cols[selectedCol]
                                                val dst = column.lastOrNull()?.card
                                                val splitAt = srcCol.indexOfFirst { sc ->
                                                    sc.faceUp && ((dst == null && sc.card.rank == 13) || (dst != null && dst.isRed != sc.card.isRed && dst.rank == sc.card.rank + 1))
                                                }
                                                if (splitAt >= 0) {
                                                    val moving = srcCol.subList(splitAt, srcCol.size)
                                                    val nc = flipTop(cols.mapIndexed { i, c ->
                                                        when (i) {
                                                            selectedCol -> c.subList(0, splitAt)
                                                            colIdx -> c + moving
                                                            else -> c
                                                        }
                                                    })
                                                    deal = Triple(nc, stock, foundations)
                                                    moves++
                                                }
                                                selectedCol = -2
                                            }
                                        }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (column.isEmpty()) {
                                        Text("K", style = DotkitType.monoLabel(10), color = palette.ink3, modifier = Modifier.padding(vertical = 12.dp))
                                    } else {
                                        column.forEach { sc ->
                                            Text(
                                                text = if (sc.faceUp) sc.card.label() else "▪▪",
                                                style = DotkitType.monoData(11),
                                                color = if (!sc.faceUp) palette.ink3 else if (sc.card.isRed) Color(0xFFDC2626) else palette.ink
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        PixelButton("New Deal", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            deal = dealKlondike(); waste = emptyList(); selectedCol = -2; moves = 0
                        })
                    }
                }
            }

            "game-blackjack" -> {
                var bankroll by remember { mutableIntStateOf(500) }
                var customBetStr by remember { mutableStateOf("0") }
                var currentBet by remember { mutableIntStateOf(25) }
                var deck by remember { mutableStateOf(BlackjackEngine.newShuffledDeck()) }
                var playerHand by remember { mutableStateOf<List<PlayingCard>>(emptyList()) }
                var dealerHand by remember { mutableStateOf<List<PlayingCard>>(emptyList()) }
                var roundActive by remember { mutableStateOf(false) }
                var statusMsg by remember { mutableStateOf("Place bet and tap Deal") }

                val pScore = BlackjackEngine.handScore(playerHand)
                val dScore = BlackjackEngine.handScore(dealerHand)

                fun startRound() {
                    val chosen = customBetStr.toIntOrNull()?.takeIf { it > 0 } ?: currentBet
                    val bet = chosen.coerceIn(5, bankroll.coerceAtLeast(5))
                    currentBet = bet
                    if (deck.size < 14) deck = BlackjackEngine.newShuffledDeck()
                    val p = listOf(deck[0], deck[2])
                    val d = listOf(deck[1], deck[3])
                    deck = deck.drop(4)
                    playerHand = p
                    dealerHand = d
                    bankroll -= bet
                    val pVal = BlackjackEngine.handScore(p)
                    if (pVal == 21) {
                        val payout = (bet * 2.5).toInt()
                        bankroll += payout
                        roundActive = false
                        statusMsg = "BLACKJACK! Won +${payout - bet}"
                        onRecordScore("game-blackjack", "Bankroll $$bankroll", bankroll.toDouble(), true)
                    } else {
                        roundActive = true
                        statusMsg = "Hit, Stand, or Double Down"
                    }
                }

                fun settleDealer(finalDeck: List<PlayingCard>, finalPlayer: List<PlayingCard>, betUsed: Int) {
                    var dk = finalDeck
                    var dh = dealerHand
                    while (BlackjackEngine.handScore(dh) < 17 && dk.isNotEmpty()) {
                        dh = dh + dk.first()
                        dk = dk.drop(1)
                    }
                    deck = dk
                    dealerHand = dh
                    val ps = BlackjackEngine.handScore(finalPlayer)
                    val ds = BlackjackEngine.handScore(dh)
                    roundActive = false
                    when {
                        ps > 21 -> statusMsg = "Bust ($ps)! Lost $$betUsed"
                        ds > 21 || ps > ds -> {
                            bankroll += betUsed * 2
                            statusMsg = "You Win ($ps vs $ds)! +$$betUsed"
                        }
                        ps == ds -> {
                            bankroll += betUsed
                            statusMsg = "Push ($ps = $ds) · Bet Returned"
                        }
                        else -> statusMsg = "Dealer Wins ($ds vs $ps)"
                    }
                    onRecordScore("game-blackjack", "Bankroll $$bankroll", bankroll.toDouble(), true)
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout("$$bankroll", statusMsg.uppercase(), hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(10, 25, 50, 100).forEach { b ->
                                PixelChip("$$b", selected = currentBet == b, chapterHue = hue, onClick = { currentBet = b; customBetStr = "0" })
                            }
                            PixelNumberField(customBetStr, { customBetStr = it }, "Custom", allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(1f))
                        }
                        if (dealerHand.isNotEmpty()) {
                            Text("DEALER (${if (roundActive) "?" else dScore})", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                dealerHand.forEachIndexed { idx, c ->
                                    val hidden = roundActive && idx == 1
                                    PixelStamp(if (hidden) "🂠" else c.label(), if (!hidden && c.isRed) Color(0xFFDC2626) else palette.ink)
                                }
                            }
                            Text("YOU ($pScore)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                playerHand.forEach { c ->
                                    PixelStamp(c.label(), if (c.isRed) Color(0xFFDC2626) else palette.ink)
                                }
                            }
                        }
                        if (!roundActive) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton("Deal Hand", chapterHue = hue, enabled = bankroll >= 5, modifier = Modifier.weight(1f), onClick = { startRound() })
                                if (bankroll < 5) {
                                    PixelButton("Rebuy $500", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = { bankroll = 500 })
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton("Hit", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    val nextCard = deck.firstOrNull() ?: return@PixelButton
                                    val nh = playerHand + nextCard
                                    val dk = deck.drop(1)
                                    playerHand = nh
                                    deck = dk
                                    if (BlackjackEngine.handScore(nh) >= 21) settleDealer(dk, nh, currentBet)
                                })
                                PixelButton("Stand", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    settleDealer(deck, playerHand, currentBet)
                                })
                                if (playerHand.size == 2 && bankroll >= currentBet) {
                                    PixelButton("Double", variant = PixelButtonVariant.OUTLINE, chapterHue = ChapterHue.COBALT, modifier = Modifier.weight(1f), onClick = {
                                        val nextCard = deck.firstOrNull() ?: return@PixelButton
                                        bankroll -= currentBet
                                        currentBet *= 2
                                        val nh = playerHand + nextCard
                                        val dk = deck.drop(1)
                                        playerHand = nh
                                        settleDealer(dk, nh, currentBet)
                                    })
                                }
                            }
                        }
                    }
                }
            }

            "game-fifteen" -> {
                var board by remember { mutableStateOf(FifteenPuzzleEngine.shuffledSolvable()) }
                var moves by remember { mutableIntStateOf(0) }
                val solved = FifteenPuzzleEngine.isSolved(board)

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("$moves", "SLIDING 15-PUZZLE MOVES", hue)
                            PixelStamp(if (solved) "SOLVED ✓" else "ACTIVE", if (solved) palette.ok else palette.chapterColor(hue))
                        }
                        for (r in 0 until 4) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (c in 0 until 4) {
                                    val idx = r * 4 + c
                                    val v = board[idx]
                                    val movable = !solved && FifteenPuzzleEngine.canSlide(board, idx)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(SwissControlShape)
                                            .background(if (v == 0) palette.paper3 else if (v == idx + 1) palette.ok.copy(alpha = 0.16f) else palette.paper)
                                            .border(1.5.dp, if (movable) palette.chapterColor(hue) else palette.hairline, SwissControlShape)
                                            .clickable(enabled = movable) {
                                                FifteenPuzzleEngine.slide(board, idx)?.let { next ->
                                                    board = next
                                                    moves++
                                                    if (FifteenPuzzleEngine.isSolved(next)) {
                                                        onRecordScore("game-fifteen", "$moves moves", moves.toDouble(), false)
                                                        onStatusToast("15-Puzzle solved in $moves moves!")
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (v > 0) Text(v.toString(), style = DotkitType.readoutDoto(22), color = palette.ink)
                                    }
                                }
                            }
                        }
                        PixelButton("Shuffle Solvable Board", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            board = FifteenPuzzleEngine.shuffledSolvable(); moves = 0
                        })
                    }
                }
            }

            "game-hanoi" -> {
                var diskCount by remember { mutableIntStateOf(4) }
                var pegs by remember { mutableStateOf(HanoiEngine.initialPegs(4)) }
                var selectedPeg by remember { mutableIntStateOf(-1) }
                var moves by remember { mutableIntStateOf(0) }
                val solved = HanoiEngine.isSolved(pegs, diskCount)
                val optimal = HanoiEngine.optimalMoves(diskCount)

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("$moves / $optimal", "MOVES (OPTIMAL $optimal)", hue)
                            if (solved) PixelStamp("SOLVED ✓", palette.ok)
                        }
                        PixelSteppedSlider("Disks", diskCount, 3..7, 1, " disks", hue, { n ->
                            diskCount = n; pegs = HanoiEngine.initialPegs(n); selectedPeg = -1; moves = 0
                        })
                        Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pegs.forEachIndexed { pegIdx, stack ->
                                val isSel = selectedPeg == pegIdx
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(190.dp)
                                        .clip(SwissControlShape)
                                        .background(if (isSel) palette.chapterColor(hue).copy(alpha = 0.14f) else palette.paper)
                                        .border(1.5.dp, if (isSel) palette.chapterColor(hue) else palette.hairline, SwissControlShape)
                                        .clickable(enabled = !solved) {
                                            if (selectedPeg == -1) {
                                                if (stack.isNotEmpty()) selectedPeg = pegIdx
                                            } else if (selectedPeg == pegIdx) {
                                                selectedPeg = -1
                                            } else {
                                                HanoiEngine.move(pegs, selectedPeg, pegIdx)?.let { next ->
                                                    pegs = next
                                                    moves++
                                                    if (HanoiEngine.isSolved(next, diskCount)) {
                                                        onRecordScore("game-hanoi", "${diskCount}d in $moves", moves.toDouble(), false)
                                                        onStatusToast("Hanoi solved in $moves moves!")
                                                    }
                                                } ?: onStatusToast("Cannot place larger disk on smaller disk.")
                                                selectedPeg = -1
                                            }
                                        }
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.Bottom,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    stack.reversed().forEach { disk ->
                                        val frac = (disk.toFloat() / diskCount.toFloat()).coerceIn(0.28f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(frac)
                                                .padding(vertical = 2.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(palette.chapterColor(hue))
                                        )
                                    }
                                    Text("PEG ${'A' + pegIdx}", style = DotkitType.monoLabel(9), color = palette.ink2, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        PixelButton("Reset Pegs", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            pegs = HanoiEngine.initialPegs(diskCount); selectedPeg = -1; moves = 0
                        })
                    }
                }
            }

            "game-reversi" -> {
                var board by remember { mutableStateOf(ReversiEngine.initialBoard()) }
                var turn by remember { mutableIntStateOf(1) } // 1 = Black, 2 = White
                var vsAi by remember { mutableStateOf(true) }
                val legal = remember(board, turn) { ReversiEngine.legalMoves(board, turn).toSet() }
                val (p1, p2) = remember(board) { ReversiEngine.counts(board) }
                val gameOver = legal.isEmpty() && ReversiEngine.legalMoves(board, if (turn == 1) 2 else 1).isEmpty()

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("● BLACK: $p1   ○ WHITE: $p2", style = DotkitType.monoData(13), color = palette.ink)
                            PixelChip(if (vsAi) "vs AI" else "2P Pass", selected = vsAi, chapterHue = hue, onClick = { vsAi = !vsAi })
                        }
                        PixelStamp(
                            text = when {
                                gameOver -> if (p1 > p2) "BLACK WINS" else if (p2 > p1) "WHITE WINS" else "DRAW"
                                turn == 1 -> "BLACK TURN (${legal.size} moves)"
                                else -> "WHITE TURN (${legal.size} moves)"
                            },
                            color = palette.chapterColor(hue)
                        )
                        for (r in 0 until 8) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                for (c in 0 until 8) {
                                    val pos = r * 8 + c
                                    val cell = board[pos]
                                    val canPlay = pos in legal && !gameOver
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .background(Color(0xFF14532D))
                                            .border(0.8.dp, Color(0xFF052E16))
                                            .clickable(enabled = canPlay) {
                                                val after = ReversiEngine.applyMove(board, pos, turn) ?: return@clickable
                                                var nextB = after
                                                var nextTurn = if (turn == 1) 2 else 1
                                                if (vsAi && nextTurn == 2) {
                                                    ReversiEngine.bestAiMove(nextB, 2)?.let { aiPos ->
                                                        nextB = ReversiEngine.applyMove(nextB, aiPos, 2) ?: nextB
                                                    }
                                                    nextTurn = if (ReversiEngine.legalMoves(nextB, 1).isNotEmpty()) 1 else 2
                                                } else if (ReversiEngine.legalMoves(nextB, nextTurn).isEmpty()) {
                                                    nextTurn = turn
                                                }
                                                board = nextB
                                                turn = nextTurn
                                                val (bCnt, wCnt) = ReversiEngine.counts(nextB)
                                                if (ReversiEngine.legalMoves(nextB, 1).isEmpty() && ReversiEngine.legalMoves(nextB, 2).isEmpty()) {
                                                    onRecordScore("game-reversi", "$bCnt-$wCnt", bCnt.toDouble(), true)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (cell != 0) {
                                            Box(Modifier.fillMaxWidth(0.75f).aspectRatio(1f).clip(CircleShape).background(if (cell == 1) Color(0xFF111827) else Color(0xFFF9FAFB)))
                                        } else if (canPlay) {
                                            Box(Modifier.fillMaxWidth(0.28f).aspectRatio(1f).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
                                        }
                                    }
                                }
                            }
                        }
                        PixelButton("New Reversi Match", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            board = ReversiEngine.initialBoard(); turn = 1
                        })
                    }
                }
            }

            else -> { // game-dots (4x4 Dots & Boxes)
                val grid = 4
                var hEdges by remember { mutableStateOf(BooleanArray((grid + 1) * grid)) }
                var vEdges by remember { mutableStateOf(BooleanArray(grid * (grid + 1))) }
                var boxOwners by remember { mutableStateOf(IntArray(grid * grid)) }
                var turn by remember { mutableIntStateOf(1) }
                var vsAi by remember { mutableStateOf(true) }

                fun checkCaptures(he: BooleanArray, ve: BooleanArray, owners: IntArray, player: Int): Int {
                    var gained = 0
                    for (r in 0 until grid) {
                        for (c in 0 until grid) {
                            val bIdx = r * grid + c
                            if (owners[bIdx] == 0) {
                                val top = he[r * grid + c]
                                val bot = he[(r + 1) * grid + c]
                                val lft = ve[r * (grid + 1) + c]
                                val rgt = ve[r * (grid + 1) + (c + 1)]
                                if (top && bot && lft && rgt) {
                                    owners[bIdx] = player
                                    gained++
                                }
                            }
                        }
                    }
                    return gained
                }

                fun playEdge(isHoriz: Boolean, edgeIdx: Int) {
                    val he = hEdges.copyOf()
                    val ve = vEdges.copyOf()
                    val own = boxOwners.copyOf()
                    if (isHoriz) { if (he[edgeIdx]) return; he[edgeIdx] = true }
                    else { if (ve[edgeIdx]) return; ve[edgeIdx] = true }
                    val gained = checkCaptures(he, ve, own, turn)
                    var nextTurn = if (gained > 0) turn else if (turn == 1) 2 else 1

                    if (vsAi && nextTurn == 2) {
                        while (nextTurn == 2 && own.any { it == 0 }) {
                            val availH = he.indices.filter { !he[it] }
                            val availV = ve.indices.filter { !ve[it] }
                            if (availH.isEmpty() && availV.isEmpty()) break
                            // Pick edge completing a box if possible, otherwise random available
                            var picked: Pair<Boolean, Int>? = null
                            for (hi in availH) {
                                val test = he.copyOf().also { it[hi] = true }
                                val testOwn = own.copyOf()
                                if (checkCaptures(test, ve, testOwn, 2) > 0) { picked = true to hi; break }
                            }
                            if (picked == null) {
                                for (vi in availV) {
                                    val test = ve.copyOf().also { it[vi] = true }
                                    val testOwn = own.copyOf()
                                    if (checkCaptures(he, test, testOwn, 2) > 0) { picked = false to vi; break }
                                }
                            }
                            if (picked == null) {
                                val all = availH.map { true to it } + availV.map { false to it }
                                picked = all[Random.nextInt(all.size)]
                            }
                            if (picked.first) he[picked.second] = true else ve[picked.second] = true
                            val aiGain = checkCaptures(he, ve, own, 2)
                            if (aiGain == 0) nextTurn = 1
                        }
                    }
                    hEdges = he; vEdges = ve; boxOwners = own; turn = nextTurn
                    if (own.all { it != 0 }) {
                        val p1 = own.count { it == 1 }
                        onRecordScore("game-dots", "$p1/${grid * grid} boxes", p1.toDouble(), true)
                    }
                }

                val p1Score = boxOwners.count { it == 1 }
                val p2Score = boxOwners.count { it == 2 }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("P1: $p1Score  ·  ${if (vsAi) "AI" else "P2"}: $p2Score", style = DotkitType.monoData(14), color = palette.ink)
                            PixelChip(if (vsAi) "vs AI" else "2P", selected = vsAi, chapterHue = hue, onClick = { vsAi = !vsAi })
                        }
                        for (r in 0..grid) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                for (c in 0 until grid) {
                                    val idx = r * grid + c
                                    val on = hEdges[idx]
                                    Box(Modifier.width(10.dp).height(10.dp).clip(CircleShape).background(palette.ink))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(18.dp)
                                            .padding(horizontal = 2.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (on) palette.chapterColor(hue) else palette.paper3)
                                            .clickable(enabled = !on) { playEdge(true, idx) }
                                    )
                                }
                                Box(Modifier.width(10.dp).height(10.dp).clip(CircleShape).background(palette.ink))
                            }
                            if (r < grid) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    for (c in 0..grid) {
                                        val vIdx = r * (grid + 1) + c
                                        val on = vEdges[vIdx]
                                        Box(
                                            modifier = Modifier
                                                .width(18.dp)
                                                .height(44.dp)
                                                .padding(vertical = 2.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (on) palette.chapterColor(hue) else palette.paper3)
                                                .clickable(enabled = !on) { playEdge(false, vIdx) }
                                        )
                                        if (c < grid) {
                                            val owner = boxOwners[r * grid + c]
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .background(
                                                        when (owner) {
                                                            1 -> palette.chapterColor(hue).copy(alpha = 0.28f)
                                                            2 -> palette.info.copy(alpha = 0.28f)
                                                            else -> Color.Transparent
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (owner != 0) Text(if (owner == 1) "1" else "2", style = DotkitType.monoData(14), color = palette.ink)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        PixelButton("Reset Grid", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            hEdges = BooleanArray((grid + 1) * grid)
                            vEdges = BooleanArray(grid * (grid + 1))
                            boxOwners = IntArray(grid * grid)
                            turn = 1
                        })
                    }
                }
            }
        }
    }
}
