# Dotkit — Architecture & Plugin Contract

## 1. Platform & Stack
- **Runtime**: Android (Kotlin 2.2 + Jetpack Compose + Coroutines/Flow), offline-first by default.
- **Database & Storage**: Room SQLite (`DotkitDatabase`) for settings, tool history, saved files, tally counters, and the session network ledger; local app files for binary/text outputs.
- **Companion Server (`/server`)**: Node 20+ TypeScript companion spec for yt-dlp, ffmpeg, aria2c, shttps, and SSRF-guarded URL tracing (delivered in Phase 2/3).

## 2. Package Layout (`app/src/main/java/com/example/`)
- `design/` — `Tokens.kt`, `Typography.kt`, `PixelIcons.kt` (ASCII 16×16 bitmap sprite engine & 64×64 scenes), `PixelComponents.kt` (PixelBox, stepped corners, hard shadows, Bayer dither, 8-segment bars, buttons, sliders, cards), `SpecimenScreen.kt` (`/specimen` QA gate).
- `registry/` — `ToolContract.kt` & `ToolRegistry.kt` (single source of truth for all 22 chapters and tools, driving Home, Index, Command Bar, `Send to…`, and Roadmap).
- `lib/` — Pure, deterministic domain logic with JUnit/Vitest-equivalent unit tests in `app/src/test/`:
  - `lib/text/CaseConverterEngine.kt`
  - `lib/crypto/HashEngine.kt`
  - `lib/command/CommandEngine.kt` (inline math, units, EMI, time zones, hashes, UUIDs, colours)
  - `lib/format/NumberFormatters.kt` (`en-IN` lakh/crore and international formatting)
- `services/` — Cross-cutting device & network services:
  - `services/capabilities/CapabilitiesService.kt` (real hardware sensor & API probing; zero fake readings)
  - `services/storage/DotkitDatabase.kt` & `DotkitRepository.kt`
  - `services/gemini/GeminiService.kt` (model definitions, offline kill-switch, consent gate, cost meter)
  - `services/sound/PixelSoundEngine.kt` (optional synthesized 8-bit WebAudio-style UI blips & haptics)
- `tools/` — Self-contained tool modules following the plugin contract:
  - `tools/time/tally/TallyCounterTool.kt` (`№ 001`)
  - `tools/words/caseconv/CaseConverterTool.kt` (`№ 002`)
  - `tools/dev/hash/HashGeneratorTool.kt` (`№ 003`)
- `app/` — `DotkitViewModel.kt`, `DotkitShell.kt` (Home TOC, Index A–Z, Command Bar modal, Files & Privacy Ledger, Settings/You, First-Run Onboarding, and Tool Workbench).

## 3. Tool Plugin Contract (`ToolMeta`)
Every tool registers a `ToolMeta` entry with:
- `id`: Stable slug (`time-tally`, `words-case`, `dev-hash`)
- `number`: Catalogue specimen number (`1`, `2`, `3`…)
- `chapterId`: Primary chapter (`1`..`22`) and `alsoIn: List<Int>`
- `name`, `tagline`, `keywords`, `aliases`
- `requires`: Set of `CapabilityRequirement` (`CAMERA`, `MIC`, `MOTION`, `GEO`, `BLUETOOTH`, `COMPANION`, `AI`, `NETWORK`, `STORAGE`, `CLIPBOARD`)
- `offline`: `Boolean`
- `io`: `ToolIO(accepts = [...], produces = [...])` powering the cross-tool **Send to…** pipeline
- `status`: `ToolStatus.LIVE` or `ToolStatus.PLANNED`
