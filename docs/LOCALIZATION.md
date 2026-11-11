# Internationalization / 国际化

## Supported languages

| Language | Android resources |
| --- | --- |
| English (fallback) | `values/` |
| 简体中文 | `values-b+zh+Hans/` |
| 繁體中文 | `values-b+zh+Hant/` |
| 日本語 | `values-ja/` |

Use script qualifiers for Chinese, not a Taiwan-only translation: Android can match
Traditional Chinese in Taiwan, Hong Kong, and other regions. Unsupported languages
fall back to English. The launch label and language autonyms are intentionally not translated.

Android 13+ uses `LocaleManager.applicationLocales` and the explicit
`app/src/main/res/xml/locales_config.xml` list. Select a language in
Settings → Appearance → App language, or Android system settings. “Follow system”
clears the per-app override. Android 13+ is the minimum supported version, so per-app selection is always available.

The existing ComponentActivity, Navigation 3 host, predictive-back transitions and immersive
window ownership are unchanged. AppCompatActivity is not introduced just for legacy per-app
language overrides. The host handles `locale|layoutDirection` configuration changes in place:
Compose observes updated resources without replacing the window, navigation state or splash
screen. Process-death state restoration remains necessary. The language override is device-local and is not written into Google Drive reading
preferences or book backups; Android owns its persistence.

## Resource ownership

- Keep feature-specific text in its feature module; shared controls belong to core-designsystem.
- Resolve Compose text with `stringResource` / `pluralStringResource`. For callbacks,
  capture text in composition, or use configuration-aware `LocalResources.current`.
- Resolve non-UI operation messages with the injected application context at the time of the
  operation, not a process-start dictionary. Do not use `Locale.setDefault` to simulate translation.
- Cache labels only with their localized values or locale configuration as keys.
- Use positional arguments (`%1$s`, `%2$d`, `%1$.2f`) so translators can reorder phrases.
  Do not concatenate translated sentence fragments. Escape a literal formatted percent as `%%`.
- Use `plurals` for counts with grammatical agreement. English requires `one` and `other`;
  Chinese and Japanese use `other`. A compact numeric unit such as `min` does not inflect.
- Preserve leading/trailing spaces explicitly with `\u0020`; Android otherwise trims them.
- Pass the current app locale to display date/number formatters. Machine-readable dates,
  hashes, filenames used as identifiers, and protocol fields remain stable.

Book text, author names, user categories, annotations and notes are user data, not UI translations.
Existing generated chapter names and metadata defaults also remain stable: translating them during
parsing would change persisted data and could affect title-based chapter recognition and old anchors.
The “All” category filter is translated only at display time; its internal token is unchanged.
Raw server errors, parser diagnostics and historical log messages are retained verbatim.
Diagnostic category names, event descriptions, field labels and outcomes are localized at display time.

## Contributing translations

1. Add a stable, descriptive resource key in the owning module.
2. Add all three localized variants, including the same format arguments.
3. Do not translate `translatable="false"` entries or add duplicate keys.
4. Check short labels on phones, long labels on tablets, and both Material and MIUIX.
5. Run:

```bash
./gradlew verifyTranslations
./gradlew verifyHostTests --continue
```

`verifyTranslations` is also a dependency of every Android `preBuild`, so both Debug and
Release stop on missing/unknown keys, type mismatches, empty values, broken format-argument
parity and missing plural forms. It needs no device or translation service.
The UI resource check additionally rejects Chinese string literals in production Compose files.
These checks are structural; they cannot judge translation quality, clipping on every screen,
or manufacturer-specific Activity recreation.

Robolectric covers Chinese regional/script fallback, unsupported-locale fallback, live shared-control
labels in both themes, English singular/plural results, search progress format arguments, and
locale-independent diagnostic failure classification. Existing reading, search, annotation,
bookmark and persistence regression tests continue to run.

The initial English, Traditional Chinese and Japanese translations need native-speaker review;
passing automated checks is not a claim of linguistic or full-device UI certification.

## Design references

- [Android per-app languages](https://developer.android.com/guide/topics/resources/app-languages):
  use system APIs for per-app locale ownership; older Compose hosts require AppCompatActivity for
  AppCompat locale overrides.
- [Mihon localization resources](https://github.com/mihonapp/mihon/tree/main/i18n/src/commonMain/moko-resources):
  centralize translatable text and use plural resources. This Android-only project keeps Android XML
  per existing module rather than introducing a cross-platform resource framework.
- [Mihon translation contribution workflow](https://github.com/mihonapp/mihon/blob/main/CONTRIBUTING.md#translations):
  translators work through Weblate. The XML here is suitable for a later Weblate integration;
  no external service, account, or publishing integration has been configured.

## Legacy-code audit

Audit unused display-name helpers, obsolete opt-ins, duplicate UI labels and unreferenced
string/plural keys alongside their call sites; resource-key parity does not prove that a key is
still used. Remove a key from all four locales together when its feature is removed.
Keep database migrations, legacy backup readers, EPUB directory upgrade/anchor compatibility,
and reading restoration/progress writers: those protect existing libraries and are not dead code.
