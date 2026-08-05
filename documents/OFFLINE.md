# Offline-first

*Part of the [Solid Share documentation set](README.md).*

Solid Share opens on the underground. Your files, your contacts and your wallet are already on the
screen, drawn from an encrypted database on the device, and anything you do to them — upload a
photo, delete a folder, add a ticket, merge two contacts — is applied immediately and queued as a
durable command that drains when the connection comes back. The local database is the source of
truth for the screen; the pod is the source of truth for the data, and the gap between the two is
a queue rather than a spinner.

The one deliberate exception is sharing, which is online-only for a reason stated in §4.

## 1. Pod shape

Offline support owns nothing on the pod. It owns one thing on the device: `solid_cache.db`, a Room
database encrypted with SQLCipher (the passphrase is generated once and held in the Android
Keystore by `data/local/cache/CacheKeyManager.kt`), currently at **version 7**, with five tables:

| Table | Entity | Holds |
|---|---|---|
| `cached_resource` | `CachedResourceEntity` | The file browser's metadata mirror: one row per resource per WebID, with `etag`, `access`, `syncState`, `cachedAt` |
| `cached_blob` | `CachedBlobEntity` | Downloaded bytes: `localPath`, `etag`, `pinned`, `lastAccessedAt`, `state` (`COMPLETE` / `PENDING_UPLOAD`) |
| `outbox_op` | `OutboxOpEntity` | The **file** queue: `UPLOAD` · `CREATE_FOLDER` · `DELETE` · `COPY`, each with a blob path for pending bytes |
| `cached_entity` | `CachedEntityEntity` | Every data module's rows: PK `(module, webId, uri)`, generic `sortKey` / `groupKey` / `searchText`, everything else in `detailJson` |
| `module_outbox_op` | `ModuleOutboxOpEntity` | Every data module's queue: `(module, webId, type, payload, status, attempts, nextRetryAt, …)` with module-defined `type` and `payload` strings |

Blob bytes live outside the database as encrypted files in app-private storage; the row holds the
path. `TicketBlobStore` is the same idea for pass images and `.pkpass` artifacts.

Every table is keyed by `webId`. Switching accounts switches the whole surface, and signing out
purges exactly that account's rows and files.

## 2. Surface

| Piece | Where | What it owns |
|---|---|---|
| `NetworkMonitor` | `util/NetworkMonitor.kt` | `ConnectivityManager` → `StateFlow<Boolean>`. The single answer to "are we online" |
| `OutboxRepository` | `data/repo/outbox/OutboxRepositoryImplementation.kt` | The file queue: enqueue, drain, backoff, error classification, per-WebID clear |
| `ModuleOutbox` | `data/repo/outbox/ModuleOutbox.kt` | The same policy written once for every data module — see [DATA_MODULES.md](DATA_MODULES.md) |
| `OutboxTrigger` | `data/repo/outbox/OutboxTrigger.kt` | The seam that lets `data/` ask for a drain without importing `worker/` |
| `OutboxWorker` / `ModuleOutboxWorker` | `worker/` | The two drains, both `NetworkType.CONNECTED`, both unique work |
| `ResourceDao` / `BlobDao` / `OutboxDao` / `CachedEntityDao` / `ModuleOutboxDao` | `data/local/cache/` | The queries, including every `syncState` filter |
| `CacheKeyManager` | `data/local/cache/CacheKeyManager.kt` | The Keystore-held SQLCipher passphrase |
| `RequiresConnection` | `presentation/components/RequiresConnection.kt` | The one affordance every online-only surface uses |
| `SyncBadge` | `presentation/container/` | Per-item status: error > pending > offline > none |

`AppError.Offline` and `AppError.ServerUnreachable` are the two causes the error layer distinguishes
by asking `NetworkMonitor`; see [ERRORS.md](ERRORS.md).

## 3. How it flows

### A read

1. The ViewModel collects a `Flow` from a repository (`observeContainer`, `observeContacts`,
   `observeTickets`).
2. The repository returns the Room `Flow` immediately, so the screen paints from cache with no
   round trip.
3. In parallel it refreshes from the pod through a library manager.
4. On a **clean** fetch it reconciles: upsert what came back, then delete only `SYNCED` rows that
   were absent. Rows in a pending state are never pruned, and a fetch that raised anything prunes
   nothing — a partial failure must not look like a deletion.
5. Room emits again and the screen updates.

### An offline write to a file

1. `enqueueUpload` / `enqueueCreateFolder` / `enqueueDelete` / `enqueueDuplicate` writes the
   optimistic row into `cached_resource` with a `PENDING_*` state, so the listing shows the change
   at once. An upload's bytes are copied into the encrypted blob store as `PENDING_UPLOAD`, so the
   source `content:` URI is free to go away.
2. The op is inserted into `outbox_op` and `OutboxTrigger.requestDrain(OutboxQueue.FILES)` is
   called.
3. `WorkManagerOutboxTrigger` enqueues `OutboxWorker` with a connectivity constraint.
4. The drain walks actionable ops in id order — `PENDING`, plus `FAILED` ones whose `nextRetryAt`
   has passed — and executes each against the pod.
5. Success reconciles the cache with server truth (adopting the URI the server assigned to a POST)
   and deletes the op. Failure is classified (§4).

**Coalescing.** Deleting a resource that is still `PENDING_CREATE` does not queue a delete for
something that never existed: the queued create is removed, its blob deleted, and the optimistic
row dropped. Nothing reaches the network.

### An offline write in a data module

Same shape, one queue lower: the module writes its optimistic row into `cached_entity` under a
**provisional URI**, encodes a payload, and enqueues into `module_outbox_op`;
`DataModuleRegistry.drainPending()` routes the drain to whichever modules have work. Editing a
still-queued create rewrites that op instead of adding a second one. The full walk-through is in
[DATA_MODULES.md](DATA_MODULES.md).

### Coming back online

WorkManager releases both workers when the connectivity constraint is met. Nothing polls, and
nothing re-drives the queue from the UI — reconnecting is the event.

## 4. Offline and failure behaviour

### What works with no connection

| Operation | Offline | Mechanism |
|---|---|---|
| Browse containers and metadata | yes | `cached_resource` |
| Open a file | if its bytes are cached | `cached_blob` — pinned, or still in the LRU window |
| Upload a file or photo | yes | `outbox_op` `UPLOAD` (POST, so the server mints the URI) |
| Create a folder | yes | `outbox_op` `CREATE_FOLDER` |
| Delete a resource | yes | `outbox_op` `DELETE` |
| Duplicate a resource | yes | `outbox_op` `COPY`, reset to owner-only on drain |
| Browse contacts and tickets | yes | `cached_entity` |
| Add, edit, delete, merge a contact or ticket | yes | `module_outbox_op` with a provisional URI |
| Import a `.pkpass` | yes | the artifact and its images are stored locally, queued with the ticket |
| **Share, change access, revoke** | **no** | `RequiresConnection` |
| Add a received share, scan, confirm access | no | needs a live WAC/ACP probe |
| The notifications hub | no | the inbox is not cached — see [NOTIFICATIONS.md](NOTIFICATIONS.md) |
| Log in, add an account | no | OIDC is online by definition |

**Why sharing is online-only.** A grant is a WAC or ACP write on someone's pod, its receiver's
WebID has to be canonicalized against a live profile, and the offer is a POST to that receiver's
inbox. None of that can be faked locally, and a share that silently did not happen is worse than
one that refused. So every such surface carries the same `RequiresConnection` affordance —
disabled with an inline explanation, never hidden, because a user should still be able to see that
the feature exists.

### Retries and terminal failures

Both queues use the same policy: exponential backoff stored **on the row**, so a restart does not
reset a failing op's schedule. The file queue doubles from 2 seconds and caps at 30 minutes; the
module queue doubles from 30 seconds and caps at an hour.

Failures split two ways. A terminal HTTP status — 400, 401, 403, 405, 409, 410, 412, 415, 422 —
marks the op `ERROR` and stops retrying it, because retrying a refusal only burns battery.
Everything else is transient: `FAILED`, `attempts + 1`, `nextRetryAt = now + backoff`. The failing
resource carries an error badge, and the row's `lastError` is kept for diagnosis only — it never
becomes a user-facing message (see [ERRORS.md](ERRORS.md)).

### Eviction, encryption and clearing

Unpinned blobs are evicted oldest-first past a 512 MB cap. Pinned blobs ("Make available offline")
and `PENDING_UPLOAD` blobs are never evicted — evicting the latter would destroy an unsent upload.
Everything is encrypted at rest: SQLCipher for the database, and the blob files are encrypted with
a Keystore-held key. Signing out purges that WebID's rows and files.

### The honest gap: conflicts

`SyncState.CONFLICT` exists in the schema and **is never set**. There is no conflicted-copy flow.
A queued write that lands after someone else changed the same resource wins, and the loser is the
remote change. This is a stated position, not an oversight:

- Creates are POSTs, so they cannot collide — the server mints the URI.
- Deletes are unconditional; delete wins.
- The data modules write through the library's N3-patch path, which re-reads and re-derives its
  diff under `If-Match`, so two clients editing different fields of the same contact merge rather
  than clobber.

What is left uncovered is a binary overwrite of a file that changed remotely while queued. Adding
it means an `If-Match` write, a 412 branch, a conflicted-copy resource and a resolution UI — worth
doing when overwrite-in-place becomes a real user path, not before. Note also that the servers
most likely to need it (NSS, solidcommunity.net) emit weak ETags, which do not survive as
validators, so conditional writes there would silently degrade to last-write-wins anyway.

### Queued work survives schema changes

The database once used `fallbackToDestructiveMigration(dropAllTables = true)`. A new data module
meant new tables, which meant a version bump, which dropped **every** table — including the queues.
Cached rows are rebuildable from the pod; queued writes are the user's unsynced work and exist
nowhere else. `MIGRATION_5_6` folded the per-module queues into `module_outbox_op` carrying every
queued row across, and `MIGRATION_6_7` did the same for the caches into `cached_entity`. Because
both tables are now module-agnostic, a new module needs no schema change at all — the class of bug
is gone rather than managed. The destructive fallback remains only for a database with no path at
all. **Never bump the version without a real migration.**

## 5. Extension points

- **A new queued operation** on files: add an `OpType`, an `execute*` branch, and the optimistic
  cache write. The backoff, the error buckets, the worker and the badges are already there.
- **A new data module**: nothing here changes. Both generic tables key by module id — see
  [DATA_MODULES.md](DATA_MODULES.md).
- **A new online-only surface**: use `RequiresConnection` / `RequiresConnectionHint`. Do not write
  a bespoke "you're offline" string; the whole point of the single component is that "what does
  this app do offline" has one answer per screen.
- **A new drain trigger**: `OutboxTrigger` is the seam. `data/` must never import `worker/`, and
  `ArchitectureTest` fails the build if it does.

## 6. Tests

Detailed in [TESTING.md](TESTING.md); the ones that pin decisions rather than plumbing:

- `presentation/container/ContainerViewModelTest` — an offline-created folder stays visible after
  an online refresh, an offline-deleted file stays hidden even when the server still lists it, and
  a synced item the server dropped disappears. Written against the pre-fix code first, where it
  failed.
- `data/repo/outbox/OutboxRepositoryTest` — enqueue, drain, backoff, terminal-vs-transient buckets,
  delete-404-as-success, create-then-delete coalescing, and `clearForWebId`, all against a stateful
  fake pod so the assertion is "the folder exists on the server", not "a mock was called".
- `data/local/cache/ResourceDaoTest` — `replaceContainer` never clobbers a locally-pending row the
  server still lists, which is what stops an offline-deleted file resurrecting on refresh.
- `data/local/cache/CacheMigrationTest` — both migrations, including a provisional offline create
  surviving the fold into the generic tables.
- `data/local/cache/BlobDaoTest` — LRU ordering excludes pinned and pending-upload blobs.
- `androidTest/…/CacheKeyManagerTest` — the Keystore passphrase and AES/GCM round-trips, on a real
  device, because Robolectric cannot shadow the Keystore.

## 7. Specifications

- [Solid Protocol](https://solidproject.org/TR/protocol) — conditional requests (`ETag`,
  `If-Match`), and the POST-to-container semantics that make a provisional URI workable.
- [Linked Data Platform](https://www.w3.org/TR/ldp/) — POST-to-create, which is why an offline
  create needs no client-side URI.
- [N3 Patch](https://solidproject.org/TR/protocol#n3-patch) — how the data modules' queued RDF
  writes merge instead of clobbering.
- Deliberately **not** implemented: a CRDT or merge engine, offline authentication, and offline
  sharing. Each is listed here so a reader can tell a decision from an omission.
