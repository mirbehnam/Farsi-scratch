# TurboWarp embedded HTML export

The `my-develop` TurboWarp GUI now embeds the official Packager source and passes
its current project automatically. It supports HTML only and inherits fa/ar/en.
The intro and project chooser are absent. See the GUI's `docs/EMBEDDED_PACKAGER.md`.

`editor/PackagedExportBridge.kt` provides `AndroidPackagedExport` through the
origin-scoped WebViewCompat message API. Only the main appassets `turbobuild/`
frame can begin/append/save/share/preview/cancel an export. Chunks are acknowledged and
validated against the announced byte count before any public file is created.
Existing project loading, SB3/sprite saving, native text input and engine choice
remain independent. Original Scratch also uses this bridge for isolated high-quality playback.

HTML Save uses PackagedHtmlRepository in private files/packaged_projects without a storage permission. JSON settings exports still use Downloads (MediaStore from API 29, capped WRITE_EXTERNAL_STORAGE on API 24–28). Sharing uses ACTION_SEND with a read-only FileProvider
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
through the real bridge, verifies private-library bytes, cleans only its own test file
and rejects a same-origin child frame. Android 24–28 legacy permissions require
additional device coverage; the current connected device is Android 16.

The device test also intercepts ACTION_CHOOSER and verifies the native sharing
launch without selecting a real recipient. Both save and share paths passed on
the connected device. The existing 154 app unit tests and compilation passed.

## Direct HTML playback

High/Medium presets disable stop, pause and fullscreen buttons inside the HTML.
Preset selection writes the same options store used by the Advanced checkboxes.
The `preview` export streams the completed HTML through the acknowledged bridge
and sets MainActivity's PackagedPlayback state. PackagedHtmlPlayer owns a separate
bridge-free WebView on player.farsiscratch.invalid inside the current ScratchScreen.
The editor WebView remains mounted but invisible, so closing preserves its project
and existing result. File/content access, file picker and popup handlers are absent.

The existing left rail stays visible. While HTML is playing, Home and the editor
scroll slider are absent and localized Refresh/Close icons appear in the rail.
The native floating keyboard stays available and dispatches keys to the player's
input view instead of the editor. Held keys are released before refresh/close.
HTML custom fullscreen replaces only the content of the player's FrameLayout,
leaving the native rail outside it. There is no horizontal player toolbar.
Closing removes the player, destroys its WebView and private temporary file,
then restores Home and the scroll slider. Android Back also closes the player first.
No Activity is launched for playback. The obsolete PackagedPlayerActivity is removed.

The player does not pause timers globally, because WebView.pauseTimers affects
other WebViews too. Browser/Electron use a sandbox iframe and left icon column.
Checkpoint before this step: fd6c9c3 in Android and cc95399 in the GUI.

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

## Native Home phone import

Home → My Projects → Load from phone opens ACTION_OPEN_DOCUMENT immediately.
Cancellation stays in the library. The selected URI is streamed on Dispatchers.IO
into private staging, then committed through ProjectRepository into
files/scratch_projects with collision-safe naming. The library refreshes and
shows the newest card. Import never launches an editor automatically: only a user
click on a saved project opens the engine chooser, then queues the chosen engine.
The original phone file is not moved or removed. The completed import survives
Activity destruction and uses the existing binary /projects/ handler, avoiding
the failed temporary /imports/ fetch observed in a cold-start device test.
The editor's own file picker remains unchanged.

Validation: cold-start device tests on 2412DPC0AG passed the actual Home library,
phone picker interception, persistent import, no automatic editor/engine launch,
explicit card click and both engine choices, and confirmed the project title and
SHOWING_WITHOUT_ID state in both Scratch and Turbo. Local unit tests also passed.

The original Scratch high-quality-run button is also authorized to use the
packaged HTML bridge: only main frames under the exact appassets origin and
assets/build/ or assets/turbobuild/ can export. Generated HTML remains on the
isolated player origin without bridges. Refresh accepts only the exact current
private player URL; arbitrary main-frame navigation is blocked.


## Saved projects: SB3 and HTML

Home → My Projects opens the SB3 tab by default. A segmented tab row switches to
HTML, whose cards offer exactly Play and Share. The editor's SB3 upload chooser
keeps its SB3-only purpose. The landscape layout has adaptive preview heights;
cards can scroll vertically on very short screens so actions stay accessible.

PackagedHtmlRepository atomically commits completed HTML saves via a temporary
file and assigns unique collision names; it never overwrites existing projects.
Partial files and directories are excluded from the library. The private path is
files/packaged_projects and its FileProvider grant is read-only per sharing intent.
Save and playback copies stream on background I/O threads. Loading a saved HTML
from Home uses ScratchScreen with playbackOnly=true and never mounts an editor
WebView. A UUID cache copy is passed to PackagedHtmlPlayer; close destroys that
WebView and deletes only its cache directory. The saved library file survives.
Closing playback returns to the HTML library tab; reopening My Projects manually
starts on SB3. Storage/download files from previous versions are not scanned.

Tests: PackagedHtmlRepositoryTest covers complete-file listing, collision safety,
copy ownership and persistence after player-cache cleanup. HtmlLibraryDeviceTest
covers default SB3 tab, HTML tab, native sharing, bridge-free playback without an
editor, and preservation of saved HTML on close. Device coverage requires ADB.

Validation for the private HTML library: 156 Android unit tests passed, including
persistence/collision/cache-copy tests. Physical Android 16 device tests passed
for default SB3/HTML tabs, native sharing, bridge-free library playback without
loading an editor, and preservation of saved HTML after close. The manual
packager device test also passed offline build, private Save, native player,
keyboard routing, fullscreen rail, refresh and editor restoration. During HTML
custom fullscreen the player keeps its WebView attached and overlays the custom
view; refreshing returns to that same WebView instead of detaching its renderer.
Browser bridge tests in fa/ar/en cover the transfer and library acknowledgement.

Both editor File menus now provide the same local HTML packaging workflow.
The toolbar quality-run action exists in both editors and skips the manual
five-second indicator. The updated web builds were copied to build/ and
turbobuild/ raw assets, and the combined APK was built and installed on the test
device. All 3,301 logical asset files were byte-compared against the APK through
its shared-media manifest (2,892 shared references).
