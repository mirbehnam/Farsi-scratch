# Downloaded sprite library

Both custom editors show a **Downloaded / دانلودی‌ها / تم تنزيلها** category in the sprite library. Only `.sprite3` archives are supported; selecting an entry imports the entire sprite, including costumes, sounds and blocks. Downloaded names use explicit locale metadata; imported archive contents remain intact apart from the selected target name. The catalog is refreshed each time the sprite library opens. Browser-only builds have no host folder access.

## Android host

The host creates `filesDir/downloaded_sprites` (private internal app storage). A future downloader should write to a temporary `.part` file in that directory, close it, then atomically rename it to `.sprite3`. No manual import UI or downloader was added. Do not put this persistent data in APK assets, editor build folders or disposable playback storage. Uninstalling the Android app removes its private data.

`DownloadedSpritesPathHandler` exposes read-only local HTTPS resources at `https://appassets.androidplatform.net/downloaded-sprites/`: `catalog.json`, `sprite/<encoded filename>` and `preview/<encoded filename>`. Canonical parent validation confines access to this folder. The preview is the first costume in the ZIP; metadata is limited to 1 MiB and preview data to 16 MiB. Invalid archives are skipped with diagnostic logs. Versioned resource URLs change when the archive changes. Native HTML playback remains bridge-free.

## Windows host

The Electron host creates **downloaded_sprites** alongside its executable, inside the installation directory. Installers preserve the directory on uninstall. Place `.sprite3` files directly inside it, then reopen the sprite library. No recursive scanning is performed. The renderer accesses list/read operations through the main-frame-only `desktopDownloadedSprites` preload API; Node integration remains disabled. Only regular sprite archives up to 128 MiB can be read, and path traversal and symbolic links are rejected.

Both Windows installers include `iran-cat.sprite3`, a 100×100 SVG costume wrapping the generated transparent PNG without raster modification. Source image and generation prompt are documented in `../auto-builder/sample-assets/README.md`. The example is stored in each editor's `desktop/downloaded-sprite-examples/` and copied into the Windows output by `scripts/build-windows.mjs`. It is not a new default sprite.

## Build and maintenance

Keep this contract consistent in both editor repositories and the Android host. Existing Android raw-build copying and host-owned shared-media deduplication are unchanged. Never delete private downloaded sprites when replacing editor assets. `npm run build:android` produces offline web assets and both Windows architectures using the existing commands; Windows gets its own folder and preload, not Android storage paths.

Relevant checks: GUI catalog/import unit tests; desktop file-access tests; real Electron x64/ia32 category, thumbnail and full-archive import; Android `DownloadedSpritesDeviceTest` for both engines. With Android product flavors, use the selected variant, e.g. `:app:assembleWebsiteDebug` and `:app:assembleWebsiteDebugAndroidTest`.

## Validation for this implementation

- Both final Windows x64 and ia32 installers built successfully for both editors. Actual unpacked Electron applications passed category selection, loaded thumbnail, full sprite import and 100×100 costume checks on all four combinations; no renderer page errors were reported. Installer execution into a user profile was not tested.
- Android Website Debug APK built, copied with both fresh raw editor builds and installed on device 2412DPC0AG. `DownloadedSpritesDeviceTest` passed for both editor engines, including native catalog/archive/preview access, traversal rejection, category filtering and VM import. The device test requests eager thumbnail loading to avoid waiting on offscreen lazy images while the phone is locked.
- Original GUI Jest tests: 2 passed. Turbo Node tests: 2 passed, including desktop traversal checks. Scoped ESLint: no errors (original retains arrow-parens warnings).
- Original i18n extraction succeeded. Turbo extraction remains blocked by the existing duplicate `gui.menuBar.downloadToComputer` message; the new downloaded-library messages are defined and Persian/Arabic overrides are included in the built editor.

## Downloaded backdrops and sounds

The same host-only Downloaded category now exists in the backdrop and sound libraries of both editors. Backdrops accept `.svg`, `.png`, `.jpg`, `.jpeg`; sounds accept `.wav`, `.mp3`. Only direct files are indexed. English filenames identify items; display names come from optional locale metadata. Files are imported through the existing costume/sound upload pipeline, so project storage contains the media after selection. Sound previews also read local bytes. Refresh occurs when reopening the library. No downloader or manual import UI was added.

| Library | Android private directory | Windows folder beside executable |
|---|---|---|
| Backdrops | `filesDir/downloaded_backdrops` | `downloaded_backdrops` |
| Sounds | `filesDir/downloaded_sounds` | `downloaded_sounds` |

The downloader should write a `.part` file and atomically rename it after completion. These directories are persistent data, separate from raw editor builds and shared APK assets. Never clean them during builds, asset replacement or playback teardown. Windows installers create and preserve both directories.

Android exposes read-only `/downloaded-backdrops/catalog.json`, `/downloaded-sounds/catalog.json`, and corresponding `file/<encoded filename>` URLs on the appassets HTTPS origin. Each file is confined to its canonical parent and limited to 128 MiB. Windows extends the existing main-frame-only downloaded-sprites IPC with `kind: backdrops|sounds`; omitted kind still means sprites. No new bridge is installed in HTML playback.

See `DownloadedMediaPathHandler.kt`, `src/lib/downloaded-media.js`, and `desktop/downloaded-sprites.cjs`. Auto Builder commands and raw-asset/deduplication contracts do not change. Build the updated host as well as both editors to deploy this feature.

### راهنمای استفاده از پس‌زمینه و صدای دانلودی

در ویندوز فایل تصویر را در پوشهٔ «downloaded_backdrops» و فایل صوتی را در «downloaded_sounds» کنار فایل اجرایی برنامه بگذارید. در اندروید دانلودکننده باید فایل‌ها را به‌ترتیب در `filesDir/downloaded_backdrops` و `filesDir/downloaded_sounds` ذخیره کند. پس از بازکردن مجدد کتابخانهٔ پس‌زمینه یا صدا، دستهٔ «دانلودی‌ها» را انتخاب کنید.

تصویرهای SVG/PNG/JPG/JPEG و صداهای WAV/MP3 پشتیبانی می‌شوند. فایل‌ها باید مستقیماً در پوشه باشند؛ زیرپوشه‌ها خوانده نمی‌شوند. نام نمایشی از ترجمه‌های names.json یا نام انگلیسی فایل گرفته می‌شود. انتخاب هر فایل، دادهٔ آن را وارد پروژه می‌کند؛ برای بازکردن پروژهٔ ذخیره‌شده نیازی به وجود فایل اولیه در پوشه نیست. فایل خراب ممکن است هنگام پیش‌نمایش یا واردکردن قابل استفاده نباشد.

### Media extension validation (2026-11-06)

Desktop filesystem isolation and import-contract tests passed in both repositories. Real x64 Electron tests successfully selected the Downloaded category and imported a local SVG backdrop and WAV sound in both editors. Android native catalog/read/traversal instrumentation passed; the updated Website Debug APK built and was installed with fresh editor assets. This does not claim a full Android library UI test for the media extension. Original scoped lint has no errors; Turbo retains its existing `react/no-did-mount-set-state` finding in the sound library.

The engine import APIs differ: original `costumeUpload` takes storage; Turbo takes the VM. Preserve this distinction. The new import-contract test covers both signatures. Existing sprite behavior is unchanged.

## English filenames and localized display names

All host folder names are now `downloaded_sprites`, `downloaded_backdrops`, `downloaded_sounds`. Android already used these names. Windows migrates regular files from the older Persian folders without overwriting conflicts; conflicting files remain in the old folder with a warning. Filenames should be stable English identifiers (`iran-cat.sprite3`, `persepolis.png`).

Each folder may contain an optional UTF-8 `names.json` (maximum 1 MiB), keyed by the exact filename:

```json
{
  "persepolis.png": {"en": "Persepolis", "fa": "تخت جمشید", "ar": "تخت جمشيد"}
}
```

The downloader owns this metadata and should update it atomically alongside completed downloads. Names are not machine-translated. Missing language translations fall back to English, then the filename without extension. The sample Iran Cat and Persepolis names have built-in translations. A locale change updates visible library names; selection imports the displayed name, including the sprite target name, without changing the downloaded archive on disk. Existing project names are not retroactively renamed. No other sprite archive entries are changed.

Downloaded entries appear first in All, ahead of built-in media (and Turbo favorites). The Downloaded tag is inserted immediately after All only when that library has at least one downloaded item.

Validation of names and ordering: five desktop unit checks passed per editor (locale selection/fallback, archive preservation and localized target name, ordering, folder isolation, migration and manifest access). Real x64 Electron checks passed in both editors for Persian/English/Arabic live names, downloaded-first display and localized sprite import. Android instrumentation passed reading multilingual names from the private folder manifest.


## Content store integration (2026-11-06)

The native Home footer now opens the content store beside the creator credit. Packages may mix all three existing library types; no editor bridge, host directory or engine build contract changed. Paid ownership belongs to the existing server account; free/ad downloads do not create permanent purchases. A successful coin purchase starts download automatically. My Purchases in the existing showcase hall lists only coin-owned packages; Wealth sums the existing server coin-spend ledger across all features.

ContentPackageInstaller validates the bundle and each manifest entry, stages the whole package, journals the named files, and installs only store-<item UUID>-<file UUID>.<extension> in the three persistent host folders. It atomically merges en/fa/ar names.json entries and rolls back an interrupted installation before libraries reopen. Removing a package deletes only its own files and restores/removes its own metadata keys. It never removes manual downloads. Engine assets are unchanged; reopen the appropriate library to see new content. Installed files survive application updates.

Operations retain their UUID and download grant across connection failures. Coin ownership survives download failure and uninstall on the server; sign in to the same registered account to restore it. Ad grants and installed store files are excluded from Android backup/device transfer so ad access does not become permanent through backup. Killing the process may require retrying the download; completed owned purchases are not charged again. Signed URLs refresh using the original operation/ad grant while it remains valid.

The dedicated Adivery placement is 969b9b33-4668-43dc-bab0-2efe5574fffb. The existing ad manager and server callback policy are reused. Production S2S delivery for this new placement must be tested after the backend migration and a test package are deployed.

Validation on phone 2412DPC0AG (AMLZSW75XOHQRSFU): Website Debug and test APKs built and installed with data preserved. ContentStorePackageDeviceTest (3 cases), DownloadedMediaDeviceTest and DownloadedSpritesDeviceTest passed (5 total). Checks cover mixed-package installation, names preservation, duplicate prevention, deletion isolation, archive/account mismatch, journal recovery, native media catalog, and sprite import in both engines. Existing library test fixtures now supply names.json and select the actual library button to match the updated localized-name contract. Full production-network purchases/ads require deployment; no claim of that test is made here.
