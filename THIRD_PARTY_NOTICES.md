# Third-party notices

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
