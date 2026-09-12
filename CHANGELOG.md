# Changelog

This file records what each release of Solid Share changed, newest first. Each release heading
links to the commits it contains. The short "what's new" text that the stores show lives under
[`fastlane/metadata/android/en-US/changelogs/`](fastlane/metadata/android/en-US/changelogs/),
one file per release, named after its `versionCode`.

## [0.4.4] — 2026-09-12

A bug-fix release, driven by the first crash reports from the stores and by the move to
AndroidSolidServices 0.7.2. No new features.

- **Donation links** — the repository carries a `FUNDING.yml`, so GitHub shows how to support the
  project (GitHub Sponsors, Liberapay, Ko-fi, Buy Me a Coffee)
- **Scanning a QR code no longer crashes** — the camera thread handed its result straight to
  navigation, which must run on the main thread
- **Built against AndroidSolidServices 0.7.2** — an expired account no longer floods crash reporting
  with one non-fatal per request, inbox notifications parse with no network, an expired session
  reaches the app as a typed error, and pods that answer a conditional read with a `304` carrying a
  `Content-Length` (Community Solid Server, e.g. solid.redpencil.io) no longer break the Share
  screen and share creation
- **Two onboarding typos fixed**
- **A changelog and a feature-overview README** — the version history moved out of the README into
  this file, and the README carries the F-Droid and Google Play badges

## [0.4.3] — 2026-08-11

Another maintenance release on the road to F-Droid. Nothing changes in the app itself.

- **Byte-for-byte reproducible builds** — the build no longer strips the native libraries that
  arrive inside AARs, because stripped output depends on which NDK the build host has. F-Droid can
  now publish the very APK the developer signs
- **No Play-only metadata in the APK** — the dependency list that only Google Play can read is no
  longer embedded in the signing block, and version-control details stay out of the artifact, so a
  rebuild from a fresh clone cannot differ from the release
- **Crashlytics wiring out of the tree** — the Play-only crash-reporting plugin reaches the build
  only through two `solidshare.play.*` Gradle properties that the Play release invocation supplies,
  so the committed Gradle files carry no reference to it and F-Droid's scanner sees a fully
  free-software checkout

## [0.4.2] — 2026-08-10

A maintenance release on the road to F-Droid. Nothing changes in the app itself.

- **Built with Java 21** — the JVM toolchain and both CI workflows moved from JDK 17 to 21, matching
  F-Droid's standard build environment
- **The version is declared literally** — `versionCode` and `versionName` are plain literals in
  `app/build.gradle.kts` again, because F-Droid's update checker parses the file statically. The
  build fails if the two fall out of step with each other, or if HEAD carries a release tag that
  disagrees with them, so a tag and a build still cannot disagree

## [0.4.1] — 2026-08-08

A small follow-up to 0.4.0, and the release that puts Solid Share on the stores.

- **The privacy policy from inside the app** — one tap under About on your Profile, next to the
  version
- **Built for Android 16** — targets API 36, the level both stores now require of a new release
- **A store listing that lives in the repository** — title, descriptions, per-version changelogs,
  icon, feature graphic and screenshots in the Fastlane layout F-Droid reads straight from the
  tagged commit, so the listing is reviewed and versioned like the code
- **A release cannot ship without its changelog** — the release workflow fails when the changelog
  for the tag's `versionCode` is missing or over the 500-character store limit, because that file is
  read from the tag and cannot be added afterwards

## [0.4.0] — 2026-08-05

The release that turns Solid Share from a pod file browser into a pod **data** app: a wallet and an
address book of your own, everything usable offline, and sharing that understands what it is
sharing.

**Wallet — new**

- **Tickets and passes on your pod** — event tickets, boarding passes, cinema tickets, loyalty
  cards and coupons stored as ordinary Solid resources you own, registered in your type index so
  any Solid app can find them
- **Passes that look like passes** — five Apple-parity layouts (boarding, coupon, event, store
  card, generic) with the issuer's own colours and artwork, a tear-line barcode panel, and a
  faded treatment once a pass has expired
- **Apple Wallet import, with nothing dropped** — a `.pkpass` or a `.pkpasses` bundle is read in
  full: pass identity, all seven field tiers, reservation, membership and Wi-Fi details, locations
  and beacons, relevancy dates and voided state. The original file is kept on the pod beside the
  ticket
- **Barcodes re-rendered byte-faithfully** in the symbology the issuer used, so gate scanners read
  the identical code, with a screen-brightness boost when you open one
- **Boarding passes understood** — IATA boarding-pass barcodes are decoded for the details Apple
  leaves inside the token
- **Journeys** — transport mode, from and to, departure and arrival for flights, trains, buses and
  boats, editable on the ticket, and travel tickets sorted by departure time
- **Issuer jargon translated** into plain, localized labels — doors, boarding, gate closes, cabin
  classes
- **Passes that stay current** — a pass carrying an issuer web service refreshes itself on wallet
  open and twice a day
- **An open ticket-QR format** — any issuer can offer one-tap add-to-pod, with no integration and
  no server of ours involved
- **Open a pass from anywhere** — a `.pkpass` shared or opened from any app lands in a preview of
  what was read, to add as-is or edit first

**Contacts — new**

- **Your address book on your pod**, in the standard vCard vocabulary, registered in the type index
  and round-tripped in full, so nothing another app wrote is lost when Solid Share writes back
- **Two-way sync with the phone's Contacts app** — one Android account per pod, contacts appear
  everywhere on the device, and editing or deleting them in any app pushes back to the pod
- **Bring contacts in** — import the phone's own contacts from any account, or a `.vcf` file
- **Take them out** — export everything as vCard
- **Duplicate review** — near-duplicates are suggested and merged only when you say so, never
  silently
- **Address books** — create private or public books, rename and delete them
- **Contacts in sharing** — pick a share receiver from your address book instead of pasting a WebID

**Sharing data, not just files**

- **Share a ticket or a contact as a thing** — the receiver is told "Alice shared a ticket with
  you", sees a real pass or contact card, and can add it to their own wallet or address book,
  owning their copy
- **A public pass link** — publish a single pass by link, revocable with a switch
- **Send a copy of a file to another app** from the file actions sheet
- **Open a shared resource's container** straight from the Share tab

**Offline-first**

- **Everything you have seen is on the device**, in an encrypted database, and opens with no
  connection
- **Every write queues** — upload, delete, create a folder, duplicate, add or edit a ticket or
  contact — and drains by itself when the connection comes back
- **Make available offline** — pin a file so its content is always there
- **One clear affordance** on the few surfaces that genuinely need a connection, instead of
  buttons that fail
- **Long jobs run in the background** — contacts import and export survive leaving the app, and
  report progress in the notification shade

**Accounts and notifications**

- **An expired session is a state, not a logout** — the account stays on your Profile page and
  reconnects in one tap, keeping its device contacts until you actually sign out
- **Notifications per account**, so switching accounts switches what you see
- **Notifications that look like Solid Share** — the app's own mark in the status bar, and file
  transfers branded to match

**Under the hood**

- **Two distributions** — a Play build with crash reporting, and a fully free-software build with
  no proprietary dependency linked at all (F-Droid ready)
- **Barcode decoding by zxing-cpp** instead of ML Kit, so both builds scan identically and neither
  ships a proprietary blob
- **Error messages written for people**, produced by one layer instead of by each screen
- **A smoother app** — barcode rendering, pass parsing and file decryption moved off the main
  thread
- **Data modules register themselves**, so a future kind of data plugs in without editing the home
  screen, navigation or scanner
- **Layering enforced by tests** with shrink-only baselines, so the structure cannot quietly rot
- **The tag is the version** — nothing writes it down, so a release can't disagree with itself, and
  pushing a tag cuts the release
- **Built against AndroidSolidServices 0.7.0** from Maven Central, so a clean checkout builds
- **A documentation page per feature**, in a reading order — see below

## [0.3.0] — 2026-06-16

**Profile**

- **Share your profile** — present a QR code of your WebID, or copy, save, and share a link, so
  others can find and add you
- **Edit your profile** — update your display name and details and write them back to your pod
- **View public profiles** — open someone else's public Solid profile from a scanned or shared WebID

**Sharing**

- **Share files & folders** — grant a specific Solid user (by WebID) or the public (anyone with the
  link) access to any file or folder in your pod, choosing the access level
- **QR codes & share links** — every share produces a branded QR code and a copyable link; tapping a
  link opens the app directly through verified HTTPS App Links
- **Shared by me / Shared with me** — two pod-backed lists of everything you've shared and
  everything shared with you, including when each was shared
- **Unified scan & confirm** — one camera scanner auto-detects a share link versus a profile,
  verifies your access, and lets you pick which logged-in account receives the share
- **Duplicate as a private copy** — duplicate a file or folder, resetting the copy to owner-only
  access

**Access grants**

- **View / Add / Edit access levels** — clear, icon-labeled access modes instead of raw
  Read/Append/Write
- **Manage access** — widen, narrow, or revoke any share inline; the recipient is notified when
  their access changes
- **Request access** — when a shared resource denies access, ask its owner for the level you need,
  and owners can accept or decline the request
- **Cross-server access control** — Web Access Control by default with an Access Control Policy
  fallback, so grants work across major Solid servers (including Inrupt ESS)
- **Share notifications** — an in-app inbox surfaces share offers, accepts, access-level updates,
  and access requests, kept current by a background polling worker

## [0.2.0] — 2026-05-03

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

## [0.1.0] — 2026-04-14

- **Onboarding flow** — introduces new users to Solid and how the app works
- **Login with multiple pod providers** — Inrupt, Solid Community, Data Pod, or any custom OIDC issuer URL
- **Multi-account support** — log into multiple Solid pods and switch between them
- **Re-login with previous WebIDs** — previously logged-in accounts are remembered for quick re-authentication
- **Profile & account management** — view active account, switch accounts, log out individually or all at once

[0.4.4]: https://github.com/erfangholami/SolidShare/compare/v0.4.3...v0.4.4
[0.4.3]: https://github.com/erfangholami/SolidShare/compare/v0.4.2...v0.4.3
[0.4.2]: https://github.com/erfangholami/SolidShare/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/erfangholami/SolidShare/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/erfangholami/SolidShare/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/erfangholami/SolidShare/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/erfangholami/SolidShare/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/erfangholami/SolidShare/releases/tag/v0.1.0
