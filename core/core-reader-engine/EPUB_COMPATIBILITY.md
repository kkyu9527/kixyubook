# EPUB compatibility policy

## Navigation is not storage

The publisher's EPUB 3 TOC (or EPUB 2 NCX fallback) defines navigation labels and
section boundaries. The spine defines the physical reading order. A section may
span multiple resources, and multiple TOC anchors may address one resource.

- Preserve physical source indices, chapter IDs/keys and all readable resources.
- Carry section and volume labels onto continuation resources; do not manufacture
  filename-based TOC entries for those resources.
- Directory selection and bookmarks cover all resources owned by a TOC section.
- Chapter buttons use navigation boundaries; page turns visit every readable resource.
- A heading-only leaf followed by its own body is a navigation alias, not an empty
  extra page. Filter only these aliases from the reading list (including consecutive
  title resources); map old bookmarks/progress to the body without changing source
  indices. Keep volume plates, illustrations, real text, named anchors and fixed pages.
- If usable navigation is absent, inspect document headings, then fall back to filenames.
- Metadata-only introduction pages may exist outside the spine. Do not drop them
  while validating navigation. Copyright/prologue pages are not automatic synopses.

## Parsing and rendering

Accept normal DTD declarations without resolving external entities, DTDs, network
URLs or local files. Use bounded DOM parsing before the low-memory SAX fallback.
A normal EPUB 2 DOCTYPE is not a reason to discard CSS and links.

Ordinary novels remain native and reflowable. The shared compatibility
layer maps supported publisher CSS into semantic emphasis, colors, visibility,
whitespace and images. Reader typography and theme colors remain authoritative.
Unsupported conditional rules and pseudo-selectors must not become unconditional
styles. Do not add book-name, author-name or publisher-filename exceptions.

- Preserve raster illustration aspect ratio; use cropping only when requested.
- Keep single-image pages and CSS background-only plates as full-page images.
- Keep substantial inline illustrations after their containing paragraph without
  splitting existing paragraph anchors. Small inline ornaments remain omitted.
- Convert semantic image-only footnote links to clickable superscript markers.
- Preserve opaque photos, scans, covers and colored artwork. Alpha-backed, nearly
  uniform neutral ink may be softly recolored when it clashes with the actual page,
  regardless of image dimensions or full-page status. Its transparent pixels remain
  transparent; dark mode must not introduce a large white plate.
- Ambiguous transparent colored/textured art keeps its original pixels; if needed,
  use only a minimum-contrast neutral backing, not near-white by default. Restore
  publisher styles when returning to a readable palette. Native and browser paths
  use `ReaderImageThemePolicy`; do not choose independent theme thresholds in UI.
- Render static external and inline vector SVG with AndroidSVG on the same image
  lane. Sanitize XML, scripts, external references and local-reference cycles first.
- Bound decoded bitmap pixels and dimensions, not only compressed entry bytes.
- Reuse the image cache/decode lane for visible pages and prefetch; do not decode
  or inspect image pixels on the main thread.
- Prefetch full-page images using the same physical viewport and pixel rounding as
  rendering, not text-safe pagination dimensions. Do not paint a temporary tinted
  full-screen placeholder. Sample image pixels in one bounded bulk read on the decode
  lane; a theme switch changes the drawing filter, not the decoded bitmap.
- Keep a composed page's image state alive when drag/settle changes selection
  availability. A shared LRU is disposable; it is not the owner of visible content.
  Move the existing composition between selection scopes rather than re-creating
  it. Scope asynchronous image state to its resource/dimensions, not just its effect.
  Publisher alt text is accessible image/error content, never a loading animation.
  Test real ZIP/bitmap pixels during live drags after memory-cache eviction.

Native navigation distinguishes parsed content, finalized page batches and a fully
paginated chapter. The next chapter's finalized prefix may enter the stable Pager
before the complete layout; its total count stays unknown until completion. Never
publish a partial previous chapter as its real final page. Append batches without
changing existing leaf keys or persisted source/character coordinates.

Required adjacent read-ahead survives drags and role changes. Distant jumps cancel
obsolete work, while completed and already-running required loads are reused.
Measure second neighbours only after the current/adjacent layouts are complete,
using the existing bounded pagination LRU; cancel that speculative work when a
gesture starts. Measurement yields at finalized page boundaries. Button bursts
retain their desired chapter destination without queuing obsolete animations;
explicit search/bookmark/directory navigation supersedes that pending destination.

Navigation initialization first filters split-resource candidates structurally.
One-XHTML-per-chapter books must not open every body just to decide that there are
no redundant title pages. `EpubPresentationCache` stores navigation and browser
routing together under a source/content-hash/revision signature in `noBackupFilesDir`;
its bounded, atomic, regenerable cache survives reader-memory release. Cache failure
must never block a readable book. Bump its semantic version when alias/routing rules
change. Validate work counts and parser-free reopening, not only machine-specific
millisecond thresholds.

The native path deliberately does not implement a browser CSS engine. Mixed
SVG/external-raster compositions and SVG filter effects remain limited there.
Raster-only SVG wrappers retain their ordinary image path.

### Complex EPUB presentation

Declared fixed-layout books, vertical writing, MathML and detected root flex/grid
layout use a separate, archive-scoped WebView presentation backend. Browser CSS,
embedded fonts, MathML and SVG rendering come from the installed Android System
WebView, not from ad-hoc replacements in the native paragraph paginator. A bounded
front-of-spine probe also checks inline CSS/MathML; it is not an exhaustive scan of
every resource in a novel. Mixed per-resource layout declarations may need further
fixtures; do not claim every EPUB or every browser feature is supported.

- Keep the shared ViewModel, original chapter/paragraph/character coordinates,
  search results, bookmarks, notes, annotations, corrections and selection host.
- Native-source ranges are validated before persistence; cross-paragraph selection
  is copy-only because the persisted annotation model owns one paragraph.
- Preserve the selected character through theme/font reflow and explicit navigation;
  inactive prefetched leaves cannot publish progress. Chapter buttons remain section
  navigation, not one-page turns. RTL/vertical pages use logical horizontal offsets.
- Retain at most `ReaderCacheBudget.MAX_BROWSER_LEAVES` browser leaves. On memory
  pressure release neighbours, never the active book's data; destroy views on exit
  and pause them with the lifecycle. Browser-process bytes are not a JVM LRU budget.
- Publisher scripts, forms, frames, remote resources, file/content access, browser
  storage and arbitrary intents are not allowed. Only the trusted bundled interaction
  script runs. The bridge is origin/main-frame scoped, token checked and range bounded.
- Every archive response is bounded and allowlisted. Preparation/provider/render
  failures expose retry rather than leaving an indefinitely invisible page.
- Fixed-layout art preserves authored geometry and page colors instead of forcibly
  applying novel typography. Native reflow remains authoritative for ordinary novels.

`src/test/browser` runs the actual bundled script in headless Chromium. This verifies
DOM layout, coordinates and interaction logic; it is not a real-device WebView,
GPU performance or vendor lifecycle guarantee.

## Upgrades and regression fixtures

### Ownership and file placement

| Responsibility | Owner |
| --- | --- |
| EPUB navigation/presentation DTOs | `core-common/model/EpubModels.kt` |
| Package, XHTML, CSS, SVG and archive-resource security | `core-reader-engine`; browser-specific parsing is in `epub/browser/` |
| Persisted text-coordinate compatibility and cache revisions | `core-database` |
| WebView lifecycle, message validation and shared reader-menu integration | `feature-reader/reader/browser/` |
| Trusted browser interaction script | `feature-reader/src/main/assets/reader/browser/` |
| Third-party attribution and license originals | repository `THIRD_PARTY_NOTICES.md` and `third_party/licenses/` |
| Generated APK license assets | App build output; packaging task lives in `buildSrc` |
| Regression tests | owning module's `src/test`; browser tests use `feature-reader/src/test/browser/` |

Engine code must not depend on a feature or open application UI. Browser code must
not write Room records, own a second set of bookmarks/annotations, fetch resources
from the network or execute publisher scripts. Preserve the native reader for
ordinary novels; route declared fixed-layout/unsupported layout CSS books to the
browser renderer as one book-level presentation decision, not per-frame switching.

Derived directory upgrades change labels in place, not stored reading data. Parser
cache versions invalidate old visual results. For previously indexed chapters,
new visual spans/images are projected onto the persisted text coordinate system;
unmatched content retains its readable old representation rather than guessing
annotation offsets. Replacing original text belongs to explicit, protected repair.

Visual theme changes are not navigation commands. Same-session content recreation
uses the last presented text anchor, gated by navigation version. A natural chapter
handoff's initial physical page is not replayed after subsequent page turns; retain
the latest settled leaf for palette-only updates and reflow by text only on actual
layout changes.

For each new compatibility issue, add a minimal synthetic ZIP fixture reproducing
the structure, not a copy of a copyrighted book. Cover both navigation and body
output, stable source/paragraph/character anchors, links, dark-theme semantics and
fallback behavior as applicable. User-supplied real books are local audit inputs
only; never commit them as fixtures.

Design references (principles, not copied implementations):

- [Foliate-js EPUB parser](https://github.com/johnfactotum/foliate-js/blob/master/epub.js): separate spine sections, TOC, landmarks and anchor resolution.
- [Readium CSS themes](https://readium.org/css/docs/CSS12-user_prefs.html): separate theme colors from image blending/darkening/inversion.
- [KOReader CSS tweaks](https://github.com/koreader/koreader/blob/master/frontend/ui/data/css_tweaks.lua): layered compatibility rules and heading-based fallback TOCs.
- [Readest customization](https://readest.com/docs/customization): separate publisher layout, reader overrides and image-specific theme behavior.
