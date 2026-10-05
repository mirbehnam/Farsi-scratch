# Farsi Scratch: in-app courses

The first-page course cards group the final bold price, gray struck-through comparison price, red discount-percent badge and configured price difference. The countdown is a compact panel capped at 240 dp immediately below the price/action row, not a full-width strip or squeezed beside the purchase button. Its heading is always «پایان تخفیف». It shows numbered Persian day/hour/minute tiles; in the final day these switch to hour/minute/second tiles. The last six hours get a stronger red treatment without changing the heading. The course-details purchase pane uses the same compact price treatment without the timer to preserve landscape space.

The timer uses the server's absolute `discount_ends_at`, only for valid, unowned discounts with a deadline; no deadline means no invented countdown. It updates once per minute until the final day, then once per second while visible, refreshes on resume, and disappears at expiry. All discount percentages are floored, never inflated to 100% for a nonzero price. Configured comparison prices are display references, not independently verified historical market prices. This is display-only: no periodic API requests, resetting countdown, fake inventory/progress, automatic price increase or changed store checkout.

Research references (reviewed 2026-10-05): [Baymard pricing/discount research](https://baymard.com/research-articles/product-page-price-discounts) recommends prominent final prices, colocated offers and explicit relative/absolute savings; [Baymard sale UX](https://baymard.com/research-articles/10-sales-ux-best-practices) emphasizes instantly recognizable sale prices; [Amazon Lightning Deals](https://www.aboutamazon.co.uk/news/retail/amazon-lightning-deals) documents a red offer badge and prominent nearby final-hours timer; [Shopify urgency guidance](https://www.shopify.com/blog/using-scarcity-urgency-increase-sales) discusses timed offers. These informed the visual design, not an untested promise of increased conversion.

Display discounts use optional `compare_at_toman` and UTC `discount_ends_at` from the backend. Prices are exclusively Toman (`currency: IRT`). A struck-through price appears only above the main price and before expiry; expiry removes only that display, even on an open/cached screen. Store checkout amounts remain store-controlled.

## Structure

`app/src/main/java/ir/behnamapps/fascratch/inappbilling/` is the independent feature directory:

- domain: models, billing/backend/storage interfaces, BuyCourse use case, metadata/URL policy.
- data: fixed HTTPS CourseApi, Keystore-encrypted PurchaseVault, atomic catalog cache, private resumable LessonDownloads.
- presentation: TrainingActivity, RTL Compose screen and TrainingController orchestration.
- flavor source sets provide the same StoreBillingFactory interface, backed by official Poolakey (bazaar) or Myket IabHelper (myket). Website contains no payment SDK and explicitly disables buying.

Existing project/editor features are unchanged except the HomeScreen آموزش button launching the non-exported TrainingActivity.

## Setup

- Initial SKU is **scratch_basic** in both stores. Each published course now uses its own active SKU from the server for the selected store; the catalog is not filtered to BuildConfig.COURSE_SKU.
- Server base URL: `https://api.behnamapp.ir/scratch/v1`, configured in BuildConfig.COURSE_API_BASE.
- Package ID remains `ir.behnamapps.fascratch` for all existing flavors. Confirm it matches publisher and server settings. No suffix is introduced that would break store receipt matching.
- Public RSA keys supplied by the owner are compiled into the appropriate flavor. These are public verification keys, not private API secrets. They can be overridden by MYKET_BILLING_PUBLIC_KEY / BAZAAR_BILLING_PUBLIC_KEY Gradle properties. Never put the private server API key into the app.
- Bazaar SDK: `com.github.cafebazaar.Poolakey:poolakey:2.2.0`; Myket SDK: `com.github.myketstore:myket-billing-client:1.19`. They are flavor-specific, not bundled together. JitPack repository is restricted to these groups.
- Google Play Billing / Play Integrity is not used. The app already uses Firebase; Myket SDK has its own transitive dependencies. Those are not store identity proofs. Real no-GMS device testing remains necessary.
- Deploy the server purchase migration and enable provider configuration first. Publish each course and associate its active store SKU. Full API contract: [SCRATCH_BACKEND_API.md](SCRATCH_BACKEND_API.md).
- Installed store, current store login, matching app package/signature and published/available SKU are needed for live purchases. Debug signatures may not be accepted by the publisher configuration. Do not claim unit tests prove real purchasing works.

## Purchase flow

Display prices now come exclusively from backend `product_prices[provider].amount_toman`, matched to the course SKU. Store SDK price queries have been removed. Missing price is not zero; fresh course-price metadata is required before starting a new purchase. Panel amounts are Toman (750,000 Rial = 75,000 Toman); actual checkout amounts remain controlled by the store and must be configured consistently. Android checks validated Internet before purchase/restore and displays a retry/dismiss dialog offline. See SCRATCH_BACKEND_API.md for the updated contract.

Opening آموزش loads a responsive course catalog; a single course uses a horizontal card on wide screens. Cards show real metadata and server prices and lead to course details before payment. Price refresh and store ownership checks do not block previews. Selecting a course opens a landscape purchase/summary pane beside the curriculum. Only lesson difficulty badges are displayed. Existing owned purchases are submitted to `/purchases/restore`. Clicking خرید queries ownership **before** opening payment; a failed query never means "unowned" and never starts a second payment. بازیابی خرید does not launch payment if ownership is missing.

Published free-preview lessons download through fresh signed same-origin URLs without a purchase token. Cached signed URLs are not reused. Free-preview files are playable without paid entitlement; paid lessons still require course-specific verified access. Catalog cache is multi-course and lesson caches are isolated by course UUID, with a fallback for the old single-course cache.

SDK receipt is persisted encrypted before server verification, protecting against process death/outages after paying. Only server success matching the selected course saves access. Receipt/server access tokens are never logged. Courses are non-consumable: no consume call is present in either adapter.

Server credentials expire after one hour. When needed, the app re-queries store ownership and restores access. 401/403 during download triggers one restore/retry. A known rejected/refunded receipt clears the locally confirmed entitlement. Server timeout/outage does not label the purchase fraudulent and does not discard a paid receipt.

The server uses receipt-based bearer access, **not store user IDs**. A receipt can be shared; this architecture does not stop a purchaser leaking it. Phone/account linking is not implemented. Server restores rotate a receipt's credential, so another device restoring the same receipt invalidates the earlier credential; the app can restore again.

## Downloads and offline use

Server administration can disable a verified receipt independently of its store status. Restore returns HTTP 403 with `error.code = access_disabled`; the app shows a support message and revokes the locally verified flag when that response is received. Restoring never bypasses an admin block. Re-enabling requires a subsequent successful store/server restoration. Existing offline files cannot be instantly revoked on disconnected devices; receipt tokens and local files are not erased by an admin block.

User downloads each lesson separately, one at a time; no automatic bulk download. Progress derives from bytes read and stays below 100% until hash verification and atomic completion. SHA-256, byte count and Content-Range are checked. Finished files use UUID/version/hash names and are saved privately under noBackupFilesDir, not Downloads/public storage.

Partial `.part` files survive interruptions. Next download resumes using HTTP Range; a server ignoring Range causes safe restart. Hash mismatch removes the corrupt partial. Low disk space is reported. Leaving/destroying the training activity cancels the foreground task; **background downloading/foreground service is not implemented**. Return and tap download again to resume. Stop leaves the partial file for resumption.

Completed lessons play inside the app via MediaPlayer/TextureView with fullscreen controls; no public URL or exported video activity is created. Deleting a file does not delete the store purchase. Cache permits offline display and playback of already-downloaded lessons after a prior server confirmation; token expiry does not remove offline lessons. Unknown/not-yet-confirmed purchases never unlock playback. Known rejection/refund locks playback; offline revocation cannot be detected until reconnecting. New content versions have different files; old versions are not automatically deleted.

## Landscape course UI and fullscreen player

The whole training activity is immersive landscape, not just the video. System bars remain temporarily reachable by swiping from an edge. The UI uses warm ivory, forest green, Shabnam typography and RTL reading order. Catalog cards are compact, with a small cover, title, instructor, price and مشاهده دوره action; promotional headings and repeated benefit copy are omitted. Details open directly on the curriculum without a large cover. Course descriptions are behind دربارهٔ دوره. Purchase/summary and curriculum scroll independently. In the side panel, title, metadata, expanded description, server price, price refresh and restore controls share one bounded scrolling body; only the 60 dp minimum خرید دوره button is anchored below it. Variable-height pricing can no longer squeeze the summary viewport to zero. Selecting another course resets the panel scroll. Below 580 dp width, or when height is less than 240 dp multiplied by system font scale, the purchase panel moves into the scrolling curriculum; that inline panel has no nested unbounded scroller. The curriculum uses one column on phones and two when its own pane is at least 740 dp wide.

All/free/downloaded filters, section headers, cover/poster artwork, instructor, lesson difficulty, duration, download progress and delete confirmation are provided. The preview filter appears only when actual free lessons exist, and downloading a preview never opens payment. Lesson descriptions have explicit expand/collapse controls. Download counts describe files, not learning completion. Purchase stays disabled until a store price is available; refresh retries fetching prices. Website builds explain that purchase requires a store version. Owned courses show access status instead of another purchase CTA. No fabricated discounts, countdowns, ratings, guarantees or enrollment claims are displayed.

Empty/error/loading states are distinct; missing artwork uses locally drawn abstract programming blocks. Public HTTPS artwork is same-origin, bounded, sampled to at most 1024px, and memory-cached (8 MiB); credentials are never sent with it. Course/lesson metadata is cached for offline display.

`stats.confirmed_purchases` from the server is explicitly labeled **confirmed purchases**, not unique students. Receipt restoration does not increment it; refunded/rejected purchases are excluded. No invented enrollment/review/rating data is shown. Real unique student totals need student accounts/identity linkage in a future phase.

The fullscreen player preserves video aspect ratio and offers pinch zoom 1–4x, bounded panning, double-tap/reset to original fit, seek bar, play/pause, ±10 second seeking and elapsed/total time. Controls hide after four idle playing seconds and reappear with a tap. Playback pauses on backgrounding or audio focus loss; returning does not automatically start it. Playback resources/audio focus and keep-awake flags are released on exit. Returning to lessons keeps the training page immersive.

Manual device QA: small and large landscape screens and large system fonts; image/video cover posters; locked/purchased/downloading/offline states; purchase return from each store; two-finger zoom/pan, reset, seek, end-of-video, background during prepare/play, audio interruption, repeated player entry/exit and transient navigation bars. JVM geometry tests do not substitute for touch/render testing on an Android device.

## Verification

```powershell
.\gradlew.bat :app:testBazaarDebugUnitTest :app:testMyketDebugUnitTest
.\gradlew.bat :app:assembleBazaarDebug :app:assembleMyketDebug :app:assembleWebsiteDebug
```

Tests cover retry-without-charge, failed inventory, receipt durability through server outage, receipt/course mismatch, secret-safe toString and safe file metadata/HTTPS origin checks. They use fake gateways/backends and are not live financial transactions.

Manually on both stores: new purchase, already-owned restore, cancellation, server outage after payment, restart/reinstall, expired credential renewal, private lesson downloads, interrupted resume, corrupt hash, offline playback, delete/redownload and refund. Confirm no duplicate charge, consume or plaintext credential in logs/backup.

Official references:

- https://github.com/cafebazaar/Poolakey/wiki
- https://myket.ir/kb/pages/java/
- https://github.com/myketstore/myket-billing-client
