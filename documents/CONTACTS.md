# Contacts

*Part of the [Solid Share documentation set](README.md).*

Solid Share treats the pod as the user's canonical address book, and the app as a **viewer, not an
editor**. Contacts arrive from real sources — the phone's own contacts, a `.vcf` file, a scanned
Solid profile, an accepted share — and leave for the places they are useful: the phone's Contacts
app, the sharing receiver picker, a `.vcf` export. There are no create or edit forms in the app,
and that is the design, not a missing screen: the system Contacts app is a better contact editor
than we will write, so we mirror into it rather than compete with it.

Everything is scoped to the active WebID. Contacts, address books, sync state, dismissed duplicate
suggestions and queued writes all key on it, so switching accounts switches the whole surface.

The pod model, the RDF codecs and the store API belong to the library and are documented at
[androidsolidservices.erfangholami.com/build/contacts](https://androidsolidservices.erfangholami.com/build/contacts/).
This page is the app's half.

## 1. Pod shape

The library allocates the module's container under the shared data-module root, registers it in
the type index, and discovers it from there afterwards:

```
{storage}datamodule/contacts/{bookUuid}/
├── index.ttl#this        ← vcard:AddressBook (dc:title, nameEmailIndex, groupIndex)
├── people.ttl            ← contact list index (cached vcard:fn per contact)
├── groups.ttl            ← group list index
├── Person/{uuid}/
│   ├── index.ttl#this    ← vcard:Individual
│   └── photo.jpg         ← photo binary (vcard:hasPhoto target)
└── Group/{Name}.ttl      ← vcard:Group
```

The layout is the W3C vCard ontology in the SolidOS arrangement, so an address book written here
opens in other Solid contact apps. Discovery is always through the **type index**, which is why
pods that registered a container before the `datamodule/` root existed keep working — the root
applies to newly allocated containers only, and relocating an existing one would change URIs that
share links and grants already point at.

Covered vCard terms per contact: `fn`, structured name (`hasName`), `nickname`, **typed**
telephones (`vcard:Cell/Home/Work/Fax/Pager/Voice/Text/Video/TextPhone`), **typed** emails,
**postal addresses** (`hasAddress` → street/locality/region/postal-code/country/po-box), `bday`,
`anniversary`, `organization-name`, `organization-unit`, `role`, `title`, `note`, typed URLs
(including `vcard:WebId`), IM handles, categories, gender, geo, language, and `hasUID` (a
`urn:uuid:` minted on create). Untyped legacy nodes still parse — they read as OTHER, and OTHER
entries are written untyped for byte-compatibility with older writers.

The app model round-trips all of it. That matters more than it sounds: a merge or an edit that
read fewer fields than it wrote would silently erase whatever it did not understand.

## 2. Surface

### Screens — `presentation/contacts/`

| Screen | What it does |
|---|---|
| `ContactsPage` | Search, book filter chips, and a "review duplicates" banner when suggestions exist |
| `ContactDetailPage` | Typed rows; the WebID row opens `PublicProfileRoute`; share and delete are the only actions |
| `ContactsSettingsPage` | Address books · import from the device · Sync now · `.vcf` import/export · review duplicates · delete all |
| `AddressBooksPage` | Create a private or public book, rename, delete (with a warning) |
| `ContactsMergePage` | Duplicate review: the clusters, what a merge would produce, merge or dismiss |
| `ContactSharingPage` | Share a contact with a person — see [ENTITY_SHARING.md](ENTITY_SHARING.md) |
| `SharedContactPage` | A contact someone shared with you, with "Add to my contacts" |
| `ContactReceiverPicker` | The receiver picker sharing uses, listing pod contacts that carry a WebID |

### Repository — `data/repo/contacts/`

`ContactsRepository` implements `DataModuleLifecycle`, so logout clearing and outbox draining
reach it through the registry rather than by name. Beyond the CRUD verbs it owns
`findMergeSuggestions`, `mergeContacts` / `queueMerge`, `findContactByWebId` (the duplicate check
behind "add scanned profile"), the address-book verbs, and the shared-contact verbs
(`getSharedContact`, `addSharedContactToBook`).

`ContactMergeEngine` is deliberately a plain class with no I/O: normalization, clustering, and
what a merged draft would contain. That is what makes duplicate detection testable.

### Sync and device I/O

`sync/` holds `ContactsAccountManager` (one Android account per logged-in WebID),
`ContactsSyncEngine` (the adapter's two phases), `ContactsSyncService` and
`SolidAuthenticatorService`. `data/device/DeviceContactsSource.kt` reads the phone's own contact
rows. `util/VCardReader.kt` and `util/VCardWriter.kt` do file I/O, driven by workers.

## 3. How it flows

### The only write paths

The app is read-only in the sense that no screen offers a contact form. Contacts still change,
through exactly six paths:

1. **Import from the device** (Settings → Import device contacts). `ContactsDeviceImportWorker`
   reads every raw contact that is *not* already one of ours, creates it in the default book, then
   runs duplicate detection over the result. It is a **one-shot import, not a continuous mirror** —
   after it runs, the imported contacts live on the pod and the pod is what syncs.
2. **The two-way mirror.** Contacts on the pod appear on the phone under the Solid Share account,
   and editing or deleting them in any contacts app pushes back to the pod (§ below).
3. **`.vcf` import** (`ContactsImportWorker` + `VCardReader`): a tolerant vCard 2.1/3.0/4.0 parser
   — folded lines, QUOTED-PRINTABLE, typed TEL/EMAIL/ADR, `ORG name;unit`, BDAY/ANNIVERSARY,
   inline base64 PHOTO. Exact duplicates are skipped; near-duplicates become review suggestions.
4. **A scanned Solid profile**: the public-profile page's add action checks `findContactByWebId`
   first — "already in your contacts" versus "added" — and stores name, emails, phones,
   organization and role plus the WebID as a `vcard:WebId` URL. Your own logged-in WebIDs are
   refused; another account you happen to be logged into is not.
5. **Accepting a shared contact**: "Add to my contacts" on `SharedContactPage` copies the sender's
   contact into your own book through the normal owned-write path.
6. **Merge and delete**, from duplicate review and the detail page respectively.

### One sync run = two phases

`ContactsSyncEngine.sync(webId)` needs `READ_CONTACTS` and `WRITE_CONTACTS`, and does nothing
without them.

1. **Upload.** Every raw contact under this WebID's Solid Share account marked `DIRTY` or `DELETED`
   is pushed to the pod: `SOURCE_ID` carries the contact URI, `SYNC1` the book URI, `SYNC2` a
   fields hash, `SYNC3` a photo key. A deletion in any contacts app becomes a pod delete; an edit
   becomes a pod update. This is what makes the mirror two-way.
2. **Download.** The pod's contacts are written back as typed `ContactsContract` rows — phone and
   email types, structured postal addresses, nickname, anniversary, department — plus groups and
   memberships. Photos are re-fetched only when their key changed, and contacts or groups that
   vanished from the pod are removed locally.

Upload runs before download so a local edit is never overwritten by the copy it was made from.

Triggers: automatic upload sync on local edits (Android notifies us for our own account's rows), a
4-hour periodic sync, and an expedited sync on opening the contacts page, on "Sync now", and after
every in-app write.

### Duplicate review, never a silent merge

Import and sync **write first**; deduplication is a separate, visible step. `ContactMergeEngine`
normalizes WebIDs, phone numbers and email addresses into match keys, and unions contacts that
share one into clusters — a name alone never matches, because "John Smith" is not evidence.
Clusters of two or more surface as *suggestions*: a banner on the contacts list, a row in
settings, and a notification when an import finishes. Nothing is merged until you say so.

A merge unions the multivalued fields, picks a survivor (one carrying a WebID wins), and fills its
blank single-valued fields from the others. Dismissed clusters are remembered per WebID in
`ContactsMergePrefs` — keyed by a signature of the cluster's URIs, so changing the cluster brings
it back — and pruned to live contacts. Signing out clears them.

The reason for all of this: an automatic merge that guesses wrong destroys data the user cannot
get back, and two contacts that should have been one is a far cheaper mistake.

## 4. Offline and failure behaviour

- **Reads are cache-first.** The list observes `cached_entity` and refreshes from the pod behind
  it; a failed refresh leaves the cache intact rather than emptying the screen.
- **Writes queue.** Delete, merge and delete-all go through `module_outbox_op` with an optimistic
  cache row, so they apply offline and drain later. See [OFFLINE.md](OFFLINE.md).
- **Sharing a contact is online-only** and carries the standard `RequiresConnection` affordance.
- **Import and export are workers**, not screen-lifetime coroutines: `ContactsImportWorker`,
  `ContactsExportWorker` and `ContactsDeviceImportWorker` run as `dataSync` foreground services
  with progress and completion notifications, and the SAF URI is persisted so a long import
  survives the app being backgrounded. The completion notification deep-links back into the
  contacts screen.
- **Without contacts permission** the sync engine returns silently and the settings page shows a
  grant button. The pod side keeps working; only the phone mirror stops.
- **A contact deleted on the device reappears** if it also exists on the pod and was not deleted
  there — the pod is the source of truth, and the mirror is a projection of it.
- **Conflicts** resolve last-write-wins per field, per sync pass. The library's write path is an
  N3 patch under `If-Match`, so two clients editing different fields of the same contact merge.

## 5. Extension points

- **Contacts is a data module**, so its plumbing is the generic one: cache table, outbox table,
  worker, lifecycle, registry. Nothing here is contacts-specific — see [DATA_MODULES.md](DATA_MODULES.md).
- **`ContactSharedEntityUi`** contributes the home card, the entity icon and the routes for a
  shared contact; **`ContactReceiverPickerContributor`** contributes the receiver picker.
  Both are `@Binds @IntoSet` in `di/EntityShareModule`, which is why sharing code never imports
  contacts.
- **`ContactsNavGraph`** owns the module's destinations, so adding a contacts screen edits no
  generic navigation file.
- **A new match rule** goes in `ContactMergeEngine.matchKeys`. Anything added there must be
  identifying on its own; if it is not, it belongs in the survivor heuristic instead.
- **A new vCard term** is a library change first (model plus RDF codec), then a row in
  `ContactDetailPage` and a mapping in `ContactsMappers`.

## 6. Tests

- `util/VCardRoundTripTest` — write then read must lose nothing. This is the test that catches a
  new field being added to the model but not to the writer.
- `data/repo/contacts/SharedContactRepositoryTest` — a foreign contact is never written into the
  local cache, and accepting one copies it through the owned-write path with its photo.
- The merge engine's clustering and survivor rules are exercised through the repository tests; the
  behaviour worth protecting is that a name alone never clusters.
- `architecture/ArchitectureTest` keeps the seams honest — generic presentation code may not
  import `presentation/contacts` or `data/repo/contacts`.

Tooling and gotchas are in [TESTING.md](TESTING.md).

## 7. Specifications

- [vCard Ontology](https://www.w3.org/TR/vcard-rdf/) — the pod model; every term above is from it.
- [RFC 6350 (vCard 4.0)](https://datatracker.ietf.org/doc/html/rfc6350) and RFC 2426 (3.0) — the
  import/export format. Export writes 3.0 because it is what other address books accept most
  reliably; import accepts 2.1, 3.0 and 4.0.
- [Solid type index](https://github.com/solid/type-indexes) — how an address book is discovered
  rather than guessed at a path.
- [Solid Protocol](https://solidproject.org/TR/protocol) — resource identity and conditional
  writes underneath the library.
- **Groups are read, never authored.** The model and the sync carry group membership, but no
  screen creates a group. Stated so a reader can tell the decision from an omission.
