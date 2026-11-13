# Dotkit — Design System ("Pixel Editorial")

## 1. Core Concept & Principles
A field guide set by a Swiss-grid typographer, printed on a 4dp pixel grid. Every tool is a numbered specimen (`№ 001` and up) in a 22-chapter catalogue:
1. **Type is the interface**: Icons support words; they never replace them.
2. **One grid**: Everything snaps to a `4dp` unit (`--u: 4dp`). Pixel art scales only by integer factors (`1×`, `2×`, `3×`).
3. **Flat colour, hard edges**: Zero gradients, zero blur, zero glassmorphism. Depth comes from paper layers (`paper` → `paper2` → `paper3`), `2dp` ink borders, hard `4dp` offset ink shadows, and Bayer 4×4 dither patterns.
4. **Colour is wayfinding**: Each of the 22 chapters owns one flat, saturated hue. HSL 250°–330° (purple/violet/indigo) is strictly banned across the entire app.
5. **Purposeful motion**: Spring physics and 8-step bitmap reveals under 400ms, honouring `Full`, `Calm`, and `Off` motion modes.

## 2. Five-Role Type System (Local SIL OFL `.ttf` in `res/font/`)
- **Display (Pixel)**: `Pixelify Sans` (`R.font.pixelify_sans`) & `Silkscreen` (`R.font.silkscreen`) — set at pixel-grid multiples (`16`, `24`, `32`, `48`, `64`, `96` sp).
- **Readout (Dot-Matrix)**: `Doto` (`R.font.doto`) — clocks, counters, scoreboards, and live meter readouts with `tnum`.
- **Text & UI**: `Bricolage Grotesque` (`R.font.bricolage_grotesque`) — body copy, buttons, forms. Replaceable in Settings → Look with `Atkinson Hyperlegible` (`R.font.atkinson_hyperlegible`) for dyslexia-friendly reading.
- **Data & Labels**: `Martian Mono` (`R.font.martian_mono`) & `JetBrains Mono` (`R.font.jetbrains_mono`) — metadata, tables, hashes, tags, uppercase labels with `+0.06em` tracking and tabular numerals (`tnum`).
- **Accent Serif**: `Newsreader` (`R.font.newsreader`) — help sheets, pull-quotes, specimen notes, and empty states.

## 3. Colour Tokens (Paper, Ink & High-Contrast Themes)
### Paper Theme (Default Light)
- `paper`: `#F2ECDD` | `paper2`: `#E8E0CC` | `paper3`: `#DCD2B8`
- `ink`: `#17150F` | `ink2`: `#4B463A` | `ink3`: `#857D69`
- `focus`: `#1E5BFF` | `ok`: `#1F8A4C` | `warn`: `#E8A200` | `err`: `#D92D20` | `info`: `#1E5BFF`

### Ink Theme (Dark)
- `paper`: `#12110D` | `paper2`: `#1B1913` | `paper3`: `#26231A`
- `ink`: `#F2ECDD` | `ink2`: `#C9C1AC` | `ink3`: `#8F8774`
- Chapter hues shift `+8%` lightness in Ink mode for ≥ 4.5:1 AA contrast.

### 22 Chapter Hues & Dither Signatures
| Ch | Name | Hue Token | Hex (Paper) | Dither Pattern |
|----|------|-----------|-------------|----------------|
| 01 | Fetch | `vermilion` | `#FF4B2B` | `diag` |
| 02 | Tunes | `rose` | `#FF6FA8` | `waves` |
| 03 | Link Check | `teal` | `#0E9AA7` | `grid` |
| 04 | Serve | `forest` | `#1C6B4A` | `brick` |
| 05 | Image Lab | `marigold` | `#FFB300` | `dots` |
| 06 | PDF Lab | `clay` | `#C4622D` | `lines` |
| 07 | Time | `sky` | `#2EB8F5` | `rings` |
| 08 | Calc | `cobalt` | `#2547FF` | `graph` |
| 09 | Money | `lime` | `#A6D608` | `coins` |
| 10 | Essentials | `tangerine` | `#FF8A1F` | `check` |
| 11 | Measure | `mint` | `#2DD4A0` | `ticks` |
| 12 | Common | `sand` | `#D9B779` | `weave` |
| 13 | Words | `vermilion` | `#FF4B2B` | `stripes` |
| 14 | Studio | `teal` | `#0E9AA7` | `film` |
| 15 | Device | `cobalt` | `#2547FF` | `scan` |
| 16 | Play | `rose` | `#FF6FA8` | `maze` |
| 17 | Body | `mint` | `#2DD4A0` | `pulse` |
| 18 | Social & Web | `tangerine` | `#FF8A1F` | `chain` |
| 19 | Dev | `forest` | `#1C6B4A` | `braces` |
| 20 | Mind Lab | `marigold` | `#FFB300` | `spark` |
| 21 | AI Lab | `sky` | `#2EB8F5` | `nodes` |
| 22 | Vault & Files | `clay` | `#C4622D` | `lock` |

## 4. Voice & Microcopy
- Short, concrete, dry. Sentence case. Buttons are verbs ("Increment", "Convert case", "Compute digest", "Save to Files").
- Default locale grouping: `en-IN` (`12,34,567`), switchable to `en-US` (`1,234,567`) in Settings → Look & Region.
