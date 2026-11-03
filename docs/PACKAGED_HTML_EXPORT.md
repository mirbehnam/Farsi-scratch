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
