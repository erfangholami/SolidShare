# Data modules in the app

*Part of the [Solid Share documentation set](README.md).*

A data module is one kind of thing the app understands — contacts, wallet tickets — with its own
screens, its own cache and its own queued writes, but none of its own plumbing. The app side
mirrors the library side: the library owns the pod pattern, the app owns the device pattern, and
a module contributes descriptions rather than machinery. This page is what you follow when adding
module number three.

The library counterpart is
[Adding a data module](https://androidsolidservices.erfangholami.com/project/adding-a-data-module/);
read it first if you have not, because the pod layout and the collection toolkit are decided there.

## 1. Pod shape

The app owns nothing on the pod that the library does not already define. A module's containers,
index documents and registrations are the library's `CollectionSpec`; the app only ever holds
identifiers it was given. That is why nothing in `app/` composes a pod path from a literal: share
targets are derived from an entity URI, never rebuilt from `{storage}` plus a folder name, so the
`datamodule/` root landing in the library changed no app code at all.

The one on-device shape the app owns is the encrypted cache, described in §2.

## 2. Surface

### The generic spine

| Piece | Where | What it owns |
|---|---|---|
| `CachedEntityEntity` / `CachedEntityDao` | `data/local/cache/CachedEntity*.kt` | One `cached_entity` table for every module: PK `(module, webId, uri)`, generic `sortKey`/`groupKey`/`searchText` columns for the real query needs, everything else in `detailJson`. |
| `ModuleOutboxOpEntity` | `data/local/cache/ModuleOutboxOpEntity.kt` | One `module_outbox_op` table for every module: `(module, webId, type, payload, status, attempts, nextRetryAt, lastError, …)`. `type` and `payload` stay module-defined strings. |
| `ModuleOutboxDao` | `data/local/cache/ModuleOutboxDao.kt` | Due ops, pending ops, pending work across modules, per-module clear, pending count. |
| `ModuleOutbox` | `data/repo/outbox/ModuleOutbox.kt` | The queue policy, written once: enqueue, FIFO drain with terminal-vs-retry handling, exponential backoff capped at an hour, rewrite and drop for provisional entities. |
| `OutboxTrigger` | `data/repo/outbox/OutboxTrigger.kt` | The seam that lets `data/` ask for a drain without importing `worker/`. |
| `DataModuleLifecycle` | `data/repo/datamodule/DataModuleLifecycle.kt` | `moduleId`, `drain(webId)`, `clearCache(webId)` — implemented by each module's repository interface. |
| `DataModuleRegistry` | `data/repo/datamodule/DataModuleRegistry.kt` | Fan-out over every registered module: clear all caches for a WebID, drain everything the outbox reports as pending. |
| `ModuleOutboxWorker` | `worker/ModuleOutboxWorker.kt` | One worker for every module, driven entirely by the registry. |
| `SharedEntityUi` / `SharedEntityRegistry` | `presentation/sharing/SharedEntityUi.kt` | The per-module UI descriptor: icon, kind label, home hub card, the routes for a received or managed share, and how to resolve an entity's display name. |
| `ReceiverPickerContributor` | `presentation/sharing/ReceiverPicker.kt` | Lets a module contribute the "pick a receiver from my data" sheet without sharing code importing that module. |

### What a module still writes

Its domain models, its cache mapping (`toCacheEntity` and back — a plain file beside the
repository, deciding what goes into `sortKey`/`groupKey`/`searchText`), its repository
(implementing `DataModuleLifecycle`), its outbox payload types and op-type enum, its screens and
ViewModels, its `SharedEntityUi`, and its routes. No Room entity, no DAO, no schema change.

## 3. How it flows

### An offline write

1. The screen calls the module repository — `queueCreate`, `queueDelete`, `mergeContacts`.
2. The repository writes the optimistic row into its own cache table with a `PENDING_*` sync
   state, so the list reflects the change immediately. Creates get a **provisional URI**, because
   the pod has not minted the real one yet.
3. It encodes a payload and calls `outbox.enqueue(moduleId, webId, type.name, payload)`.
4. `ModuleOutbox` inserts the row and asks `OutboxTrigger` for a drain.
5. `WorkManagerOutboxTrigger` enqueues `ModuleOutboxWorker` with a connectivity constraint.
6. The worker asks `DataModuleRegistry.drainPending()`, which reads the distinct
   `(module, webId)` pairs with queued work and calls `drain(webId)` on each matching module.
7. `ModuleOutbox.drain` walks the due ops in id order, handing each to the module's executor.
   Success deletes the row; failure marks it `FAILED`, increments `attempts` and sets
   `nextRetryAt = now + backoff(attempts)`.
8. The module's executor performs the real pod write and reconciles the cache — for a create,
   deleting the provisional row and upserting the server one under its real URI.

Editing a still-queued create does not enqueue a second op: the repository finds the pending
`CREATE` whose payload carries that provisional URI and rewrites it in place
(`ModuleOutbox.rewrite`). Deleting one drops the queued op instead of queueing a delete for a
resource that never existed (`ModuleOutbox.drop`).

### Logout

`ProfileViewModel.logout` clears the file cache, the file outbox, and then calls
`dataModules.clearCache(webId)`. It does not name a module. Adding one means the account's data
is cleared on logout by construction rather than by remembering to add a line.

### A shared entity arriving

Covered end to end in [ENTITY_SHARING.md](ENTITY_SHARING.md). The part that matters here is that
every generic surface — the Share tab, the notifications hub, the confirm-access sheet — routes
through `SharedEntityRegistry.forType(resourceType)` and never switches on a module.

## 4. Offline and failure behaviour

- **Reads are cache-first.** The list observes Room and refreshes from the pod in the background;
  a failed refresh leaves the cache intact rather than emptying the screen.
- **Writes always queue.** There is no online-only write path in a data module. The queue is the
  write path, and connectivity only decides when it drains.
- **Backoff** doubles from 30 seconds, capped at one hour, and is stored on the row, so a restart
  does not reset a failing op's schedule.
- **Sharing is online-only**, deliberately: a WAC or ACP grant that cannot be written cannot be
  faked locally, and a share that silently did not happen is worse than one that refused.
- **Queued work survives schema changes.** See §5.

## 5. Extension points

Adding a module means writing its files and registering them. Concretely:

1. Domain models and a cache-mapping file (`{Module}CacheMapping.kt`) building
   `CachedEntityEntity` rows from them.
2. A repository interface extending `DataModuleLifecycle`, and its implementation taking
   `CachedEntityDao` and `ModuleOutbox`.
3. An op-type enum and payload types beside the repository, in
   `data/repo/{module}/{Module}OutboxPayloads.kt`. Both the cache and the queue key rows by
   module id, so **no schema change and no database version bump, for either**.
4. Screens, ViewModels and routes.
5. A `SharedEntityUi` implementation if the module's entities are shareable, with a `homeCard` if
   it deserves a hub card.
6. A `NavGraphContributor` — your destinations, living in your own package
   (`ContactsNavGraph`, `WalletNavGraph` are the two examples).
7. A `ScanContributor`, if the module claims QR payloads, deep links or open-with files
   (`TicketScanContributor` is the example): `classify(raw)` and `classifyContent(bytes, name)`
   return a route or null, and the scanner, the deep-link handler and the share sheet all route
   through `ScanRouter` without knowing who answered.
8. Bindings: `@Binds @IntoSet` for `DataModuleLifecycle` in `di/DataModuleModule.kt`, and for
   `SharedEntityUi` / `ReceiverPickerContributor` / `NavGraphContributor` / `ScanContributor` in
   `di/EntityShareModule.kt`, plus the repository binding in `di/RepositoryModule.kt`.

Every one of those is a **new** file plus a binding line. No generic file changes shape: `Home`
reads its cards from the registry, the Share tab and the notifications hub route through it,
`ConfirmAccessViewModel` asks it for a display name, the outbox worker drains through
`DataModuleRegistry`, and `AppNavHost` iterates `NavGraphRegistry`. Both architecture-test
baselines are empty, so the next module that reaches for a generic file fails the build.

### The one that was a bug, not just a cost

The database used `fallbackToDestructiveMigration(dropAllTables = true)`, and a new module needed
two new tables, which needed a version bump, which dropped **every** table — including the outbox
tables. Cached rows are rebuildable from the pod, which is the stated policy. Queued writes are
the user's unsynced work and exist nowhere else.

Two things changed. `MIGRATION_5_6` (`data/local/cache/CacheMigrations.kt`) folds the per-module
outbox tables into `module_outbox_op` and carries every queued row across rather than letting the
fallback drop them. And because the outbox schema is now module-agnostic, a new module's queue
needs no schema change at all, so the class of bug is gone rather than managed.

`MIGRATION_6_7` then folded the per-module cache tables into `cached_entity` the same way, so
neither the cache nor the queue needs schema work for a new module. The destructive fallback
stays only as a last resort for databases with no path at all.

## 6. Tests

- `app/src/test/java/…/architecture/ArchitectureTest.kt` pins the rules this design rests on, with
  shrink-only baselines: `data/` must not import `worker/` (baseline now empty), generic
  presentation code must not import a module package (baseline: the nav graph), and production
  code carries no comments except KDoc (baseline empty). A baseline that grows is a failed build.
- `app/src/test/java/…/data/repo/tickets/TicketsRepositoryOfflineTest.kt` pins the queue
  behaviours that are easy to break: a provisional row appears immediately, drain replaces it with
  the server one, editing a queued create rewrites rather than duplicates, and a stale provisional
  row is cleaned up.
- `CacheTestFactory.testOutbox(db)` builds a `ModuleOutbox` over an in-memory database with a
  no-op trigger, so a repository test drains explicitly instead of racing WorkManager.

## 7. Specifications

The app implements no pod-facing specification directly; everything on the wire goes through the
library. The relevant ones are [Solid Protocol](https://solidproject.org/TR/protocol) for resource
identity, the [Solid type index](https://github.com/solid/type-indexes) for discovery, and
[WAC](https://solidproject.org/TR/wac) / [ACP](https://solidproject.org/TR/acp) for the grants a
share writes.
