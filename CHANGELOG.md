# Changelog

## CTS Lite — Fork of [Circle to Search](https://github.com/aks-labs/circle-to-search) by AKS-Labs

---

## [1.0.0] - 2026

### MAIN CHANGES - Accessibility Service Removed

The original app required the Android Accessibility Service to capture screenshots,
which is a privacy nightmare as well as causing incompatibility with banking and financial apps.

CTS Lite replaces this entirely with Android's native **VoiceInteraction API**
(`VoiceInteractionSession.onHandleScreenshot()`), which delivers a screenshot
automatically when the app is invoked while set as the default assistant. No Accessibility
permission is required.

The app now only uses one permission (INTERNET). It also has VIBRATE but it has not been implemented yet.

**Removed:**
- `CircleToSearchAccessibilityService`
- `CircleToSearchTileService` (Quick Settings tile)
- `TileTriggerActivity`
- `OverlayConfig.kt` and its Gson dependency
- All related permissions (`BIND_ACCESSIBILITY_SERVICE`, `FOREGROUND_SERVICE`, etc.)

**Added:**
- `CircleToSearchVoiceService` - registers the app as a system assistant
- `AssistSessionService` - receives screenshots via `onHandleScreenshot()`
- `StubRecognitionService` - required stub to satisfy Android's VoiceInteraction validator

---

### UI Refactor - CircleToSearchScreen Split

The original `CircleToSearchScreen.kt` (~600 lines) handled all UI and logic in a
single composable, causing unnecessary recomposition across the entire screen on
every state change.

It has been split into four focused composables:

| File | Responsibility                                     |
|---|----------------------------------------------------|
| `CircleToSearchScreen.kt` | Layout coordinator - shared state only             |
| `DrawingLayer.kt` | Freehand selection canvas and bracket animation    |
| `OverlayHeader.kt` | Close button and dropdown menu                     |
| `SearchEngineWebView.kt` | Single WebView lifecycle per search engine         |
| `SearchResultsSheet.kt` | Tab row, upload logic, loading indicator, WebViews |

---

### Other small changes

- **Search engines** - Perplexity and ChatGPT removed (they didn't work with image URL searches)
- **Dark mode** - improved CSS injection to get better colors for dark mode
- **Desktop mode** - desktop mode is now persistent across tabs that you selected it for (Bing, Google, etc)
- **Upload failure UI** - clear error message shown when both Litterbox and Catbox
  are unreachable, with guidance to retry - easier to spot upload problems or add new upload services in the future
- **Search pill** - redesigned with semi-transparent background, rainbow gradient
  border
- **`isDefaultAssistant()`** - bug fix, changed it to use `flattenToShortString()` so the setup
  screen correctly shows assistant status
- **`BitmapRepository`** - `@Volatile` added for thread safety
- **`OverlayActivity`** - gracefully finishes when no screenshot is available
  rather than displaying a blank screen
- **Chrome user agent** - updated from 120 to 135
- **Dead code removed** - `WebViewActivity`, `PulsingSearchLoader`, `SupportSheet`,
  `vibrateClick()`, `loadBitmap()`, `getGoogleImagesUrl()`, and other unused
  components cleaned up throughout because they no longer served a purpose or were leftovers

---

### Package & Branding

- Package renamed from `com.akslabs.circletosearch` to `com.zawyer1.ctslite`
- App name changed to **CTS Lite**
- App icon updated
- Copyright headers updated to credit both AKS-Labs (original dev) and Zawyer1 (fork)
- AI (Claude Opus 4.6) was utilized to fork the project and the key changes related to Digital Assistant/VoiceInteraction