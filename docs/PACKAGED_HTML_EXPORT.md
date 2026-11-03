# TurboWarp embedded HTML export

The `my-develop` TurboWarp GUI now embeds the official Packager source and passes
its current project automatically. It supports HTML only and inherits fa/ar/en.
The intro and project chooser are absent. See the GUI's `docs/EMBEDDED_PACKAGER.md`.

`editor/PackagedExportBridge.kt` provides `AndroidPackagedExport` through the
origin-scoped WebViewCompat message API. Only the main appassets `turbobuild/`
frame can begin/append/save/share/cancel an export. Chunks are acknowledged and
validated against the announced byte count before any public file is created.
Existing project loading, SB3/sprite saving, native text input and engine choice
remain independent. Scratch's `assets/build` does not need this export feature.

Downloads uses MediaStore from API 29, or the capped WRITE_EXTERNAL_STORAGE
permission on API 24–28. Sharing uses ACTION_SEND with a read-only FileProvider
URI limited to `cache/packaged_html`. Shared files survive the chooser and are
pruned after 24 hours on the next bridge attachment. Partial transfers are removed
on detach/dispose. Save collisions get unique filenames; existing files survive.

Update `app/src/main/assets/turbobuild` with the **complete** GUI `build` including
`packager`, then assemble with the existing `-PscratchEngines=both|turbo|scratch`
selection. The shared-media asset transform remains unchanged. Both the new GUI
and rebuilt Android client are needed; copying the new web files into an old APK
does not add its native export bridge.

Checkpoint before this change: `5eb399a`. The integration test
`PackagedExportDeviceTest` packages the real current project offline, saves HTML
through the real bridge, verifies Downloads bytes, cleans only its own test file
and rejects a same-origin child frame. Android 24–28 legacy permissions require
additional device coverage; the current connected device is Android 16.

The device test also intercepts ACTION_CHOOSER and verifies the native sharing
launch without selecting a real recipient. Both save and share paths passed on
the connected device. The existing 154 app unit tests and compilation passed.

## Direct HTML playback

The GUI now offers High/Medium presets, collapsed Advanced settings and a minimum
five-second generation screen. The `preview` export operation streams the completed
HTML through the same acknowledged bridge, then opens PackagedPlayerActivity.
This Activity is not exported and accepts only an HTML file from the private
packaged_html cache. Its WebView has no JavaScript interfaces, file/content access,
file picker or popup handler. It uses a local asset loader on the separate player.farsiscratch.invalid origin,
supports fullscreen,
keeps the screen on while playing and has localized icon buttons for Refresh/Close.
Refresh reloads the HTML. Close destroys its WebView, deletes its own temporary
file and returns to the existing editor/result. Save and Share remain available.
The player does not pause timers globally, because WebView.pauseTimers affects
other WebViews too. Browser/Electron use an isolated sandbox iframe instead.

The native device test verifies the minimum loading time, player launch, absence
of all editor bridges, refresh resetting the document and return to the result,
then tests Save/Share. These checks passed on the connected Android 16 device.
Checkpoint before this step: d741710.

## Saved-project startup

TurboWarp queues an early host URL until the default project reaches a SHOWING
state. This prevents an undefined upload action when loading from My Projects
before the editor initializes. Android's queue/asset URLs are unchanged.
QueuedProjectDeviceTest uses queueProjectFromHome before a fresh WebView loads,
serves the SB3 through ProjectRepository and verifies the saved project title
and absence of the invalid-project error. Its uniquely named fixture is removed;
existing user projects are not changed.

Final verification: both QueuedProjectDeviceTest and PackagedExportDeviceTest passed
on Android 16 after the origin separation. The final APK contains the exact GUI
build including the queued-load fix. All 154 Android app unit tests passed.
