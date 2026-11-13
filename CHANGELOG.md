# Dotkit — Changelog

## v2 — Minimalist UI, 6 Themes, Categories & 15 Live Functional Tools
- **Minimalist & Responsive Layout**: Simplified cards, replaced keyboard shortcut names (`ESC / CLOSE`, `⌘K`, `[01]`/`[CFG]`, `[TICKER]`) with clean icons and symbol triggers (`←`, `★`/`☆`, `?`, `✕`), added Material icons in the bottom NavigationBar (phones) and Side Rail (tablets `>= 720dp`), and constrained content width (`widthIn(max = 860–920.dp)`) for tablet readability.
- **Persistent Top Search & Instant Solver**: Added a top search bar on both Home and Tools tabs that searches tool names, taglines, keywords, and aliases alongside instant inline math/unit/EMI/time/hash/UUID results.
- **Home Recent Tools & Category Tabs**: Added a dedicated `RECENTLY USED` strip and category browser on Home, plus scrollable category tabs (`All`, `Time`, `Calc & Units`, `Words`, `Dev & Crypto`, `Daily & Device`) in `Tools`.
- **Settings Menu with 6 Themes**: Added visual theme swatch cards for `Paper`, `Ink`, `Blueprint`, `Terminal`, `Sepia`, `High Contrast`, plus `System` auto, accent hue picker, text scale (`0.9×–1.3×`), density, motion, sound/haptics, region format, hardware probe list, and data reset.
- **15 Fully Functional Tools**:
  1. `Tally Counter` (`time-tally`)
  2. `Stopwatch & Timer` (`time-watch`)
  3. `World Clock & Date Diff` (`time-world`)
  4. `Scientific Calculator` (`calc-sci`)
  5. `Unit Converter` (`com-units`)
  6. `EMI, % & Bill Split` (`money-emi`)
  7. `Case Converter` (`words-case`)
  8. `Word Counter & Diff` (`words-diff`)
  9. `Morse & Text Codec` (`words-morse`)
  10. `Hash & HMAC Generator` (`dev-hash`)
  11. `JSON Formatter & JWT` (`dev-json`)
  12. `Password, UUID & QR` (`dev-pass`)
  13. `Quick Notes & Checklist` (`ess-notes`)
  14. `Color & Contrast Studio` (`dev-color`)
  15. `Torch, Level & Device` (`meas-sensor`)

## Phase 0 — Foundations
- Established Pixel Editorial design system, local SIL OFL fonts, adaptive launcher icon, Room SQLite database, and initial 3 tools.
