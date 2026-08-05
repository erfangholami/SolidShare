# Files

*Part of the [Solid Share documentation set](README.md).*

The Files tab is a plain file browser over a pod: containers and resources, with the operations
you expect — open, upload, create a folder, download, duplicate, rename by way of copy, delete,
share. It is the one surface where the user works with resources as resources rather than as data
identities, and it is the oldest part of the app, so it is also where the offline-first machinery
was first built.

## 1. Pod shape

Files owns nothing and imposes nothing. A container is an LDP container, a resource is whatever
the pod holds, and the browser reflects the storage roots the profile advertises through
`pim:storage`. Type, size, modification time and access come from the response headers and the
container's membership triples, not from an app-side registry.

Two consequences worth stating, because both were decisions:

- **Container size is not recursive.** A container shows its immediate item count, not the summed
  byte size of everything beneath it. Recursive sizing was implemented and reverted: it means
  walking the whole subtree on every listing, which is slow on a real pod and wrong the moment
  anything changes.
- **Nothing is renamed in place.** A rename is a copy plus a delete, because a resource's URI is
  its identity and moving it breaks every share link and grant pointing at it.

## 2. Surface

### Repository

`data/repo/file/FileRepository.kt` splits into four groups:

| Group | Verbs |
|---|---|
| Listing | `observeContainer`, `refreshContainer`, `getCachedContainer`, `cacheContainer`, `lastCachedAt`, `getContainerContents`, `getContainerItemCount` |
| Metadata | `getResourceMeta`, `getResourceCreatedTime`, `probeAccess` |
| Content | `downloadFile`, `downloadToDevice`, `uploadFile`, `createFolder`, `deleteResource`, `duplicateResource` |
| Offline | `observeAvailableOffline`, `observePendingUris`, `observeErrorUris`, `pinOffline`, `unpinOffline`, `clearCacheForWebId` |

### The outbox

`data/repo/outbox/OutboxRepositoryImplementation.kt` owns queued file work: uploads, folder
creation, deletes and copies, in an `outbox_op` table with a blob path for pending upload bytes.
It is separate from the data modules' `module_outbox_op` because its payload is a *file* — bytes
on disk, encrypted at rest, with a lifecycle the modules' JSON payloads do not have.

Draining is asked for through `OutboxTrigger.requestDrain(OutboxQueue.FILES)`, which is what keeps
`data/` from importing `worker/`. `worker/` decides that this means `OutboxWorker` with a
connectivity constraint and a `KEEP` policy.

### Screens

`presentation/container/`: `ContainerView` is the previewable screen driven by
`ContainerViewState`, and `Container.kt` is its ViewModel host. `ResourceDetailsPage` is the info
screen. `FileActionsBottomSheet` holds the per-resource actions.

## 3. How it flows

### Listing a container

1. `observeContainer` emits the cached rows from Room immediately, so the screen paints without a
   round trip.
2. `refreshContainer` fetches the live listing.
3. On a clean fetch the cache is reconciled: upsert what came back, then delete only `SYNCED` rows
   that were absent. Rows in a pending state are never pruned, and a fetch that raised anything
   prunes nothing.
4. Room emits again.

### The actions sheet

`FileActionsBottomSheet` offers, in order: Share, Manage access, Duplicate, [Download], Copy link,
[Open in], Info, Delete. Manage access and Duplicate are shown only for resources you own.
**Duplicate** is a deep recursive copy that is then reset to owner-only through
`SharingRepository.makePrivate` — a copy of a shared thing must not inherit the original's
audience.

Deleting a resource also purges its share records: `SharingRepository.purgeGivenShares` removes
the given-share rows for the resource and its descendants. Without that, deleting a shared file
left rows pointing at a URI that no longer resolves, and no way to remove them, because every
repair path started by reading the resource.

### Creating inside a container

`createInContainer` POSTs rather than PUTs. A share that grants Append but not Write allows a POST
into the container and refuses a PUT at a chosen URI, so POST is what makes uploading into
someone else's shared folder work at all. Owned writes still use `create()` with PUT, where
choosing the URI is the point.

## 4. Offline and failure behaviour

Full treatment in [OFFLINE.md](OFFLINE.md); the file-specific parts:

- **Reads are cache-first**, and pinned resources (`pinOffline`) keep their bytes in the encrypted
  blob store so they open with no connection.
- **Writes queue.** Upload, create-folder, delete and copy all go through the outbox, with the
  upload's bytes copied into the encrypted blob store at enqueue time so the source URI can go
  away.
- **Backoff** is exponential with a cap, stored on the row so a restart does not reset a failing
  op's schedule.
- **Sharing is online-only.** A WAC or ACP grant that cannot be written cannot be faked locally,
  and a share that silently did not happen is worse than one that refused. Every such surface
  carries the single `RequiresConnection` affordance rather than inventing its own wording.
- **Access failures are distinguished from absence.** `probeAccess` reports what you may do with a
  resource, so "you cannot read this" and "this is not there" produce different messages.

## 5. Extension points

Files is a leaf feature: nothing registers into it and it contributes nothing to other surfaces
beyond the share entry points. The seams it does use are shared ones — `OutboxTrigger` for
draining, `SharingRepository` for grants and purges, `ResourceTypeIcon` for the file-type tile,
and `ContainerView`/`ContainerViewState` for a previewable screen.

The one place a future change is expected is the actions sheet: `ResourceActions` is a per-context
set rather than a fixed list, so a new context (currently: owned, shared-with-me, shared entity)
supplies its own action set instead of the sheet growing conditionals.

## 6. Tests

- `data/repo/file/FileRepositoryCacheTest.kt` pins the reconciliation rule that is easiest to get
  wrong: a failed refresh must not prune, and pending rows must survive a successful one.
- `data/repo/outbox/OutboxRepositoryTest.kt` pins the queue: enqueue, drain, retry with backoff,
  and clearing per WebID. It builds the repository with `NoOutboxTrigger` so draining is explicit
  rather than racing WorkManager.
- `data/local/cache/ResourceDaoTest.kt` and `BlobDaoTest.kt` cover the DAO queries directly, which
  is where the `syncState` filters live.

## 7. Specifications

- [Solid Protocol](https://solidproject.org/TR/protocol) — resource and container semantics,
  `WAC-Allow`, conditional writes.
- [Linked Data Platform](https://www.w3.org/TR/ldp/) — container membership and POST-to-create.
- [WAC](https://solidproject.org/TR/wac) / [ACP](https://solidproject.org/TR/acp) — what
  Manage access reads and writes.
