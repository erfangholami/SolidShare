# OFFLINE.md — Offline‑first support for SolidShare

Design reference for making SolidShare usable without a connection: browse cached pod content,
queue mutations, and reconcile when connectivity returns.

This doc is the working reference. It is grounded in the actual code as of this writing (repository
signatures, worker setup, DI conventions, and the ASS `0.6.0` API surface). Where a design choice
depends on a Solid‑protocol detail, the relevant spec is cited.

---

## 1. Goal & mental model

**Principle: the local database is the source of truth; the network is a background sync detail
behind it.** The UI reads from a local cache (instant, works offline). Every mutation is applied
optimistically to the cache and recorded as a durable command in a single **outbox**, which a sync
engine drains — respecting order and auth — when the device is online. This is Android's
recommended offline‑first architecture
.

What we are **not** building: a general CRDT/merge engine, offline authentication, or offline
sharing. Those are called out as online‑only (§4).

### Scope decisions (settled)

| Decision | Choice | Why |
|---|---|---|
| Where the cache lives | **App**, not the ASS library | Cache/queue/pinning/conflict‑UI are product concerns. ASS stays a clean Solid transport (stateless‑ish, no Android storage/WorkManager coupling, no AIDL mess). App‑side also makes clear‑on‑logout trivial. |
| Conflict primitive | **In the library** (already present) | ETag reads + `If-Match` writes + distinct 412 are Solid‑HTTP concerns only the transport can surface. ASS `0.6.0` already exposes them (§6). |
| The "single conduit" | **One `SyncGateway` + one durable outbox table** | All pod writes funnel through one owner and one Room‑backed queue = the single source of truth for pending mutations. |
| Read vs write path | **Reads are cache‑first and do NOT queue behind writes** | A folder refresh must not wait behind 50 pending uploads. Reads pass through the gateway for auth/connectivity but run concurrently. |
| Ordering | **Per‑resource, not global** | Ops on the same resource are ordered + coalesced; different resources run in parallel. (Generalizes the contacts per‑account serialization.) |
| Sharing offline | **Online‑only, with a "requires connection" affordance** | `#me` canonicalization + WAC probe + inbox notification all need live reads (§4). |

---

## 2. Architecture

Slots into the existing Clean Architecture without changing the layer contract — the repository
becomes cache‑aware behind the same interface.

```
presentation/ ──observes Flow──▶ data/repo/ (cache-aware)
                                      │
                        ┌─────────────┼──────────────┐
                   read-through   write-through   reactive Flow
                        │             │             (from Room)
                        ▼             ▼                │
                data/local/cache/ (Room) ◀────────────┘   ← single source of truth
                   cached_resource   cached_blob   outbox_op
                        ▲             ▲             │
                        │             │             ▼
                 sync/SyncGateway  (sole owner of pod I/O: auth · connectivity · retry · logging)
                        │             │
                        │             └─drains outbox──▶ ASS managers ──▶ Pod
                        ▼
                 worker/OutboxWorker (NetworkType.CONNECTED, unique work)
                 util/NetworkMonitor  (ConnectivityManager → StateFlow<Boolean>)
```

**Grounding (from the current code):**

- **No Room/SQLite exists yet** — clean slate. KSP is already wired (`app/build.gradle.kts`), so
  Room is a drop‑in `ksp(libs.room.compiler)`.
- Today's only caching is an in‑memory `LruCache<String, DownloadedFile>(20)` +
  ad‑hoc `cacheDir` writes in `FileRepositoryImplementation`. Phase 0 formalizes this into a
  durable, size‑capped, pinnable Room‑backed blob store.
- `NotificationPollingWorker` (unique periodic + `NetworkType.CONNECTED`) is the exact template for
  `OutboxWorker`. Current `UploadWorker`/`DownloadWorker` have **no** network constraint and **no**
  unique‑work dedup — the outbox adds both.
- DI conventions to follow: provide the Room DB + DAOs via a `@Provides object` module (mirror
  `ApplicationModule`/`SolidApiModule`); bind a DAO‑wrapping local data source in `DataSourceModule`
  (`@Binds`, no `@Singleton`, per its convention); bind the cache repo in `RepositoryModule`
  (`@Binds @Singleton`).

---

## 3. Data model (Room)

Three tables. **Phase 0 introduces `cached_resource` + `cached_blob` only**; `outbox_op` arrives in
Phase 1. Keys are stored **decoded** (per the URI‑encoding contract); encode only at the ASS
boundary.

### 3.1 `cached_resource` — metadata mirror of `ContainerItem`

Mirrors every field the app already assembles in `FileRepositoryImplementation.getContainerContents`.

| Column | Source (`ContainerItem`) | Notes |
|---|---|---|
| `webId` (PK part) | context | one cache per logged‑in WebID |
| `identifier` (PK part) | `identifier` | decoded full URI; PK = (`webId`,`identifier`) |
| `parentContainerUri` | derived | for container listing queries |
| `isContainer` | `isContainer` | |
| `name`,`extension`,`mimeType` | same | |
| `resourceType` | `resourceType` | enum |
| `resourceTypes` | `resourceTypes` | `List<String>` via `@TypeConverter` |
| `sizeBytes`,`lastModified`,`createdTime`,`itemCount` | same | epoch millis for times |
| `etag` | `etag` | the conflict validator; may be `null` on weak‑ETag servers (§6) |
| `access` | `access` | `ResourceAccess`, JSON via `@TypeConverter` (already `@Serializable`) |
| `syncState` | — | `SYNCED` in Phase 0; `PENDING_*`/`CONFLICT`/`ERROR` from Phase 1 |
| `cachedAt` | — | last time refreshed from pod → drives staleness ("synced X ago") |

`ContainerItem` and `ResourceAccess` are already `@Serializable`, so converters are trivial.

### 3.2 `cached_blob` — the actual file bytes

Replaces the in‑memory `LruCache` + `DownloadedFile`.

| Column | Notes |
|---|---|
| `webId`,`uri` (PK) | decoded |
| `localPath` | file under app‑private storage |
| `etag`,`mimeType`,`sizeBytes` | `etag` = validator used by `downloadFile` freshness check |
| `pinned` | user hit "Make available offline" → never LRU‑evicted |
| `lastAccessedAt` | LRU eviction ordering for unpinned blobs |
| `state` | `COMPLETE`; `PENDING_UPLOAD` from Phase 1 |

Eviction: unpinned blobs evicted LRU past a configurable cache cap (e.g. 512 MB). Pinned blobs and
Phase‑1 `PENDING_UPLOAD` blobs are never evicted.

### 3.3 `outbox_op` — the single write queue (Phase 1+)

| Column | Notes |
|---|---|
| `id` (PK) | |
| `webId` | whose queue |
| `type` | `UPLOAD` · `DELETE` · `CREATE_FOLDER` · `OVERWRITE` · `RENAME` · `MOVE` · `DUPLICATE` |
| `targetLocalId` / `targetUri` | provisional local id until the server assigns a URI |
| `parentContainerUri` | for creates |
| `payload` | JSON args and/or a `cached_blob` reference for bytes |
| `dependsOn` | `List<opId>` — e.g. upload‑into‑folder depends on the folder create |
| `status` | `PENDING` · `IN_FLIGHT` · `FAILED` · `CONFLICT` · `PARKED` |
| `attempts`,`nextRetryAt` | exponential backoff |
| `errorBucket` | `TRANSIENT` vs `TERMINAL_UNAUTHORIZED` (§5.3) |
| `createdAt`,`updatedAt` | |

---

## 4. What's doable offline (feature matrix)

| Operation | Offline? | Mechanism | Solid gotcha |
|---|---|---|---|
| Browse folders / metadata | ✅ | `cached_resource` | staleness only |
| View file content | ✅ *if cached* | `cached_blob` (pinned or LRU) | must be pre‑cached |
| **Upload photo/file** | ✅ | outbox `UPLOAD` → `createInContainer` (POST) | server assigns URI → provisional id remap |
| **Delete** | ✅ | outbox `DELETE` | ASS `delete` is **unconditional** (§6 gap) |
| Create folder | ✅ | outbox `CREATE_FOLDER` (POST) | children depend on it |
| Overwrite content | ✅ | outbox `OVERWRITE` → `update`/`putRaw` w/ `If-Match` | conflicted‑copy on 412 |
| Rename / Move | ✅ | outbox compound copy+delete | **no native LDP MOVE** |
| Duplicate | ⚠️ | needs source bytes cached; else defer | deep recursive copy |
| Edit contact | ✅ | existing `DIRTY`‑row model | N3 patch, mergeable |
| Add/edit ticket | ✅ | outbox | first‑time typeIndex registration needs net |
| **Share / change access** | ❌ **online‑only** | `RequiresConnection` affordance | `#me` canon + WAC probe + inbox notify need net |
| Accept/add received share | ❌ | — | live WAC‑Allow probe |
| Scan profile/share | ❌ | — | live probe |
| Login / add account | ❌ | — | OIDC is online |

### Online‑only affordance

A reusable `RequiresConnection` gate (driven by `NetworkMonitor`): when offline, Share · Manage
access · Scan · Add‑account are **disabled with an inline "You're offline — connect to share"
hint**, never hidden (users should know the feature exists). Offline‑safe helpers that need no gate:
`SharingRepository.parseDeepLink` / `deepLinkFor` / `bareUrlFor` (pure string codecs).

---

## 5. Sync engine

### 5.1 The `SyncGateway`

The **sole owner of pod I/O**. No repository calls an ASS manager directly anymore. Responsibilities:
centralize connectivity checks, retry/backoff, logging, and (implicitly) auth — the ASS managers
already refresh tokens transparently under every call, so the gateway does **not** manage tokens
(and must not; the app has no token surface by design).

- **Read path (Phase 0):** cache‑first. Serve `cached_resource`/`cached_blob` immediately; if online,
  fetch from the pod, upsert Room, and let the reactive `Flow` push the update to the UI. Offline →
  serve cache, surface staleness. Reads run concurrently; they never sit behind the write queue.
- **Write path (Phase 1):** every mutation writes an optimistic row to `cached_resource`/`cached_blob`
  and appends an `outbox_op`, then fires an expedited drain (like the contacts `requestSync` trigger).

### 5.2 Draining the outbox (`OutboxWorker`)

Unique work + `NetworkType.CONNECTED` (copy `NotificationPollingWorker`). One drain pass:

1. Select `PENDING`/`FAILED`‑past‑`nextRetryAt` ops, **topologically by `dependsOn`**, grouped so
   **same‑resource ops serialize and different‑resource ops run in parallel**.
2. **Coalesce** before executing: `CREATE`→`DELETE` of an unsynced resource = drop both (zero
   network); consecutive `OVERWRITE`s collapse to the latest.
3. Execute via the relevant ASS manager. On success, reconcile Room with server truth (adopt the
   returned URI + fresh ETag), clear the op. On failure, classify (§5.3).
4. **Provisional‑URI remap:** offline‑created resources have a local id
   (`solidshare-local:<uuid>`). `createInContainer`/`post` return the server `Location`; adopt it and
   rewrite every dependent op's `targetUri` — exactly the contacts `SOURCE_ID = podUri` mapping,
   generalized.

Trigger points: connectivity regained (WorkManager constraint), app foreground, user pull‑to‑refresh,
and immediately after enqueuing a mutation (expedited).

### 5.3 Auth & the two‑bucket error model

The ASS `Authenticator` already classifies failures; the outbox mirrors it exactly:

- **Transient** (DPoP‑nonce challenge, 5xx, transport, expired *access* token): keep the op, retry
  with backoff. Access‑token expiry while queued is a **non‑event** — the next manager call refreshes
  lazily (per‑WebID mutex, coalesced; `offline_access` scope guarantees a refresh token). A stale
  DPoP nonce self‑heals via 401‑retry.
- **Terminal‑unauthorized** (`invalid_grant`/`invalid_client` → refresh token dead): **park the whole
  WebID's queue**, flip a "re‑login needed" state, prompt the user. Never drop the queued work; resume
  after `submitAuthorizationResponse`.

### 5.4 Concurrency

Per‑resource serialization via an in‑memory keyed `Mutex` map (generalizes the ASS Authenticator's
`mutexFor(webId)` pattern). The outbox `dependsOn` DAG handles cross‑resource ordering (folder before
its children).

---

## 6. Conflict resolution — grounded in the ASS `0.6.0` API

**What the library already gives us (no change needed):**

- Reads carry the validator: `SolidMetadata.etag` / `getHeaders().getETag()` / `getLastModified()`
  (epoch millis). At the app layer this already flows into `ContainerItem.etag` and
  `DownloadedFile.etag`.
- Conditional writes: `update`, `patch`, `patchRaw`, `putRaw` take `ifMatch: String?`. Pass the cached
  ETag; a mid‑air change yields **412**, surfaced as `SolidNetworkResponse.Error(errorCode = 412)`.
  The library's own sharing code already runs a read‑ETag → conditional‑patch → retry‑on‑412 loop —
  we reuse that shape.
- `create()` uses `If-None-Match: *` internally → **409** if the resource already exists.
- `createInContainer`/`post` (POST) have no precondition — correct for provisional creates (the server
  assigns the URI, so there's nothing to conflict on).

**Strategy by content type:**

| Case | Mechanism | On conflict |
|---|---|---|
| New upload | `createInContainer` (POST) | none possible — adopt returned URI |
| Overwrite file (binary) | `putRaw` / `update` with `ifMatch = cachedEtag` | **412 → conflicted copy**: keep server version, re‑upload local as `name (conflicted copy YYYY‑MM‑DD).ext`, surface in "Needs attention" |
| RDF (contacts/tickets) | `patch` with `ifMatch` | 412 → re‑read, re‑derive N3 patch, retry (triple‑level merge; same‑triple collisions prompt) |
| Delete | `delete` (unconditional) | default **delete wins**; optional soft guard = HEAD‑before‑delete and prompt if `etag`/`lastModified` drifted |

**Library gaps (all optional hardening, none block Phase 0/1):**

1. **Conditional delete** — `delete` has no `ifMatch`. Without it we can't atomically detect a
   remote change before deleting. Default "delete wins" is acceptable; add `ifMatch` to `delete` for
   safe conditional deletes.
2. **Weak‑ETag servers** — `getETag()` returns `null` for `W/"…"` validators (NSS,
   solidcommunity.net emit these for RDF). Conditional writes then silently fall back to
   **unconditional (last‑write‑wins)** → *no conflict detection on those servers*. Fallback lever:
   compare `Last-Modified`; a real fix needs a write path that consumes `If-Unmodified-Since`.
3. **`If-None-Match` with an explicit ETag** isn't caller‑exposed (only `create()` uses `*`). Only
   needed for exotic conditional‑create semantics; `create()`'s 409 covers create‑only.

**Bonus UX — soft‑delete undo window:** enqueue deletes with a short delay before the op fires,
giving a Drive‑like "Undo" and a local "recently deleted" grace period at no extra cost (Solid delete
is otherwise permanent — there is no server trash).

> Solid grounding: ETag/conditional‑request semantics per **Solid Protocol** (HTTP conditional
> requests, `ETag`/`If-Match`). Sharing is online‑only because access grants are WAC/ACP writes and
> share offers are **Solid Notifications** POSTs to the receiver's inbox.

---

## 7. View‑section indicators (for users *and* for us)

Per‑item status surfaced as a small badge/overlay, driven by `cached_resource.syncState` +
`cached_blob`:

- ✓ **Synced** · ⟳ **Pending** (queued upload/edit/delete; sub‑state "uploading…") ·
  ⚠ **Conflict/Error** → tap to resolve *(Pending/Conflict are Phase 1)*
- ● **Available offline** (pinned, full local copy) vs ○ **metadata‑only** (visible, can't open
  content offline) *(Phase 0)*
- Global **offline banner** ("Showing your last synced copy") + connectivity/sync chip in the top
  bar, driven by `NetworkMonitor` *(Phase 0)*
- **"Synced X ago"** staleness from `cachedAt` *(Phase 0)*
- **Dev diagnostic** surface: dump `outbox_op` rows + last error per item — free once the outbox
  exists, and the fastest way for *us* to see what the queue is doing.

---

## 8. Phased roadmap

| Phase | Scope | Risk |
|---|---|---|
| **0 — Read cache** | Room (`cached_resource`+`cached_blob`), cache‑aware reads, `NetworkMonitor`, offline browsing, pinning + LRU blobs, offline banner + local‑copy + staleness indicators, clear‑on‑logout | 🟢 read‑only, no conflicts |
| **1 — Write outbox** | `outbox_op` + `SyncGateway` write path + `OutboxWorker`; **upload · delete · create folder**; optimistic UI; coalescing; provisional‑URI remap; 412 conflicted‑copy; two‑bucket auth handling; Pending/Conflict badges | 🟡 the core ask |
| **2 — Move set** | rename · move · duplicate (compound ops) | 🟡 |
| **3 — Data modules** | contacts + tickets offline (contacts largely there via `DIRTY` model) | 🟢 |
| **4 — Sharing** | keep online‑only; polish the `RequiresConnection` affordance | 🔴 (deliberately not queued) |

---

## 9. Phase 0 — detailed plan

**Deliverable: browse and open your pod content with no connection, with clear cache/staleness
indicators. No writes, no outbox, no conflicts.**

1. **Dependencies** — add Room to `gradle/libs.versions.toml` (`room` + `room-runtime`/`room-ktx`/
   `room-compiler`); in `app/build.gradle.kts` apply `ksp(libs.room.compiler)` beside the existing
   `ksp(...)` lines.
2. **`data/local/cache/`** — `SolidCacheDatabase` (`@Database`), `ResourceDao`, `BlobDao`, the two
   entities, `@TypeConverter`s for `List<String>` + `ResourceAccess`/`ResourceType`.
3. **DI** — `@Provides object` module for the DB + DAOs (mirror `ApplicationModule`); a
   `@Binds` DAO‑wrapping local data source in `DataSourceModule`.
4. **`util/NetworkMonitor.kt`** — `ConnectivityManager.registerDefaultNetworkCallback` →
   `StateFlow<Boolean>`; provided as a `@Singleton`. Drives the offline banner and the
   `RequiresConnection` gate.
5. **Cache‑aware `FileRepository`** — add `observeContainer(webId, containerUrl): Flow<List<ContainerItem>>`
   backed by `ResourceDao`, plus a `suspend refresh(webId, containerUrl)` that fetches from the pod and
   upserts. Keep existing suspend methods for compatibility. `downloadFile` becomes `cached_blob`‑backed
   (persist path+etag; keep the existing HEAD‑ETag freshness check, which already tolerates being
   offline). Add `setPinned(uri, Boolean)` + cache‑cap LRU eviction.
6. **UI** — `ContainerViewModel` collects `observeContainer` and calls `refresh()` (pull‑to‑refresh /
   `ON_RESUME`); render offline banner, local‑copy/available‑offline badges, "synced X ago". Add a
   "Make available offline" action to the resource actions sheet.
7. **Clear‑on‑logout** — on `removeProfile`, purge that WebID's `cached_resource` + `cached_blob`
   rows and files (reuse the contacts‑mirror teardown precedent).
8. **Verify** — airplane‑mode browse of a previously visited folder; pinned file opens offline;
   unpinned‑and‑evicted file shows metadata‑only; banner + staleness update on reconnect.

**Open decisions for Phase 0:**

- **Cache cap & auto‑cache policy** — proposed: metadata always cached; blobs via explicit pin **plus**
  auto‑cache‑on‑view LRU under a configurable cap (default 512 MB) + a storage‑management screen.
- **Encryption at rest** — cached pod data is personal. Floor = clear‑on‑logout (in plan). Optional =
  `EncryptedFile` for blobs / SQLCipher for the DB. Decision: floor now, encryption as a fast‑follow?

---

## 10. Existing conventions this reconciles with

- **Contacts `SyncAdapter`** — the proven template: upload‑before‑download ordering,
  `SOURCE_ID = podUri` provisional mapping, hash‑drift compare, "expedited manual sync = drain now."
  Its "queue" is Android's `RawContacts` `DIRTY`/`DELETED` columns — which files lack, hence our own
  `outbox_op`.
- **ASS Authenticator** — the two‑bucket error model (§5.3) and per‑WebID mutex, copied verbatim in
  spirit.
- **WorkManager** — `NotificationPollingWorker` (unique + `NetworkType.CONNECTED`) as the
  `OutboxWorker` template; `UploadWorker`/`DownloadWorker` fold into the outbox.
