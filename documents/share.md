# Sharing in SolidShare

> A high-level reference for how resource sharing works in **SolidShare** (the
> Android app) and how the **AndroidSolidServices (ASS)** library structures the
> sharing engine beneath it.
>
> This is the on-ramp. For the exhaustive design history read `SHAREV2.md`; for
> the original proposal read `Sharing in Solid Share.md`. This document is the
> map, not the territory.

## Abstract

Sharing in SolidShare lets a person grant another party a defined level of access
(**View / Add / Edit**) to any file or folder in their Solid pod, see everything
they have shared and everything that has been shared with them, and revoke or
change that access at any time. Recipients can be a specific Solid user (by
WebID), the public (anyone with the link), or — defensively in the data model — a
group. The design is **pod-resident and server-agnostic**: all bookkeeping lives
on the pod (not the device), works across the major Solid servers via Web Access
Control with an Access Control Policy fallback, and requires no central
SolidShare server. Delivery happens out-of-band (QR / link) and, when the
receiver has an inbox, in-band via Linked Data Notifications.

The heavy lifting lives in the ASS library's `SharingManager`; the SolidShare app
is a thin, well-mannered client on top of it.

---

## 1. Goals & criteria

The original R&D standard ("Sharing in Solid Share") set out five qualities a
good share/receive experience must have. They remain the spine of the design,
and every one is satisfied by the current implementation — usually more strongly
than originally specified.

| # | Original criterion                                           | How it holds today                                                                                                                                                                                                               |
|---|--------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Any file or folder must be shareable                         | Files share via WAC `acl:accessTo` / ACP per-resource policy; containers via `acl:default` / `acp:memberAccessControl` (covers current and future members).                                                                      |
| 2 | Users must see everything they shared and received           | Two on-pod index files (`given_shares.ttl`, `received_shares.ttl`) back the **Shared by me** / **Shared with me** tabs; `getAccessGrants` additionally unifies inbox requests and SAI-registry grants.                           |
| 3 | Shares can be created or revoked at any moment               | `createShare` / `updateShare` / `revokeShare` are idempotent owner-side operations; received shares can be removed locally.                                                                                                      |
| 4 | Restarting / switching clients must not lose track of shares | Indexes live **on the pod**, never on the device; any SolidShare install (or any client honoring the format) recovers the full picture.                                                                                          |
| 5 | Any Solid client should recognize the shares                 | Shares are real WAC/ACP authorizations (the ground truth); the index is portable RDF. The custom `solidshare:` index vocabulary is the one app-specific convention, abstracted behind `ShareVocabulary` so it can be retargeted. |

The current design also adds goals the original did not state: **both delivery
channels** (out-of-band QR/link *and* in-band LDN inbox), **multi-account**
support (every operation is scoped to a `webId`), **best-effort server-agnosticism**
(WAC default, ACP fallback), and **owner-lockout protection** baked into every
access-control write.

---

## 2. Architecture at a glance

Sharing is layered. The app never touches Solid or HTTP types directly; it speaks
only to its own `SharingRepository`, which is a near-pure pass-through to the
library's `SharingManager`. The manager owns all access-control writes, the on-pod
index, and notification delivery — and runs everything on `Dispatchers.IO`
internally, so the repository stays thin.

```
┌──────────────────────────────────────────────────────────────────┐
│  SolidShare app  (presentation + repo)                           │
│                                                                  │
│   Compose UI ── ViewModels ── SharingRepository                  │
│   (Share tab, CreateShareSheet, ManageSharingPage,               │
│    ScanPage, ConfirmAccessPage, ChooseReceiverDialog)            │
│        owns: View/Add/Edit labels, SharingError mapping,         │
│        owner guard (pim:storage), account-switching deep links   │
└───────────────────────────┬──────────────────────────────────────┘
                            │  app-domain types only
┌───────────────────────────▼──────────────────────────────────────┐
│  AndroidSolidServices library                                    │
│                                                                  │
│   api/      SharingManager  ◄──►  NotificationsManager           │
│             (grants + on-pod      (LDN inbox: discover,          │
│              given/received        post, read, decisions)       │
│              indexes)                                            │
│                 │                                                │
│             access/  AccessBackend  ──  WacBackend / AcpBackend  │
│                 │     (pickBackend selects per resource)         │
│                                                                  │
│   Shared/   model (GivenShare, ReceivedShare, ShareReceiver,     │
│             ShareMode …) · rdf wrappers · vocab (solidshare:,    │
│             acl:, as:, dcterms:, foaf:, vcard:, interop:)        │
│                                                                  │
│   client/   SolidSharingClient / SolidNotificationsClient        │
│             (AIDL/IPC façade for third-party apps — NOT used     │
│              by SolidShare, which injects the api managers)      │
└───────────────────────────┬──────────────────────────────────────┘
                            │  HTTP (DPoP), WAC/ACP RDF, LDN POST
┌───────────────────────────▼──────────────────────────────────────┐
│  Solid pod                                                       │
│   resource + .acl (WAC)  /  ACR (ACP)   ·   ldp:inbox (LDN)      │
│   {podRoot}/solidshare/shares/{given,received}_shares.ttl        │
└──────────────────────────────────────────────────────────────────┘
```

Two consumption boundaries exist in ASS: the **in-process `api` managers**
(`SharingManager` / `NotificationsManager`) and a **cross-process `client` SDK**
(`SolidSharingClient` / `SolidNotificationsClient`, an AIDL/IPC façade for
third-party apps). **SolidShare uses the in-process `api` managers directly** via
Hilt, so it gets the full surface — including the `NotificationsManager` methods
`sendUpdate` / `sendAccept` / `recordDecisionGranted` / `recordDecisionRejected`,
and `SharingManager`'s `syncReceivedShares`, that the IPC clients deliberately
omit. The client SDK exists for other apps wanting cross-process access to the ASS
app's exported services.

---

## 3. How sharing works in SolidShare (the app)

This section is about *behavior*. The mechanics live in §5.

### Share a resource

From a file/folder's actions sheet (or the Share tab), the user opens
**CreateShareSheet** — a four-stage modal (Form → Submitting → Result → Error):

1. **Who can access** — pick a **Receiver WebID** (paste a WebID) or **Anyone with
   the link** (public). Group creation is not surfaced in the UI.
2. **Access mode** — **View / Add / Edit** (never the raw Read/Append/Write names).
3. On submit, the app calls `createShare`. The receiver WebID is passed *raw*; the
   library canonicalizes a fragment-less profile-doc URL into the real `#me` WebID.
4. On success the sheet shows **ShareLinkPanel**: a scannable QR (branded with the
   app logo), a copyable link, "Save image", and "Share link". For a public share a
   toggle switches between the SolidShare deep link (`https://solidshare.app/s…`)
   and the bare browser URL (the raw resource URL); for a WebID share only the deep
   link is shown.

### Receive a share

1. **Scan or tap.** The unified **ScanPage** (CameraX + ML Kit) auto-detects what
   was scanned: a share link, a profile WebID, or something unrecognized. Tapping a
   share deep link opens the app directly via HTTPS App Links.
2. **Choose account** (deep-link path). A tap routes through **ChooseReceiverDialog**,
   which lists logged-in accounts; picking one *switches the active account* and
   proceeds. (This replaced an older silent auto-add.)
3. **Confirm access.** **ConfirmAccessPage** probes the resource: if the active user
   *owns* it, an owner guard blocks the add ("You own this"); otherwise it
   verifies access and, on confirm, calls `addReceivedShare` to record the row. If
   access is denied, the user can send an **access request** with a chosen mode.

### Manage access (widen / narrow / revoke)

**ManageSharingPage** lists the owner row plus one row per recipient, each showing
the mode and when access was granted. A per-person mode chip opens a bottom sheet to
**change the level** (`updateShare`, which fires a separate *"X updated your
access"* notification, never a re-offer) or **remove access** (`revokeShare`). An
"Add people" button reopens CreateShareSheet.

### The Given / Received lists

The **Share tab** has two tabs with live counts. **Shared by me** groups by
resource (one row per resource, mode-grouped recipient avatars, "Shared …" time).
**Shared with me** shows the owner, the mode they granted, and "Added …" time, with
actions to open/browse, download, reshare (the reshare link carries the *owner's*
WebID, not yours), copy link, remove from list, or — only when you hold Edit —
delete the remote resource. (Requesting access is not a row action; it appears
only as a recovery action when access has been lost or denied.) The Received list
auto-refreshes on resume after the library's inbox sync.

### Share a profile

**ShareProfilePage** shows a QR of the user's exact `#me` WebID with Share / Copy
link / Download actions. This is identity-sharing and is entirely separate from
resource sharing — it does not touch `SharingRepository` at all.

---

## 4. The structure of sharing in the ASS library

This is the heart of the document.

### 4.1 Module layering

ASS splits the sharing engine across three artifacts:

| Module     | Holds                                                                                                                                                                                                                                                                                                                                                        |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Shared** | Platform-agnostic, `@Parcelize` domain models (`GivenShare`, `ReceivedShare`, `ShareReceiver`, `ShareMode`, `ShareNotification`, `ShareRequest`, `AccessGrant`, `CatalogEntry`, `ParsedShareLink`); the RDF reader/writer wrappers for each on-pod document; and the vocabularies (`solidshare:`, `acl:`, `as:`, `dcterms:`, `foaf:`, `vcard:`, `interop:`). |
| **api**    | The public, in-process managers and their implementations: `SharingManager` (grants + indexes), `NotificationsManager` (LDN inbox), the `access/` backends (`WacBackend`, `AcpBackend`) plus the `pickBackend` selector in `AuthBackendDiscovery`, and the parameterization points (`SharingProfile`, `ShareStorageLayout`, `ShareLinkCodec`).               |
| **client** | Thin AIDL/IPC façades (`SolidSharingClient`, `SolidNotificationsClient`) for third-party cross-process consumers. Not used by SolidShare.                                                                                                                                                                                                                    |

### 4.2 Public API surface

`SharingManager` is an interface obtained via `getInstance(authenticator)` or
`getInstance(resourceManager)`, both defaulting to the `SolidShareProfile`. Every
method takes the acting `webId` first and returns a `SolidNetworkResponse<T>`
(Success / Error / Exception). It is a process-wide singleton; received-index
mutators are serialized by a per-WebID mutex.

**Given (owner-side) reads & repair**

- `getStoredGivenShares` — fast index read.
- `refreshGivenShares` — re-validate tracked shares against live ACLs, drop ones no longer granted.
- `rebuildGivenIndex` — expensive full pod-tree walk rebuilding the index from live ACLs (skips
  excluded paths, preserves unreadable rows).
- `getGivenSharesForResource` — authoritative per-resource read straight from the ACL.
- `repairOwnerControl` — re-assert owner R/W/C after a self-lockout (additive).
- `makePrivate` — reset a resource to owner-only (used by the app's Duplicate action).

**Mutating shares**

- `createShare(webId, resourceUri, mode, receiver, notifyReceiver=true)` — grant; container →
  `acl:default`; posts `as:Offer` to a WebID receiver's inbox.
- `updateShare(…, notifyReceiver=false)` — widen/narrow, preserving the original `dcterms:created`;
  fires a separate `as:Update`.
- `revokeShare(webId, resourceUri, receiver)` — remove the authorization + index row; posts
  `as:Undo`.

**Received (recipient-side)**

- `getStoredReceivedShares` / `refreshReceivedShares` — fast read / re-validate via HEAD (
  `WAC-Allow` + `solid:owner`).
- `addReceivedShare(webId, resourceUri, ownerHint?)` — verify access and sync: grant → row
  added/returned; no access → row removed, `null` returned.
- `removeReceivedShare` — drop a tracked row (does not touch the resource).
- `syncReceivedShares(webId, notifications)` — reconcile the index against an inbox notification
  batch.

**Unified overview, requests, catalog**

- `getAccessGrants` — unify given, received, incoming requests, and SAI-registry grants into
  `AccessGrant`s.
- `acceptShareRequest` / `rejectShareRequest` — grant + `as:Accept` (and self-decision memo) /
  `as:Reject`.
- `publishCatalogEntry` / `removeCatalogEntry` / `getOwnerCatalog` — owner-published catalog of
  requestable resources.

**Share-link codec (synchronous)**

- `getShareDeepLink` / `parseShareDeepLink` / `getShareBareUrl`.

**SharingProfile — the parameterization point.** A `SharingProfile` bundles four
properties so the same engine can run under a different vocabulary, pod layout, or
link scheme: `vocabulary: ShareVocabulary`, `storageLayout: ShareStorageLayout`,
`linkCodec: ShareLinkCodec`, and `catalogEnabled: Boolean`. The default
`SolidShareProfile` binds these to the `https://solidshare.com/ns#` vocabulary, the
`solidshare/` pod layout, the `solidshare.app/s` link codec, and the catalog
enabled.

**ShareStorageLayout** returns the on-pod URIs given a `podRoot`: `rootContainer`,
`sharesContainer`, `givenIndex`, `receivedIndex`, `catalog`, plus
`excludedScanPaths()` (`/solidshare/`, `/inbox/`, `/profile/card`) — protocol-public
or engine-private paths a rebuild skips so they never appear as revocable shares.

**ShareLinkCodec** produces the current `https://solidshare.app/s?resource=…&owner=…`
form, and on *parse* recognizes both that form and the legacy `solidshare://…`
custom scheme.

### 4.3 Data model

All models are `@Parcelize` Parcelables (they cross the AIDL boundary).

- **`ShareMode`** — `READ` / `APPEND` / `WRITE`. Because WAC has **no mode
  subsumption**, `impliedAclModes()` expands a logical mode into the full set a grant
  must write: View = `{Read}`, Add = `{Read, Append}`, Edit = `{Read, Write}`.
  `strongest()` (Write > Append > Read) folds those back to one logical level. The app
  renders these as **View / Add / Edit**, never `mode.name`.
- **`ShareReceiver`** — sealed: `WebIdReceiver(webId)`, `GroupReceiver(groupUri)`,
  `Public` (→ `foaf:Agent`). AIDL-flattened via `kind()` / `value()`.
- **`GivenShare(receiver, mode, resourceUri, createdAt?)`** — `createdAt` is the
  ISO-8601 `dcterms:created` (null for legacy/ACL-scan rows). One record per
  `(receiver, resource)` pair.
- **`ReceivedShare(ownerWebId, mode, resourceUri, addedAt?)`** — `addedAt` is the
  owner's original share time when auto-synced from an offer, else the moment added.
- **
  `ShareNotification(notificationUri, type, ownerWebId, resourceUri, mode?, summary?, publishedAt?, targetWebId?)`
  **
  with `ShareNotificationType` = `OFFER` (`as:Offer`), `UPDATED` (`as:Update`),
  `UNDO` (`as:Undo`), `REJECT` (`as:Reject`), `ACCEPTED` (`as:Accept`),
  `DECISION_GRANTED` / `DECISION_REJECTED` (self-authored memos, no received-share
  effect).
- **`ShareRequest(requestUri, requesterWebId, resourceUri, requestedMode, summary, publishedAt)`**
  — an incoming inbox access request.
- **
  `AccessGrant(direction, counterpartWebId, resourceUri, mode, status, source, grantedAt, requestUri)`
  **
  — the unified "who has access to what" row (direction GIVEN / RECEIVED /
  INCOMING_REQUEST; source APP_INDEX authoritative, SAI_REGISTRY additive).
- **`CatalogEntry(resourceUri, title, description?, depictionUri?)`** and
  **`ParsedShareLink(resourceUri, ownerWebId?)`**.

### 4.4 On-pod storage & RDF formats

All bookkeeping lives under a **non-dotted** `solidshare/` namespace (Community
Solid Server reserves `/.*` for server-internal resources and 403s them):

```
{podRoot}/solidshare/
  shares/                  ← private, owner-only ACL
    given_shares.ttl
    received_shares.ttl
  catalog.ttl              ← publicly readable
```

Each share is stored as a **reified `solidshare:Share` node** (not a bare triple),
so it can carry a `dcterms:created` timestamp. One node covers every `acl:mode`
granted to a `(counterpart, resource)` pair. Node IRIs are deterministic fragments
on the index document (a SHA-1 of `counterpart|resource`), so re-creating the same
pair reuses the node. A representative `given_shares.ttl` record:

```turtle
<#share-…>
    rdf:type            solidshare:Share ;
    solidshare:resource <resourceUri> ;
    solidshare:receiver <receiver> ;     # WebID, group URI, or foaf:Agent (public)
    acl:mode            acl:Read , acl:Append ;
    dcterms:created     "2026-06-04T12:00:00Z"^^xsd:dateTime .
```

`received_shares.ttl` is symmetric but uses `solidshare:owner` (the resource owner)
instead of `solidshare:receiver`. Group receivers add a `<groupUri> rdf:type
vcard:Group` marker to disambiguate a group URI from a WebID. *(The files carry a
`.ttl` extension but the wrappers serialize `application/ld+json` by default; the
Turtle above is illustrative.)*

Earlier versions wrote bare `<counterpart> acl:Mode <resource>` triples with no
node and no time; these legacy rows are still read (with null timestamps) and
migrated to node form the next time the pair is touched. All index writes go
through an **N3 Patch with `If-Match`** (re-read → minimal insert/delete diff →
conditional PATCH), retried up to three times on a 412 conflict — so concurrent
edits merge rather than clobber.

**Notification / request RDF** is standards-based Activity Streams 2.0 posted to
the LDN inbox:

```turtle
<#offer>
    rdf:type      as:Offer ;
    as:actor      <ownerWebId> ;
    as:object     <resourceUri> ;
    as:target     <receiverWebId> ;
    acl:mode      acl:Read ;
    as:published  "2026-05-29T10:00:00Z"^^xsd:dateTime .
```

Access requests are typed `interop:AccessRequest` (the SAI standard term) — the
only place SAI vocabulary types a sharing document. Modes are read from the standard
WAC `acl:mode` IRIs, with a `solidshare:mode` / `solidshare:requestedMode` *string
literal* accepted only as a backward-compat fallback. The catalog uses
`solidshare:CatalogEntry` + `dcterms:title` / `dcterms:description` /
`foaf:depiction`.

### 4.5 Access-control backends (WAC default + ACP fallback)

Beneath the index sits an `AccessBackend` seam with five operations
(`grant` / `revoke` / `listShares` / `ensureOwnerOnly` / `reclaimOwnerControl`) and
two implementations:

- **`WacBackend`** — the default; works on every targeted server. Writes an
  `.acl` sidecar: an `acl:Authorization` with `acl:accessTo` (direct) or
  `acl:default` (container inheritance), the implied `acl:mode`s, and the receiver
  mapped to `acl:agent` (WebID) / `acl:agentClass foaf:Agent` (public) /
  `acl:agentGroup` (group).
- **`AcpBackend`** — for Inrupt ESS in ACP mode. Writes an ACR with the canonical
  per-`(receiver, mode)` chain: `acp:AccessControl` → `acp:apply` → `acp:Policy`
  (`acp:allow`) → `acp:allOf` → `acp:Matcher` (`acp:agent` for a WebID, or
  `acp:agent acp:PublicAgent` for public; `acp:vc` for a group); containers add
  `acp:memberAccessControl`.

**Detection** is by `pickBackend`: HEAD the resource, read the `Link: rel="acl"`
target, and compare its origin to the resource's. Same origin → WAC sidecar;
different origin (or none) → ACP. The origin comparison is the real discriminator
— Inrupt ESS in ACP mode advertises its ACR under `rel="acl"` on a *separate*
authorization host.

**Implied-mode expansion** is load-bearing: every grant explicitly writes
`acl:Read` (View → `{Read}`, Add → `{Read, Append}`, Edit → `{Read, Write}`)
because WAC does not entail Read from Write. The one exception is the LDN inbox,
which is granted public **`acl:Append` only** (`includeImpliedModes=false`) so
anyone may POST a notification but only the owner may read the inbox.

**Owner-lockout protection** is structural: every write path re-asserts the
owner's Read/Write/Control self-rule, so a first ACL write can never lock the owner
out. **Revoke narrows rather than deletes** — a rule covering other resources or
subjects is split so only the targeted receiver loses access. **Reads collapse**
the several implied modes back to one logical level per `(receiver, resource)` via
`strongest()`.

**Public sharing** is `acl:agentClass foaf:Agent` (WAC) or `acp:agent
acp:PublicAgent` (ACP), mapped back to `ShareReceiver.Public` on read.

**Server quirks handled**: Inrupt's remote-`@context` ACRs (parsed directly
instead of failing, so live rows are never pruned), Inrupt's proprietary
compacted-JSON-LD ACR shape (sidestepped by writing standard N-Triples, which
Inrupt advertises in `Accept-Put` and has no context to disagree about — the same
N-Triples write format the library uses for all WAC and ACP writes), the
`WAC-Allow` short-circuit on `ensureOwnerOnly`
(skip the write when already owner-only — the Inrupt ACR endpoint refuses to
materialize via PUT), and NSS weak-ETag 412s (surfaced as `StaleAcl`, retried).

### 4.6 Notifications (LDN inbox)

`NotificationsManager` owns the inbox; `SharingManager` calls into it on
create/update/revoke/accept/reject. The model is **pull, not push**: the receiver
polls their inbox (SolidShare runs a 15-minute periodic worker plus on-demand
refresh), because sharing events are rare and a persistent socket isn't worth the
cost.

- **Discovery** reads `ldp:inbox` from the WebID profile (with HEAD `Link` and
  extended-profile fallbacks). Foreign WebIDs are read **anonymously first**
  (`readPublic`) because identity hosts reject foreign-issuer tokens with 401.
- **Provisioning** (`ensureInbox`) creates `{podRoot}inbox/`, grants the public
  **write-only `acl:Append`**, and advertises `ldp:inbox`.
- **Activities**: `as:Offer` (share), `as:Update` (level changed), `as:Undo`
  (revoke), `interop:AccessRequest` (request), `as:Accept` / `as:Reject`
  (request decision). Accept links back via `as:inReplyTo`.
- **Decision records**: accepting/rejecting a request also posts a *self-addressed*
  `as:Accept` / `as:Reject` memo into the owner's **own** inbox, distinguished
  purely by `as:actor == inbox owner`. These surface as "you approved/declined X"
  and carry **no** received-share side effect.
- **Anti-impersonation gate**: a notification claiming "I shared X with you" is only
  trusted if the claimed `as:actor` is the real owner of X — verified via
  `solid:owner` from HEAD, then a same-site `pim:storage` check, then a host
  fallback. Forged grants to *your* resources are dropped.

**Receiver pull-sync.** `syncReceivedShares` reconciles the index against an inbox
batch. It collapses presence-changing notifications (`OFFER`, `ACCEPTED`,
`UPDATED`, `UNDO`) per canonicalized `(owner, resource)` to the single newest by
`as:published`, with **grant-wins-on-tie** so a stale `as:Undo` (the inbox is in
server-container order, not chronological) can't delete a still-granted share.
Grants re-probe access and upsert a row (trusting a gate-verified notification's
own mode/owner when a cross-pod probe is indeterminate); undos remove the row;
rejects and decision memos are no-ops. *(Note: this sync is orchestrated by the
SolidShare app's notifications repository — "list then sync" — not as a side effect
of listing inside the library, despite some stale KDoc suggesting otherwise.)*

### 4.7 Share links & deep links

The codec produces `https://solidshare.app/s?resource=<enc>&owner=<enc>` (the
owner is embedded so the receiver can trust it ahead of weaker owner-resolution
signals). The app registers this as an **HTTPS App Link** (`solidshare.app/s`,
`autoVerify=true`, verified via `assetlinks.json`), with the legacy
`solidshare://share` custom scheme kept only as a parse-time fallback. The **bare
URL** is just the encoded resource URL itself, for non-Solid scanners / "open in
any browser".

---

## 5. Solid protocol alignment

| Spec                            | How sharing uses it                                                                                                                                                                                                                                              |
|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Solid Protocol**              | Resources, LDP containers, and auxiliary resources advertised via HTTP `Link` headers (`rel="acl"`, `solid:owner`, `ldp#inbox`, `storageDescription`); pod root + `pim:storage` resolve the bookkeeping location and the owner guard.                            |
| **Web Access Control (WAC)**    | The default authorization backend: `.acl` sidecar with `acl:Authorization`, `acl:agent`/`acl:agentClass foaf:Agent`/`acl:agentGroup`, `acl:mode` (Read/Append/Write), `acl:accessTo` / `acl:default`. The `WAC-Allow` response header validates received access. |
| **Access Control Policy (ACP)** | The fallback backend for ACP pods: an ACR with `acp:AccessControl` → `acp:Policy` (`acp:allow`) → `acp:Matcher` (`acp:agent` for a WebID, or `acp:agent acp:PublicAgent` for public; `acp:vc` for a group); `acp:memberAccessControl` for container inheritance. |
| **Solid Notifications / LDN**   | In-band delivery: AS2 activities POSTed to a receiver's `ldp:inbox`; pull-based polling. The inbox is provisioned append-only per the LDN model (write-not-read).                                                                                                |
| **N3 Patch**                    | All index mutations are `solid:InsertDeletePatch` documents (minimal insert/delete diff) with `If-Match`, so concurrent edits from multiple clients merge.                                                                                                       |
| **WebID Profile**               | Receiver WebIDs are canonicalized (profile-doc URL → real `#me` via `foaf:primaryTopic`) so WAC actually grants the person; inbox discovery reads `ldp:inbox` from the profile.                                                                                  |
| **SAI (interoperability)**      | Access requests are typed `interop:AccessRequest`; `getAccessGrants` additively enriches from the SAI registry graph (`interop:hasAccessGrant` / `hasDataGrant`).                                                                                                |

---

## 6. Comparison with the original R&D standard

The original "Sharing in Solid Share" (PourMohamad & Gholami) proposed a lean,
**WAC-only, flat-triple, QR-delivered** design with lazy "update-only-when-looked-at"
consistency. The current implementation **keeps the spirit of every criterion** but
has evolved substantially in mechanism and grown several entire features the
original never described. The biggest shifts: the index moved from bare `acl:`
triples to **reified `solidshare:Share` nodes with `dcterms:created`** (so shares
carry timestamps); ACP joined WAC as a fallback (so Inrupt ESS in ACP mode works);
and an entire **LDN notification layer** (offer/accept/reject/update/undo,
request-to-share, decision records) was added alongside the original QR delivery.
Where the original was honest about being WAC-only and QR-only, the implementation
went broader; where the original chose flat triples and a recursive scan, the
implementation chose richer records and an index-first model with scan as a
fallback.

| Aspect                               | Original R&D proposal                                                                             | Current SolidShare / ASS implementation                                                                                                                                         | Status                                                        |
|--------------------------------------|---------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| **Access protocol**                  | WAC-only (ACP acknowledged but deliberately not used, for universal support)                      | WAC default **+ ACP fallback** (`AccessBackend` seam, per-resource `pickBackend`)                                                                                               | **Evolved**                                                   |
| **Index file shape**                 | Flat single triple `<subject> acl:Mode <resourceURL>`; no reification, no metadata, no timestamps | Reified `solidshare:Share` node: `rdf:type`, `solidshare:resource`, `solidshare:receiver`/`owner`, `acl:mode`, **`dcterms:created`**; legacy flat triples still read & migrated | **Evolved**                                                   |
| **`.shares` container & file names** | `.shares/` at pod root (dot-prefixed); `given_shares.ttl` / `received_shares.ttl`                 | `solidshare/shares/` (non-dotted, because CSS 403s `/.*`); same `given_shares.ttl` / `received_shares.ttl`                                                                      | **Evolved**                                                   |
| **Subject grammar**                  | given→subject = receiver, received→subject = sharer; public = `foaf:Agent`                        | given record uses `solidshare:receiver`, received uses `solidshare:owner`; public still `foaf:Agent`; group adds `vcard:Group` marker                                           | **Evolved**                                                   |
| **Access modes**                     | read / append / edit → `acl:Read` / `acl:Append` / `acl:Write`                                    | Same three modes, surfaced as **View / Add / Edit**; written with full implied-mode expansion (WAC has no subsumption)                                                          | **Kept** (display + expansion refined)                        |
| **Share delivery**                   | QR code of a resource URI, scanned by the receiver; no inbox                                      | QR / link **+ HTTPS deep links** (`solidshare.app/s`, App Links) **+ LDN inbox** offers/accepts/updates/undos                                                                   | **Evolved / New**                                             |
| **Given-shares maintenance**         | Recursive full-pod ACL scan on every list access                                                  | **Index-first** (`getStoredGivenShares` / `refreshGivenShares` per-resource); recursive scan demoted to opt-in `rebuildGivenIndex`                                              | **Evolved**                                                   |
| **Receiver types**                   | People (WebID) + public only                                                                      | WebID + Public + **Group** (`vcard:Group`) in the model; **non-Solid** still public-only                                                                                        | **Evolved** (group added in model; non-Solid limitation kept) |
| **Profile sharing**                  | Not part of the original standard                                                                 | Distinct feature: QR of the exact `#me` WebID (snapshot / live-partial-view variants in the library)                                                                            | **New**                                                       |
| **Request-to-share**                 | Mentioned as a concept ("by request from the receiving user") but no mechanism described          | `interop:AccessRequest` → owner accepts (`as:Offer`/`as:Accept`) or rejects (`as:Reject`); self-decision memos                                                                  | **New**                                                       |
| **Owner-published catalog**          | Not described                                                                                     | `catalog.ttl` of requestable resources (`publishCatalogEntry` / `getOwnerCatalog`)                                                                                              | **New**                                                       |
| **Multi-account**                    | Single account assumed                                                                            | Every operation scoped to a `webId`; account-switching deep-link router; per-account indexes & mutexes                                                                          | **New**                                                       |
| **Consistency model**                | Lazy "update-only-when-looked-at", stored-then-WAC two-phase emit; stale received dropped on view | Same spirit: stored index for fast reads, live re-validation on refresh, stale rows dropped; hardened with N3-Patch+`If-Match`, per-WebID mutex, grant-wins-on-tie sync         | **Kept** (mechanism hardened)                                 |
| **Receiver WebID canonicalization**  | Not described                                                                                     | Library resolves a fragment-less receiver (profile doc → `#me`) so WAC actually grants the person                                                                               | **New**                                                       |
| **Owner-lockout protection**         | Not described                                                                                     | Every access-control write re-asserts owner R/W/C                                                                                                                               | **New**                                                       |

The honest divergences: the original was an interoperable *cross-client standard*
with a deliberately minimal RDF shape any client could read; the current index uses
a custom `solidshare:` vocabulary and reified nodes that a naive WAC client would
not understand (the **WAC/ACP authorizations remain the portable ground truth**,
but the *index* is now app-specific, abstracted behind `ShareVocabulary`). And the
original's "scan the whole pod on every list" was explicitly *removed* from the
default path as too expensive — a clear, intentional departure from the proposed
algorithm.

---

## 7. Known limitations & open items

- **Group sharing has no creation UI.** `GroupReceiver` exists in the model and is
  rendered defensively, but the SolidShare screens only ever create WebID and Public
  shares. Group membership management is consumed, never authored.
- **No private sharing with non-Solid receivers.** WAC has no identifier to scope
  to; only public shares (the URL *is* the access) reach non-Solid recipients. The
  capability-URL pattern was considered and rejected.
- **Pull-only notifications.** No live push (WebSocket/streaming) — deferred because
  events are rare; SolidShare relies on a 15-minute worker plus on-demand refresh.
- **Container `refreshGivenShares` does not enumerate descendants** — only the
  explicit, costly `rebuildGivenIndex` walks the tree.
- **Index vs. ACL can diverge transiently.** `createShare` rolls back the WAC grant
  if the index write fails; if rollback also fails, a `rebuildGivenIndex` is needed
  to reconcile. Best-effort notification failures never block a share.
- **No accept/dismiss for notifications** — the inbox itself is the durable history
  (only *requests* have an accept/reject decision).
- **Catalog / SAI-registry / request-to-share** have library support but limited or
  no live UI surface in the app today.
- **Singletons keyed only by `webId`.** `SharingManagerImplementation` is a
  process-wide singleton, safe today because all lookups route by WebID; any future
  per-account caching must revisit this.

---

*Cross-references: `SHAREV2.md` (full canonical design, ~2500 lines), `SHAREV1.md`
(the pre-code plan), and `Sharing in Solid Share.md` (the original R&D standard).*
