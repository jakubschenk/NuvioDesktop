# WebView CSS Image Sampling Research

This document is a documentation-only research note for making Nuvio desktop images match WebView/CSS image rendering as closely as possible. It separates what the web specs require from what current Chromium/WebView appears to do internally, then maps that to the current Compose/Coil/Skia pipeline.

## Step 1 - Surface Pass

### Mental Model

The browser pipeline is roughly:

1. Choose an image resource.
2. Fetch and decode it.
3. Compute the CSS layout box and the source-to-destination rectangle.
4. Paint an image draw operation with a sampling intent.
5. Decode/downscale/cache the image for raster.
6. Draw through Skia into tiles or surfaces.
7. Composite the result with clips, opacity, transforms, masks, gradients, filters, and other effects.

The first conclusion is that there is no single "CSS downscaling algorithm" mandated by the specs. Specs define resource selection and geometry. Sampling/downsampling is mostly user-agent behavior. For matching WebView, Chromium/Blink/cc/Skia behavior matters more than generic CSS text.

### Initial Risks For Nuvio

- We can request a source image that is smaller than the final physical-pixel draw size.
- We can decode at one size, prescale to another, and then draw through Compose at a third size.
- We currently use desktop `FilterQuality.High` and a Lanczos3 prescale path in some cases; current Chromium normal CSS image rendering is usually closer to medium quality, linear filtering, and mip-level selection.
- Logos and SVGs are a separate path from bitmap posters/backgrounds.
- Rounded clips, opacity, blur, gradients, and `graphicsLayer` transforms can force intermediate surfaces and extra sampling.

## Step 2 - Deep Research

### 1. WebView Is Chromium/Blink/cc/Skia

Android WebView should be treated as a Chromium content embedder for this problem. The WebView-specific layer is relatively thin; rendering largely flows through Chromium `content`, Blink, cc, and Skia.

Primary references:

- Android WebView architecture: https://chromium.googlesource.com/chromium/src/+/refs/heads/main/android_webview/docs/architecture.md
- Chromium "how cc works": https://chromium.googlesource.com/chromium/src/+/3db3c343c824dd53441b09cfd85e434d8c398d58/docs/how_cc_works.md
- Chromium "life of a frame": https://chromium.googlesource.com/chromium/src/+/refs/heads/main/docs/life_of_a_frame.md
- cc paint README: https://chromium.googlesource.com/chromium/src/+/HEAD/cc/paint/README.md

### 2. Resource Selection

HTML defines how image candidates are parsed and normalized, especially for `<img srcset>` and `sizes`.

- `x` descriptors describe pixel density.
- `w` descriptors are normalized into effective density using the source size from `sizes`.
- The final source selection is implementation-defined enough that exact candidate choice is not guaranteed across engines or even all circumstances in one engine.
- Browser parity therefore starts before sampling: if WebView picks a larger candidate than we fetch, no resampler in Compose will make the result match.

Primary reference:

- HTML image source selection: https://html.spec.whatwg.org/multipage/images.html

Nuvio implication:

- We usually receive or construct one URL directly, for example TMDB or Metahub sizes. To match WebView-like rendering, the chosen URL should be at least as large as the final destination in physical pixels, and ideally close to what a browser would select for the same CSS box and DPR.

### 3. CSS Pixels, DPR, And Layout Geometry

CSS layout is expressed in CSS px. A CSS px is a reference unit; the mapping to device pixels depends on device scale, page zoom, and browser policy. `devicePixelRatio` is effectively CSS pixel size at the current zoom divided by device pixel size.

Primary references:

- CSS Values absolute lengths and reference pixel: https://www.w3.org/TR/css-values-4/#absolute-lengths
- CSS resolution units: https://www.w3.org/TR/css-values-4/#resolution
- CSSOM View `devicePixelRatio`: https://drafts.csswg.org/cssom-view/#dom-window-devicepixelratio

Nuvio implication:

- Compose `Dp` is not automatically equal to browser CSS px unless we define that mapping. For image parity, the important target is the final physical-pixel destination rectangle after density, window scale, transforms, and clipping.

### 4. Object Fitting And Background Sizing

CSS defines geometry, not a fixed resampling kernel.

For replaced elements:

- `object-fit: fill` stretches to the content box.
- `object-fit: contain` fits inside while preserving aspect ratio.
- `object-fit: cover` fills the box while preserving aspect ratio and cropping overflow.
- `object-position` aligns the rendered object inside the box.

For CSS backgrounds:

- `background-size: contain` and `cover` define sizing relative to the background positioning area.
- `background-position` then aligns the result.

Primary references:

- CSS Images object sizing, `object-fit`, `object-position`: https://www.w3.org/TR/css-images-3/#sizing
- CSS Backgrounds `background-size`: https://www.w3.org/TR/css-backgrounds-3/#background-size

Nuvio mapping:

- CSS `object-fit: cover` -> Compose `ContentScale.Crop`.
- CSS `object-fit: contain` -> Compose `ContentScale.Fit`.
- CSS `object-position: center top` -> Compose `Alignment.TopCenter`.
- CSS background cover positioning needs explicit alignment and source crop math, not just "draw image in a box".

### 5. CSS `image-rendering`

The `image-rendering` property is the closest CSS-level sampling knob, but it is not a precise algorithm contract.

Spec-level behavior:

- `auto` leaves the smoothing choice to the user agent.
- `smooth` requests smoothing but does not mandate one exact filter.
- `crisp-edges` requests contrast-preserving/non-blurry scaling, but the exact algorithm can vary.
- `pixelated` is more specific for integer multiples, but non-integer final scaling still involves smoothing behavior.

Primary reference:

- CSS Images `image-rendering`: https://www.w3.org/TR/css-images-3/#the-image-rendering

Current Chromium/Blink behavior:

- `pixelated` and `crisp-edges` map to no interpolation.
- `-webkit-optimize-contrast` maps to low interpolation.
- Other values generally fall through to default interpolation.
- Default interpolation is usually medium, unless the runtime feature `UseLowQualityInterpolation` is enabled.

Primary Chromium references:

- CSS property definitions: https://chromium.googlesource.com/chromium/src/+/main/third_party/blink/renderer/core/css/css_properties.json5
- `ComputedStyle::GetInterpolationQuality`: https://chromium.googlesource.com/chromium/src/+/refs/heads/main/third_party/blink/renderer/core/style/computed_style.cc
- Interpolation quality defaults/types: https://chromium.googlesource.com/chromium/src/+/refs/heads/main/third_party/blink/renderer/platform/graphics/graphics_context_types.cc

### 6. Chromium Sampling Mapping

Blink converts interpolation quality into Skia sampling through `GraphicsContext::ComputeSamplingOptions()` and cc `PaintFlags::FilterQualityToSkSamplingOptions()`.

Current mapping from the Chromium sources:

| Blink/cc quality | Skia sampling | Meaning |
| --- | --- | --- |
| none | nearest, no mipmaps | nearest-neighbor point sampling |
| low | linear, no mipmaps | bilinear interpolation |
| medium | linear + nearest mipmap | bilinear sampling from the selected mip level |
| high | cubic or linear+mipmap depending on scale | not the normal CSS image path |

Primary references:

- Blink `GraphicsContext`: https://chromium.googlesource.com/chromium/src/+/refs/heads/main/third_party/blink/renderer/platform/graphics/graphics_context.cc
- cc `paint_flags.cc`: https://chromium.googlesource.com/chromium/src/+/HEAD/cc/paint/paint_flags.cc
- Skia sampling definitions: https://skia.googlesource.com/skia/+/786d3a4bb792/include/core/SkSamplingOptions.h

Skia meanings:

- nearest: one nearest sample.
- linear: 2x2 bilinear interpolation.
- mipmap nearest: choose one mip level.
- mipmap linear: blend between mip levels.
- cubic: cubic reconstruction, for example Catmull-Rom/Mitchell style options.

Key parity point:

- If WebView is using normal CSS `auto`, the closest implementation target is likely medium quality: linear sampling with nearest mip-level selection, plus Chromium's decode cache downscale behavior. It is probably not a direct Lanczos3 resize to final size.

### 7. Decode Cache And Downscaling

Chromium does not simply decode every full image and always draw it once with one filter.

For normal bitmap images:

- Blink `BitmapImage::Draw()` passes sampling options into `drawImageRect`.
- cc paint records store the image draw operation and sampling options.
- At raster time, cc obtains decoded content through an image provider/decode cache.
- Software raster chooses cache keys from the draw scale. If the image is reduced and the matrix is decomposable, it can select a power-of-two mip level larger than the target, decode/scale to that mip size, and then final raster usually draws with low/bilinear sampling.
- For nearest/pixelated paths, the decode cache avoids that downscale path so nearest-neighbor semantics are preserved.
- GPU raster has a similar mip-oriented strategy. It caps desired quality to medium, computes upload scale/mip level, may decode or scale into the target pixmap, and can generate mips when quality is at least medium.

Primary references:

- Blink `BitmapImage::Draw`: https://chromium.googlesource.com/chromium/src/+/refs/heads/main/third_party/blink/renderer/platform/graphics/bitmap_image.cc
- cc paint op raster path: https://chromium.googlesource.com/chromium/src/+/HEAD/cc/paint/paint_op.cc
- software image decode cache utils: https://chromium.googlesource.com/chromium/src/+/refs/heads/main/cc/tiles/software_image_decode_cache_utils.cc
- software image decode cache: https://chromium.googlesource.com/chromium/src/+/refs/heads/main/cc/tiles/software_image_decode_cache.cc
- mipmap utility: https://chromium.googlesource.com/chromium/src/+/master/cc/tiles/mipmap_util.cc
- GPU image decode cache: https://chromium.googlesource.com/chromium/src/+/refs/heads/main/cc/tiles/gpu_image_decode_cache.cc

Practical interpretation:

- WebView-style downscaling is likely "choose/decode a reasonable mip-sized representation, then bilinear sample it", not "high-quality Lanczos all the way to destination".
- This can look sharper than bad upscaling and smoother than a single poor linear downscale, while still avoiding the halo/ringing traits of Lanczos.

### 8. Effects And Extra Passes

CSS effects can alter the pipeline:

- `border-radius`, overflow clipping, masks, and clip paths can clip before or after image raster depending on layerization.
- `opacity`, transforms, filters, and backdrop effects can promote content to composited layers or offscreen surfaces.
- `filter: blur(...)` and CSS gradients are separate draws/effects, not part of the image resampling kernel.
- Subpixel transforms and fractional destination rectangles can change sample positions.

Nuvio implication:

- To compare pixels, the same source image, destination rectangle, crop, alignment, rounded clip, alpha, gradient overlay, blur, transform, and physical-pixel scale must be matched. Otherwise "sampling" differences may actually be geometry or compositing differences.

## Current Nuvio Image Pipeline

### Global Loader

- `composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt` configures the singleton Coil loader with crossfade, memory cache, disk cache, and platform configuration.
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/DesktopUi.desktop.kt` adds SVG decoding on desktop.

### App AsyncImage Wrapper

Main file:

- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/AsyncImage.kt`

Behavior:

- The wrapper delegates to Coil `AsyncImage`.
- For `String` models, it waits until the composable has a measured draw size.
- It upgrades known TMDB/Metahub URLs first.
- It builds an `ImageRequest` with:
  - `Size(requestWidthPx, requestHeightPx)`
  - `Precision.EXACT`
  - Coil `Scale.FILL` for `ContentScale.Crop`, `FillBounds`, `FillHeight`, `FillWidth`
  - Coil `Scale.FIT` for other scales
  - desktop prescale transformation
- It passes Compose `filterQuality`, which defaults to platform `nuvioImageFilterQuality`.

Desktop defaults:

- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/AsyncImageFilterQuality.desktop.kt`
- `FilterQuality.High`
- TMDB preferred size: `original`
- Metahub preferred size: `large`
- Metahub episode preferred size: `original`
- decode size multiplier: `2f`

Mobile defaults:

- Android/iOS use `DefaultFilterQuality`.
- Preferred source sizes are unset.
- decode multiplier is `1f`.

### Sized Image Requests

File:

- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/SizedImageRequest.kt`

Behavior:

- Converts target `Dp` to physical px.
- Applies platform quality decode sizing.
- Builds exact Coil requests with memory cache keys that include dimensions.

Desktop quality dimension:

- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/NuvioImageDecodeQuality.desktop.kt`
- Desktop sized requests use 2x dimensions, rounded to 64 px buckets, capped at 1600 px.

### Desktop Prescale And Painter Workaround

Files:

- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/DesktopImagePrescale.desktop.kt`
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/AsyncImagePainterWorkaround.desktop.kt`
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/DesktopImageResampling.desktop.kt`

Behavior:

- Desktop adds a Coil transformation:
  - `Scale.FILL` -> crop-to-fill bitmap.
  - `Scale.FIT` -> fit-to-box bitmap.
- The success painter is replaced with `DesktopScaledBitmapPainter`, which caches destination-sized `ImageBitmap`s.
- Current resize path:
  - Prefers JVM Lanczos3 for target sizes under limits.
  - Falls back to Skia `scalePixels`.
  - Crop fallback uses cubic sampling.
  - Downsample fallback uses linear + linear mipmap.

This is not obviously WebView-equivalent. Current Chromium default CSS image rendering is closer to linear + nearest mipmap, often with power-of-two decode-cache downscaling.

### Major Screen Paths

- Home hero background:
  - `features/home/components/HomeHeroSection.kt`
  - `AsyncImage`, `Alignment.TopCenter`, `ContentScale.Crop`
  - URL uses `(banner ?: poster).withTmdbImageSize("original")`.
- Home hero logo:
  - `ContentScale.Fit`
  - desktop painter workaround disabled.
- Posters:
  - `features/home/components/HomePosterCard.kt` delegates to `core/ui/NuvioShelfComponents.kt`.
  - Fixed target dimensions in Dp.
  - Exact Coil request, `Scale.FILL`, desktop prescale, then `ContentScale.Crop`.
  - Bottom-left logo uses `Fit` and disables desktop workaround.
- Continue watching:
  - `features/home/components/HomeContinueWatchingSection.kt`
  - local exact sized requests, `Scale.FILL`, desktop prescale, draw with `ContentScale.Crop`.
- Detail hero:
  - `features/details/components/DetailHero.kt`
  - background/poster, `Alignment.TopCenter`, `ContentScale.Crop`.
  - logo uses `Fit` and disables desktop workaround.
- Detail backgrounds:
  - `features/details/MetaDetailsScreen.kt`
  - `ContentScale.Crop`, explicit `NuvioImageFilterQuality`.
- Series/episode images:
  - `features/details/components/DetailSeriesContent.kt`
  - mostly `ContentScale.Crop`, explicit high filter quality in several places.
- Cast/person avatars:
  - `features/details/components/DetailCastSection.kt`
  - `features/details/PersonDetailScreen.kt`
  - some paths build unsized `ImageRequest`s directly, so they do not get the string-model automatic sizing path.

### Full Codebase Image Loader Inventory

Scope for this inventory:

- Desktop-relevant runtime paths in `composeApp/src/commonMain/kotlin` and `composeApp/src/desktopMain/kotlin`.
- Non-desktop actuals exist for Android/iOS, but they are not the desktop sampling issue. The shared expect/actual boundaries are still called out where they affect desktop.
- This includes image entry points that do not use Coil `AsyncImage`, especially Compose resources and generated `ImageBitmap`s loaded from app/package files or decoded manually.

Search patterns used:

- `AsyncImage(`, `CoilAsyncImage`, `ImageRequest.Builder`, `rememberSizedImageRequest`, `CollectionCardRemoteImage(`
- `Image(`, `androidx.compose.foundation.Image(`, `painterResource(`, `ImageBitmap`, `toComposeImageBitmap`, `asComposeImageBitmap`
- `ImageIO`, `BufferedImage`, `drawImage`, `Image.makeFromBitmap`

#### Coil And AsyncImage Infrastructure

- `composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt:642`
  - Installs the singleton Coil `ImageLoader` with cache configuration and platform image-loader hooks.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/AsyncImage.kt`
  - The app wrapper around Coil `AsyncImage`.
  - Plain `String` models are measured through `onSizeChanged`, upgraded for known image hosts, and converted to exact `ImageRequest`s.
  - Non-`String` models, especially prebuilt `ImageRequest`s, bypass the wrapper's measured-size request creation.
  - The desktop painter workaround is applied by default unless a caller disables it.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/SizedImageRequest.kt`
  - Shared helper for exact-size profile avatar requests.
  - It exact-sizes the Coil request, but does not attach `nuvioPrescaleToDrawSize`; the final desktop painter workaround still runs when passed through `AsyncImage`.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/DesktopImagePrescale.kt`
  - Shared expect declaration for attaching desktop prescale behavior to Coil requests.
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/DesktopImagePrescale.desktop.kt`
  - Desktop actual that adds the prescale transformation.
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/AsyncImagePainterWorkaround.desktop.kt`
  - Replaces successful Coil bitmap painters with `DesktopScaledBitmapPainter`.
  - Draws cached destination-sized `ImageBitmap`s using `drawImage`.
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/DesktopImageResampling.desktop.kt`
  - Shared desktop resampling implementation used by the prescale and painter workaround path.
  - Uses Skia bitmap conversion, JVM `BufferedImage`, Lanczos3, Skia scale fallback, and direct `Image.makeFromBitmap(...).toComposeImageBitmap()`.
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/DesktopUi.desktop.kt`
  - Configures Coil with `SvgDecoder.Factory()` on desktop.
  - Also exposes `appIconPainter(...)`, which is a resource-painter path, not Coil.

#### Explicit ImageRequest Producers

These produce `ImageRequest` models manually. Once passed to `AsyncImage`, the wrapper will not remeasure or rebuild the model from final draw size.

- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/AsyncImage.kt:142`
  - Internal measured-size request for plain `String` models.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/SizedImageRequest.kt:35`
  - Exact profile avatar requests.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/NuvioShelfComponents.kt:152`
  - Poster/square/landscape shelf cards.
  - Exact request dimensions, `Scale.FILL`, desktop prescale.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeContinueWatchingSection.kt:610`
  - Continue-watching cards and preview backgrounds.
  - Exact request dimensions, `Scale.FILL`, desktop prescale.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailCastSection.kt:111`
  - Cast avatar requests with cache keys only.
  - No explicit `size`, `scale`, `precision`, or prescale. This is a high-priority audit item.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/PersonDetailScreen.kt:332`
  - Main person avatar request with cache keys only.
  - No explicit `size`, `scale`, `precision`, or prescale.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/PersonDetailScreen.kt:489`
  - Person card/list avatar request with cache keys only.
  - No explicit `size`, `scale`, `precision`, or prescale.

#### Coil AsyncImage Call Sites

These call the Nuvio `AsyncImage` wrapper. Plain URL/string models get the measured-size path. Prebuilt `ImageRequest` models rely on the caller's request dimensions.

- Core reusable UI:
  - `core/ui/NuvioShelfComponents.kt:180` poster image from explicit `ImageRequest`.
  - `core/ui/NuvioShelfComponents.kt:205` poster bottom-left logo from a URL string, `ContentScale.Fit`, desktop workaround disabled.
  - `core/ui/NuvioPosterActionSheet.kt:174` poster action-sheet artwork.
  - `core/ui/NuvioContinueWatchingActionSheet.kt:129` continue-watching action-sheet artwork.
  - `core/ui/NuvioFloatingPrompt.kt:193` prompt artwork.
- Home:
  - `features/home/components/HomeHeroSection.kt:181` hero background.
  - `features/home/components/HomeHeroSection.kt:391` hero logo, desktop workaround disabled.
  - `features/home/components/HomeContinueWatchingSection.kt:483` continue-watching card artwork from explicit `ImageRequest`.
  - `features/home/components/HomeContinueWatchingSection.kt:583` continue-watching background/preview from explicit `ImageRequest`.
  - `features/home/components/HomeCollectionRowSection.kt:164` collection row cards through `CollectionCardRemoteImage`.
- Catalog, search, collection, addons, and settings:
  - `features/catalog/CatalogScreen.kt:284` catalog poster.
  - `features/search/SearchDiscoverContent.kt:402` search/discover poster.
  - `features/collection/FolderDetailScreen.kt:189` collection folder cover.
  - `features/addons/AddonsScreen.kt:519` addon image/logo.
  - `features/settings/SupportersContributorsPage.kt:790` contributor/supporter avatar.
- Details:
  - `features/details/TmdbEntityBrowseScreen.kt:149` browse background.
  - `features/details/TmdbEntityBrowseScreen.kt:255` browse header logo.
  - `features/details/MetaDetailsScreen.kt:805` desktop source-layout backdrop.
  - `features/details/MetaDetailsScreen.kt:836` cinematic blurred backdrop.
  - `features/details/MetaDetailsScreen.kt:889` cinematic logo.
  - `features/details/components/DetailHero.kt:62` hero backdrop/poster.
  - `features/details/components/DetailHero.kt:109` detail hero logo, desktop workaround disabled.
  - `features/details/components/DetailFloatingHeader.kt:116` compact floating logo.
  - `features/details/components/DetailCastSection.kt:166` cast avatar from explicit unsized request or URL fallback.
  - `features/details/PersonDetailScreen.kt:377` person avatar from explicit unsized request or URL fallback.
  - `features/details/PersonDetailScreen.kt:556` person card/list avatar from explicit unsized request or URL fallback.
  - `features/details/components/DetailProductionSection.kt:131` production-company logo.
  - `features/details/components/DetailSeriesContent.kt:643` season poster.
  - `features/details/components/DetailSeriesContent.kt:816` episode card thumbnail.
  - `features/details/components/DetailSeriesContent.kt:1191` panel/list episode thumbnail.
  - `features/details/components/DetailTrailersSection.kt:204` YouTube trailer thumbnail.
- Player and streams:
  - `features/player/PlayerOverlays.kt:126` player loader/buffering artwork.
  - `features/player/PlayerOverlays.kt:170` player loader logo.
  - `features/player/PlayerOverlays.kt:229` player dimmed artwork.
  - `features/player/PlayerOverlays.kt:386` player source/info logo, desktop workaround disabled.
  - `features/player/PlayerEpisodesPanel.kt:385` episode panel thumbnail.
  - `features/player/skip/NextEpisodeCard.kt:91` next-episode thumbnail.
  - `features/streams/StreamsScreen.kt:379` stream header logo.
  - `features/streams/StreamsScreen.kt:598` stream hero artwork.
  - `features/streams/StreamsScreen.kt:757` stream full header logo.
  - `features/streams/StreamsScreen.kt:806` stream thumbnail.
  - `features/streams/StreamsTabletLayout.kt:105` tablet stream backdrop.
  - `features/streams/StreamsTabletLayout.kt:234` tablet stream logo.
  - `features/streams/StreamsTabletLayout.kt:296` tablet stream logo variant.
- Profiles:
  - `features/profiles/ProfileEditScreen.kt:419` custom avatar preview from exact `rememberSizedImageRequest`.
  - `features/profiles/ProfileEditScreen.kt:434` catalog avatar preview from exact `rememberSizedImageRequest`.
  - `features/profiles/ProfileEditScreen.kt:543` avatar choice item from URL string.
  - `features/profiles/ProfileSelectionScreen.kt:381` profile card avatar from exact `rememberSizedImageRequest`.
  - `features/profiles/ProfileSwitcherTab.kt:440` profile switcher avatar from URL string.
  - `features/profiles/ProfileSwitcherTab.kt:743` active mini profile avatar from URL string.

#### Direct Compose Resource Images

These do not go through Coil, `AsyncImage`, URL upgrading, exact decode sizing, `SvgDecoder`, desktop prescale, or `DesktopScaledBitmapPainter`. Raster PNG resources loaded this way are a separate sampling path and can suffer the same Compose/Desktop resize downgrade.

- App shell and navigation:
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt:2462` topbar discover icon via `painterResource(Res.drawable.sidebar_discover)`.
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt:2479` topbar library icon via `painterResource(Res.drawable.sidebar_library)`.
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt:2499` topbar search icon via `painterResource(Res.drawable.sidebar_search)`.
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt:2516` topbar settings icon via `painterResource(Res.drawable.sidebar_settings)`.
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt:2915` launch overlay wordmark via `Image(painterResource(Res.drawable.app_logo_wordmark))`.
  - `composeApp/src/desktopMain/kotlin/com/nuvio/app/DesktopApp.kt:231` window icon via `painterResource(Res.drawable.nuvio_window_icon)`.
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/NuvioNavigationBar.kt:144` generic navigation icon via `painterResource(icon)`.
- Auth:
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/features/auth/AuthScreen.kt:113` auth wordmark via `Image(painterResource(Res.drawable.app_logo_wordmark))`.
- Detail ratings:
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailMetaInfo.kt:229` rating-provider logos via `Image(painterResource(visuals.logo))`.
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailSeriesContent.kt:1115` IMDb episode rating badge via `Image(painterResource(Res.drawable.rating_imdb))`.
- Settings and integrations:
  - `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/settings/SettingsDesktop.desktop.kt:120` TMDB integration logo via `painterResource(Res.drawable.rating_tmdb)`.
  - `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/settings/SettingsDesktop.desktop.kt:121` Trakt integration logo via `painterResource(Res.drawable.trakt_tv_favicon)`.
  - `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/settings/SettingsDesktop.desktop.kt:122` MDBList integration logo via `painterResource(Res.drawable.mdblist_logo)`.
  - `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/settings/SettingsDesktop.desktop.kt:123` IntroDB integration logo via `painterResource(Res.drawable.introdb_favicon)`.
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/LicensesAttributionsPage.kt:271` draws integration logo painters with `Image(...)`.
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsComponents.kt:257` draws generic settings `iconPainter` with `Image(...)`.
  - `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/TraktSettingsPage.kt:563` draws Trakt glyph with `Image(painter = traktBrandPainter(...))`.
  - `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/trakt/TraktDesktop.desktop.kt:67` Trakt glyph via `painterResource(Res.drawable.trakt_tv_favicon)`.
  - `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/trakt/TraktDesktop.desktop.kt:68` Trakt wordmark via `painterResource(Res.drawable.trakt_logo_wordmark)`.
- Player/app custom icon painters:
  - `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/DesktopUi.desktop.kt:37` `appIconPainter(...)` maps player and library icons to resource painters.

#### Direct Bitmap And Manual Decode Paths

These are non-Async image paths and should be audited separately from Coil.

- `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/home/components/CollectionCardRemoteImage.desktop.kt`
  - Desktop collection GIF path downloads the GIF with Ktor `HttpClient(Java)`.
  - Decodes frames with `ImageIO` into `BufferedImage`.
  - Composites frames through Java2D.
  - Scales to the target canvas with `Graphics2D.drawImage(..., canvasW, canvasH, null)` using bicubic rendering hints.
  - Converts frames using `toComposeImageBitmap()`.
  - Draws them with direct `Image(bitmap = ..., filterQuality = nuvioImageFilterQuality)`.
  - Static fallback uses the normal Nuvio `AsyncImage` wrapper.
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/AsyncImagePainterWorkaround.desktop.kt`
  - Custom painter path for successful Coil bitmaps.
  - Uses `drawImage` directly after destination-size caching.
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/DesktopImageResampling.desktop.kt`
  - Not a UI loader by itself, but all prescaled Coil bitmap paths pass through its Skia/JVM bitmap conversion and resize helpers.
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/home/components/HomeHeroLogoDump.desktop.kt`
  - Diagnostic-only path.
  - Downloads hero logo bytes and uses `ImageIO` only to inspect dimensions and write debug artifacts.
  - It does not render UI, but it is still a disk/network image decode surface.
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/PlayerDesktop.desktop.kt:1540`
  - Creates a 16x16 transparent `BufferedImage` for a hidden mouse cursor.
  - Not content artwork and not relevant to the poster/logo sampling issue.

#### Non-Desktop Source-Set Image Entry Points

These are outside the desktop target, but they were included in the search so the codebase inventory is explicit.

- `composeApp/src/androidMain/kotlin/com/nuvio/app/core/ui/AppIconPainter.android.kt`
  - Android actual for `appIconPainter(...)`, using Android `R.drawable` resources.
- `composeApp/src/iosMain/kotlin/com/nuvio/app/core/ui/AppIconPainter.ios.kt`
  - iOS actual for `appIconPainter(...)`, using Compose `Res.drawable` resources.
- `composeApp/src/androidMain/kotlin/com/nuvio/app/core/ui/PlatformImageLoader.android.kt`
  - Android Coil platform configuration adds animated image/GIF decoders.
- `composeApp/src/iosMain/kotlin/com/nuvio/app/core/ui/PlatformImageLoader.ios.kt`
  - iOS Coil platform configuration.
- `composeApp/src/androidMain/kotlin/com/nuvio/app/features/home/components/CollectionCardRemoteImage.android.kt`
  - Android actual for collection card images.
  - Builds an `ImageRequest` and draws through `AsyncImage`.
- `composeApp/src/iosMain/kotlin/com/nuvio/app/features/home/components/CollectionCardRemoteImage.ios.kt`
  - iOS actual for collection card images.
  - Static path uses `AsyncImage`.
  - Animated path downloads GIF bytes, decodes to `UIImage`, and displays through `UIKitView`/`UIImageView`, bypassing Compose/Coil drawing.
- `composeApp/src/androidMain/kotlin/com/nuvio/app/features/settings/IntegrationLogoPainter.android.kt`
  - Android actual for integration logos, mixing Compose `Res.drawable` painters and Android `R.drawable` painters.
- `composeApp/src/iosMain/kotlin/com/nuvio/app/features/settings/IntegrationLogoPainter.ios.kt`
  - iOS actual for integration logos, using Compose `Res.drawable` painters.
- `composeApp/src/androidMain/kotlin/com/nuvio/app/features/trakt/TraktBrandPainter.android.kt`
  - Android actual for Trakt brand painters, using Android `R.drawable`.
- `composeApp/src/iosMain/kotlin/com/nuvio/app/features/trakt/TraktBrandPainter.ios.kt`
  - iOS actual for Trakt brand painters, using Compose `Res.drawable`.
- `composeApp/src/androidMain/kotlin/com/nuvio/app/features/downloads/DownloadsLiveStatusPlatform.android.kt`
  - Android notification small icon through `setSmallIcon(...)`.
- `composeApp/src/androidMain/kotlin/com/nuvio/app/features/notifications/EpisodeReleaseNotificationPlatform.android.kt`
  - Android notification small icon through `setSmallIcon(...)`.

#### Compose Resource Assets Used By These Paths

Raster PNG resources:

| Resource | Size | Main usage |
| --- | ---: | --- |
| `app_logo_wordmark.png` | 1085x344 | launch overlay, auth logo |
| `introdb_favicon.png` | 256x256 | settings integration logo |
| `nuvio_window_icon.png` | 1080x1080 | desktop window icon |
| `rating_audience_score.png` | 106x140 | detail rating logo |
| `rating_imdb.png` | 575x290 | detail and episode rating logo |
| `rating_letterboxd.png` | 500x500 | detail rating logo |
| `rating_metacritic.png` | 88x88 | detail rating logo |
| `rating_rotten_tomatoes.png` | 139x141 | detail rating logo |
| `rating_tmdb.png` | 185x133 | detail/settings rating logo |
| `rating_trakt.png` | 145x145 | detail rating logo |

Vector/SVG/XML resources:

- SVG: `ic_player_aspect_ratio.svg`, `ic_player_audio_filled.svg`, `ic_player_pause.svg`, `ic_player_play.svg`, `ic_player_subtitles.svg`, `library_add_plus.svg`, `mdblist_logo.svg`, `trailer_play_button.svg`, `trakt_logo_wordmark.svg`, `trakt_tv_favicon.svg`.
- XML vector: `sidebar_discover.xml`, `sidebar_home.xml`, `sidebar_library.xml`, `sidebar_search.xml`, `sidebar_settings.xml`, `compose-multiplatform.xml`.
- Vector resources are not bitmap-resolution limited like PNGs, but they still use the resource painter path and do not receive Coil-specific sizing, SVG decoding, or desktop bitmap workaround behavior.

#### Loader Parity Risks From This Inventory

- Direct `Image(painterResource(...))` raster resources are the biggest non-Async gap. They should get a resource-image policy if we want all app-bundle PNGs to use the same desktop sampling workaround.
- `ImageRequest` models passed into `AsyncImage` are not automatically remeasured. Cast/person avatar requests are currently unsized and should be converted to a sized helper or allowed to fall back to the string-model measured path.
- The desktop animated collection GIF path is independent of Coil and uses Java2D bicubic resize plus Compose `Image(bitmap)` draw. It needs its own browser-parity/sampling decision.
- Logos that disable `useDesktopImagePainterWorkaround` avoid the bitmap workaround. That may be good for SVG logos, but raster logos in the same path can still show the Compose desktop sampling issue.
- Resource icons that are vectors are lower risk for pixelation, but raster rating/provider logos are often drawn small and can expose poor downsampling immediately.

## Browser-Parity Implementation Notes For Later

No code is changed in this research pass. These are implementation directions for a later task.

### Preferred Target Pipeline

To emulate normal WebView CSS `image-rendering: auto`:

1. Resolve the final destination in physical pixels.
2. Resolve CSS geometry:
   - cover/contain/stretch
   - source crop rectangle
   - alignment
   - clipping
3. Fetch a source asset at least as large as the physical target after crop.
4. Avoid accidental upscaling.
5. Avoid multiple independent resampling passes.
6. Build a Chromium-like downscale:
   - if heavily reduced, choose a mip/downsample level larger than the target, likely power-of-two based
   - final draw with bilinear from that level
   - for `pixelated`/nearest equivalent, avoid pre-downscaling and use nearest
7. Use one final draw pass with a browser-like sampling choice.

### What To Reconsider In Current Desktop Path

- `FilterQuality.High` may be too aggressive for WebView parity.
- Lanczos3 may produce different edge contrast and ringing than Chromium's medium path.
- Linear + linear mipmap fallback differs from Chromium medium's linear + nearest mipmap mapping.
- Direct unsized `ImageRequest`s can bypass the measured-size wrapper path and should be audited.
- Logo paths that disable the desktop workaround may be correct for SVGs, but bitmap logos need their own parity decision.
- The 2x decode multiplier may be good for avoiding undersized assets, but if followed by Lanczos and another draw scale it may drift from browser behavior.

### Test Strategy For Later

Pixel parity needs a WebView oracle:

1. Build a minimal WebView page that renders the same image URL in a fixed CSS box.
2. Render with the target CSS:
   - `object-fit`
   - `object-position`
   - `image-rendering`
   - border radius
   - transforms
   - opacity/filter if relevant
3. Capture screenshot at known DPR/page zoom.
4. Render the Compose component at the same physical size.
5. Compare:
   - geometry first: source crop and destination rectangle
   - then sampling: edges, diagonals, fine text, gradients
   - then compositing: rounded corners, overlays, opacity, blur

## Step 3 - Validation And Corrections

### Validated Facts

- Specs define layout/resource-selection behavior, not exact default sampling kernels.
- Chromium/WebView normal CSS image path currently uses Blink interpolation quality and cc/Skia sampling.
- Chromium default interpolation is normally medium, mapping to linear sampling plus nearest mipmap.
- Chromium decode caches may pre-downscale to mip-like sizes before final raster.
- Nuvio desktop currently uses high filter quality and a Lanczos3-first prescale path in many bitmap cases.

### Explicit Unknowns

- Installed WebView Chromium milestone can differ from Chromium `main`.
- Runtime flags can change interpolation quality, especially low-quality interpolation features.
- GPU driver/backend behavior can affect exact visual output.
- JPEG/WebP/AVIF decoder behavior, color profile conversion, premultiplied alpha, and chroma upsampling can affect pixels before Skia sampling.
- Fractional layout coordinates and transforms can shift sample positions even with the same filter.

### Research Output Checklist

- WebView -> Chromium/Blink/cc/Skia pipeline: covered.
- CSS resource selection and DPR: covered.
- CSS object/background geometry: covered.
- `image-rendering` and sampling behavior: covered.
- Downsampling and mip/decode-cache behavior: covered.
- Effects/compositing considerations: covered.
- Current Nuvio Compose/Coil/Skia path: covered.
- Later implementation direction without code changes: covered.

## Step 4 - StackOverflow Follow-Up And Resizer Library Options

### StackOverflow Thread Follow-Up

Thread:

- https://stackoverflow.com/questions/31077776/what-algorithm-do-browsers-commonly-use-when-scaling-images-with-css

The useful part of this thread is not a single final answer like "browsers use Lanczos" or "browsers use bilinear". The answer links into old Chromium image scaling code, and that code still exists in current Chromium as `skia/ext/image_operations.h`, but it is a resize utility path, not the full normal CSS paint path.

Current Chromium `ImageOperations` exposes quality intents and algorithm-specific modes:

- `RESIZE_GOOD`, `RESIZE_BETTER`, and `RESIZE_BEST` are quality/speed intents that may map differently by platform and situation.
- The algorithm-specific modes include box, Hamming, and Lanczos3.
- Chromium documents `RESIZE_HAMMING1` as a faster alternative than Lanczos2 and `RESIZE_LANCZOS3` as the sharper high-quality option.

Primary source:

- https://chromium.googlesource.com/chromium/src/+/HEAD/skia/ext/image_operations.h

That does not mean a regular CSS `<img>` or CSS background is always Lanczos3. The current normal Chromium/Blink image draw path computes interpolation quality in `GraphicsContext`, passes draw-time sampling into image drawing, and cc maps filter quality into Skia sampling options.

Primary sources:

- https://chromium.googlesource.com/chromium/src/+/refs/heads/main/third_party/blink/renderer/platform/graphics/graphics_context.cc
- https://chromium.googlesource.com/chromium/src/+/HEAD/cc/paint/paint_flags.cc

The most important current Chromium mapping for our use case:

| Path | Current observed mapping |
| --- | --- |
| CSS/default-style lazy decoded image | default interpolation quality |
| cc `kMedium` | linear filter + nearest mipmap |
| cc `kLow` | linear filter, no mipmap |
| cc `kHigh` default | Catmull-Rom cubic |
| cc `kHigh` upscale | Mitchell cubic |

MDN lines up with this at the CSS level: `image-rendering: auto` is user-agent dependent, `smooth` allows smoothing such as bilinear, and `crisp-edges`/`pixelated` are the contrast-preserving/pixel-art direction rather than the normal poster/photo path.

Primary source:

- https://developer.mozilla.org/en-US/docs/Web/CSS/image-rendering

Nuvio implication:

- Browser parity is probably not "always Lanczos3".
- A more browser-like experiment should compare:
  - current Lanczos3 prescale,
  - Skia linear + nearest mipmap,
  - Skia linear + linear mipmap,
  - a Hamming downscale prepass if we can get one,
  - Catmull-Rom/Mitchell cubic for the cases where Chromium would choose high quality.

### Current Local Resizer Baseline

Nuvio already has a JVM high-quality scaler:

- `composeApp/build.gradle.kts` depends on `com.mortennobel:java-image-scaling:0.8.6`.
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/DesktopImageResampling.desktop.kt` uses `ResampleFilters.getLanczos3Filter()`.

Local class inspection of the installed jar shows these available filters:

- Bell
- Bicubic
- Bicubic high-frequency response
- Box
- B-spline
- Hermite
- Lanczos3
- Mitchell
- Triangle

Notably, this library does not expose Hamming. That makes it useful for our current Lanczos/Mitchell experiments, but incomplete if the next test is a Chromium `RESIZE_GOOD`/Hamming-style downscale.

### Library Options

#### Keep `java-image-scaling` For First A/B Tests

Pros:

- Already integrated and packaged.
- Pure JVM, no native packaging work.
- Gives us Lanczos3, Mitchell, Triangle, Box, and a few other filters.

Cons:

- No Hamming filter.
- Uses `BufferedImage` conversion, which is extra CPU/memory churn.
- It only helps the prescale path; it does not fix direct `painterResource`, direct `Image(bitmap)`, SVG, unsized `ImageRequest`, or final Compose draw sampling paths by itself.

Recommendation:

- Keep it as the baseline and add a filter-mode switch before replacing it.
- Test Mitchell/Triangle/Box only as controlled comparisons; none is a clear browser-parity answer by itself.

#### `fast_image_resize`

Project:

- https://github.com/Cykooz/fast_image_resize
- https://docs.rs/fast_image_resize/latest/fast_image_resize/enum.FilterType.html

Relevant facts:

- Rust crate focused on fast resize with SIMD.
- Supports common u8/u16/f32 pixel layouts, optional Rayon multithreading, and no-std use.
- Filter options include Box, Bilinear, Hamming, Catmull-Rom, Mitchell, Gaussian, Lanczos3, and custom filters.
- Its docs explicitly describe Hamming as bilinear-speed with downscale quality comparable to bicubic-style filters.

Fit for Nuvio:

- Strong candidate if we decide the missing piece is high-speed Hamming/Mitchell/Lanczos experimentation.
- It does not appear to provide a ready JVM binding. We would need JNI, JNA/FFM with a C wrapper, or a small native bridge that ships platform binaries.
- The integration cost is not the filter math; it is getting pixels from Skia/Coil into native memory and back without adding another expensive copy that cancels out the win.

#### `pic-scale`

Project:

- https://github.com/awxkee/pic-scale
- https://raw.githubusercontent.com/awxkee/pic-scale/master/picscale/include/picscale.h

Relevant facts:

- Rust crate focused on high-performance scaling with SIMD and multithreading.
- Supports many filters, including Bilinear, Nearest, Cubic, Mitchell-Netravalli, Catmull-Rom, Hermite, B-spline, Hann, Bicubic, Hamming, Hanning, Blackman, and others.
- Has explicit color-space positioning in the README, including linear downscale recommendations.
- Has generated C bindings under `picscale/include/picscale.h`.
- The C header currently exposes RGBA/RGB/planar resize functions and a smaller C enum of filters: Nearest, Bilinear, Lanczos3, Mitchell-Netravalli, Bicubic, and Catmull-Rom.

Fit for Nuvio:

- Better than `fast_image_resize` if we want a C ABI without building our own wrapper from scratch.
- The public C ABI does not currently expose Hamming even though the Rust crate supports it, so browser-Hamming parity may still require extending or wrapping it.
- Stronger than `java-image-scaling` for future color-space experiments, especially if we want to test linear-light downscaling for posters/backgrounds.

#### JVM/Java Alternatives

The obvious JVM alternatives are less compelling for this exact issue:

- `imgscalr` and Thumbnailator are easier Java APIs, but they generally sit on Java2D/progressive scaling choices and do not give us a more browser-like Hamming/mipmap pipeline than what we can test now.
- `scrimage` is a mature JVM/Scala image library, but it would be a larger dependency and does not remove the core Compose Desktop final-draw problem.
- libvips JVM bindings are powerful, but they are too heavy for per-card UI resizing unless we move image processing into an offline/cache generation stage.

### Recommended Experiment Plan

Before introducing native Rust, add one desktop-only env-controlled strategy switch around the existing resampling path. Suggested values:

| Mode | Purpose |
| --- | --- |
| `lanczos3` | Current baseline |
| `skia-linear-nearest-mip` | Closest to Chromium medium/default cc mapping |
| `skia-linear-linear-mip` | Current Skia fallback comparison |
| `skia-catmull-rom` | Chromium high/default comparison |
| `skia-mitchell` | Chromium high/upscale comparison |
| `jvm-mitchell` | Pure JVM comparison without native work |
| `jvm-triangle` | Softer bilinear-ish comparison |

If this matrix still cannot match WebView closely enough, then add a native prototype:

1. Prefer `fast_image_resize` if the target is Hamming specifically.
2. Prefer `pic-scale` if we want a ready C ABI and color-space experiments first.
3. Keep the native integration desktop-only.
4. Cache native-resized bitmaps by source key, crop rect, target physical size, DPR, filter mode, and alpha/color-space mode.
5. Benchmark copy cost separately from resize cost. A faster Rust filter is not a win if Skia/BufferedImage/native byte copies dominate.

### Most Likely Remaining Causes Of Choppy Images

Based on this research, the remaining visible problem is likely a combination of these rather than "wrong Lanczos library":

- Some images still bypass the desktop workaround entirely, especially direct `painterResource` PNGs, direct `Image(bitmap)` paths, and logo paths with `useDesktopImagePainterWorkaround = false`.
- Some prebuilt `ImageRequest`s bypass measured sizing, especially cast/person/avatar paths identified in the inventory.
- Some images may still be fetched below final physical draw size.
- Fractional layout sizes or positions can shift sample points and make text/edges look worse even with a good filter.
- Current prescale can create a second resampling pass if the cached bitmap size differs from final draw size.
- Compose final drawing may still use sampling behavior that differs from Chromium's draw path after our prescale.

### Updated Research Output Checklist

- StackOverflow thread checked and mapped to current Chromium source: covered.
- Linked MDN `image-rendering` path checked: covered.
- Old Chromium `ImageOperations` resize path checked against current source: covered.
- Current Chromium/Blink/cc sampling path checked: covered.
- Existing local `java-image-scaling` capabilities inspected: covered.
- `fast_image_resize` researched: covered.
- `pic-scale` researched, including C binding surface: covered.
- Recommendation written without changing runtime code: covered.

## Step 5 - Implemented Desktop Sampling Switch

Runtime implementation:

- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/DesktopImageResampling.desktop.kt`
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/DesktopImagePrescale.desktop.kt`
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/AsyncImageFilterQuality.desktop.kt`
- `composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/AsyncImagePainterWorkaround.desktop.kt`

Configuration:

- Env var: `NUVIO_IMAGE_SAMPLING=chrome`
- JVM property: `-Dnuvio.image.sampling=chrome`
- Legacy/research alias also supported: `NUVIO_IMAGE_RESAMPLER=chrome` or `-Dnuvio.image.resampler=chrome`
- Optional debug logging: `NUVIO_IMAGE_DEBUG=1`

Available values:

| Value | Behavior |
| --- | --- |
| `lanczos3` / `current` / unset | Existing JVM Lanczos3-first path, Skia linear+linear mip fallback |
| `chrome` / `chromium` / `browser` | Skips JVM Lanczos and uses Skia linear + nearest mipmap |
| `skia_linear_nearest_mip` / `medium` | Same core sampling as `chrome`, kept as an explicit technical alias |
| `skia_linear_linear_mip` | Skia linear + linear mipmap comparison |
| `skia_catmull_rom` | Skia Catmull-Rom cubic comparison |
| `skia_mitchell` | Skia Mitchell cubic comparison |
| `jvm_mitchell` | JVM java-image-scaling Mitchell comparison |
| `jvm_triangle` | JVM java-image-scaling Triangle comparison |
| `jvm_hamming` / `hamming` | JVM implementation of Chromium Hamming1 |

Cache behavior:

- The Coil prescale transformation cache key now includes the sampling mode.
- The desktop destination-sized painter cache also includes the sampling mode.
- This avoids comparing stale Lanczos-scaled bitmaps after starting the app in `chrome` mode.

Important limitation:

- `chrome` mode currently approximates Chromium's cc medium-quality sampling choice. It does not reimplement Chromium's full decode-cache/mip selection pipeline, power-of-two downscale decisions, GPU upload behavior, JPEG chroma handling, or all CSS compositing details.
- Direct `painterResource` images and non-Coil manual bitmap paths still need separate rendering-policy work if they are visibly affected.

## Step 6 - Anti-Aliased Draw Pass And Hamming Experiment

Follow-up finding:

- The `tt0203259` Metahub logo URL used during debugging is an 800x310 transparent PNG, not an SVG.
- That means the visible jagged white slopes are not only an SVG decoder issue.
- The previous `chrome` mode still used Skia `Image.scalePixels(...)` for final Skia resizing, while Chromium's normal paint path is closer to `drawImageRect(...)` with paint flags plus sampling.

Implementation update:

- Skia fallback resizing now renders through `Canvas.drawImageRect(...)`.
- The draw pass uses:
  - `Paint.isAntiAlias = true`
  - `Paint.isDither = true`
  - the selected sampling mode
- This is closer to the browser paint path than `Image.scalePixels(...)`, especially for transparent edges and diagonal alpha coverage.

Additional experiment:

- Added `NUVIO_IMAGE_SAMPLING=jvm_hamming`.
- The filter follows Chromium's `EvalHamming(1, x)` formula from `skia/ext/image_operations.cc`.
- This gives us a software Hamming comparison without adding a Rust/native dependency yet.

Recommended next comparison order:

1. `NUVIO_IMAGE_SAMPLING=chrome`
   - Tests the anti-aliased draw pass with Chromium-like medium sampling.
2. `NUVIO_IMAGE_SAMPLING=jvm_hamming`
   - Tests Chromium's software resize utility filter.
3. `NUVIO_IMAGE_SAMPLING=skia_catmull_rom`
   - Tests a sharper cubic path.
4. `NUVIO_IMAGE_SAMPLING=lanczos3`
   - Existing baseline.

If logos are still jagged after this, the next likely gap is not the filter kernel. It is probably one of:

- source PNG edge quality,
- alpha premultiplication/color-space handling,
- subpixel layout or fractional draw size,
- final Compose painter path for `useDesktopImagePainterWorkaround = false`,
- or direct resource/non-Coil image paths that still bypass this pipeline.

## Step 7 - Logo Path Correction

Screenshot checked:

- `C:\Users\abukc\Pictures\nuvio-pixelation.png`

Finding:

- The visible issue is concentrated on high-contrast transparent logo edges.
- The affected remote logo call sites were using `ContentScale.Fit` and explicitly disabling the desktop painter workaround.
- They still went through the measured Coil request path, so many logos were being prescaled into a 1x destination-sized transparent bitmap before final display.
- Chrome typically keeps the decoded image and paints it into the destination surface, so diagonal alpha coverage gets a final smoothing pass instead of being baked into a 1x mask.

Implementation update:

- Added `useDesktopImagePrescale` to the shared `AsyncImage` wrapper.
- Remote logo call sites now use `useDesktopImagePrescale = false` instead of disabling the desktop painter workaround.
- Non-prescaled image requests now ask Coil for `Size.ORIGINAL`, so Metahub logos keep their highest fetched source size instead of decoding near the drawn box first.
- The desktop painter workaround now supports two original-source logo paths:
  - JVM sampling modes (`lanczos3`, `jvm_hamming`, `jvm_mitchell`, `jvm_triangle`) resample the original decoded bitmap into the final draw box once and cache that bitmap.
  - Skia-only modes (`chrome`, `skia_linear_nearest_mip`, `skia_linear_linear_mip`, `skia_catmull_rom`, `skia_mitchell`) use a direct native Skia draw path.
- The direct path draws the decoded bitmap with `Canvas.drawImageRect(...)`, `Paint.isAntiAlias = true`, `Paint.isDither = true`, and the active sampling mode.

Affected logo paths:

- Home hero logo.
- Detail hero logo.
- Desktop cinematic/detail logo.
- Detail floating header logo.
- Shelf logo overlays.
- Player logo overlays.
- Streams header/tablet logos.

Expected result:

- Logos should draw from the higher-resolution decoded image instead of a pre-baked 1x transparent canvas.
- `NUVIO_IMAGE_SAMPLING=jvm_hamming` now tests Hamming on logos from the original decoded asset.
- `NUVIO_IMAGE_SAMPLING=chrome` remains the direct Skia comparison path.
- `NUVIO_IMAGE_DEBUG=1` logs original decoded bitmap size and destination draw size for these paths.

## Step 8 - ANGLE Runtime Packaging

The Windows desktop app defaults to Skiko `ANGLE`, but Skiko needs the separate ANGLE runtime jar on the classpath.
Without it, startup logs:

```text
[SKIKO] warn: Fallback to next API
org.jetbrains.skiko.RenderException: Failed to load ANGLE library
```

Implementation update:

- Added runtime dependency: `org.jetbrains.skiko:skiko-awt-runtime-angle-windows-x64:0.144.5`.
- `createDistributable` and `createReleaseDistributable` now include `skiko-awt-runtime-angle-windows-x64-0.144.5-*.jar`.
- The jar contains `libEGL.dll` and `libGLESv2.dll`, which are the files Skiko's ANGLE loader requests.

## Step 9 - Local Nuvio Wordmark

The startup/auth Nuvio wordmark was not using the Coil/desktop image pipeline. It was drawn directly with
`painterResource(Res.drawable.app_logo_wordmark)`, so it bypassed the higher-quality desktop resampling path.

Implementation update:

- Added `NuvioWordmarkImage`.
- Desktop decodes `app_logo_wordmark.png` into a Skia bitmap, waits for the actual layout pixel size, then generates an exact-size transparent wordmark bitmap with `nuvioScaleToFitBitmap`.
- The final `Image` draws that exact-size bitmap with `FilterQuality.None`, so Compose does not run a second scaling pass.
- The attempted Coil `Res.getUri(...)` path was removed because packaged Compose resource URIs are not a reliable Coil source in the release app image.
- Startup overlay and auth screen now use this shared component.

## Chrome Comparison Workflow

Helper page:

- `chrome-image-sampling-check.html`

How to compare:

1. Open the helper in Chrome, not the embedded browser:
   - `C:\Users\abukc\NuvioDesktop\chrome-image-sampling-check.html`
2. Set Chrome zoom to 100%.
3. Paste the exact image URL from Nuvio logs, DevTools, or the image debug dump.
4. Pick the same geometry:
   - Poster: `300x450`, `cover`, `center center`
   - Hero: use the actual hero CSS/app box, `cover`, `center top`
   - Landscape/continue-watching: use the actual card box, `cover`, usually `center center`
5. Read the page's `Chrome DPR` and `Estimated physical box`.
6. For a physical-pixel match, use:
   - Chrome CSS width = Nuvio physical draw width / Chrome DPR
   - Chrome CSS height = Nuvio physical draw height / Chrome DPR
7. Capture Chrome's result with DevTools screenshot or Windows screenshot, then compare against the same app view launched with `NUVIO_IMAGE_SAMPLING=chrome`.

Example launch from PowerShell:

```powershell
$env:NUVIO_IMAGE_SAMPLING = "chrome"
$env:NUVIO_IMAGE_DEBUG = "1"
.\gradlew.bat :composeApp:runDistributable
```
