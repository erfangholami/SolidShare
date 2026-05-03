# Solid Share

<p align="center">
  <img src="documents/icon.png" alt="Solid Share Logo" width="120">
</p>

**Solid Share** is an open-source Android application that brings the [Solid](https://solidproject.org/) ecosystem to everyday mobile users. It lets people use their Solid pods as a personal data wallet — logging in with multiple accounts, browsing and managing files, and sharing data — all from their Android phone, without needing any technical background.

The goal is to make Solid accessible to regular people: a smooth, familiar mobile experience that puts users in control of their own data.

## Screenshots

<p align="center">
    <img src="screenshots/onboarding.png" alt="Onboarding" width="200">
    &nbsp;
    <img src="screenshots/login.png" alt="Login" width="200">
    &nbsp;
    <img src="screenshots/login_podproviders.png" alt="List of Pod providers" width="200">
    &nbsp;
     <img src="screenshots/profile.png" alt="Profile" width="200">
    &nbsp;
    <img src="screenshots/login_back.png" alt="Login after logout" width="200">
    &nbsp;
    <img src="screenshots/files_list.png" alt="Directory in List mode" width="200">
    &nbsp;
    <img src="screenshots/files_grid.png" alt="Directory in Grid mode" width="200">
    &nbsp;
    <img src="screenshots/add_resources.png" alt="Add a resource" width="200">
    &nbsp;
    <img src="screenshots/resource_actions.png" alt="Resource actions" width="200">
</p>


## Features

### v0.2.0 - Current

- **Pod file browser** — browse containers and resources in your Solid pod with list or grid layout
- **File download & open** — download resources to your device and open them with any compatible app
- **File upload** — upload files from your device storage directly to a pod container
- **Camera capture & upload** — take a photo or video with your camera and upload it immediately to
  your pod
- **File deletion** — delete resources from your pod with a confirmation prompt
- **Sorting** — sort resources by name, type, or date in the container view
- **Background transfers** — uploads and downloads run as background workers with progress
  notifications
- **In-flight resource caching** — resources are cached as they load to improve responsiveness

### v0.1.0

- **Onboarding flow** — introduces new users to Solid and how the app works
- **Login with multiple pod providers** — Inrupt, Solid Community, Data Pod, or any custom OIDC issuer URL
- **Multi-account support** — log into multiple Solid pods and switch between them
- **Re-login with previous WebIDs** — previously logged-in accounts are remembered for quick re-authentication
- **Profile & account management** — view active account, switch accounts, log out individually or all at once

### Planned

- Share private files via QR code or generated link
- Sync Solid data modules (e.g. Contacts) with the Android ecosystem
- Store and use travel tickets and passes from pods
- Offline-first access for convenience

## Architecture

The app follows **Clean Architecture** with **MVVM**, organized in a single `app` module:

```
presentation/  -->  domain/model/  -->  data/repo/  -->  data/local/
(Composables        (plain data        (Repository      (DataStore /
 + ViewModels)       classes)           interfaces       Authenticator)
                                        + impls)
```

- **UI**: Jetpack Compose with Material 3
- **Navigation**: Type-safe Compose Navigation with serializable routes
- **Dependency injection**: Hilt
- **Local storage**: DataStore Preferences
- **Solid communication**: [Android Solid Services (solidandroidapi)](https://github.com/pondersource/Android-Solid-Services)
- **Authentication**: OpenID Connect via AppAuth, delegated through `AuthRepository`

## Tech Stack

| Component              | Version          |
|------------------------|------------------|
| Kotlin                 | 2.3.21           |
| Android Gradle Plugin  | 9.2.0            |
| KSP                    | 2.3.5            |
| Jetpack Compose BOM    | 2026.04.01       |
| Hilt                   | 2.59.2           |
| Navigation Compose     | 2.9.8            |
| WorkManager            | 2.11.2           |
| Android Solid Services | 0.4.0            |
| Min SDK                | 26 (Android 8.0) |
| Target SDK             | 35               |
| Compile SDK            | 37               |
| JVM Toolchain          | 17               |

## Getting Started

### Prerequisites

- Android Studio (latest stable)
- JDK 17
- An Android device or emulator running Android 8.0+
- A Solid pod account (you can create one at [Inrupt](https://login.inrupt.com) or [solidcommunity.net](https://solidcommunity.net))

### Build & Run

```bash
# Clone the repository
git clone https://github.com/erfangholami/SolidShare.git
cd SolidShare

# Build debug APK
./gradlew assembleDebug

# Install on a connected device
./gradlew installDebug
```

### Release Build

Release builds require signing environment variables:

| Variable            | Description update               |
|---------------------|----------------------------------|
| `KEYSTORE_PATH`     | Path to the `.jks` keystore file |
| `KEYSTORE_PASSWORD` | Keystore password                |
| `KEY_ALIAS`         | Key alias inside the keystore    |
| `KEY_PASSWORD`      | Key password                     |

```bash
./gradlew assembleRelease
```

A GitHub Actions workflow automatically builds and publishes a release APK when changes are pushed to `master` with a tag.

## Project Structure

```
app/src/main/java/com/erfangholami/solidshare/
├── data/
│   ├── local/          # DataStore & Authenticator implementations
│   └── repo/           # Repository interfaces & implementations
│       ├── auth/       # Auth repository (login, accounts)
│       └── file/       # File repository (browse, upload, download, delete)
├── di/                 # Hilt dependency injection modules
├── domain/
│   └── model/          # Domain models (PodServer, LoggedInUser, DownloadedFile, etc.)
├── notification/       # NotificationHelper for transfer progress notifications
├── presentation/
│   ├── container/      # Container (folder) browser screen & ViewModel
│   ├── login/          # Login screen & ViewModel
│   ├── main/           # Main screens (Files, Profile)
│   ├── navigation/     # Navigation graph & route definitions
│   ├── onboard/        # Onboarding flow
│   ├── startup/        # Startup auth-check screen
│   ├── theme/          # Material 3 theme, colors, typography
│   └── MainActivity.kt
├── util/               # DateUtils, MediaUtils helpers
├── worker/             # DownloadWorker & UploadWorker (WorkManager)
└── SolidShareApplication.kt
```

## Dependencies

This app uses the [Android Solid Services](https://github.com/pondersource/Android-Solid-Services) library (`solidandroidapi`) for communicating with Solid pods.

## Contributing

Contributions are welcome! The project is open source under the MIT License.

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run `./gradlew compileDebugKotlin` to verify compilation
5. Submit a pull request

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

## Acknowledgments

This project is funded by [NLnet](https://nlnet.nl/) as part of [Mobifree](https://mobifree.org/).

<p align="center">
  <a href="https://nlnet.nl/"><img src="https://nlnet.nl/logo/banner.svg" alt="NLnet" width="120"></a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://mobifree.org/"><img src="https://nlnet.nl/image/logos/NGI_Mobifree_tag.svg" alt="NGI Mobifree" width="120"></a>
</p>
