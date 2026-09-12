# Solid Share

<p align="center">
  <img src="documents/icon.png" alt="Solid Share Logo" width="120">
</p>

**Solid Share** is an open-source Android application that brings the [Solid](https://solidproject.org/) ecosystem to everyday mobile users. It lets people use their Solid pods as a personal data wallet — logging in with multiple accounts, browsing and managing files, and sharing data — all from their Android phone, without needing any technical background.

The goal is to make Solid accessible to regular people: a smooth, familiar mobile experience that puts users in control of their own data.

<p align="center">
  <a href="https://f-droid.org/packages/com.erfangholami.solidshare/"><img src="documents/badges/f-droid.png" alt="Get it on F-Droid" height="80"></a>
  &nbsp;&nbsp;
  <a href="https://play.google.com/store/apps/details?id=com.erfangholami.solidshare"><img src="documents/badges/google-play.png" alt="Get it on Google Play" height="80"></a>
</p>

<img width="14060" height="7908" alt="solid share coverr" src="https://github.com/user-attachments/assets/502e4bfb-cc60-4271-ad32-70a96c41ae4a" />


## Features

Solid Share has no server of its own: the app talks to your pod directly, and nothing you store
passes through us. Everything belongs to the account you are signed in with. What each release
added is in [CHANGELOG.md](CHANGELOG.md).

### Accounts and profile

- **Several pods at once** — Inrupt, Solid Community, Data Pod, or any OIDC issuer; switch between
  accounts, and an expired session reconnects in one tap instead of logging you out
- **Your profile** — edit it, share it as a QR code or link, and open other people's public
  profiles

### Files

- **Browse your pod** in list or grid, sorted by name, type, or date
- **Upload, download, and open** files, or capture a photo or video straight into your pod
- **Duplicate as a private copy**, send a copy to another app, or delete with a confirmation
- **Transfers run in the background** with progress notifications

### Sharing

- **Share files, folders, tickets, and contacts** with a Solid user by WebID, or with anyone who
  has the link, at a View, Add, or Edit level
- **A QR code and a link for every share** — the link opens the app directly, and the scanner
  tells share links and profiles apart
- **Shared by me / Shared with me** — two pod-backed lists, with the time of each share
- **Manage and request access** — widen, narrow, or revoke a share inline; ask an owner for access
  when a resource denies you, and accept or decline requests on your own
- **Works across Solid servers** — Web Access Control with an Access Control Policy fallback

### Wallet

- **Tickets and passes on your pod** — event tickets, boarding passes, loyalty cards, and coupons
  as ordinary Solid resources, registered in your type index
- **Apple Wallet `.pkpass` import, read in full**, shown in five Apple-parity layouts with the
  original file kept beside the ticket
- **Scanner-faithful barcodes** in the issuer's own symbology, with a brightness boost when opened
- **Passes that stay current** through the issuer's web service, and an open ticket-QR format any
  issuer can adopt

### Contacts

- **Your address book on your pod**, in standard vCard, round-tripped in full
- **Two-way sync with the phone's Contacts app**, one Android account per pod
- **Import from the phone or a `.vcf` file**, and export everything back out
- **Duplicate review** — near-duplicates are suggested, and merged only when you say so

### Notifications

- **An in-app inbox** for share offers, accepts, access changes, and requests, per account

### Offline-first

- **Everything you have seen opens with no connection**, from an encrypted database on the device
- **Every write queues** and drains by itself when the connection comes back
- **Pin a file** to keep its content available offline

### Free software

- **Two builds** — a Play build with crash reporting, and the F-Droid build, fully free software
  with no proprietary dependency
- **Reproducible builds**, byte-for-byte, so F-Droid can publish the very APK the developer signs

## Documentation

Every feature above has a page under [`documents/`](documents/README.md) explaining how it actually
works — its shape on the pod, its screens, what it does when the network or the server misbehaves,
and the seams a future change is expected to use. If you want more detail on how any one of these
is handled, **open its page**; they are written for whoever has to change the code next.

**Read them in this order.** The first four are the layers every feature sits on; after that, any
page stands alone.

| # | Page | Read it for |
|---|---|---|
| 1 | [Architecture](documents/ARCHITECTURE.md) | The layers and what may depend on what, dependency injection, the library boundary, and which rules a failing build enforces rather than a reviewer |
| 2 | [Authentication & accounts](documents/AUTH.md) | Signing in to a pod, holding several identities at once, why everything is scoped to the active WebID, and expiry as a state rather than a crash |
| 3 | [Offline-first](documents/OFFLINE.md) | The encrypted cache and the two write queues everything goes through, what works with no connection, and what deliberately refuses |
| 4 | [Errors](documents/ERRORS.md) | How a failure becomes a sentence a person can act on instead of a status code, from one layer instead of from each screen |
| 5 | [Files](documents/FILES.md) | The pod file browser, its queue, and the decisions behind rename-as-copy and non-recursive container sizes |
| 6 | [Sharing](documents/share.md) | View/Add/Edit as WAC and ACP grants, the on-pod given and received indexes, links and QR codes, and inbox delivery |
| 7 | [Entity sharing](documents/ENTITY_SHARING.md) | Sharing *a ticket* or *a contact* rather than a file, and the contract a data module implements to join in |
| 8 | [Notifications](documents/NOTIFICATIONS.md) | The bell hub over your pod's LDN inbox, typed rows, and why polling beat a live socket |
| 9 | [Contacts](documents/CONTACTS.md) | The address book on your pod, the two-way mirror into the phone's Contacts app, and duplicate review |
| 10 | [Wallet (tickets)](documents/TICKETS.md) | Passes as pod resources, the open ticket-QR format any issuer can adopt, `.pkpass` import, and issuer refresh |
| 11 | [Data modules](documents/DATA_MODULES.md) | The framework the last two are built on, and what adding a third actually costs |
| 12 | [Ticket vocabulary](documents/TICKET_VOCAB.md) | The normative term dictionary for a ticket on a pod, and why each minted term exists |
| 13 | [Testing](documents/TESTING.md) | What the suite pins, how to run it, and the gotchas that cost an afternoon each |

The full index — including the original sharing R&D standard and the modularization record — is in
[documents/README.md](documents/README.md). The library that talks to the pod documents itself
separately, at [androidsolidservices.erfangholami.com](https://androidsolidservices.erfangholami.com).

## Architecture

The app follows **Clean Architecture** with **MVVM**, organized in a single `app` module:

```
presentation/  -->  domain/model/  -->  data/repo/  -->  data/local/
(Composables        (plain data        (Repository      (DataStore /
 + ViewModels)       classes)           interfaces       Room /
                                        + impls)         Authenticator)
```

- **UI**: Jetpack Compose with Material 3
- **Navigation**: Type-safe Compose Navigation with serializable routes
- **Dependency injection**: Hilt
- **Local storage**: DataStore Preferences for settings, and an SQLCipher-encrypted Room database
  for the offline cache and the write queues
- **Background work**: WorkManager (uploads, downloads, queue drains, inbox polling, contacts
  import/export, pass refresh)
- **Solid communication**: [Android Solid Services](https://github.com/erfangholami/Android-Solid-Services)
- **Authentication**: Solid-OIDC via AppAuth, delegated through `AuthRepository`

A tour of all of it is in [documents/ARCHITECTURE.md](documents/ARCHITECTURE.md).

## Tech Stack

| Component              | Version          |
|------------------------|------------------|
| Kotlin                 | 2.3.21           |
| Android Gradle Plugin  | 9.3.1            |
| KSP                    | 2.3.5            |
| Jetpack Compose BOM    | 2026.06.01       |
| Hilt                   | 2.60.1           |
| Navigation Compose     | 2.9.8            |
| WorkManager            | 2.11.2           |
| Room                   | 2.8.4            |
| SQLCipher              | 4.17.0           |
| Android Solid Services | 0.7.2            |
| Min SDK                | 26 (Android 8.0) |
| Target SDK             | 36 (Android 16)  |
| Compile SDK            | 37               |
| JVM Toolchain          | 21               |

## Getting Started

### Prerequisites

- Android Studio (latest stable)
- JDK 21
- An Android device or emulator running Android 8.0+
- A Solid pod account (you can create one at [Inrupt](https://login.inrupt.com) or [solidcommunity.net](https://solidcommunity.net))

### Two distributions

The app builds in two flavours, along a single `distribution` dimension:

| Flavour | For | Telemetry |
|---------|-----|-----------|
| `foss`  | F-Droid and other free-software stores | none — no proprietary SDK is linked at all |
| `gms`   | Google Play | Crashlytics and Analytics |

The split is a source-set split rather than a runtime flag, so the FOSS APK contains no Firebase
classes whatsoever. Barcode decoding is free software in both builds. Building `gms` needs your own
`app/src/gms/google-services.json` (never committed); `foss` builds without one, so **start with
`foss` if you just want to run the app.**

### Build & Run

```bash
# Clone the repository
git clone https://github.com/erfangholami/SolidShare.git
cd SolidShare

# Build and install the free-software debug build
./gradlew installFossDebug

# Play-flavoured build (needs app/src/gms/google-services.json)
./gradlew installGmsDebug

# Kotlin-only compile check, and the test suite
./gradlew compileFossDebugKotlin
./gradlew testFossDebugUnitTest testGmsDebugUnitTest
```

### Release Build

Release signing is read at configuration time from a gitignored `keystore.properties` at the repo
root, which must exist for any Gradle task:

| Key                 | Content                          |
|---------------------|----------------------------------|
| `KEYSTORE_PATH`     | Path to the `.jks` keystore file |
| `KEYSTORE_PASSWORD` | Keystore password                |
| `KEY_ALIAS`         | Key alias inside the keystore    |
| `KEY_PASSWORD`      | Key password                     |

```bash
./gradlew :app:assembleFossRelease   # what F-Droid's builder reproduces
./gradlew :app:bundleGmsRelease      # the Play upload artifact
```

**The version is declared in `app/build.gradle.kts`.** The `versionCode` and `versionName`
literals in `defaultConfig` are what F-Droid's update checker reads — it parses the file
statically, so nothing computed can stand in for them. The build fails if the two literals fall
out of step with each other, or if HEAD carries a release tag that disagrees with them, so a tag
and a build still cannot disagree. Check what a checkout declares with
`./gradlew -q :app:printVersion`.

Cutting a release is bumping those two literals and pushing the matching `v` tag. The `Release`
workflow runs the same verification as every pull request, builds both flavours, checks that the
FOSS APK carries no
proprietary code, and attaches the artifacts to a GitHub release.

### Store metadata

The store listing lives in the repository, in the Fastlane layout F-Droid reads directly:

```
fastlane/metadata/android/en-US/
├── title.txt                 # 11 chars  (Play caps the app name at 30)
├── short_description.txt     # 70 chars  (cap 80)
├── full_description.txt      # 2989 chars (cap 4000)
├── changelogs/400.txt        # 453 chars (Play caps "what's new" at 500)
├── changelogs/401.txt        # one file per release, named for its versionCode
└── images/
    ├── icon.png              # 512×512, rendered from the vector mark
    └── phoneScreenshots/     # 30 × 1080×2340, shown in filename order
```

Two conventions matter. **A changelog is named after its `versionCode`**, which the build derives
from the tag as `major × 10000 + minor × 100 + patch` — so `v0.4.0` is `400.txt` and `v0.4.1` is
`401.txt`. Every new tag needs its own file, or that release ships with no "what's new"; check the
number with `./gradlew -q :app:printVersion -PappVersionName=X.Y.Z`. **Screenshots are ordered by
filename**, and the order is the one a new user meets the screens in: first run, then Home and the
two cards it opens, then the tabs left to right — Files, scan, Share, Profile — with each tab's
sheets in the order you reach them. Keep the prefix zero-padded, or a tenth screenshot sorts
between the first and the second, and name each file after its screen so a re-shoot keeps its slot.

`phoneScreenshots/` mirrors the repository's own `screenshots/` directory file for file; keep the
two in step so there is one place to re-shoot a screen.

Google Play accepts at most **eight** phone screenshots, and journey order is not the order that
sells. Upload these eight, which cover every feature area and stand alone without captions:

```
05_home_hub  08_files_browser  16_share_create  17_share_link_qr
23_shared_by_me  27_notifications_access_request  06_wallet_ticket_pass  29_profile_share_qr
```

Adding a locale means a sibling directory (`de-DE`, `fa-IR`, …) with the same shape.

## Project Structure

```
app/src/main/java/com/erfangholami/solidshare/
├── data/
│   ├── device/               # The phone's own contacts, read for import
│   ├── local/
│   │   ├── auth/             # Active WebID, logged-in / logged-out accounts (DataStore)
│   │   ├── cache/            # Encrypted Room database: cache, blobs, and the two write queues
│   │   └── settings/         # App preferences & one-shot flags (DataStore)
│   ├── passimport/           # .pkpass, .pkpasses and boarding-pass barcode parsing
│   └── repo/                 # Repository interfaces & implementations
│       ├── auth/             # Login, multi-account, active WebID (wraps the library Authenticator)
│       ├── contacts/         # Address books, contacts, duplicate detection
│       ├── datamodule/       # The data-module lifecycle & registry
│       ├── file/             # Browse, upload, download, delete, access probing
│       ├── notifications/    # Inbox notifications + unread-badge store
│       ├── outbox/           # The write queues and their drain policy
│       ├── profile/          # Public-profile reads
│       ├── settings/         # App settings
│       ├── sharing/          # Create / manage / revoke shares, given & received indexes
│       └── tickets/          # Wallet passes, the ticket QR codec
├── di/                       # Hilt modules (Repository, DataSource, DataModule, EntityShare,
│                             #   SolidApi, Application, Local, Cache)
├── domain/
│   ├── error/                # AppError × AppOperation → the one message layer
│   └── model/                # Domain models (ContainerItem, Sharing, Ticket, Contact, …)
├── notification/             # NotificationHelper for system-tray notifications
├── presentation/
│   ├── components/           # Reusable UI (AccountSwitcher, NotificationBell, RequiresConnection…)
│   ├── contacts/             # Contacts list, detail, settings, books, merge review, sharing
│   ├── container/            # Container (folder) browser & ViewModel
│   ├── login/                # Login screen & ViewModel
│   ├── main/                 # Bottom-nav host & tabs (Home, Files, Share, Profile, Edit Profile)
│   ├── navigation/           # Navigation graph & typed routes
│   ├── notifications/        # Notifications hub (All / Unread / Requests)
│   ├── onboard/              # Onboarding flow
│   ├── permissions/          # Runtime permission gate
│   ├── sharing/              # Share, scan, confirm-access, manage-access & profile-share screens
│   ├── startup/              # Startup auth-check screen
│   ├── theme/                # Material 3 theme, colors, typography
│   ├── util/                 # Avatar colors, clipboard & QR-code helpers
│   ├── wallet/               # Wallet list, pass rendering, detail, edit, import, sharing
│   ├── MainActivity.kt
│   └── MainViewModel.kt      # Deep-link handling
├── sync/                     # Android account + contacts SyncAdapter
├── telemetry/                # Auth analytics interfaces (implemented per flavour)
├── util/                     # DateUtils, MediaUtils, StringProvider, vCard I/O, barcode rendering
├── worker/                   # Uploads, downloads, queue drains, inbox polling, contacts import /
│                             #   export, pass refresh
└── SolidShareApplication.kt  # Application + WorkManager configuration
```

## Dependencies

Core Solid communication is provided by the
[Android Solid Services](https://github.com/erfangholami/Android-Solid-Services) library
(`com.erfangholami.androidsolidservices:api`, plus its transitive `shared` artifact), resolved from
**Maven Central** — it handles authentication, resource management, sharing (WAC/ACP grants and the
on-pod given/received indexes), inbox notifications, and the contacts and tickets data modules. Its
own documentation is at
[androidsolidservices.erfangholami.com](https://androidsolidservices.erfangholami.com).

`settings.gradle.kts` lists only `google()` and `mavenCentral()`, deliberately: F-Droid and any
outside contributor build from a clean checkout, so anything available only in a local `~/.m2`
would make the app unbuildable for everyone else.

The app's other notable dependencies:

- **UI** — Jetpack Compose (BOM), Material 3, Material Icons Extended, Compose UI Tooling, Google
  Fonts
- **Dependency injection** — Hilt, with the Hilt Navigation Compose and Hilt Work integrations
- **Navigation** — Navigation Compose (type-safe serializable routes)
- **Background work** — WorkManager
- **Local storage** — DataStore (Preferences), and Room over SQLCipher for the offline cache and
  write queues
- **Lifecycle** — Lifecycle ViewModel KTX and Lifecycle Runtime Compose
- **Async & serialization** — Kotlin Coroutines and Kotlinx Serialization (JSON)
- **Barcodes** — ZXing Core to render branded QR codes and ticket barcodes, CameraX for the camera
  preview, and zxing-cpp to decode from camera frames and gallery images. All three are free
  software, so both distributions scan and render identically
- **Crash reporting and analytics** — Firebase, in the `gms` flavour only
- **Testing** — JUnit, Robolectric, mockk, kotlinx-coroutines-test

All versions are pinned in the `gradle/libs.versions.toml` version catalog.

## Contributing

Contributions are welcome! The project is open source under the MIT License.

1. Fork the repository
2. Create a feature branch
3. Read the [page for the feature you are changing](documents/README.md) — each one names the seams
   a change is expected to use, and the decisions it should not quietly reverse
4. Make your changes, and update that page if the behaviour it describes moved
5. Run `./gradlew testFossDebugUnitTest lintFossRelease` — the same checks CI runs on your pull
   request, and the ones a release has to pass before it can publish
6. Submit a pull request

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

## Acknowledgments

This project is funded by [NLnet](https://nlnet.nl/) as part of [Mobifree](https://mobifree.org/).

<p align="center">
  <a href="https://nlnet.nl/"><img src="https://nlnet.nl/logo/banner.svg" alt="NLnet" width="120"></a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://mobifree.org/"><img src="https://nlnet.nl/image/logos/NGI_Mobifree_tag.svg" alt="NGI Mobifree" width="120"></a>
</p>
