# P4 Files automatic update flow

## Current implementation

```text
App start (5 s delay; at most once per 24 h) / Update button
  → fixed HTTPS GitHub latest/update.json
  → validate package, increasing version, minimum SDK, exact versioned Release URL,
    APK size limit, and installed signer pin
  → resumable download into app-specific Updates/
  → verify exact size, APK SHA-256, archive package/version/minSdk/signer
  → narrow non-exported FileProvider content:// URI
  → Android package installer (user approval required)
  → on return, confirm installed versionCode and signer
```

The user can instead choose a document location or save a verified copy to public Download/USB. A completed save is reported as a save, not as a successful installation. The updater never requests silent installation.

## Evidence labels

- **[APK 정적 확인]** Source and manifest declare package `com.polestar4.drivefilemanager`, `minSdk 26`, `targetSdk 35`; the updater endpoint is hardcoded to the public `jeonbj005-tech/p4-files` Release.
- **[APK 정적 확인]** v1.45 is versionCode 55 / versionName 1.45 and the artifact is signed with the same pinned certificate as the previously distributed build.
- **[에뮬레이터 확인]** API 31 generic AVD installed/launched v1.45 without a crash; the Update button read the public latest manifest and reported v1.45 (55) as current.
- **[에뮬레이터 확인]** v1.44 parsed the expanded manifest and detected v1.45, but download failed with `ENOENT` under its app-specific Downloads path.
- **[소스 정적 확인 + 에뮬레이터 재현]** v1.44 downloaded into `Downloads/P4-Files-v1.45.apk`, then copied that same file onto itself with overwrite enabled. v1.45 stores the verified APK under `Updates/`, removing the source/destination collision.
- **[미검증]** Vehicle access to GitHub, Range resume behavior through the car network, source-install AppOp, package-installer policy, and install result on Polestar 4.
- **[제한]** v1.44 cannot receive the fix through its own updater; the first v1.45 install from v1.44 may require direct GitHub APK installation or USB sideload. Later updates use the new flow.

## Permission review

The baseline is the PolDock v1.3.17 manifest. “Declared” is a source-manifest check; actual runtime grants and vehicle AppOps were not queried.

| PolDock v1.3.17 permission | P4 Files declared | Updater needs it | Actual vehicle grant |
|---|---:|---:|---|
| SYSTEM_ALERT_WINDOW | No | No | Not checked |
| FOREGROUND_SERVICE | Yes | No, updater check is short and download is app worker | Not checked |
| RECEIVE_BOOT_COMPLETED | No | No | Not checked |
| INTERNET | Yes | Yes | Not checked |
| CAMERA | No | No | Not checked |
| REQUEST_INSTALL_PACKAGES | Yes | Only for system-approved package install flow | AppOp not checked |
| POST_NOTIFICATIONS | Yes | No for update download UI | Runtime grant not checked |
| WRITE_EXTERNAL_STORAGE (maxSdk 28) | No | No; MediaStore/SAF used | Not checked |
| ACCESS_FINE_LOCATION | No | No | Not checked |
| ACCESS_COARSE_LOCATION | No | No | Not checked |
| CAR_POWERTRAIN | No | No | Not checked |
| CAR_EXTERIOR_ENVIRONMENT | No | No | Not checked |
| CAR_INFO | No | No | Not checked |
| CAR_SPEED | No | No | Not checked |
| CAR_ENERGY | No | No | Not checked |
| CAR_VENDOR_EXTENSION | No | No | Not checked |
| FOREGROUND_SERVICE_DATA_SYNC (P4 Files extra) | Yes | No for updater | Not checked |
| INSTALL_PACKAGES / MANAGE_DOCUMENTS | No | No; privileged bypass not used | Not checked |

No permission was added for this updater change. The APK installer remains controlled by Android and vehicle policy.

## API compatibility

- **[APK 정적 확인]** `minSdk 26` supports `canRequestPackageInstalls()` and the existing unknown-app-source settings flow. Signing certificate extraction uses `signingInfo` on API 28+ and the legacy signature field on API 26–27.
- **[APK 정적 확인]** APK archive version uses `longVersionCode` on API 28+ with the API 26–27 fallback. Update delivery uses an app-specific external-files directory and a narrowly scoped, non-exported FileProvider.
- **[APK 정적 확인]** Build uses compileSdk 35. API 36 compilation/runtime was not checked in this release.
- **[미검증]** Polestar 4 Android 12 / API 32 vendor installer behavior. `ACTION_INSTALL_PACKAGE` is retained for compatibility, but Android documents it as deprecated from API 29; a later migration to PackageInstaller sessions needs vehicle verification.

## Test matrix

No automated test suite was run. Build/lint and the emulator UI checks above are separate from vehicle validation.

| Scenario | Expected result | Status |
|---|---|---|
| First install or current version | No update prompt | Not run |
| v1.44 to v1.45 with same signer | v1.44 detects the update; its download fails from same-path overwrite (reproduced) | Failed on API 31 AVD |
| v1.45 latest-version check | Shows v1.45 (55) as current | Passed on API 31 AVD |
| Wrong package/version/minimum API | Reject before install | Not run |
| Wrong APK SHA/size/certificate | Reject and remove invalid partial | Not run |
| Interrupted download with valid Range response | Resume and verify complete APK | Not run |
| Server ignores Range or returns wrong Content-Range | Restart or reject; never append corrupt bytes | Not run |
| Offline/HTTP error | No install prompt; next launch can retry | Not run |
| Install source disabled / settings blocked | Offer verified Download or user-selected storage | Not run |
| User cancels or vehicle policy rejects installer | Do not report installed; preserve a verified save path | Not run |
| App returns after accepted install | Confirm versionCode and signer | Not run |
| API 32 vehicle and API 36 device | Same-signature update flow | Not run |

## Remaining risks

- GitHub must remain publicly reachable from the car, and the Release must contain matching `update.json` and versioned APK assets.
- A same-signer APK can still be blocked by Android Automotive policy or unknown-source restrictions.
- App-specific APK storage may be cleared if the app is uninstalled; public Download/USB is the durable fallback.
- The `ACTION_INSTALL_PACKAGE` compatibility flow and Drive/USB file interactions need in-car verification.
- Existing v1.44 installs need a one-time sideload to get past its same-path copy bug; v1.45 cannot repair the downloader in an app version that has not yet been installed.
