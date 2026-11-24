# Third-party notices

Repository policy: third-party license texts live once in `third_party/licenses/`.
This file owns component attribution; App packages it and those license texts into
`assets/licenses/` using generated build output. Core/feature modules must not carry
independent copies of distribution license assets.

## AndroidSVG

- Project: https://github.com/BigBadaboom/androidsvg
- Artifact: `com.caverock:androidsvg-aar`
- Version: 1.4
- Copyright: Paul LeBeau and contributors
- License: Apache License 2.0
- Use: render sanitized static EPUB vector illustrations on the existing bounded image decode lane.
- License text: [Apache-2.0](third_party/licenses/Apache-2.0.txt)

## AndroidX WebKit

- Project: https://developer.android.com/jetpack/androidx/releases/webkit
- Artifact: `androidx.webkit:webkit`
- Version: 1.16.0
- Copyright: The Android Open Source Project
- License: Apache License 2.0
- Use: origin-scoped WebView messaging for the optional complex-EPUB presentation backend. Android System WebView itself is supplied by the operating system, not bundled here.
- License text: [Apache-2.0](third_party/licenses/Apache-2.0.txt)

## Glasmio

- Project: https://github.com/NEORUAA/Glasmio
- Artifacts: `io.github.neoruaa.glasmio:glasmio-glass`, `glasmio-core`, `glasmio-icons`
- Version: 0.1.2
- License: Apache License 2.0
- Use: liquid-glass material rendering and native edge treatment for app chrome, reader controls and floating surfaces.

The host app records and samples page content through Glasmio's backdrop backend. The bottom
navigation uses a clear track, a separate native tab-indicator lens, and sharp foreground labels,
adapted from `GlassBottomBar.kt` at v0.1.2. Glasmio does not own navigation, data or settings.

## AndroidLiquidGlass

- Project: https://github.com/Kyant0/AndroidLiquidGlass
- Copyright: Kyant and contributors
- License: Apache License 2.0
- Reference: `LiquidBottomTabs.kt`, layered backdrop/selection rendering. The app uses the
  existing Glasmio renderer rather than adding a second rendering dependency.
