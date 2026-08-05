# Publishing Solid Share

*Part of the [Solid Share documentation set](README.md).*

Working checklist for shipping to **Google Play** and **F-Droid**. Tick items as they land. Nothing
here is committed as done until it is verified against the store's *current* policy — several of
these requirements move on annual deadlines, and the notes below mark the ones worth re-reading
before each release.

---

## 1. The two distributions

One dimension, `distribution`, with two flavours:

| Flavour | Store | Telemetry | Firebase config |
|---|---|---|---|
| `gms` | Google Play | Crashlytics, Analytics, Performance | `app/src/gms/google-services.json` (never committed) |
| `foss` | F-Droid, other FOSS stores | none — no proprietary SDK is linked | not present, not needed |

The split is a source-set split, not a runtime flag:

- `main/` declares `AuthAnalytics` and `TelemetryInstaller` as interfaces carrying no monitoring types.
- `gms/` binds `FirebaseAuthAnalytics` + `FirebaseTelemetryInstaller`, and provides the Firebase singletons.
- `foss/` binds `NoAuthAnalytics` + `NoTelemetryInstaller`, both of which discard everything.

So the FOSS APK contains no Firebase classes at all — which is what F-Droid's inclusion policy
requires, and what keeps an Exodus Privacy scan clean.

**Barcode decoding is not flavoured.** Both builds decode with `io.github.zxing-cpp:android`, which
is free software. ML Kit was removed outright: it is proprietary, pulls
`com.google.android.gms:play-services-{basement,tasks,base}` in transitively, and shipped
`libbarhopper_v3.so` plus `.tflite` models — roughly 20 MB across the four ABIs, in *both* builds.
Its presence would have made the FOSS build F-Droid-ineligible on its own, independently of Firebase.
Verify after any dependency change:

```bash
unzip -l app/build/outputs/apk/foss/release/app-foss-release.apk \
  | grep -iE "barhopper|mlkit|play-services|firebase"   # must return nothing
```

**Build commands**

```bash
./gradlew :app:assembleGmsDebug        # Play, debug
./gradlew :app:bundleGmsRelease        # Play upload artifact (.aab)
./gradlew :app:assembleFossRelease     # what F-Droid's builder reproduces
./gradlew testGmsDebugUnitTest testFossDebugUnitTest
```

---

## 2. Blockers found (do these first)

- [x] **A LICENSE file exists.** MIT, at the repo root, referenced from `README.md`. Note for the
      F-Droid metadata: `License: MIT`.
- [ ] **Google Play contacts-permission declaration.** The app holds `READ_CONTACTS` and
      `WRITE_CONTACTS`. Play treats these as sensitive: expect a Permissions Declaration Form plus a
      **video walkthrough** showing the in-app flow that needs them. This is usually the slowest
      review step — start it early.
- [ ] **Closed-testing requirement.** Personal (non-organisation) Play developer accounts must run a
      closed test with a minimum number of opted-in testers for a continuous period before
      production access is granted. Verify the current thresholds in the Console — they have changed
      more than once. Budget weeks, not days.
- [ ] **Privacy policy URL.** Mandatory for Play, and needed for the F-Droid listing too. Must
      describe: Solid pod data stays on the user's pod; contacts read/written locally and mirrored to
      the pod; Crashlytics/Analytics **in the Play build only**; nothing collected in the FOSS build.
      Written and living in the website repo at `privacy/index.html`, i.e.
      **`https://solidshare.app/privacy`** once the website repo is deployed. The contact and
      data-request address in it is the personal address `erfangholami76@gmail.com`; use the same one
      in the Play Console contact field, since Play checks that the stated contact actually works. If
      a `privacy@solidshare.app` alias is ever set up (Cloudflare Email Routing), swap both. Keep the
      policy and the Data safety form saying the same thing; the permission-by-permission table in
      the policy is the same list the declaration forms need.

---

## 3. Before *any* publish — update the documentation

- [ ] The feature pages reflect what actually ships — walk the ordered index in
      [README.md](README.md) and check each one against the code it describes
- [ ] [README.md](README.md) lists every file in `documents/`, in order, with no stale entries
- [ ] The root `README.md` feature list matches the release, and its documentation section links
      the same set
- [ ] `CHANGELOG` entry for the version (create one if absent — F-Droid users read it)
- [ ] Root `README.md` build instructions cover **both** flavours, and state that `gms` needs your
      own `google-services.json`
- [ ] Screenshots regenerated if the UI changed
- [ ] [TESTING.md](TESTING.md) matches the current test layout
- [ ] ASS library version in `gradle/libs.versions.toml` points at a version **published to Maven
      Central**, not only to `mavenLocal()` — otherwise nobody else can build the app

---

## 4. Shared release preparation

### Versioning

**The tag is the version.** Nothing in the source declares it: `app/build.gradle.kts` resolves it
with `git describe`, and `versionCode` is derived as `major * 10000 + minor * 100 + patch` — so
`1.2.3` is `10203`. The build refuses a version that is not `MAJOR.MINOR.PATCH`, or whose minor or
patch reaches 100 (that would break the ordering both stores require). Check what a tag produces
with `./gradlew -q :app:printVersion`.

Resolution order, first hit wins:

1. `-PappVersionName=0.4.0`, or `APP_VERSION_NAME` in the environment — the escape hatch for a
   build from a source archive with no git metadata.
2. The tag on the commit being built (`git describe --tags --exact-match`). This is what a release
   build resolves to.
3. The most recent tag before it, so a development build reports the release it descends from.
4. `0.0.0`, for a checkout with no tags at all.

This removes the failure mode where a tag and a hardcoded name disagree and the release aborts
half-way. It costs one thing worth knowing: **the build now needs git metadata.** F-Droid builds
from the tag in a real git clone, so (2) answers there; if their builder ever hands over a tree
without `.git`, supply (1) from the metadata's build recipe rather than reintroducing a written-down
version. In CI the `Checkout` step must keep `fetch-depth: 0`, or `git describe` answers with an
older release and the workflow's version check stops the run.

### Cutting a release

- [ ] Nothing to bump — the tag you push at the end *is* the version
- [ ] Confirm `targetSdk` still meets Play's current requirement (it rises roughly annually; the app
      is on 35, `compileSdk` 37 — re-check before each submission)
- [ ] `./gradlew test lint` clean
- [ ] `./gradlew :app:assembleFossRelease :app:bundleGmsRelease` both succeed
- [ ] Install both release builds on a device and smoke-test login, share, contacts sync, wallet
- [ ] Verify the FOSS APK really has no Firebase: `unzip -l app-foss-release.apk | grep -i firebase`
      should return nothing
- [ ] Push the tag: `git tag v0.x.y && git push origin v0.x.y`

Pushing the tag is the whole publish step — it *is* the version, so there is no bump to commit
first. The `Release` workflow resolves the version from the tag, builds both flavours, gates the
FOSS APK for proprietary code, and attaches `solidshare-<version>-foss.apk`, `-gms.apk` and
`-gms.aab` to a GitHub release. Re-run it from the Actions tab with the `tag` input if it fails
after the tag is already pushed.

**Retagging.** A tag that pointed at the wrong commit is moved, not worked around:

```bash
git push origin :refs/tags/v0.x.y   # drop the remote tag
git tag -d v0.x.y                   # and the local one
git tag v0.x.y && git push origin v0.x.y
```

---

## 5. Google Play

### One-time setup
- [ ] Play Console developer account created and verified (identity verification is now required)
- [ ] App created; package `com.erfangholami.solidshare` reserved
- [ ] **Play App Signing** enrolled; upload keystore backed up somewhere safe
- [ ] `keystore.properties` present locally and in CI secrets (never committed)

### Store listing
- [ ] App name (≤30 chars), short description (≤80), full description (≤4000)
- [ ] App icon 512×512, feature graphic 1024×500
- [ ] Phone screenshots (min 2; more is better), tablet screenshots if claiming tablet support
- [ ] Category, contact email, privacy policy URL

### Policy forms
- [ ] **Data safety**: declare crash logs + analytics (Play build), plus contacts handling. Must match
      real behaviour — mis-declaring is a common rejection cause
- [ ] Content rating questionnaire (IARC)
- [ ] Target audience and content
- [ ] Ads: none
- [ ] Permissions declaration for `READ_CONTACTS` / `WRITE_CONTACTS` (see §2)
- [ ] Foreground service justification for `dataSync` (used by the contacts import/export workers)
- [ ] Account deletion: the app creates no accounts of its own — state that Solid identities are
      managed by the user's own pod provider

### Release
- [ ] Upload `app-gms-release.aab` to internal testing first
- [ ] Promote through closed → open → production per the testing requirement
- [ ] Confirm the mapping file uploaded so Crashlytics deobfuscates (the Crashlytics plugin does this
      for `gms` variants only)

---

## 6. F-Droid

F-Droid builds from source on their infrastructure and signs with **their** key. They do not accept
a prebuilt APK for a normal inclusion.

### Prerequisites
- [ ] Public repo with a free licence (§2) and a tagged release
- [ ] Builds offline-ish: no build step that pulls proprietary tooling. The `foss` flavour must not
      pull the Firebase plugins' outputs — the `tasks.matching { it.name.contains("Foss") … }` gate in
      `app/build.gradle.kts` disables the Google plugin tasks for that flavour
- [ ] `app/src/gms/google-services.json` absent from the repo (already gitignored) so a clean clone
      builds `foss` without it
- [ ] Verify a clean clone builds: `git clone` to a temp dir, then `./gradlew :app:assembleFossRelease`

### Store metadata (Fastlane layout, read by F-Droid)
- [ ] `fastlane/metadata/android/en-US/title.txt`
- [ ] `fastlane/metadata/android/en-US/short_description.txt` (≤80)
- [ ] `fastlane/metadata/android/en-US/full_description.txt`
- [ ] `fastlane/metadata/android/en-US/images/icon.png`
- [ ] `fastlane/metadata/android/en-US/images/phoneScreenshots/*.png`
- [ ] `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` — the *derived* code, so
      `0.3.0` files it as `300.txt` (`./gradlew -q :app:printVersion` prints it)

### Submission
- [ ] Fork `fdroiddata`, add `metadata/com.erfangholami.solidshare.yml`
- [ ] In it: `Categories`, `License`, `SourceCode`, `IssueTracker`, `RepoType: git`, and a build entry
      pinned to the tag with `gradle: [foss]`
- [ ] `CurrentVersion` / `CurrentVersionCode` match the tag and its derived code (e.g. `0.3.0` /
      `300`)
- [ ] Open the merge request; expect review iterations
- [ ] Check no anti-features get flagged. The FOSS build should attract none — if `Tracking` appears,
      something proprietary or non-consensual leaked into the flavour

### Optional
- [ ] Reproducible builds — not required for inclusion, but valued. Worth attempting once the app is
      accepted

---

## 7. Secrets and CI

`app/src/gms/google-services.json` is gitignored. Two ways to supply it:

**Locally** — copy `app/src/gms/google-services.json.template`, fill in real values, or download the
file from the Firebase console. Only needed to build `gms`; `foss` builds without it.

**In CI** — store the file base64-encoded as a GitHub Actions secret and write it before the build:

```yaml
- name: Restore Firebase config
  env:
    GOOGLE_SERVICES_JSON: ${{ secrets.GOOGLE_SERVICES_JSON }}
  run: |
    echo "$GOOGLE_SERVICES_JSON" | base64 --decode > app/src/gms/google-services.json

- name: Build Play bundle
  run: ./gradlew :app:bundleGmsRelease
```

Create it with `base64 -i app/src/gms/google-services.json | pbcopy`.

Signing secrets follow the same pattern: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, written to `keystore.properties` in the workflow.

### About the exposed key

The Android API key committed in `af8c4e9` is **still in public git history** — moving the file does
not unpublish it. Firebase Android API keys are designed to ship inside the APK and are not access
credentials, but an *unrestricted* key can be billed against other Google Cloud APIs on the project.

- [ ] Restrict the key in Cloud Console: application restriction = Android app
      (`com.erfangholami.solidshare` + release and debug SHA-1s), API restriction = Firebase APIs only
- [ ] Check enabled APIs and billing on project `solid-share-503813` for unexpected usage
- [ ] Optionally rotate — with the file now gitignored, a new key never enters the repo
- [ ] Dismiss the GitHub secret-scanning alert once restricted

---

## 8. Known cross-store issues

- **Different signing keys.** Play signs via Play App Signing; F-Droid signs with its own key. Same
  `applicationId`, incompatible signatures — a user cannot update across stores, they must uninstall
  and reinstall. This is normal and worth stating in the README.
- **`versionCode` shared.** Both stores read the same derived value, so a version bump is monotonic
  across both by construction. The two ways to break it are to tag *backwards*, or to release
  `0.99.x` and then `0.100.0` — the build blocks the latter.
- **OAuth redirect.** The redirect URI and the hosted Client ID Document are tied to
  `com.erfangholami.solidshare` and `solidshare.app`. Do **not** add an `applicationIdSuffix` per
  flavour — it would break Solid-OIDC login and the App Links verification.
