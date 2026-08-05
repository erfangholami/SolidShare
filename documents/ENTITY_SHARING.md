# Entity sharing — sharing a thing, not a file

*Part of the [Solid Share documentation set](README.md).*

You do not share `ticket.ttl`. You share **a ticket**. The person receiving it sees "Alice shared a
ticket with you", opens a real pass — barcode, seat, gate — and can add it to their own wallet, at
which point they own their copy. The same holds for a contact.

Underneath, this is still ordinary Solid sharing: WAC or ACP authorizations over real resources,
delivered by the same links and inbox notifications as a file share. What entity sharing adds is
**type**: a share can say what kind of thing it points at, and every generic surface asks a
registry what to do with that type rather than switching on a module it would have to know about.

Resource sharing itself is documented in [share.md](share.md); this page is the typed layer on top.

## 1. Pod shape

Entity sharing mints nothing new on the pod. It adds two optional, type-open fields to things that
already existed:

**In the share index** — the reified `solidshare:Share` node gains a type IRI and a title:

```turtle
<#share-…>
    rdf:type                solidshare:Share ;
    solidshare:resource     <…/tickets/{uuid}/> ;
    solidshare:receiver     <https://bob.example/profile/card#me> ;
    solidshare:resourceType <https://schema.org/Ticket> ;
    dcterms:title           "Coldplay — Music of the Spheres" ;
    acl:mode                acl:Read ;
    dcterms:created         "2026-08-01T12:00:00Z"^^xsd:dateTime .
```

**In the notification** — the `as:Offer` additionally *describes its object*, which is valid
Activity Streams and inert to any client that does not care:

```turtle
<#offer>  a as:Offer ; as:actor <…#me> ; as:object <…/tickets/{uuid}/> ; as:target <…#me> .
<…/tickets/{uuid}/>  rdf:type schema:Ticket ; schema:name "Coldplay — Music of the Spheres" .
```

**In a copy** — accepting a shared entity records `dcterms:source <original entity URI>` on the new
one. That is what makes "already in your wallet" possible, and it keeps provenance honest.

Deep links carry an optional `&type=<enc-iri>` and deliberately **no name**: a QR payload should not
leak what the ticket is called.

Type IRIs are an open vocabulary — `https://schema.org/Ticket`,
`http://www.w3.org/2006/vcard/ns#Individual`. An unknown IRI renders generically rather than
failing, and a share written before typing existed reads as null and renders as a file. That
fallback is the compatibility story: nothing needed migrating.

### What actually gets granted

| Share | Target | Why |
|---|---|---|
| Ticket → person | the ticket's `{uuid}/` container | The document, the `.pkpass` artifact and the images inherit through `acl:default` / `acp:memberAccessControl`, so one grant covers the whole pass |
| Ticket → public | the `.pkpass` **artifact only** | Anyone with the bare URL downloads a pass file. Disabled, with a hint, when the ticket has no artifact or carries `solidshare:sharingProhibited` |
| Contact → person | the contact's `Person/{uuid}/` container | vCard document plus photo |
| Contact → public | *not offered* | A contact is someone else's personal data; publishing it is not ours to offer |

Entity shares are **View-only**. Ownership moves by copying, never by editing the sharer's pod —
which also means a receiver can keep a pass the sharer later deletes.

## 2. Surface

### The library contract — `ShareableEntityStore<T>`

Implemented by each data-module store. It knows nothing about grants; it answers questions about
entity identity and layout, which is why `api/datamodule` and `api/sharing` depend on each other in
neither direction:

```kotlin
public interface ShareableEntityStore<T> {
    public val entityTypeIri: String
    public fun shareTarget(entityUri: String): String
    public fun publicShareTarget(entity: T): String?
    public fun displayName(entity: T): String?
    public suspend fun findInContainer(ownerWebId: String, containerUri: String): SolidResult<T>
}
```

`TicketStore` maps `…/{uuid}/ticket#this` to its `{uuid}/` container — never to the shared
`tickets/` root, which would hand over the whole wallet — and returns the artifact for a public
share only when there is one and sharing is not prohibited. `ContactStore` maps
`…/Person/{uuid}/index.ttl#this` to `Person/{uuid}/` and returns null for public.

`findInContainer` is the receiving half: given a container someone shared, find the entity inside it
by membership and `rdf:type`, without knowing how that module names its documents.

### The app contract — `SharedEntityUi` + `SharedEntityRegistry`

Each module contributes one handler, bound `@Binds @IntoSet` in `di/EntityShareModule`:

```kotlin
interface SharedEntityUi {
    val typeIri: String
    val icon: ImageVector
    @get:StringRes val kindLabelRes: Int
    val homeCard: HomeModuleCard? get() = null
    fun receivedShareRoute(resourceUri: String, ownerWebId: String?): Any
    fun manageShareRoute(resourceUri: String): Any
    suspend fun resolveName(webId: String, resourceUri: String): String? = null
}
```

`SharedEntityRegistry.forType(typeIri)` is the only lookup, and `homeCards()` is where the Home
hub's cards come from. Handlers: `TicketSharedEntityUi`, `ContactSharedEntityUi`.

The rule this exists to enforce: **no generic surface names a module.** The Share tab, the
notifications hub, the confirm-access sheet and Home all go through the registry, and
`ArchitectureTest` fails the build if generic presentation code imports a module package.

### Screens

`TicketSharingPage` and `ContactSharingPage` on the owner side; `SharedTicketPage` and
`SharedContactPage` on the receiver side, each with an "Add to my …" action. The shared pieces live
in `presentation/sharing/EntityShare.kt`.

## 3. How it flows

### Sharing a ticket with a person

1. `TicketSharingPage` shows the pass header and a **People with access** card.
2. Picking a receiver — pasted WebID, or from the contacts picker via
   `ReceiverPickerContributor` — calls
   `createShare(shareTarget, READ, WebIdReceiver, resourceType, resourceName)`.
3. The library canonicalizes the receiver, writes the grant over the container, writes the typed
   index row, and posts an `as:Offer` carrying the object description.
4. The sheet shows the QR and link, exactly as for a file.

### Publishing a public pass link

The **Public pass link** switch grants public read on the `.pkpass` artifact alone
(`notifyReceiver = false` — there is nobody to notify) and turns it off with a revoke. The switch is
disabled with an explanation when the ticket has no artifact, when sharing is prohibited, or when
offline, rather than being hidden.

### Receiving one

1. The offer arrives in the inbox; the library's received-share sync records a typed row.
2. The Share tab renders it as a typed row — entity icon, the name, a "Ticket" kind chip — and
   skips the metadata fetch a file row would do.
3. Tapping asks the registry for `receivedShareRoute` and lands on `SharedTicketPage`, which
   renders the real pass from the sharer's pod without writing anything to the cache.
4. **Add to my wallet** fetches the artifact and images, builds a draft with
   `copiedFrom = remote.uri`, and queues a create through the module's normal owned-write path — so
   the copy is a first-class ticket of yours, type-index registered, offline-capable, shareable in
   turn.
5. A second attempt finds the existing copy by `dcterms:source` and says "already in your wallet"
   rather than making another.

The link and QR path is identical: `ChooseReceiverRoute` picks the account, `ConfirmAccessRoute`
probes access, and the typed copy makes the confirmation read "shared a ticket with you".

## 4. Offline and failure behaviour

- **Entity sharing is online-only**, like all sharing: a grant is a write on a pod, a receiver's
  WebID needs a live profile read, and the offer is a POST to their inbox. Every entity share
  surface carries `RequiresConnection`.
- **Typed rows still render offline**, because the type and name are copied into the received-share
  index when the notification is synced — the row does not need the entity to be reachable to say
  what it is.
- **A foreign entity is never cached.** Reads for a shared ticket or contact bypass
  `cached_entity`; only your own copy is stored.
- **An unreachable entity** leaves the row rendered with what the index knows, and opening it
  reports lost access with a **Request access** action where the owner is known.
- **An unknown type IRI** degrades to a generic file row. Nothing throws, and no share is dropped
  for being of a kind this version does not understand.
- **A refused RDF read on a public pass** falls back to parsing the `.pkpass` binary, because a
  bare artifact URL commonly answers 406 to an RDF `Accept`.

## 5. Extension points

Adding a third shareable module:

1. **Library** — implement `ShareableEntityStore<T>` on the module's store, including
   `findInContainer`.
2. **App repository** — a `getShared…` read that writes no cache, and an `addShared…ToMine` that
   deep-copies through the module's normal owned write path, stamping provenance.
3. **App UI** — a share page (people card, plus a public card if the module has a publishable
   artifact), a received-entity page with "Add to my …", and one `SharedEntityUi` handler bound
   `@IntoSet`.
4. **Strings and previews.**

The Share tab, deep links, notifications, the confirm-access flow and the on-pod indexes need **no
changes**. That is the property this design exists to have, and the architecture tests are what
keep it true.

Known gaps, deliberately: there is no "shared with you" section inside the Wallet or Contacts
lists (received entities live in the Share tab only), no public contact sharing, no group
receivers, and no offline entity sharing.

## 6. Tests

- Library `SharingEngineTest` — a typed index row round-trips, an update preserves typing rather
  than dropping it on a mode change, a legacy row reads as null, a typed notification syncs into
  the received index, and the link codec round-trips a type.
- Library ticket and contact engine tests — `shareTarget` mapping (especially that a ticket maps to
  its own container and never to the wallet root) and `findInContainer`.
- App `SharedTicketRepositoryTest` / `SharedContactRepositoryTest` — a remote read never touches
  the cache; a copy queues a create carrying artifact, images and `dcterms:source`; the duplicate
  guard finds it afterwards.
- `data/repo/sharing/SharingMappersTest` — the typed fields survive the domain mapping.

## 7. Specifications

- [WAC](https://solidproject.org/TR/wac) / [ACP](https://solidproject.org/TR/acp) — unchanged
  ground truth: container grants with `acl:default` / `acp:memberAccessControl`, public as
  `foaf:Agent` / `acp:PublicAgent`.
- [Activity Streams 2.0](https://www.w3.org/TR/activitystreams-core/) — the offer, and the object
  description that carries the type. Describing an activity's object is standard AS2, which is why
  a non-Solid-Share client can read the offer and simply ignore the extra triples.
- [Dublin Core](https://www.dublincore.org/specifications/dublin-core/dcmi-terms/) — `dcterms:title`
  on the index row, `dcterms:source` for copy provenance.
- [Solid type index](https://github.com/solid/type-indexes) — the copy registers itself through the
  module's normal create, so a received ticket is discoverable exactly like one you added yourself.
