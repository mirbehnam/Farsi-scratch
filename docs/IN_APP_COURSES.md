# Farsi Scratch: in-app courses

## Structure

`app/src/main/java/ir/behnamapps/fascratch/inappbilling/` is the independent feature directory:

- domain: models, billing/backend/storage interfaces, BuyCourse use case, metadata/URL policy.
- data: fixed HTTPS CourseApi, Keystore-encrypted PurchaseVault, atomic catalog cache, private resumable LessonDownloads.
- presentation: TrainingActivity, RTL Compose screen and TrainingController orchestration.
- flavor source sets provide the same StoreBillingFactory interface, backed by official Poolakey (bazaar) or Myket IabHelper (myket). Website contains no payment SDK and explicitly disables buying.

Existing project/editor features are unchanged except the HomeScreen آموزش button launching the non-exported TrainingActivity.

## Setup

- Product SKU is **scratch_basic** in both stores.
- Server base URL: `https://api.behnamapp.ir/scratch/v1`, configured in BuildConfig.COURSE_API_BASE.
- Package ID remains `ir.behnamapps.fascratch` for all existing flavors. Confirm it matches publisher and server settings. No suffix is introduced that would break store receipt matching.
- Public RSA keys supplied by the owner are compiled into the appropriate flavor. These are public verification keys, not private API secrets. They can be overridden by MYKET_BILLING_PUBLIC_KEY / BAZAAR_BILLING_PUBLIC_KEY Gradle properties. Never put the private server API key into the app.
- Bazaar SDK: `com.github.cafebazaar.Poolakey:poolakey:2.2.0`; Myket SDK: `com.github.myketstore:myket-billing-client:1.19`. They are flavor-specific, not bundled together. JitPack repository is restricted to these groups.
- Google Play Billing / Play Integrity is not used. The app already uses Firebase; Myket SDK has its own transitive dependencies. Those are not store identity proofs. Real no-GMS device testing remains necessary.
- Deploy the server purchase migration and enable provider configuration first. Publish the course and associate its active SKU scratch_basic with each store. The app selects the published course mapped to this SKU, not a hardcoded course UUID.
- Installed store, current store login, matching app package/signature and published/available SKU are needed for live purchases. Debug signatures may not be accepted by the publisher configuration. Do not claim unit tests prove real purchasing works.

## Purchase flow

Opening آموزش loads published course/sections/lessons, store price and prior ownership. Existing owned purchases are submitted to `/purchases/restore`. Clicking خرید دوره queries ownership **before** opening payment; a failed query never means "unowned" and never starts a second payment. بازیابی خرید does not launch payment if ownership is missing.

SDK receipt is persisted encrypted before server verification, protecting against process death/outages after paying. Only server success matching the selected course saves access. Receipt/server access tokens are never logged. Courses are non-consumable: no consume call is present in either adapter.

Server credentials expire after one hour. When needed, the app re-queries store ownership and restores access. 401/403 during download triggers one restore/retry. A known rejected/refunded receipt clears the locally confirmed entitlement. Server timeout/outage does not label the purchase fraudulent and does not discard a paid receipt.

The server uses receipt-based bearer access, **not store user IDs**. A receipt can be shared; this architecture does not stop a purchaser leaking it. Phone/account linking is not implemented. Server restores rotate a receipt's credential, so another device restoring the same receipt invalidates the earlier credential; the app can restore again.

## Downloads and offline use

User downloads each lesson separately, one at a time; no automatic bulk download. Progress derives from bytes read and stays below 100% until hash verification and atomic completion. SHA-256, byte count and Content-Range are checked. Finished files use UUID/version/hash names and are saved privately under noBackupFilesDir, not Downloads/public storage.

Partial `.part` files survive interruptions. Next download resumes using HTTP Range; a server ignoring Range causes safe restart. Hash mismatch removes the corrupt partial. Low disk space is reported. Leaving/destroying the training activity cancels the foreground task; **background downloading/foreground service is not implemented**. Return and tap download again to resume. Stop leaves the partial file for resumption.

Completed lessons play inside the app via VideoView/MediaController; no public URL or exported video activity is created. Deleting a file does not delete the store purchase. Cache permits offline display and playback of already-downloaded lessons after a prior server confirmation; token expiry does not remove offline lessons. Unknown/not-yet-confirmed purchases never unlock playback. Known rejection/refund locks playback; offline revocation cannot be detected until reconnecting. New content versions have different files; old versions are not automatically deleted.

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
