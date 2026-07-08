# TESTING.md — offline feature test suite

Covers the offline-first work (read cache, write outbox, duplicate, connection gating). The suite is
JVM-first: DAOs and repositories run under **Robolectric** (real in-memory Room + SQLite + Context),
the ASS library boundary is faked with **mockk**, and coroutines use **kotlinx-coroutines-test**. The
one exception is the Keystore crypto, which Robolectric can't shadow — that runs as an instrumented
test on a device.

Tests assert **the behaviour the user expects**, not the implementation — where a test targets a fixed
bug it was confirmed to fail on the pre-fix code first (e.g. the ContainerViewModel regression suite).
Write paths are checked against a **stateful fake pod** (a set of created URIs) so we assert real
outcomes ("the folder exists on the server") rather than "a mock was called".

> **VM tests + Room:** Room dispatches suspend DAO calls to its own executor, so `advanceUntilIdle()`
> returns while a `viewModelScope` coroutine is still parked on Room. `ContainerViewModelTest` builds
> the in-memory DB with same-thread query/transaction executors (`.setQueryExecutor { it.run() }`) so
> DAO work runs synchronously on the test dispatcher.

## Running

```bash
./gradlew testDebugUnitTest                    # all JVM tests (no device needed)
./gradlew testDebugUnitTest --tests "*Outbox*" # a subset
./gradlew connectedDebugAndroidTest            # CacheKeyManager crypto (needs a device/emulator)
./gradlew createDebugUnitTestCoverageReport    # coverage → app/build/reports/coverage/test/debug/
```

## What is covered

| Area | Test | Key corner cases |
|---|---|---|
| **Container VM (regression)** | `presentation/container/ContainerViewModelTest` | **an offline-created folder stays visible after an online refresh** (the reported bug); an offline-deleted file stays hidden even if the server still lists it; a synced item the server dropped disappears. Verified to **fail on the pre-fix code** and pass after. |
| Outbox write path | `data/repo/outbox/OutboxRepositoryTest` | optimistic enqueue (upload/delete/create-folder/duplicate); drain success/reconcile; **create+delete coalescing**; terminal vs transient error buckets; delete-404-as-success; pending-blob pinned; clearForWebId. **Stateful fake server**: an offline-created folder actually lands on the server after drain; a folder + a file uploaded into it both land; a create cancelled by an offline delete never reaches the server |
| Offline affordance | `presentation/sharing/ManageSharingViewModelTest` | "who has access" shows an offline state (not a raw error) when offline; loads normally when online |
| Read cache | `data/repo/file/FileRepositoryCacheTest` | **offline-serve of a null-ETag cached blob without any network call** (the weak-ETag bug fix); observe/cache/getCached round-trip; pin/unpin without re-download; clear-on-logout deletes rows + files; pending/error observation |
| Resource DAO | `data/local/cache/ResourceDaoTest` | `PENDING_DELETE` excluded from listings; `deleteMissing`/`deleteAllInContainer` preserve pending rows on refresh; **`replaceContainer` never clobbers a locally-pending row the server still lists** (no resurrecting an offline-deleted file); folders-first ordering; pending/error uri queries; per-webId purge |
| Blob DAO | `data/local/cache/BlobDaoTest` | LRU `unpinnedByAge` (oldest first, excludes pinned + pending-upload); `unpinnedSize`; pin/touch; complete-uris |
| Outbox DAO | `data/local/cache/OutboxDaoTest` | `nextActionable` (earliest pending; skips future retries; ignores ERROR/IN_FLIGHT); `resetInFlight`; `countUnfinished`; `deleteByTarget` |
| Converters/mappers | `CacheConvertersTest`, `CacheMappersTest` | enum/list/access round-trips; entity↔domain lossless mapping |
| Sync badge | `presentation/container/SyncBadgeTest` | precedence ERROR > PENDING > OFFLINE > NONE |
| Keystore crypto (device) | `androidTest/.../CacheKeyManagerTest` | 32-byte passphrase persistence; AES/GCM byte + stream round-trips |

## Coverage (line, offline packages)

- `data/repo/outbox` — **96%** (the enqueue/drain/coalesce/error logic).
- `data/local/cache` — **86%** (DAOs 96–99%, entities/converters/mappers 100%; `CacheKeyManager` shows
  0% here because it's exercised only by the device test).
- `data/repo/file` — the offline methods (observe/cache/pin/downloadFile-offline/clear) are covered;
  the class-level figure is low only because `FileRepositoryImplementation` is mostly *online* network
  code (HEAD-fanout listing, download-to-device, upload) that is out of scope for offline tests.

## Notes / gotchas

- **jacoco + Robolectric**: Robolectric loads classes in a sandbox classloader that jacoco misses,
  so coverage reports 0% for Robolectric-run tests unless `testOptions.unitTests.all { jacoco {
  isIncludeNoLocationClasses = true } }` is set (it is, in `app/build.gradle.kts`).
- The DAO tests build a **plain in-memory Room DB** (`inMemoryCacheDb()` in `CacheTestFactory`) — no
  SQLCipher — so they validate query behaviour; encryption is validated separately by the device test.
- Shared test builders live in `app/src/test/.../data/local/cache/CacheTestFactory.kt`.
