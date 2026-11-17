package com.example

import com.example.design.APP_NAME
import com.example.design.RegionFormat
import com.example.design.ToolCategory
import com.example.design.ToolIconResolver
import com.example.design.sanitizeNumericInput
import com.example.lib.calc.DebtAccount
import com.example.lib.calc.EvalResult
import com.example.lib.calc.ExpressionEvaluator
import com.example.lib.calc.ExtendedMathEngines
import com.example.lib.calc.FinanceEngine
import com.example.lib.calc.UnitConverterEngine
import com.example.lib.calc.UnitDomain
import com.example.lib.color.ColorContrastEngine
import com.example.lib.color.RgbColor
import com.example.lib.command.CommandEngine
import com.example.lib.crypto.HashAlgorithm
import com.example.lib.crypto.HashEngine
import com.example.lib.dev.Base32HexEngine
import com.example.lib.dev.DevSuiteEngines
import com.example.lib.dev.PasswordQrEngine
import com.example.lib.dev.SerpPreviewEngine
import com.example.lib.dev.SqlFormatterEngine
import com.example.lib.dev.ThreadSplitterEngine
import com.example.lib.expansions.AspectRatioEngine
import com.example.lib.expansions.GradientBezierEngine
import com.example.lib.expansions.GradientKind
import com.example.lib.expansions.GradientStopSpec
import com.example.lib.expansions.ImagePaletteEngine
import com.example.lib.expansions.JsonDiffKind
import com.example.lib.expansions.JsonDiffSchemaEngine
import com.example.lib.expansions.SubnetTotpEngine
import com.example.lib.expansions.SvgVectorEngine
import com.example.lib.format.NumberFormatters
import com.example.lib.games.BlackjackEngine
import com.example.lib.games.CheckersEngine
import com.example.lib.games.ChessEngine
import com.example.lib.games.Connect4Engine
import com.example.lib.games.FifteenPuzzleEngine
import com.example.lib.games.Game2048Engine
import com.example.lib.games.Game2048State
import com.example.lib.games.HanoiEngine
import com.example.lib.games.LightsOutEngine
import com.example.lib.games.MazeEngine
import com.example.lib.games.MinesweeperEngine
import com.example.lib.games.MoveDir
import com.example.lib.games.NonogramEngine
import com.example.lib.games.PlayingCard
import com.example.lib.games.ReversiEngine
import com.example.lib.games.SudokuDifficulty
import com.example.lib.games.SudokuEngine
import com.example.lib.games.WordleEngine
import com.example.lib.games.WordleTile
import com.example.lib.net.UrlInspectorEngine
import com.example.lib.text.CaseConverterEngine
import com.example.lib.text.CaseStyle
import com.example.lib.text.TextExtraEngine
import com.example.lib.text.TextToolsEngine
import com.example.registry.DataKind
import com.example.registry.ToolRegistry
import com.example.tools.common_suite.FrequencyHearingClassifier
import com.example.tools.time.world.WorldTimezoneDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExampleUnitTest {
    @Test
    fun appBranding_isOmniDeckEverywhere() {
        assertEquals("Omni Deck", APP_NAME)
    }

    @Test
    fun indianAndInternationalNumberFormatting_worksCorrectly() {
        assertEquals("12,34,567", NumberFormatters.formatNumber(1234567L, RegionFormat.EN_IN))
        assertEquals("1,234,567", NumberFormatters.formatNumber(1234567L, RegionFormat.EN_US))
        assertEquals("5,00,000", NumberFormatters.formatNumber(500000L, RegionFormat.EN_IN))
    }

    @Test
    fun caseConverterEngine_transformsAllStylesDeterministically() {
        val sample = "the quick brown fox in the lab"
        assertEquals("THE QUICK BROWN FOX IN THE LAB", CaseConverterEngine.convert(sample, CaseStyle.UPPER))
        assertEquals("The Quick Brown Fox in the Lab", CaseConverterEngine.convert(sample, CaseStyle.TITLE_AP))
        assertEquals("theQuickBrownFoxInTheLab", CaseConverterEngine.convert(sample, CaseStyle.CAMEL))
        assertEquals("TheQuickBrownFoxInTheLab", CaseConverterEngine.convert(sample, CaseStyle.PASCAL))
        assertEquals("the_quick_brown_fox_in_the_lab", CaseConverterEngine.convert(sample, CaseStyle.SNAKE))
        assertEquals("the-quick-brown-fox-in-the-lab", CaseConverterEngine.convert(sample, CaseStyle.KEBAB))
        assertEquals("THE_QUICK_BROWN_FOX_IN_THE_LAB", CaseConverterEngine.convert(sample, CaseStyle.CONSTANT))
    }

    @Test
    fun hashEngine_computesKnownCryptographicVectors() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", HashEngine.digestText("hello", HashAlgorithm.MD5))
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", HashEngine.digestText("hello", HashAlgorithm.SHA256))
        assertEquals("3610a686", HashEngine.digestText("hello", HashAlgorithm.CRC32))
        val id = HashEngine.identifyHash("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
        assertTrue(id.isHex)
        assertTrue(id.candidates.contains("SHA-256"))
        assertTrue(HashEngine.constantTimeEquals("3610A686", "3610a686"))
    }

    @Test
    fun commandEngine_evaluatesInlineQueries() {
        val km = CommandEngine.evaluateInline("5 km in miles")
        assertNotNull(km)
        assertTrue(km!!.headline.startsWith("3.1069"))

        val pct = CommandEngine.evaluateInline("18% of 2400")
        assertNotNull(pct)
        assertEquals("432", pct!!.copyValue)

        val emi = CommandEngine.evaluateInline("emi 5L 8.5% 5y", RegionFormat.EN_IN)
        assertNotNull(emi)
        assertTrue(emi!!.headline.contains("10,258"))

        val md5 = CommandEngine.evaluateInline("md5 hello")
        assertNotNull(md5)
        assertEquals("5d41402abc4b2a76b9719d911017c592", md5!!.copyValue)
    }

    @Test
    fun expressionEvaluator_handlesScientificCombinatoricsAndPrecedence() {
        val r1 = ExpressionEvaluator.evaluate("sqrt(144) + sin(30) * 10", degreesMode = true)
        assertTrue(r1 is EvalResult.Success)
        assertEquals(17.0, (r1 as EvalResult.Success).value, 1e-6)

        val r2 = ExpressionEvaluator.evaluate("5! + nCr(5, 2) + gcd(24, 36)")
        assertEquals("142", (r2 as EvalResult.Success).formatted)

        val r3 = ExpressionEvaluator.evaluate("ans * 2", degreesMode = true, ansValue = 21.0)
        assertEquals("42", (r3 as EvalResult.Success).formatted)

        val err = ExpressionEvaluator.evaluate("10 / 0")
        assertTrue(err is EvalResult.Error)
    }

    @Test
    fun unitConverterAndFinanceEngines_computeAllFormulasAccurately() {
        assertEquals(212.0, UnitConverterEngine.convert(UnitDomain.TEMPERATURE, 100.0, "c", "f"), 1e-6)
        assertEquals(1000.0, UnitConverterEngine.convert(UnitDomain.LENGTH, 1.0, "km", "m"), 1e-6)

        val emi = FinanceEngine.calculateEmi(500_000.0, 8.5, 60)
        assertTrue(emi.monthlyEmi in 10258.0..10259.0)

        val si = FinanceEngine.calculateSimpleInterest(10_000.0, 10.0, 2.0)
        assertEquals(12_000.0, si.maturityValue, 1e-4)

        val ci = FinanceEngine.calculateCompoundInterest(10_000.0, 10.0, 2.0, 1)
        assertEquals(12_100.0, ci.maturityValue, 1e-4)

        val sip = FinanceEngine.calculateSip(5_000.0, 12.0, 10)
        assertTrue(sip.maturityValue > sip.investedAmount)

        val rd = FinanceEngine.calculateRd(1_000.0, 6.0, 12)
        assertTrue(rd.maturityValue > 12_000.0)

        val gstExcl = FinanceEngine.calculateGst(1_000.0, 18.0, inclusive = false)
        assertEquals(1_180.0, gstExcl.totalAmount, 1e-4)
        assertEquals(90.0, gstExcl.cgstAmount, 1e-4)

        val split = FinanceEngine.calculateTipSplit(1000.0, 10.0, 4)
        assertEquals(275.0, split.perPerson, 1e-6)
    }

    @Test
    fun urlInspectorHeuristicsAndStripper_workAccurately() {
        val (cleaned, stripped) = UrlInspectorEngine.stripTrackingParams("https://example.com/post?id=42&utm_source=x&fbclid=abc123")
        assertEquals("https://example.com/post?id=42", cleaned)
        assertEquals(listOf("utm_source", "fbclid"), stripped)

        val (_, homograph, findings) = UrlInspectorEngine.analyzeHostHeuristics("http://xn--pple-43d.com/login")
        assertTrue(homograph)
        assertTrue(findings.any { "HTTP" in it })
        assertTrue(findings.any { "Punycode" in it })
    }

    @Test
    fun devSuiteAndGameEngines_workDeterministically() {
        val rx = DevSuiteEngines.testRegex("\\d+", "a12b34", "#", false, false)
        assertTrue(rx.valid)
        assertEquals(2, rx.matchCount)
        assertEquals("a#b#", rx.replacedText)

        val (_, nextRuns) = DevSuiteEngines.describeAndPreviewCron("*/15 * * * *")
        assertEquals(5, nextRuns.size)

        val trace = DevSuiteEngines.evaluateScriptSandbox("x = 10\ny = x * 3 + 5")
        assertTrue(trace.last().contains("35"))

        val s2048 = Game2048State(board = intArrayOf(2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        val (afterMove, moved) = Game2048Engine.move(s2048, MoveDir.LEFT, Random(1))
        assertTrue(moved)
        assertEquals(4, afterMove.board[0])
        assertEquals(4, afterMove.score)

        val sud = SudokuEngine.generate(SudokuDifficulty.EASY, Random(7))
        assertEquals(81, sud.puzzle.size)
        assertTrue(sud.solution.all { it in 1..9 })

        val mines0 = MinesweeperEngine.createBlank(9, 9, 10)
        val mines1 = MinesweeperEngine.reveal(mines0, 0, Random(3))
        assertTrue(mines1.generated)
        assertFalse(mines1.gameOver)

        val chess0 = ChessEngine.initialState()
        val e2Moves = ChessEngine.legalMovesForSquare(chess0, 52) // e2
        assertEquals(2, e2Moves.size)
    }

    @Test
    fun textToolsAndPasswordQrAndColorEngines_workProperly() {
        assertEquals("... --- ...", TextToolsEngine.encodeMorse("SOS"))
        assertEquals("SOS", TextToolsEngine.decodeMorse("... --- ..."))
        assertEquals("OmniDeck", TextToolsEngine.fromBase64(TextToolsEngine.toBase64("OmniDeck")))
        assertEquals("OK", TextToolsEngine.fromBinary(TextToolsEngine.toBinary("OK")))
        assertEquals("OmniDeck", TextToolsEngine.rot13(TextToolsEngine.rot13("OmniDeck")))

        val diff = TextToolsEngine.diffLines("a\nb", "a\nc")
        assertTrue(diff.any { it.kind == '-' && it.text == "b" })
        assertTrue(diff.any { it.kind == '+' && it.text == "c" })

        val pw = PasswordQrEngine.generatePassword(length = 20)
        assertEquals(20, pw.password.length)
        assertTrue(pw.entropyBits > 80.0)

        val qr = PasswordQrEngine.encodeQrMatrix("https://omnideck.local")
        assertNotNull(qr)
        assertTrue(qr!!.size >= 21)
        assertTrue(qr[0][0])
        assertTrue(qr[6][6])
        assertFalse(qr[1][1])

        val eval = ColorContrastEngine.evaluateContrast(RgbColor(0, 0, 0), RgbColor(255, 255, 255))
        assertEquals(21.0, eval.ratio, 0.01)
        assertTrue(eval.passAaaNormal)
    }

    @Test
    fun toolRegistry_all206ToolsUniqueWithUniqueIconsAndKeywordSearchWorks() {
        assertEquals(206, ToolRegistry.LIVE_TOOLS.size)
        assertEquals(206, ToolRegistry.ALL_TOOLS.map { it.id }.distinct().size)
        assertEquals(206, ToolRegistry.ALL_TOOLS.map { it.name }.distinct().size)

        ToolRegistry.ALL_TOOLS.forEach { tool ->
            assertNotNull("Missing explicit icon for ${tool.id}", ToolIconResolver.toolIconById(tool.id))
        }

        assertTrue(ToolRegistry.searchTools("seal").any { it.id == "dl-video" })
        assertTrue(ToolRegistry.searchTools("spotdl").any { it.id == "dl-music" })
        assertTrue(ToolRegistry.searchTools("shttps").any { it.id == "srv-shttps" })
        assertTrue(ToolRegistry.searchTools("imagetoolbox").any { it.id == "img-filters" })
        assertTrue(ToolRegistry.searchTools("paperknife").any { it.id == "pdf-merge" })
        assertTrue(ToolRegistry.searchTools("flashlight").any { it.id == "ess-flash" })
        assertTrue(ToolRegistry.searchTools("pomodoro").any { it.id == "time-pomodoro" })
        assertTrue(ToolRegistry.searchTools("chess").any { it.id == "game-chess" })
        assertTrue(ToolRegistry.searchTools("wordle").any { it.id == "game-wordle" })
        assertTrue(ToolRegistry.searchTools("caffeine").any { it.id == "hlth-caffeine" })

        val dlTools = ToolRegistry.getToolsByCategory(ToolCategory.DOWNLOAD)
        assertEquals(2, dlTools.size)

        val targets = ToolRegistry.findCompatibleSendToTargets("txt-case", DataKind.TEXT)
        assertTrue(targets.any { it.id == "dev-hash" })
    }

    @Test
    fun numericSanitizerTimezonesAndHearingSpecies_workCorrectly() {
        assertEquals("653", sanitizeNumericInput("0", "0653"))
        assertEquals("0.5", sanitizeNumericInput("0", "0.5"))
        assertEquals("0", sanitizeNumericInput("12", ""))

        assertTrue(WorldTimezoneDirectory.ALL_ZONES.size > 300)
        assertTrue(WorldTimezoneDirectory.filter("tokyo").any { it.id == "Asia/Tokyo" })

        val infra = FrequencyHearingClassifier.listenersForFrequency(12)
        assertTrue(infra.any { it.name == "Blue Whale" })
        assertFalse(infra.any { it.name == "Human" })

        val audible = FrequencyHearingClassifier.listenersForFrequency(1000)
        assertTrue(audible.any { it.name == "Human" })
        assertTrue(audible.any { it.name == "Dog" })

        val ultra = FrequencyHearingClassifier.listenersForFrequency(28000)
        assertFalse(ultra.any { it.name == "Human" })
        assertTrue(ultra.any { it.name == "Bat" })
    }

    @Test
    fun textExtraAndNewGameEngines_workAccurately() {
        val enc = TextExtraEngine.caesar("OmniDeck", 5, decrypt = false)
        assertEquals("OmniDeck", TextExtraEngine.caesar(enc, 5, decrypt = true))
        val vig = TextExtraEngine.vigenere("SWISSGRID", "KEY", decrypt = false)
        assertEquals("SWISSGRID", TextExtraEngine.vigenere(vig, "KEY", decrypt = true))

        val stego = TextExtraEngine.embedZeroWidth("Public report", "TOPSECRET")
        assertTrue(stego.contains("Public"))
        assertEquals("TOPSECRET", TextExtraEngine.extractZeroWidth(stego))

        val ulid = TextExtraEngine.generateUlid()
        assertEquals(26, ulid.length)
        assertEquals(21, TextExtraEngine.generateNanoId().length)
        assertTrue(TextExtraEngine.shannonEntropyBitsPerChar("abcdefgh") > 2.9)

        val kp = TextExtraEngine.generateAsymKeyPair(useEcdsa = true)
        val sig = TextExtraEngine.signPayload("OmniDeck", kp.privateKey, isEc = true)
        assertTrue(TextExtraEngine.verifyPayload("OmniDeck", sig, kp.publicKey, isEc = true))

        // Connect 4 vertical win
        var c4 = Connect4Engine.emptyBoard()
        repeat(4) { c4 = Connect4Engine.dropDisc(c4, 3, 1)!!.first }
        assertEquals(1, Connect4Engine.checkWinner(c4)?.winner)

        // Wordle duplicate letter scoring
        val eval = WordleEngine.evaluateGuess("ABBEY", "BABES")
        assertEquals(listOf(WordleTile.PRESENT, WordleTile.PRESENT, WordleTile.EXACT, WordleTile.EXACT, WordleTile.ABSENT), eval)

        // Lights Out optimal toggle solve
        var lo = LightsOutEngine.generateSolvable(4, Random(9))
        assertFalse(lo.isSolved)
        for (idx in lo.solutionSet.toList()) {
            lo = LightsOutEngine.toggle(lo, idx / LightsOutEngine.SIDE, idx % LightsOutEngine.SIDE)
        }
        assertTrue(lo.isSolved)

        // Checkers initial legal moves (7 red moves on row 5 -> row 4)
        val chk0 = CheckersEngine.initialState()
        assertEquals(7, CheckersEngine.legalMoves(chk0).size)
    }

    @Test
    fun extendedMathEngines_workAccurately() {
        val solar = ExtendedMathEngines.computeNoaaSolarTimes(2026, 6, 21, 37.7749, -122.4194, -7.0)
        assertNotNull(solar.sunriseLocalMin)
        assertNotNull(solar.sunsetLocalMin)
        assertTrue(solar.daylightMinutes in 860.0..920.0)

        val conv = ExtendedMathEngines.convertCalendar(2026, 1, 1)
        assertTrue(conv.hijriDate.contains("1447"))
        assertTrue(conv.jalaliDate.startsWith("1404-10-"))

        assertEquals(5, ExtendedMathEngines.countBusinessDays(0L, 6L))

        val bits = ExtendedMathEngines.formatBitwiseBases(255L)
        assertEquals("FF", bits["HEX"])
        val (sign, exp, _) = ExtendedMathEngines.ieee754FloatBreakdown(-2.0f)
        assertEquals("1", sign)
        assertEquals("10000000", exp)

        val m2 = ExtendedMathEngines.solve2x2Matrix(listOf(listOf(4.0, 7.0), listOf(2.0, 6.0)))
        assertEquals(10.0, m2.determinant, 1e-6)
        assertNotNull(m2.inverse)

        val m3 = ExtendedMathEngines.solve3x3Matrix(listOf(listOf(1.0, 0.0, 0.0), listOf(0.0, 2.0, 0.0), listOf(0.0, 0.0, 3.0)))
        assertEquals(6.0, m3.determinant, 1e-6)

        val reg = ExtendedMathEngines.computeStatsAndRegression(listOf(1.0, 2.0, 3.0), listOf(2.0, 4.0, 6.0))!!
        assertEquals(2.0, reg.slope!!, 1e-6)
        assertEquals(1.0, reg.r!!, 1e-6)

        val accts = listOf(DebtAccount("Small", 1000.0, 12.0, 50.0), DebtAccount("HighApr", 3000.0, 24.0, 80.0))
        val aval = ExtendedMathEngines.simulateDebtPayoff(accts, 150.0, avalanche = true)
        val snow = ExtendedMathEngines.simulateDebtPayoff(accts, 150.0, avalanche = false)
        assertTrue(aval.totalInterestPaid <= snow.totalInterestPaid)
        assertEquals(250_000.0, ExtendedMathEngines.computeFireNumber(10_000.0, 4.0), 1e-6)

        val read = ExtendedMathEngines.analyzeReadability("Simple short text.\u200B Another line.")
        assertTrue(read.invisibleCharsFound.isNotEmpty())
        assertTrue(ExtendedMathEngines.applyMailMerge("Hi {{n}}", "n\nAda").contains("Hi Ada"))
    }

    @Test
    fun moreGamesAndDevSocialEngines_workAccurately() {
        val shuffled15 = FifteenPuzzleEngine.shuffledSolvable(Random(5))
        assertTrue(FifteenPuzzleEngine.isSolvable(shuffled15))
        assertFalse(FifteenPuzzleEngine.isSolved(shuffled15))

        assertEquals(15, HanoiEngine.optimalMoves(4))
        val h0 = HanoiEngine.initialPegs(3)
        assertNotNull(HanoiEngine.move(h0, 0, 2))

        val rev0 = ReversiEngine.initialBoard()
        assertEquals(4, ReversiEngine.legalMoves(rev0, 1).size)
        val rev1 = ReversiEngine.applyMove(rev0, 2 * 8 + 3, 1)!!
        assertEquals(4 to 1, ReversiEngine.counts(rev1))

        val nono = NonogramEngine.generatePuzzle(5, Random(2))
        assertTrue(NonogramEngine.isSolved(nono, nono.solution))

        val maze = MazeEngine.generate(6, 6, Random(4))
        val path = MazeEngine.shortestPathBfs(maze, 0, 35)
        assertTrue(path.first() == 0 && path.last() == 35)

        assertEquals(21, BlackjackEngine.handScore(listOf(PlayingCard(1, 0), PlayingCard(13, 1))))
        assertEquals(12, BlackjackEngine.handScore(listOf(PlayingCard(1, 0), PlayingCard(1, 1))))

        val thread = ThreadSplitterEngine.splitThread("word ".repeat(80), maxChars = 100, includeCounter = true)
        assertTrue(thread.size >= 4)
        assertTrue(thread.all { it.length <= 100 })

        assertTrue(SerpPreviewEngine.estimateTitlePx("Omni Deck Title") > 80)
        assertTrue(SqlFormatterEngine.formatSql("select id from users where id = 1").contains("\nFROM"))
        assertTrue(SqlFormatterEngine.lintSql("DELETE FROM users").any { it.contains("WHERE") })

        val b32 = Base32HexEngine.encodeBase32("Omni".toByteArray())
        assertEquals("Omni", Base32HexEngine.decodeBase32(b32).toString(Charsets.UTF_8))
    }

    @Test
    fun expansionPackIII_enginesWorkAccurately() {
        val diff = JsonDiffSchemaEngine.diffJson("""{"a":1,"b":2}""", """{"a":1,"b":3,"c":true}""")
        assertTrue(diff.valid)
        assertTrue(diff.entries.any { it.path == "$.b" && it.kind == JsonDiffKind.CHANGED })
        assertTrue(diff.entries.any { it.path == "$.c" && it.kind == JsonDiffKind.ADDED })
        assertTrue(JsonDiffSchemaEngine.generateKotlinDataClasses("""{"id":1,"name":"Omni"}""").getOrThrow().contains("data class RootPayload"))
        assertTrue(JsonDiffSchemaEngine.generateTypeScriptInterfaces("""{"id":1}""").getOrThrow().contains("export interface RootPayload"))

        val svg = SvgVectorEngine.optimizeAndInspect("""<svg viewBox="0 0 24 24"><!-- c --><metadata>x</metadata><path d="M1.12345 2.0000 Z"/></svg>""", 2)
        assertFalse(svg.optimizedSvg.contains("<metadata>"))
        assertTrue(svg.savingsPercent > 0f)

        val css = GradientBezierEngine.toCss(GradientKind.LINEAR, 90, listOf(GradientStopSpec(0xFF000000.toInt(), 0), GradientStopSpec(0xFFFFFFFF.toInt(), 100)))
        assertTrue(css.contains("linear-gradient(90deg"))
        assertEquals(0.5f, GradientBezierEngine.sampleCubicBezier(0f, 0f, 1f, 1f, 0.5f), 0.02f)

        val ratio = AspectRatioEngine.analyze(1920, 1080)
        assertEquals("16:9", ratio.ratioLabel)
        assertEquals(720, AspectRatioEngine.scaleHeightForRatio(1920, 1080, 1280))
        assertEquals(50.0, AspectRatioEngine.ruleOfThree(2.0, 10.0, 10.0)!!, 1e-6)

        val subnet = SubnetTotpEngine.parseIpv4Cidr("192.168.1.42/24")
        assertTrue(subnet.valid)
        assertEquals("192.168.1.0", subnet.networkAddress)
        assertEquals("192.168.1.255", subnet.broadcastAddress)
        assertEquals(254L, subnet.usableHosts)

        // RFC 6238 SHA-1 test vector for ASCII "12345678901234567890" -> Base32 GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ at epoch 59 -> "94287082" (8 digits)
        val totp = SubnetTotpEngine.computeTotp("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", epochSeconds = 59L, periodSec = 30, digits = 8)
        assertTrue(totp.valid)
        assertEquals("94287082", totp.code)

        val swatches = ImagePaletteEngine.extractTopSwatches(intArrayOf(0xFF2563EB.toInt(), 0xFF2563EB.toInt(), 0xFF10B981.toInt()), 4)
        assertEquals(2, swatches.size)
    }
}
