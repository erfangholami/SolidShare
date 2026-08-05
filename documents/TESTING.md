# Testing

*Part of the [Solid Share documentation set](README.md).*

The suite is **JVM-first**. DAOs, repositories and ViewModels run under Robolectric with a real
in-memory Room database, the library boundary is faked with mockk, and coroutines run on
`kotlinx-coroutines-test`. Only one thing needs a device: the Keystore crypto, which Robolectric
cannot shadow.

Two rules decide what gets written. **Tests assert the behaviour a user would notice**, not the
implementation — a test that only proves a mock was called proves nothing about the pod. And where
a test targets a fixed bug, it was confirmed to **fail on the pre-fix code first**; a regression
test that never went red is a hopeful comment.

Write paths are checked against a **stateful fake pod** — a set of created URIs that responds like
a server — so the assertion is "the folder exists on the server", not "upload was invoked".

## Running

```bash
./gradlew testGmsDebugUnitTest testFossDebugUnitTest   # both flavours, no device
./gradlew testGmsDebugUnitTest --tests "*Outbox*"      # a subset
./gradlew connectedGmsDebugAndroidTest                 # CacheKeyManager crypto (needs a device)
./gradlew createGmsDebugUnitTestCoverageReport         # → app/build/reports/coverage/
```

Both flavours are worth running: the `gms` and `foss` source sets bind different telemetry
implementations, and only compiling one of them is how a FOSS build breaks.

## What is covered

### The rules, not just the behaviour

`architecture/ArchitectureTest` walks `src/main/java` and asserts six rules against **shrink-only
baselines** — the assertion is equality against a known set, so introducing a violation fails the
build *and* fixing one fails until you shrink the baseline. A baseline that grows is a failed
build.

| Rule | Baseline | Why it exists |
|---|---|---|
| `data/` does not import `worker/` | empty | Draining is asked for through `OutboxTrigger`; `worker/` decides how |
| Generic `presentation/` does not import a module package | empty | Typed rows, hub cards, routes and the receiver sheet go through registries |
| Generic `presentation/` does not import a module's data layer | `presentation/sharing/PublicProfileViewModel.kt` | The QR add-to-contacts flow, pending an add-to-module seam |
| No `something.message ?: fallback` anywhere | empty | A library diagnostic must never reach a screen — see [ERRORS.md](ERRORS.md) |
| `SolidError` / `SolidResultException` / `SharingException` stay out of `presentation/`, `worker/`, `sync/` | empty | Screens speak `AppError` |
| No comments in production code except KDoc | empty | Intent goes in names |

The one non-empty baseline is honest rather than tolerated, and it is named in
[ARCHITECTURE.md](ARCHITECTURE.md) with what would remove it.

### Offline, cache and queues

| Area | Test | What it pins |
|---|---|---|
| Container VM regression | `presentation/container/ContainerViewModelTest` | An offline-created folder stays visible after an online refresh (the reported bug); an offline-deleted file stays hidden even when the server still lists it; a synced item the server dropped disappears. **Verified to fail on the pre-fix code** |
| File outbox | `data/repo/outbox/OutboxRepositoryTest` | Optimistic enqueue for upload/delete/create-folder/duplicate; drain and reconcile; create-then-delete coalescing; terminal versus transient buckets; delete-404-as-success; pending blobs pinned; `clearForWebId`. Against the stateful fake pod |
| Read cache | `data/repo/file/FileRepositoryCacheTest` | Offline serving of a null-ETag cached blob **with no network call** (the weak-ETag fix); observe/cache round-trip; pin and unpin without re-downloading; clear-on-logout removes rows *and* files |
| Resource DAO | `data/local/cache/ResourceDaoTest` | `PENDING_DELETE` excluded from listings; `replaceContainer` never clobbers a locally-pending row the server still lists; folders-first ordering; per-WebID purge |
| Blob DAO | `data/local/cache/BlobDaoTest` | LRU ordering excludes pinned and pending-upload blobs |
| Outbox DAO | `data/local/cache/OutboxDaoTest` | `nextActionable` skips future retries and ignores `ERROR`/`IN_FLIGHT`; `resetInFlight`; `deleteByTarget` |
| Migrations | `data/local/cache/CacheMigrationTest` | Both folds into the generic tables carry queued writes across, including a provisional offline create |
| Converters and mappers | `CacheConvertersTest`, `CacheMappersTest` | Enum/list/access round-trips; lossless entity↔domain mapping |
| Sync badge | `presentation/container/SyncBadgeTest` | Precedence: error > pending > offline > none |

### Errors

`domain/error/AppErrorMapperTest` (16 cases) pins the classification decisions: offline versus
pod-unreachable is re-decided against live connectivity, 401 is a dead session rather than a
denial, 413/507/408 are refined out of the library's generic bucket, a stale ACL differs from a
stale resource, a wrapped cause is still found, `ENOSPC` is called out, and local `content:` work
is never reported as a network problem.

`ErrorPresenterTest` (13 cases) pins the wording contract. Its last test renders **every cause
under every operation** and asserts each is a finished sentence with no leaked format specifier —
which is what makes adding either half of the catalogue safe.

### Data modules

| Area | Test | What it pins |
|---|---|---|
| Ticket queue | `data/repo/tickets/TicketsRepositoryOfflineTest` | A provisional row appears immediately; drain replaces it with the server one; editing a queued create rewrites rather than duplicates; a stale provisional row is cleaned up |
| Ticket images | `TicketsRepositoryImagesTest` | Images survive the queue, including for a ticket with no `.pkpass` |
| Shared entities | `SharedTicketRepositoryTest`, `SharedContactRepositoryTest` | A foreign entity never enters the cache; a copy carries `dcterms:source`, so the duplicate guard can find it |
| Pass import | `data/passimport/PkpassParserTest`, `BcbpParserTest`, `TicketFileSnifferTest` | Real pass files → drafts, boarding-pass barcode decoding, classification by magic bytes |
| Wallet logic | `presentation/wallet/PassCardLogicTest`, `PassVocabularyTest`, `TicketInstantTest`, `WithDerivedEventStartTest` | Layout selection, field vocabulary, and the date derivations behind upcoming-versus-past |
| Ticket import VM | `TicketImportViewModelTest` | The confirm-before-write path, including multi-pass bundles |
| vCard | `util/VCardRoundTripTest` | Write-then-read loses nothing — the test that catches a model field the writer forgot |

### Sharing

`data/repo/sharing/SharingMappersTest` and `SharingFormatTest` cover the domain mapping and the
link format; `presentation/sharing/ManageSharingViewModelTest` pins that "who has access" shows an
offline state rather than a raw error when there is no connection. The sharing engine's own
behaviours — index collapsing, grant-wins-on-tie, the anti-impersonation gate — are tested in the
library, which is where that state machine lives.

### On a device

`androidTest/…/CacheKeyManagerTest` — the 32-byte SQLCipher passphrase persists across runs, and
AES/GCM byte and stream round-trips work. This is the only test that needs hardware, because
Robolectric cannot shadow the Android Keystore.

## Gotchas

- **ViewModel tests plus Room.** Room dispatches suspend DAO calls to its own executor, so
  `advanceUntilIdle()` can return while a `viewModelScope` coroutine is still parked on Room. Build
  the in-memory database with same-thread executors (`.setQueryExecutor { it.run() }`, and the same
  for transactions) so DAO work runs on the test dispatcher. `ContainerViewModelTest` shows the
  shape.
- **jacoco plus Robolectric.** Robolectric loads classes in a sandbox classloader jacoco does not
  see, so coverage reports 0% unless `testOptions.unitTests.all { jacoco { isIncludeNoLocationClasses = true } }`
  is set — it is, in `app/build.gradle.kts`.
- **DAO tests use a plain in-memory Room database** (`inMemoryCacheDb()` in `CacheTestFactory`),
  with no SQLCipher, so they validate queries; encryption is validated by the device test.
- **Firebase needs `FirebaseApp.initializeApp`** under Robolectric, or anything touching analytics
  fails on a missing default app.
- **Shared builders** live in `data/local/cache/CacheTestFactory.kt` — `inMemoryCacheDb()`,
  `testOutbox(db)` (a `ModuleOutbox` with a no-op trigger, so a repository test drains explicitly
  instead of racing WorkManager), plus row builders. Add to it rather than re-declaring fixtures.

## What is not tested here

Compose UI is covered by `@Preview` coverage and manual device passes, not by instrumentation
tests; the previews are the convention (see [ARCHITECTURE.md](ARCHITECTURE.md)). The pod-facing
protocol behaviour — WAC and ACP writes, N3 patches, inbox reads, token refresh — is tested in the
AndroidSolidServices library against an in-memory pod, because that is where the logic is. When a
sharing or auth behaviour needs pinning, the library's suite is usually the right place.
