# Nuvio Desktop Audit

Date: 2026-05-21
Scope: Compose Multiplatform desktop runtime, player overlay, gradients, image loading, network/cache behavior, release packaging, and broad code quality.

## Work Completed

- Player component rewrites were reverted after follow-up testing pointed at a lower-level FPS issue. `PlayerControls.kt`, `PlayerOverlays.kt`, `NextEpisodeCard.kt`, and `PlayerScreen.kt` are back to the branch version.
- The owned desktop player overlay no longer depends on `javax.swing.Timer` for 120 Hz repaint scheduling. It now uses a dedicated high-resolution daemon repaint pump and immediate overlay panel painting while keeping the existing SwingGraphics overlay renderer and player controls.
- The owned desktop player overlay window was split out of `PlayerDesktop.desktop.kt` into `DesktopPlayerOverlayWindow.desktop.kt`, leaving `PlayerDesktop.desktop.kt` focused more narrowly on platform player behavior, settings storage, keyboard hooks, and fullscreen helpers.
- Desktop image resampling cache keys now include the source request identity rather than only the decoded bitmap object identity, reducing repeat software resizes when Coil re-decodes the same URL.
- The desktop overlay repaint pump and desktop scaled image cache have been split out of large rendering files into focused desktop modules.
- Home next-up metadata enrichment is capped, lower concurrency, disabled when Continue Watching is hidden, and now tied to the visible Continue Watching row budget instead of the entire completed-series candidate set.
- Release-path watch-progress and image-resampling `println` calls were routed through existing logging paths.
- Playback settings now expose desktop runtime diagnostics for renderer, MPV surface, overlay renderer/repaint mode, image sampler, native path, and runtime log location.
- Continue Watching next-up metadata misses are now persisted with a roughly five-minute TTL. Home filters still-fresh miss keys before issuing metadata enrichment requests, and positive hits clear the matching miss key.
- A Windows desktop smoke checklist now lives in `Docs/DesktopSmokeTest.md`.
- The Who's Watching background now uses the platform gradient helper on desktop. The desktop implementation draws a Skia `Color4f` sRGB gradient and applies a very low-alpha dither pass to reduce visible banding.
- Windows release packaging now copies Skiko ANGLE DLLs and MPV/MediaMP native DLLs into the app image native folder. The launcher config no longer points at a stale or missing native path.
- The packaged release app was rebuilt and smoke-tested from `composeApp/build/compose/binaries/main-release/app/Nuvio/Nuvio.exe`.

## Verification

- `./gradlew.bat :composeApp:compileKotlinDesktop --console=plain` passed after the latest audit implementation changes, including the desktop overlay repaint pump, overlay-window split, visible-budgeted home metadata enrichment, and TTL-backed next-up miss caching.
- `./gradlew.bat :composeApp:createDistributable :composeApp:createReleaseDistributable :composeApp:packageWindowsNativeRuntime --console=plain` passed.
- Release exe smoke test stayed alive for 15 seconds and was stopped manually. It did not exit or crash in that run.
- Fresh stdout/stderr files next to the exe only contain Supabase startup output and SLF4J "no provider" warnings.
- `%LOCALAPPDATA%/Nuvio/cache/logs/desktop-runtime.log` shows the stale pre-fix crashes were missing `skiko-windows-x64.dll`. Current packaged runs show `skiko.renderApi=ANGLE`, a valid `skiko.library.path`, `nativeBootstrap dllCount=51`, and successful `mediampv.dll` load.
- Screenshot validation: `screenshots/nuvio-gradient-check.png` shows the Who's Watching screen running under D3D11/ANGLE at 120 FPS. The previous obvious two-color split is gone.

## Latest Crash Report

The latest "crashes without an error dialog" report is not reproduced locally with the current packaged release exe. The only logs found next to the exe are the stdout/stderr launch captures. No fresh `hs_err`, dump, or application crash log was present in the exe directory. The runtime log still contains old `LibraryLoadException` entries from before the native DLL packaging fix; no newer `CRASH` entry was recorded after the current packaged launch checks.

If this reappears, the next useful data is the exact file name and timestamp of the new exe-adjacent log, because the current known logs do not contain a fatal error.

## High Priority Findings

1. `PlayerScreen.kt` and `PlayerDesktop.desktop.kt` are still too large and too stateful. `PlayerScreen.kt` mixes navigation, metadata fetches, skip/next episode logic, volume/session persistence, keyboard/mouse input, panels, overlay visibility, and playback lifecycle. `PlayerDesktop.desktop.kt` has had the owned overlay window extracted, but it still mixes MPV bridge behavior, settings storage, keyboard hooks, fullscreen coordination, diagnostics, and rendering policy. Continue splitting these into focused controllers before the next major player iteration.

2. The player desktop render path is stable but delicate. The current default is ANGLE for Compose, native-window MPV for video, and an owned overlay window for controls. That is the right direction for D3D11 stability, but every animated or pointer-driven element in the overlay must stay cheap because it can still compete with video presentation. Skia overlay rendering should remain behind `NUVIO_PLAYER_OVERLAY_RENDERER=skia` until it is proven stable across fullscreen, back navigation, and source changes.

3. Image quality fixes are spread across multiple paths. The central `AsyncImage` wrapper, desktop `AsyncImagePainterWorkaround`, `DesktopImageResampling`, `NuvioWordmarkImage`, collection remote image loading, GIF handling, and resource painters all render images differently. This explains why logo/poster fixes improved most surfaces but not every image at once. Consolidate image sizing/resampling policy into one desktop image module and make all remote and local image paths pass through it where possible.

4. The desktop image workaround can still do expensive software resize work near draw time on cache miss. Quality is better, but this should move closer to decode/request time or a background transform stage. The current cache key uses bitmap identity, so re-decoding the same URL can miss the resized cache. A model/url-based key would avoid repeated high-quality resampling for the same displayed size.

5. Request pressure is improved but not fully solved. Source prefetch is limited, home next-up metadata enrichment is capped to the visible Continue Watching budget, and next-up metadata misses now use a short persisted TTL. Remaining work is runtime verification with `NUVIO_NETWORK_TRACE` enabled across profile/app transitions to confirm the miss cache suppresses repeat calls under real addon behavior.

## Medium Priority Findings

1. `App.kt`, `PlaybackSettingsPage.kt`, `CollectionEditorScreen.kt`, `MetaDetailsScreen.kt`, `TmdbMetadataService.kt`, `StreamsScreen.kt`, and `DetailSeriesContent.kt` are each large enough to hide regressions. Several combine rendering, state transforms, persistence calls, and navigation decisions. Prioritize splitting screen state/model preparation from composable layout.

2. Release/runtime behavior depends on custom environment flags and packaging tasks. The defaults are now reasonable, but the app should have one documented runtime config surface for `NUVIO_SKIKO_RENDER_API`, `NUVIO_MPV_SURFACE`, `NUVIO_PLAYER_OVERLAY_RENDERER`, `NUVIO_PLAYER_OVERLAY_REPAINT_HZ`, `NUVIO_GRADIENT_DITHER`, `NUVIO_IMAGE_SAMPLING`, `NUVIO_IMAGE_DEBUG`, and `NUVIO_NETWORK_TRACE`.

3. Release-path `println` calls in the common watch-progress and desktop image-resampling paths have been removed. Remaining `println` usage is isolated to iOS player runtime tracing.

4. The Gradle packaging task is essential and should remain wired into every Windows app image path. A skipped `packageWindowsNativeRuntime` task directly recreates the old missing-Skiko-DLL crash.

5. ProGuard emits duplicate class/resource notes during release distributable creation. The build succeeds, but dependency/resource duplication should be cleaned up to reduce release ambiguity and package size.

## Render Pipeline Notes

- Windows default should remain ANGLE/D3D11 for Compose. The OpenGL path exists for legacy compatibility but has worse performance on this machine.
- For non-OpenGL render APIs, MPV should continue using `native-window`. OpenGL interop should be reserved for explicit OpenGL mode.
- `compose.interop.blending=false` is correct for the native-window path; blending attempts caused worse player failures earlier.
- Fullscreen diagnostics show stable 120 FPS in the menu after startup warmup. The first second can still show long frames due to startup/resource initialization.
- White flashes around player startup are likely native-surface/window lifecycle gaps. The startup loading overlay now does less work, but eliminating flashes completely likely needs earlier surface preparation or retaining/reusing the player host window between media transitions.
- The gradient helper should be used instead of direct desktop `Brush` gradients for large static backgrounds where banding is visible. Skeleton shimmers can keep direct animated brushes, but avoid large fullscreen animated gradients.

## Image Loading Inventory

- `core/ui/AsyncImage.kt`: central Coil wrapper for most remote images. It measures string models before creating sized requests.
- `core/ui/AsyncImagePainterWorkaround.desktop.kt`: desktop painter replacement/resampling layer for successful Coil bitmap painters.
- `core/ui/DesktopImageResampling.desktop.kt`: desktop high-quality software resampling implementation and env-controlled sampling modes.
- `core/ui/NuvioWordmarkImage.*.kt`: platform-specific startup/logo rendering. Desktop uses a special path because resource scaling was visibly pixelated.
- `features/home/components/CollectionCardRemoteImage.*.kt`: separate remote/static/GIF handling for collection cards.
- `features/home/components/HomeHeroSection.kt`, detail hero, streams, continue watching, cast, profile, catalog, search, add-ons, and settings screens all use the central `AsyncImage` wrapper in many places.
- `painterResource` remains in icons, ratings, integration logos, navigation assets, and some platform branding. These local resources may still bypass the remote image workaround.

## Network And Cache Notes

- `ApiKacheClient` gives generic GET responses a short TTL and source responses a roughly five minute TTL. Empty source responses and rate-limit responses are not persisted, which is correct.
- Continue-watching source prefetch should stay limited to the first two or three visible items. It must not expand back to every carousel or every home catalog.
- Metadata enrichment no longer runs for the entire completed-series candidate set on every home load. It is skipped when Continue Watching is hidden, prioritizes candidates that can replace visible in-progress series, and fills only the remaining visible row budget.
- Next-up metadata enrichment misses are cached for roughly five minutes by series id, seed season/episode, and the unaired preference. This prevents immediate refetch loops for unresolved next episodes while allowing a short retry window.
- Keep `NUVIO_NETWORK_TRACE` off by default. It is useful for diagnosing rate limits, but it should not write noisy logs unless explicitly enabled.

## Compose Performance Notes

- Keep frequently changing values as low in the composition tree as possible. The player seek time fix follows this and should be the model for other frame-driven UI.
- Avoid `LaunchedEffect` keys that include values changing many times per second unless the effect truly needs to restart. Player auto-hide, volume, and seek effects should be especially guarded.
- Prefer derived state for expensive UI transforms and precompute screen models outside dense item composables.
- Use `LazyRow`/`LazyColumn`/grids for large lists and cap home carousel item counts to what can actually be seen.
- Avoid always-on infinite transitions in fullscreen/player surfaces. Skeleton shimmer is acceptable for loading states, but it should disappear as soon as content is available.
- Stable keys and stable data models should be checked in every lazy list, especially home, discover, search, cast, seasons, episodes, and sources.

## Recommended Next Work

1. Split `PlayerScreen.kt` into playback session state, metadata/next episode coordinator, input controller, overlay chrome, panels, and route adapters.
2. Continue splitting desktop player native code into surface host, MPV options, fullscreen coordination, and diagnostics modules. The owned overlay host is now separate.
3. Build a single desktop image service that owns decode size, high-quality resize, cache keys, SVG/vector handling, and local resource scaling.
4. Continue moving expensive image resampling off the draw path. The cache now uses source request identity plus target size, but cache misses still perform resize work during drawing.
5. Run a network-trace pass over home/profile/app transitions and source playback to verify the source cache, Continue Watching prefetch cap, and next-up miss TTL behave correctly against real addons.
6. Keep release-path logging behind structured/debug logging; the known common/desktop stdout prints have been removed.
7. Keep the desktop smoke checklist updated as release/runtime behavior changes.
8. Add a diagnostics screen or settings subsection listing the active renderer, MPV surface mode, overlay renderer, image sampling mode, and native DLL path.

## Session

Codex session id: 019e3bdb-5b69-7ae2-bc79-45dccf015c6a
