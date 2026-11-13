# Dotkit — Architectural & Licensing Decisions

## 1. Android Native + Offline-First Execution
- **Context**: The AI Studio workspace is an Android project (Kotlin + Jetpack Compose) while the Dotkit specification describes a 22-chapter offline-first multi-tool application with web/PWA and companion-server ergonomics.
- **Decision**: Implement Dotkit natively in Kotlin + Jetpack Compose adhering strictly to the **Pixel Editorial (§3)** design system, 22-chapter catalogue, plugin registry (`ToolMeta`), inline Command Bar, `/specimen` QA screen, Room SQLite storage (`DotkitDatabase`), and real Android hardware capability probing (`SensorManager`, `BatteryManager`, `ConnectivityManager`, `Vibrator`).

## 2. Clean-Room Licensing Policy (§5.6)
- **Seal (GPL-3.0)** & **PaperKnife (AGPL-3.0)**: Zero code is copied from Seal or PaperKnife. Features are re-implemented from scratch using permissive MIT/Apache-2.0 libraries so Dotkit remains permissively licensed. The name "Seal" is never used inside the UI.
- **ImageToolbox (Apache-2.0)** & **shttps**: Credited in the built-in Open-Source Licences screen (`Settings → Licences`).

## 3. Typography Bundling
- Installed all five requested SIL OFL font roles locally in `app/src/main/res/font/` via `font-util`:
  - `pixelify_sans.ttf` & `silkscreen.ttf` (Display Pixel)
  - `doto.ttf` (Readout Dot-Matrix)
  - `bricolage_grotesque.ttf` & `atkinson_hyperlegible.ttf` (UI & Dyslexia-Friendly toggle)
  - `martian_mono.ttf` & `jetbrains_mono.ttf` (Data & Labels)
  - `newsreader.ttf` (Accent Serif for help sheets & empty states)

## 4. Cryptography & Pure-Kotlin Digests in Phase 0
- `HashEngine` combines `java.security.MessageDigest` (`MD5`, `SHA-1`, `SHA-224`, `SHA-256`, `SHA-384`, `SHA-512`, `SHA3-256`, `SHA3-512`), `javax.crypto.Mac` (`HMAC`), `SecretKeyFactory` (`PBKDF2WithHmacSHA256`), `java.util.zip.CRC32` / `Adler32`, and pure-Kotlin deterministic implementations of `FNV-1a (32/64-bit)`, `MurmurHash3 (x86 32-bit)`, and `DJB2` so all 14 algorithms run 100% offline with zero external native dependencies.
