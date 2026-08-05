# Modularization plan — AndroidSolidServices + Solid Share

*Part of the [Solid Share documentation set](README.md). This is a record of work done, not a
guide — for how to add a data module today, read [DATA_MODULES.md](DATA_MODULES.md).*

> **Status: complete**, except phase 6 (Gradle feature modules), which remains deliberately
> optional until build times justify it. Every other phase is done and verified in both repos;
> residual follow-ups live in the per-row notes and §8.2.
> This is the plan to make both codebases grow-by-addition instead of grow-by-editing, before
> the next wave of data modules lands. It spans two repositories: the **ASS library**
> (`../AndroidSolidServices`) and the **Solid Share app** (this repo).

## 0. Progress

| Phase | State | What landed |
|---|---|---|
| 0 — enforcement + docs | ✅ done | Architecture tests in **both** repos (`app/src/test/…/architecture/ArchitectureTest.kt`, `api/src/test/…/architecture/LayeringTest.kt`) with shrink-only baselines; doc index + template in `documents/`; `docs/features/` + MkDocs nav in the library |
| 2a — library `Shared` fixes | ✅ done | One neutral catcher (`solidCatching`; `runResult` deleted, 29 call sites migrated); `nowIsoDateTime` moved to `shared/util`; both cross-package leaks gone; `SettingTypeIndex` no longer knows about contacts; **`AddressBookRDF` crash-on-partial-document fixed** (four `!!` getters are now honestly nullable, callers treat absence as "empty") with regression tests |
| 1 — library auth seams | ✅ done | All six §5.2 seams landed on top of the hardening tranche. **`SolidSession`** is public and `Authenticator` extends it — the transports (`SolidHttpClient`, notification transport, WebSocket client) take the seam and the `asSession()` downcast is deleted. **Refresh owns its own scope**: single-flight `Deferred` per WebID on a coordinator-owned `SupervisorJob` scope, so caller cancellation can no longer lose a rotation (`NonCancellable`-by-convention gone); pinned by `TokenRefreshOwnScopeTest` (the deferred double-spend regression). **`RefreshPolicy`** split from the coordinator (lead time, coalescing, forced cooldown, 429 windows — fake-clock tested). **`SessionState`** is the one classification (`Active | NeedsReauth | Revoked`, deadness blind to completeness); account flows, dead-gate and reconciler are projections. **Store-first reads**: `ProfileManager` persists-then-publishes under one lock — the `recentWrites` overlay and the stale-emission reconciler branch are deleted, not patched. **Quarantine, never wipe**: a corrupt store is copied aside as `profiles.json.quarantined.<ts>` before an empty one replaces it (`ProfileStoreQuarantineTest`). §5.3 cleanup: `AuthTrace` logcat instrumentation removed (telemetry is the record), 401 classifier landed earlier (`AuthChallenge`, 8 tests), `submitAuthorizationResponse` decomposed into stages. **detekt is now fully green** — the 4 baselined findings dissolved with the split — and the LayeringTest comments baseline is empty (issuer-URL knowledge moved into `IssuerUrlTest` names and `docs/features/auth.md`) |
| 2b — collection engine + `datamodule/` root | ✅ done | `EntityCollection` + `CollectionSpec` + `PodCollections` in `api/datamodule/core/` own bootstrap, type-index registration incl. legacy migration, per-entity container allocation, index CAS, attachments and `findInContainer`; tickets migrated onto it (helper 436 → 298 lines), contacts uses the shared registry/attachment/storage verbs; both module roots repointed to `{storage}datamodule/{segment}/`; 8 new engine tests |
| 2c — IPC collapse + neutral test fixtures | ✅ done | `InMemoryPodResourceManager` moved to `api/testing/` with the missing verbs implemented, plus an `inMemoryPod(…)` seed. **IPC callback zoo collapsed (§4.3e):** two generic callbacks (`IASSParcelableCallback`/`IASSParcelableListCallback`) + `IpcEnvelope` (Bundle keys, pack/unpack, class-loader handling) + `CallbackBridges` in `:client` — 30 per-type callback AIDLs deleted, 41 anonymous `Stub()` bridges → 2 generic factories, public SDK signatures unchanged; envelope round-trips (incl. the sealed `AccessProbe`) pinned by 10 real-`Parcel` Robolectric tests; library suite 477 green |
| 3 — app persistence spine | ✅ done | One `module_outbox_op` table + `ModuleOutbox` + one `ModuleOutboxWorker` replace two entities, two DAOs, two workers and two drain loops; **`MIGRATION_5_6` carries queued writes across** instead of the destructive fallback dropping them. Then `cached_entity` (PK `(module, webId, uri)`, generic `sortKey`/`groupKey`/`searchText`) replaced `cached_contact`/`cached_ticket` via **`MIGRATION_6_7`**, so a new module changes **no schema at all**; both migrations pinned by `CacheMigrationTest` (6 tests, incl. a provisional offline create surviving) |
| 4 — app contributor seams | ✅ done | `DataModuleLifecycle` + `DataModuleRegistry`; `SharedEntityUi.homeCard`/`resolveName`; `ReceiverPickerContributor` (breaks sharing → contacts); `NavGraphContributor` + `NavGraphRegistry`; and `ScanContributor` + `ScanRouter` — `ScanViewModel` and `MainViewModel` lost `TicketsRepository`/`TicketDraft`/`TicketImportHolder`, deep links and open-with files route through one `pendingModuleRoute`. A **fourth architecture rule** forbids generic presentation reaching a module's data layer (baseline: `PublicProfileViewModel` — the QR add-to-contacts flow, pending an add-to-module seam); the other three baselines are empty. §6.4 housekeeping: `ContactsMergePrefs` cleared on logout, `SolidAccountManager` renamed `ContactsAccountManager`, CLAUDE.md DI section corrected |
| 5 — feature docs | ✅ done | App: `ARCHITECTURE.md`, `DATA_MODULES.md`, `AUTH.md`, `FILES.md`, `NOTIFICATIONS.md` (757 lines) written and indexed. Library: all ten pages in `docs/features/` (3,061 lines) — auth, resources, access-control, sharing, notifications, data-modules, tickets, contacts, type-index, telemetry — with the MkDocs nav wired |
| device-test feedback round (2026-08-02) | ✅ done | **Jank**: ticket open no longer blocks main — ZXing barcode renders via bulk `setPixels` off-main (`produceState` + `Dispatchers.Default`; was ~78k `setPixel` JNI calls during composition), pass images decode off-main, `TicketBlobStore` is `suspend` + `Dispatchers.IO`, pkpass parse/extract moved to `Default`; file open/download paths (`decryptToOpenTemp`, stream copies, `persistBlob`) wrapped in `Dispatchers.IO` so the existing `isDownloading` indicator can actually draw. **Legacy layouts deleted** (pre-release, no published data): `CollectionSpec.legacyIndexDocumentName`, `EntityCollection`'s `instanceContainer` migration + flat-layout branches, `LEGACY_TICKETS_INDEX_FILE_NAME`, flat delete/artifact paths, `registeredContainers` — sharing's own container registration untouched. **Address-book management shipped**: `AddressBooksPage` (create private/public, rename, delete w/ warning) via new repo verbs + `CachedEntityDao.deleteByGroupKey`; registered through `ContactsNavGraph` — zero generic files edited, as designed |
| tidy-up — library packages (0.7.0 list, pulled forward) | ✅ done | `api/repository/` folded into **`api/auth/store/`** (it was only ever auth's persistence); **`api/transport/`** extracted (`SolidHttpClient`, `AuthChallenge`, `SolidResponseCache`, plus the old `api/http` pair `SolidRawResponse`/`TelemetryOrigin`); `Shared/error` merged into `shared/result`; **`Profile`/`ProfileList` internalized** — the last AppAuth types in public API are gone (`SolidAccount` is the account surface). Pre-1.0 policy: moved outright, consumers updated; SolidShare imports none of the moved symbols |

Both repos are green at each checkpoint: full unit suites, spotless, and the architecture tests;
the library is republished to mavenLocal as 0.6.1 and the app builds against it.

## 1. Why

Two data modules exist today — contacts and wallet/tickets — and more are coming. They are the
same shape: a collection of entities on a pod, cached locally, edited through an outbox, listed
and detailed in the UI, shareable as a data identity. Only the *data* differs. Yet adding one
today means writing the same machinery again in both repos and editing a dozen generic files
that have no business knowing a new module exists.

Three symptoms, all of them structural:

1. **Copy-paste per module.** The pod-side plumbing (container bootstrap, type-index
   registration, index-document row cache, CAS writes, attachment naming, per-entity container
   layout) and the app-side plumbing (cache-first repository, Room entity + DAO, outbox +
   payloads + drain loop + backoff + provisional URIs, worker, mappers, DI bindings, routes)
   are near-identical between contacts and tickets.
2. **Generic code names specific modules.** Logout clearing, outbox draining, the Share tab,
   notifications, scanning and deep links each enumerate the modules they know about. Every new
   module edits all of them — the classic open/closed violation.
3. **Nothing enforces the architecture.** The library has spotless + detekt; the app has
   **no static analysis at all** (no ktlint, no detekt, no architecture tests), so layering and
   the no-comments rule survive only by attention.

Auth is a separate case with the same root disease — it is not a god class (19 files, ~2.4k
lines, already split into coordinator/generator/verifier/profile-manager), but its *seams* are
wrong, and the bugs come from that. See §5.

## 2. Principles

These are the rules the target design has to satisfy. Everything in §4–§6 is derived from them.

1. **A new data module is additive.** Adding one means writing new files and registering them;
   it must not require editing any generic file. If a switch statement or a fan-out list grows
   per module, it is a defect in the design.
2. **The framework owns the pattern; the module owns the data.** Everything that is the same
   for every module (layout, index, CAS, cache, outbox, drain) lives once. A module contributes
   a *description* of itself: its RDF codec, its type IRIs, its layout names, its attachments.
3. **Seams are interfaces with a no-op or default implementation**, in the spirit of the
   existing `Telemetry` seam: dependencies point at the seam, never at a concrete
   implementation, and never at a *cast* of one (see `asSession()` in §5).
4. **The durable store is the source of truth.** Caches and flows derive from it; nothing
   important is reconstructed from an in-memory snapshot that may lag.
5. **Public API is a deliberate act.** `explicitApi()` already forces visibility in the library;
   the app should gain the same discipline through architecture tests instead of review.
6. **Documentation is part of the feature.** Every feature carries an `.md` describing how it
   works (§7). A feature without its doc is unfinished.
7. **No comments in code except KDoc on public declarations** — unchanged, and now enforced
   mechanically rather than socially.

## 3. What is *not* proposed

Being explicit about the roads not taken, and why:

- **No Gradle-module split of the published library.** The ClientSample consumes ASS through a
  composite build that substitutes exactly three coordinates (`api`, `shared`, `client`), and
  those are the published Maven artifacts. Splitting `:api` would change the public artifact set
  for every consumer to buy a compile-time boundary we can get from architecture tests. Three
  further obstacles confirm it: the shared seams a module needs (`TypeIndexResolver`,
  `casUpdate`, `StorageDiscovery`) are **`internal` to `:api`**, so a split forces either
  promoting them to public — contradicting their intent — or extracting a `:datamodule-core`
  artifact; `IASSDataModulesService.aidl` **imports each module's interface**, so per-module
  Gradle modules invert that dependency; and each new published module adds ~60 lines of
  publishing boilerplate, a detekt baseline and a row in the release matrix. Revisit only if
  build times demand it. (Related cleanup: `build-logic/` exists on disk but contains only build
  output — no sources, no `includeBuild`. Delete it or make it real convention plugins.)
- **No feature-module split of the app, yet.** It is a single 226-file / ~38k-line module.
  Gradle feature modules would pay off in build time and hard boundaries, but they impose real
  cost on Hilt, Compose previews, navigation and the single Room database. The plan gets the
  *package* boundaries and the registry right first (§6); the Gradle split becomes mechanical
  afterwards and is listed as an optional later phase.
- **No rewrite of the auth tranche in flight.** A concurrent debug session is actively fixing
  live session-loss bugs across the whole auth surface, uncommitted. This plan sequences after
  it and consolidates what it learned (§5).

## 4. ASS library — the data-module toolkit

### 4.1 What a module costs today

Tickets is 5 files / 758 lines in `:api`; contacts is 9 files / 1,060. Adding module #3 means
**19 new files and 8 edited ones, ~2,000–3,300 new lines, touching all four Gradle modules** plus
five test source sets — of which **~900–1,100 lines (35–45 %) is mechanical duplication**.

A 22-item census found the same logic written twice, often verbatim:

- `requireStorage`, `ensureContainer`, `deleteTolerant` (404 ⇒ OK), the binary read
  `(uri, contentType, bytes)`, and the LDP `NonRDFSource` link-header PUT are **byte-identical**
  in both modules.
- `TicketArtifact` and `ContactPhoto` are the **same Parcelable twice**, hand-written
  `equals`/`hashCode` included.
- `findInContainer` is the same ~30-line algorithm in both (I wrote the second copy myself, in
  this session — which is the point).
- `casUpdate(...) { read = { rm.read(...) }, mutate = {...} }` appears at **11 call sites**;
  "CAS-update the index document" is a 4th copy of one 8–12-line helper.
- "Mint a UUID → ensure the container → compose the document URI" appears **4 times**; container
  path arithmetic **6 times**.
- Singleton `@Volatile` + double-checked `getInstance` ×2 appears **5 times** across `:api` and
  `:client`.

Two of these have already **diverged into a bug surface**: content-type→extension mapping has 7
branches falling back to `.bin` in tickets and 4 falling back to `""` in contacts, and "404 means
absent" is expressed as `runCatching{}.getOrDefault(emptyList())` in one module and
`dataOrNullIfMissing()` in the other.

### 4.2 Three structural problems behind the duplication

1. **No canonical layering.** Tickets is *thin engine + 447-line singleton helper*; contacts is
   *fat engines + 132-line non-singleton pod access*. There is no rule, so module #3 picks one at
   random and the divergence compounds. Evidence that the boundary is already leaking:
   `TicketEngine` imports `runResult` from **`datamodule.contacts.implementation`**, and the
   tickets helper imports `nowIsoDateTime` from **`sharing.implementation`**.
2. **The reusable primitives are private or misplaced.** The typed RDF accessors
   (`str/bool/follow/putStr/putDate/putEnum/…`, ~60 lines) are **private members of `TicketRDF`**.
   `ContactRDF` re-implements them by hand; `AddressBookRDF` instead uses `quads.find{…}!!`, which
   **crashes on a partial or foreign document** where `TicketRDF` would return `null`. Three
   near-identical error catchers (`runResult`, `wrapSharing`, `solidCatching`) exist, none in a
   neutral home.
3. **`Shared` knows about specific modules.** `SettingTypeIndex` carries contacts-only members
   (`getAddressBooks`/`addAddressBook`/…) bolted onto the generic `getInstances`/`addInstance`,
   and the `SolidShare` vocabulary object mixes sharing terms with 138 ticket terms in one file.

### 4.3 Target: a generic collection engine + a per-module spec

**a. One shape, enforced.** Every data module is: `SolidXDataModule` (facade) → one or more
`XStore` interfaces (public verbs) → **the generic engine** → `SolidResourceManager`. The
fat-helper/fat-engine choice disappears because the machinery is no longer per-module code.

**b. `EntityCollection<M, N, R : SolidRDFResource>` in `api/datamodule/core/`** — one
implementation of everything in the §4.1 census: container bootstrap, type-index registration
including the legacy `instanceContainer` migration, UUID allocation and per-entity container
layout, index-document row caching with CAS rewrite on every write, attachment put/get with a
**single** content-type↔extension table, tolerant delete, `findInContainer`, foreign-pod reads,
and one "404 means absent" rule.

**c. The module contributes a spec, not machinery:**

```kotlin
public interface DataModuleSpec<M, N, R : SolidRDFResource> {
    val entityTypeIri: String                  // schema:Ticket | vcard:Individual
    val indexTypeIri: String?                  // solidshare:TicketIndex, when the module has one
    val layout: CollectionLayout               // container, document and index names
    val codec: Class<R>                        // constructed reflectively, as today
    val attachments: List<AttachmentSlot<M>>   // artifact + 6 image roles | one photo
    fun summary(entity: M): IndexRow
}
```

Contacts is the harder case and the one that proves the design: it is a *nested* collection
(books → contacts → groups). The engine therefore takes the parent container as a parameter
rather than assuming a single root — tickets pass the tickets container, contacts pass the book.
If that does not hold when contacts is migrated, the honest outcome is that contacts keeps a thin
module-specific layer on top of the engine, not that the engine grows special cases.

**d. `Shared` fixes that pay for themselves immediately:**
- Promote the typed RDF primitives from `TicketRDF`'s private section onto `RDFResource` as
  protected/extension functions. This deletes ~60 lines per module **and fixes the
  `AddressBookRDF` non-null-assertion crashes**, because the shared accessors return `null`.
- One `runResult` in a neutral package; delete `wrapSharing` and the duplicate catcher; move
  `nowIsoDateTime` to `Shared` util. That also removes the tickets→contacts and
  tickets→sharing package leaks.
- Move the contacts-specific members off `SettingTypeIndex`, and split the vocabulary so a module
  owns its terms (`vocab/Tickets.kt`) while `SolidShare.kt` keeps only sharing.

**e. The IPC tax is the second-biggest cost** (~340 lines/module: 9–17 AIDL files, an `:app`
stub of 85–196 lines, a `:client` SDK of 173–313). Most of it is the **callback zoo** — one
`IASS…Callback.aidl` per return type, with 8 identical anonymous `Stub()` bridges across the two
modules. Collapse it to two generic callbacks (`IASSParcelableCallback`,
`IASSParcelableListCallback`) plus reusable bridge adapters in `:client`; keep one typed
interface per module for the verbs. That removes the per-return-type file explosion and ~2/3 of
the client plumbing. Code-generating the whole IPC layer from the store interface (KSP) is the
logical endpoint but is not worth it at two modules — revisit at four.

**f. Test fixtures.** `InMemoryPodResourceManager` is 100 % reusable and already imported
cross-package by the tickets tests — but it lives in the **contacts test package** and returns
`NotImplemented` for `patch`/`post`/`createInContainer`. Move it to a neutral testing package,
implement the missing verbs, and extract the ~50-line "seed WebID + both type indexes +
containers" preamble into a fixture so a module's engine test starts at its first assertion.

**g. Enforcement.** A Konsist/ArchUnit test in `:api` forbidding cross-module imports
(`datamodule.X` may not import `datamodule.Y`) — it has two existing violations to catch on day
one — plus "no `datamodule.*` may import `sharing.implementation`".

### 4.4 Pod layout: one root container for every data module

Today each module allocates its own top-level container off the storage root — `{storage}tickets/`
and `{storage}contacts/{bookUuid}/` — so the pod root accumulates a new folder per module and a
person browsing their pod cannot tell app data from anything else.

**Decision: every data module lives under a single `datamodule/` container.**

```
{storage}
├── datamodule/                     ← framework-owned root, created once
│   ├── tickets/                    ← module segment
│   │   ├── index                   ← solidshare:TicketIndex, in the type index
│   │   └── {uuid}/                 ← one container per ticket
│   │       ├── ticket              ← the schema:Ticket document
│   │       ├── artifact.pkpass
│   │       └── logo.png, strip.png, …
│   └── contacts/
│       └── {bookUuid}/             ← address book
│           ├── index.ttl#this      ← vcard:AddressBook
│           ├── people.ttl
│           └── Person/{uuid}/…
└── solidshare/                     ← unchanged: sharing bookkeeping, not a data module
```

This makes the root a **framework concern**: `CollectionLayout` owns the `datamodule/` prefix and
a module supplies only its own segment, so module #3 cannot invent a new top-level folder even by
accident. `ensureContainer` already creates parent chains bottom-up, so the extra level costs
nothing at runtime.

**Existing pods are not migrated, and that is deliberate.** Discovery is by type index —
"a followed URL, never a guessed name" — so a pod whose registration points at `{storage}tickets/`
keeps working untouched; only newly bootstrapped containers use the new root. Moving live data
would be actively harmful: a container's URI is its identity, so relocating it would **break every
share link and every WAC/ACP grant** pointing at the old URI, and every receiver's
`received_shares.ttl` row would start 404-ing. The tickets module already has the precedent —
it migrates a legacy `solid:instanceContainer` registration to the `solid:instance` form *without
moving a single resource*.

Consequences to handle when this lands:

- `CollectionLayout` gains the root; `Shared/model/tickets/Constants.kt` and
  `Shared/model/contacts/Constants.kt` lose their top-level segment and keep only their own.
- The bootstrap path creates `datamodule/` then `datamodule/<segment>/`; registration is unchanged.
- `rebuildGivenIndex`'s pod walk now descends `datamodule/` — correct, since those containers are
  legitimately shareable; no new exclusion is needed (`solidshare/`, `inbox/`, `profile/card` stay
  excluded).
- Nothing in the app changes: share targets are derived from the entity URI, never composed from a
  literal path.
- `documents/TICKETS.md` §1 and `documents/CONTACTS.md` §1 are rewritten to show the new layout,
  noting the old one as still-supported.

## 5. ASS library — auth

### 5.1 Where it actually stands

Auth is already decomposed: `AuthenticatorImplementation`, `TokenRefreshCoordinator`,
`DPoPGenerator`, `DPoPTokenRequester`, `IdTokenVerifier`, `ProfileManager`, `WebIdResolver`,
`ClientRegistrationService`, `AuthHeaderFactory`, `InProgressAuthStore`, plus the durable
`UserRepositoryImplementation` + `KeystoreCipher`. The problem is not file size. It is that
**four structural decisions leak across those files**, and the live bugs are consequences:

| Structural decision | Consequence observed in production |
|---|---|
| Token refresh runs on **the caller's cancellable coroutine**, inside a shared response-cache single-flight | A UI flow tearing down (`WhileSubscribed(5s)`) cancelled a token rotation mid-POST; the rotated token was lost, the spent one re-sent, and the provider revoked the whole grant family |
| The HTTP layer **guesses what a 401 means** — it cannot tell "my token expired" from "I have no access to this foreign origin" | Cross-pod reads produced a forced-refresh storm (~8/min) against a healthy token until the provider answered 429 |
| Account state is served from **cached flows that lag the durable store** | The reconciler acted on a stale emission and silently switched the active account — the user's "it takes me out" |
| The profile store's corruption handler **replaces the file with an empty list** | One Keystore hiccup wiped every session |

The debug session has patched all four — `NonCancellable`, a 60 s forced-refresh cooldown plus
429 backoff, a read-your-writes overlay and stale-emission skipping, strike-counted corruption.
Those are the right emergency fixes. They are also, by its own description, *"patched, not
redesigned"*: the coupling that allowed each failure is still there, so the next feature can
reintroduce any of them. That is what this plan addresses — **afterwards, never in parallel**.

### 5.2 Target seams

1. **`SolidSession` — a real, public seam for "who am I to HTTP".**
   Today the transport reaches auth through `internal fun Authenticator.asSession(): AuthSession
   = this as? AuthSession ?: error(...)` — an unchecked downcast that fails unless you passed the
   one true singleton. It makes `SolidHttpClient`, `NotificationTransport` and the WebSocket
   client untestable without the whole authenticator, and it couples every manager's
   `getInstance(authenticator)` to account management when all it needs is a token.
   *Target:* `SolidSession { authHeaders(webId, method, uri); updateNonce(...); tokenState(webId) }`
   obtained from the authenticator once, by a factory, and injected. `getInstance(authenticator)`
   stays for source compatibility and derives the session internally. Fakes become trivial.
2. **A typed challenge, not a guessed one.** `AuthChallenge.parse(wwwAuthenticate, status)` →
   `TokenExpired | InsufficientScope | NotAuthorized | NonceStale`. The HTTP layer refreshes only
   on `TokenExpired`/`NonceStale`; everything else is an authorization outcome and never touches
   the token endpoint. The cooldown stays as a backstop, not as the mechanism.
3. **Refresh owns its own scope.** A `SessionScope` (`SupervisorJob` + IO) inside the refresh
   coordinator, so a rotation is uncancellable *by construction* rather than by remembering to
   wrap each call site in `NonCancellable`. Callers await a `Deferred`; cancelling the *await*
   never cancels the *rotation*.
4. **One state machine for a session**, replacing scattered booleans and parallel flows:
   `Active | Refreshing | NeedsReauth(reason) | Revoked`. `isAuthorizedFlow`,
   `expiredProfilesFlow` and the app's ad-hoc expiry watcher all become projections of it.
5. **Store-first account reads.** `ProfileManager`'s cached flows become a thin projection over
   the durable store with a single reader; the read-your-writes overlay disappears because there
   is nothing to be stale against. Store failure is **quarantine, never wipe** — a corrupt blob is
   renamed aside, the accounts are reported unreadable, and the user is asked to re-authenticate;
   the app must never silently lose accounts.
6. **Split the two responsibilities that keep growing**: `TokenRefreshCoordinator` (457 lines and
   climbing) becomes *lifecycle* (locks, scope, rotation, dead-session gate, backoff) and
   *policy* (cooldowns, rate-limit windows, expiry thresholds) so the policy is unit-testable in
   isolation and the lifecycle stops accreting knobs.

### 5.3 Cleanup that comes with it

- Remove the shipped `AuthTrace` logcat instrumentation once its telemetry replacement is
  verified (the debug session flags it as temporary).
- Land the deferred regression test for the cancellation double-spend — with the seams above it
  becomes writable, because a fake `SolidSession` and a scripted token endpoint replace the
  AppAuth discovery-document choreography that blocked it.
- Fold the auth findings into `docs/features/auth.md` (§7): the four failure modes above are
  exactly the knowledge that must not live only in a session transcript.

### 5.4 Sequencing constraint

**Nothing in §5 starts until the debug session's tranche is committed.** It currently owns, with
uncommitted work: all of `api/auth/implementation/**`, `api/repository/implementation/**`,
`SolidHttpClient.kt`, `SolidAccount.kt`/`AccountMapping.kt`, and in the app `telemetry/**`,
`SolidShareApplication.kt`, `di/ApplicationModule.kt`, `data/repo/auth/**`,
`presentation/login/**`, `presentation/startup/**`, `presentation/main/Profile*.kt` and the
Firebase build files. Touching any of it now would collide.

## 6. Solid Share app — the data-module framework

### 6.1 What a module costs today

Measured, not estimated. Contacts is 30 files / ~4.5k LOC, wallet 31 files / ~6.2k LOC. Adding
module #3 means **creating ~26 files across 9 layers and editing ~16 generic files across 7
more** — and roughly **60 % of the new data-layer code is a mechanical rename** of code that
already exists twice: the Room entity, both DAOs, the outbox table, the drain loop, the backoff
function, the enqueue helper, the worker, the clear-cache method.

Concretely duplicated today, character-for-character in places:

- `ContactOutboxOpEntity` and `TicketOutboxOpEntity` have **identical column lists** and indices.
- The drain loops (`ContactsRepositoryImplementation:356-379` / `TicketsRepositoryImplementation:270-293`)
  and `backoffMillis` (`:417` / `:647`) are the same code twice.
- `ContactOutboxWorker` and `TicketOutboxWorker` differ **only in four identifiers**.
- The two `SharedXViewModel` and two `XShareViewModel` pairs share their entire `UiState`
  shape, offline gate, load/try/catch and message flow.

The two `refresh` implementations have already **drifted** (one guards on `overview.complete`,
the other on `tickets.isEmpty()`) — the expected outcome when a policy is expressed twice.

### 6.2 The one that is not just cost: destructive migration

`SolidCacheDatabase` is at `version = 5` with `fallbackToDestructiveMigration(dropAllTables = true)`
plus a delete-and-recreate fallback. Adding a module's two tables **requires bumping the
version**, which drops every table — including `outbox_op`, `contact_outbox_op` and
`ticket_outbox_op`. Cached data is rebuildable from the pod, which is the stated policy; **queued
offline writes are not**. So today, shipping a new data module silently destroys other modules'
unsynced user work on first launch.

This alone justifies the persistence part of the refactor: with a module-agnostic schema (§6.3),
adding a module requires **no schema change at all**, and the problem disappears rather than
being managed.

### 6.3 Target: one generic spine, N descriptors

**a. Module-agnostic persistence.** Replace the per-module tables with two generic ones:

- `cached_entity(module, webId, uri, sortKey, groupKey, searchText, detailJson, etag, syncState, cachedAt)`
  — PK `(module, webId, uri)`. The promoted columns become three *generic* ones that cover the
  real query needs (wallet sorts by event start, contacts sort by name and filter by book, both
  search text); everything else lives in `detailJson` exactly as it does now.
- `module_outbox_op(id, module, webId, type, payload, status, attempts, nextRetryAt, lastError, createdAt, updatedAt)`
  — one table, `type` and `payload` stay module-defined strings, as they already are.

This removes 4 entities, 4 DAOs, 2 converter pairs and both workers, and makes the schema
**closed to modification** — the property that fixes §6.2.

**b. `CacheFirstStore<T>` and `OutboxDrainEngine`.** The cache-first read policy
(observe → refresh → prune only `SYNCED` rows and only after a failure-free fetch) and the drain
policy (FIFO per WebID, exponential backoff, terminal-vs-transient classification,
provisional-URI remap as a hook) are written **once**. A module supplies serialization and the
"execute this op" body.

**c. Contributor seams, bound `@IntoSet`.** Not one god descriptor — small interfaces so each
generic surface depends only on what it needs:

| Seam | Replaces today's hard-coding |
|---|---|
| `DataModuleLifecycle` — `clearCache(webId)`, `drain(webId)`, `pendingWebIds()` | the four `clearCacheForWebId` call sites in `ProfileViewModel:136-139/150-153` and the worker fan-out in `SolidShareApplication:68-70`; collapses both outbox workers into one |
| `DataModuleUi` — `icon`, `labels`, `homeRoute` (extends today's `SharedEntityUi`) | the hand-listed hub cards in `Home.kt:115-129` and its `onOpenX` parameters |
| `NavGraphContributor` — `NavGraphBuilder.register(navController)` | `NavigationGraph.kt` importing 25+ module symbols and `AppNavHost` carrying `pendingTicketDraft`/`pendingImport` |
| `ScanContributor` / `DeepLinkContributor` — `classify(raw): Any?` | `TicketsRepository` inside `MainViewModel` and `ScanViewModel`, and `ScanTarget.Ticket` inside a generic sealed interface |
| `EntityNameResolver` (fold into `SharedEntityUi`) | the `when (resourceType)` over module IRIs in `ConfirmAccessViewModel:121-131` and its two repository injections |

`SharedEntityRegistry` is the proof this works: `ShareViewModel`, `Share.kt`, `NotificationsViewModel`
and `NotificationsPage` are already module-agnostic and stayed that way through the entity-sharing
feature. The plan is to extend that one working idea to the remaining surfaces.

**d. Break the one real cycle.** `presentation/sharing/CreateShareSheet.kt` and `EntityShare.kt`
import `presentation.contacts.ContactReceiverPicker`, while contacts' share ViewModels depend on
sharing. That is a genuine cycle and it blocks any future module split; hoist a
`ReceiverPickerContributor` seam so sharing stops importing a specific module's UI.

**e. Move cross-cutting concerns out of module constructors.** Both module repositories now take
`SharingRepository` purely to purge share rows on delete, and both reach *upward* into `worker/`
to enqueue their drain. Both become framework concerns: a post-delete hook on the store, and the
drain trigger owned by the outbox engine.

### 6.4 Housekeeping this exposes

- `ContactsMergePrefs` is **never cleared on logout** — a per-account data leak the lifecycle
  seam fixes by construction.
- `SolidAccountManager` is generic in name only (`ContactsContract.AUTHORITY` hard-coded); either
  generalise it or rename it `ContactsAccountManager` and stop pretending.
- `CLAUDE.md` says "five modules in `di/`"; there are **seven**. The doc drifts because nothing
  checks it.
- There are **no DAO tests** for any module table — the duplicated code is also the untested code.
- `CacheTestFactory` still has only file-cache builders; the ticket/contact builders promised in
  `ENTITY_SHARING.md` were never added, so each module test retypes ~60 lines of preamble.

### 6.5 Enforcement (the missing half)

The app has **no detekt, ktlint, spotless or architecture tests**. Whatever we refactor will decay
without them. Add, in this order:

1. **spotless + detekt**, configured exactly like the library's, so both repos fail the same way.
2. **Architecture tests** (Konsist or a plain JVM test walking sources) asserting the rules that
   this plan is built on, so a regression is a red build rather than a review comment:
   - no file under `presentation/` outside `presentation/<module>/` may import
     `presentation.<module>.*`;
   - no file under `data/` may import `worker.*`;
   - generic packages (`presentation/main`, `presentation/sharing`, `di`, `navigation`) may not
     name a concrete module package;
   - every `@Composable` under `presentation/` that is a screen has a `@Preview`;
   - no comments in `app/` except KDoc on public declarations (this is currently a convention with
     no teeth).

## 7. Documentation scheme

The rule: **every feature owns a markdown file describing how it works**, written for someone who
has to change it — not a tutorial and not an API dump.

### 7.1 Library (`../AndroidSolidServices/docs/`)

The library already publishes a MkDocs site (`docs/` + `mkdocs.yml` nav + Dokka at `docs/api`).
Feature docs join it as a new nav section, so they become public documentation:

```
docs/features/
  README.md          index: one line per feature, links
  auth.md            OIDC + DPoP, token lifecycle, session states, the four failure modes of §5.1
  resources.md       SolidResourceManager: verbs, conditional writes, streaming, copy/move
  access-control.md  WAC + ACP backends, pickBackend, implied modes, owner-lockout
  sharing.md         given/received indexes, typed entity shares, links, catalog
  notifications.md   LDN inbox, AS2 activities, the ownership gate, WebSocket channel
  data-modules.md    the toolkit (§4) + "how to add a data module" walkthrough
  tickets.md         the tickets module as implemented on the toolkit
  contacts.md        the contacts module as implemented on the toolkit
  type-index.md      discovery, registration, legacy migration
  telemetry.md       the Telemetry seam and what it reports
```

### 7.2 App (`documents/`)

The app's `documents/` already works this way (CONTACTS, TICKETS, OFFLINE, share, ENTITY_SHARING,
TESTING) but has no index and no stated shape. Add:

```
documents/
  README.md              index + how to write one of these
  ARCHITECTURE.md        layers, DI, the data-module framework, the enforcement rules
  DATA_MODULES.md        "how to add a data module" — the checklist this plan makes short
  AUTH.md                app-side session handling, expiry UX, account switching
  FILES.md               the file browser + outbox (currently undocumented)
  NOTIFICATIONS.md       bell hub, polling, badges (currently undocumented)
  … existing feature docs, unchanged in spirit
```

### 7.3 Template

Each feature doc, both repos, follows the same skeleton — chosen because it matches what
`TICKETS.md` and `share.md` already do well:

1. **What it is** — one paragraph, plain language.
2. **Pod shape** — the RDF/containers it owns, with a Turtle example where it has one.
3. **Public surface** — the API or the screens, whichever side the doc is on.
4. **How it flows** — the two or three paths that matter, end to end.
5. **Offline / failure behaviour** — what happens with no network, and what each error means.
6. **Extension points** — what a future change is expected to plug into.
7. **Tests** — where they live and what they pin.
8. **Spec references** — the Solid/W3C documents it implements.

## 8. Phasing, risk and verification

Every phase ends green (both suites, both repos) and is independently shippable. No phase depends
on a later one.

| # | Phase | Depends on | Risk | Why this order |
|---|---|---|---|---|
| 0 | Docs skeleton + enforcement tooling (app spotless/detekt, architecture tests, doc index/template) | — | 🟢 none functional | Cheap, immediately stops further drift, and the architecture tests encode the target before the code moves |
| 1 | **ASS auth seams** (§5) | debug session **committed** | 🔴 highest | It is where the live bugs are; doing it first while the knowledge is fresh, but only once the tree is clean |
| 2a | ASS `Shared` fixes: promote the RDF primitives, one `runResult`, de-module `SettingTypeIndex`/vocab (§4.3d) | — | 🟢 small, wide | Pure extraction; **fixes the `AddressBookRDF` crash-on-partial-document bug** as a side effect |
| 2b | ASS `EntityCollection` engine + the `datamodule/` root (§4.3a–c, §4.4) + migrate tickets, then contacts | 1, 2a | 🟡 | Tickets is the canonical flat case; contacts proves the engine handles a nested collection — if it can't, contacts keeps a thin layer and the engine stays honest. The layout change rides along because `CollectionLayout` is exactly what the engine introduces |
| 2c | ASS IPC: generic callbacks + client bridge adapters (§4.3e), neutral test fixtures (§4.3f) | 2b | 🟢 | Removes the per-return-type AIDL explosion; KSP codegen deferred until module #4 |
| 3 | App generic persistence + drain engine (§6.3a–b) | — (can run parallel to 1–2) | 🟡 needs a real Room migration, once | Fixes the destructive-migration data loss before any new module exists |
| 4 | App contributor seams + registry (§6.3c–e) | 3 | 🟢 mechanical | Each seam lands independently; `SharedEntityRegistry` is the proven template |
| 5 | Feature docs written against the refactored code (§7) | 1–4 | 🟢 | Written last so they describe what is, not what was planned |
| 6 | *Optional:* Gradle feature modules | 4 (cycle broken) | 🟡 | Only if build times justify it; the package boundaries make it mechanical |

**Verification per phase:** both unit suites green; `compileDebugKotlin` clean; detekt/spotless
clean in both repos; architecture tests green; and for phases 2–4 a manual pass of the two
existing modules (list, detail, offline edit, drain, share, logout) — the refactor is a no-op for
users, so any visible change is a defect.

**The measure of success:** adding data module #4 means writing its RDF codec, its models, its
screens and one descriptor — and editing **no generic file**. If a fan-out list or a `when` over
module types has to grow, the design failed and gets fixed rather than extended.

### 8.1 Before / after, in numbers

| | Library (`:api`+`:Shared`+`:client`+`:app`) | App |
|---|---|---|
| **Today** | 19 new files, 8 edited, ~2,000–3,300 LOC, of which **900–1,100 mechanical** | ~26 new files, ~16 edited across 7 layers, **~60 % of new data-layer code mechanical**, plus a Room version bump that **destroys queued offline writes** |
| **After** | codec + models + spec + store interface + verbs; the engine, index, CAS, attachments, bootstrap and registration are inherited | store + screens + one descriptor set; cache, outbox, drain, worker, logout, nav, hub card, scan are inherited; **no schema change** |
| **Generic files edited** | 1 (module registration) | **0** |

### 8.2 Open questions to settle before phase 2b

1. **Does the nested case really fit?** Contacts is books → contacts → groups. The engine takes a
   parent container, which should cover it — but this is the assumption most likely to break, and
   the plan's answer if it does is "contacts keeps a thin layer", not "the engine grows a flag".
2. **How far do generic Room columns stretch?** `sortKey`/`groupKey`/`searchText` cover today's
   queries (wallet sorts by start date, contacts sort by name and filter by book). A module that
   needs SQL-level filtering on a fourth axis forces either another generic column or a per-module
   index table. Worth agreeing the escape hatch now.
3. **Typed IPC vs. generic envelope.** The plan keeps one typed interface per module and only
   collapses the callbacks. Going fully generic (module + verb + payload) would erase the IPC tax
   entirely at the cost of the SDK's type safety. My recommendation is to stay typed; it is worth
   a decision rather than a default.
4. **Who owns "purge shares on delete"?** Today it is a constructor dependency on every module
   repo. The plan moves it to a framework hook — the question is whether more such cross-cutting
   hooks are coming (indexing? search? backup?), which would argue for a small hook bus rather
   than one-off callbacks.
5. ~~Module segment names under `datamodule/`.~~ **Settled: plural** — `datamodule/tickets/`,
   `datamodule/contacts/`. The segment names are unchanged from today; only the `datamodule/`
   parent is new, so the diff is one constant per module.
6. **Do we ever migrate existing pods?** The plan says no, because a container's URI is its
   identity and moving it breaks live share links and grants (§4.4). If you do want old data
   relocated eventually, it needs its own design: re-share, re-grant, and notify every receiver —
   not a file move.
