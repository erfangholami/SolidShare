# Wallet (tickets)

*Part of the [Solid Share documentation set](README.md).*

The Wallet holds event tickets, boarding passes, cinema tickets, loyalty cards and coupons as
ordinary Solid resources on the user's own pod — not in a vendor's cloud, and not in an app-private
database. A pass added on one device is on every device signed into that pod, is readable by any
Solid client that understands the vocabulary, and stays after Solid Share is uninstalled.

Two rules shape everything below. **The barcode payload is sacred**: whatever the issuer encoded is
stored verbatim and re-rendered in the symbology it was captured in, or the gate scanner rejects
it. And **a pass is a document, not a screenshot**: the wallet stores the structured fields, so it
can lay a pass out properly, refresh it from its issuer, and share it as a thing rather than a file.

The pod model and store API belong to the library and are documented at
[androidsolidservices.erfangholami.com/build/tickets](https://androidsolidservices.erfangholami.com/build/tickets/).
The normative term dictionary is [TICKET_VOCAB.md](TICKET_VOCAB.md) — read that before adding a
field, because most of what a source can yield already has a term.

## 1. Pod shape

```
{storage}datamodule/tickets/         ← container, allocated on first ticket, type-index registered
├── index                            ← one cached row per ticket, so the list is a single GET
└── {uuid}/                          ← one container per ticket
    ├── ticket                       ← the RDF document, primary subject #this
    ├── {artifact}.pkpass            ← the original imported file, kept verbatim
    └── {logo,icon,strip,…}          ← the pass images
```

The index document and the ticket document are **extension-less on purpose**: they are linked data
reached by URL — the type index links to the index, its rows link to each ticket — so the URI never
encodes a representation. Discovery always follows the registered URL, whatever it is named.

Giving every ticket its own container is what makes sharing work: granting View on `{uuid}/` covers
the document, the artifact and the images in one grant, and they inherit it through WAC
`acl:default` / ACP `acp:memberAccessControl`.

A ticket is `schema:Ticket` with the schema.org vocabulary wherever schema.org has a term, and
`solidshare:` only where it demonstrably does not:

```turtle
<#this>  a schema:Ticket ;
    schema:name              "Coldplay — Music of the Spheres" ;
    schema:ticketNumber      "TKT-0042" ;
    schema:ticketToken       "c3RhZGl1bS10aWNrZXQ…" ;   # the exact issuer payload
    solidshare:barcodeFormat "AZTEC" ;                   # the symbology it was captured in
    solidshare:category      "EVENT" ;
    schema:issuedBy          <#issuer> ;
    schema:underName         <#underName> ;
    schema:ticketedSeat      <#seat> ;
    solidshare:event         <#event> ;
    schema:validFrom         "2026-07-14T17:00:00Z"^^xsd:dateTime ;
    schema:validThrough      "2026-07-14T23:59:00Z"^^xsd:dateTime ;
    solidshare:source        "PKPASS" ;                  # MANUAL | SCAN | PKPASS
    solidshare:artifact      <boarding.pkpass> ;
    dcterms:source           <originalUri> ;             # present on a copy of a shared ticket
    dcterms:created          "2026-07-02T09:15:00Z"^^xsd:dateTime .
```

Sub-entities (`#issuer`, `#underName`, `#seat`, `#event`, `#place`) are fragment nodes of the same
document. Enumerations are stored as string literals and unknown values are tolerated on read, so a
future category never makes a ticket unreadable. Every property is optional except `schema:name`.

## 2. Surface

### Screens — `presentation/wallet/`

| Screen | What it does |
|---|---|
| `WalletPage` | Upcoming and past passes, rendered as cards |
| `PassCard` | The pass itself — five Apple-parity layouts, see §3 |
| `TicketDetailPage` | The pass back: barcode with a brightness boost, the fields, share, edit, delete |
| `TicketEditPage` | The one form: manual entry, and the confirmation step for every import |
| `TicketImportPage` | What a `.pkpass` or `.pkpasses` bundle yielded, before it is written |
| `TicketSharingPage` | People with access, plus the public pass link — see [ENTITY_SHARING.md](ENTITY_SHARING.md) |
| `SharedTicketPage` | A pass someone shared, with "Add to my wallet" |

### Repository — `data/repo/tickets/`

`TicketsRepository` implements `DataModuleLifecycle`. Its verbs split into the queued writes
(`queueCreate`, `queueUpdate`, `queueDelete` — the only write path), the reads
(`observeTickets`, `getTicket`, `getTicketImages`, `getTicketArtifact`), the shared-entity verbs
(`getSharedTicket`, `getSharedTicketImages`, `addSharedTicketToWallet`, `findTicketCopiedFrom`),
the parsers (`parseTicketQr`, `parseTicketFile`) and `refreshIssuerPasses`.

`TicketBlobStore` (`data/local/cache/`) holds artifacts, pass images and the refresh bookkeeping as
encrypted files keyed by `(webId, ticketUri, role)`, which is what lets a pass render with no
connection.

### Import and rendering

`data/passimport/` holds `PkpassParser`, `PkpassImages`, `BcbpParser` (IATA boarding-pass barcodes)
and `TicketFileSniffer`. `util/BarcodeRenderer.kt` renders the token with ZXing;
`presentation/sharing/BarcodeDecoder.kt` decodes with zxing-cpp.

## 3. How it flows

### Getting a pass in

Every route ends at the same place — a pre-filled `TicketEditPage` the user confirms — so nothing
is written to the pod without being seen.

| Route | What happens |
|---|---|
| **Scan any barcode** | The payload is stored verbatim in `schema:ticketToken` with its symbology in `solidshare:barcodeFormat`. Unrecognized content is still a valid ticket: a token and a title |
| **A Solid Share ticket QR** | `TicketQrCodec` decodes the JSON and fills the whole form (§ below) |
| **`.pkpass` / `.pkpasses`** | Unzipped, `pass.json` mapped best-effort, images extracted, and — for a boarding pass — the barcode token decoded with `BcbpParser` for the fields Apple leaves inside it. The **original bytes are kept** as `solidshare:artifact`. A `.pkpasses` bundle yields up to ten passes |
| **Open-with** | The same path, entered from another app's share sheet, routed through `TicketScanContributor` |

`TicketFileSniffer` classifies by magic bytes rather than by file name, because a pass arriving
from a messaging app is frequently named something else.

Signatures are **not** verified. Import is transcription, not validation: the pod is the user's own
store, and a pass they chose to keep is theirs to keep.

### The Solid Share ticket QR format

Any issuer can offer one-tap add-to-pod, with no integration and no server of ours involved, by
encoding a ticket as JSON in either of two equivalent forms:

```
https://solidshare.app/t#<base64url(JSON)>        ← also an App Link: any camera app opens Solid Share
{ "solidshare": "ticket", "v": 1, "title": …, "token": …, "format": "AZTEC", … }
```

`title` is required; everything else is optional; unknown fields are ignored and `v` allows the
format to evolve. `token` is what the venue scanner expects and `format` names its symbology
(defaulting to `QR_CODE`). Dates are ISO-8601. In the link form the payload lives in the
**fragment**, so it never reaches the solidshare.app server — the link is a container for the
ticket, not a lookup of it.

The full field list is in [TICKET_VOCAB.md](TICKET_VOCAB.md); the parser is
`data/repo/tickets/TicketQrCodec.kt`.

### Rendering a pass

`PassCard` reproduces the five Apple Wallet layouts — **boarding, coupon, event, store card,
generic** — chosen from `solidshare:category`, because a boarding pass that looks like a loyalty
card reads as wrong even when every field is present. The colour comes from the pass where the
artifact carried one and from the category otherwise. The barcode is rendered off the main thread
via a bulk `setPixels` (a per-pixel loop was ~78k JNI calls during composition, and it showed), and
opening a pass boosts screen brightness so a gate scanner can read it.

### Refreshing from the issuer

A `.pkpass` may carry a PassKit web service URL and an authentication token. `PassRefreshWorker`
runs on wallet open and every 12 hours, both network-constrained, and for each such ticket issues
`GET {webServiceUrl}/v1/passes/{passTypeId}/{serialNumber}` with `Authorization: ApplePass <token>`
and an `If-Modified-Since` from the last refresh. A 200 means a new pass file: the ticket's fields,
its artifact and its images are all replaced, and the new `Last-Modified` is stored for next time.
Anything else — 304, an error, no network — leaves the ticket exactly as it was.

This is poll-only. Apple's push channel needs an APNs registration and a server we do not run, and
polling twice a day is enough for a gate change.

## 4. Offline and failure behaviour

The wallet is the feature that most has to work offline — a pass is needed at a gate, and a gate is
where the signal is worst.

- **Everything renders offline.** Rows come from `cached_entity`; images and artifacts come from
  `TicketBlobStore`; the barcode is rendered locally from the stored token.
- **Every write queues.** There is no online-only write path: `queueCreate` mints a **provisional
  URI**, writes the optimistic row and the blobs under it, and enqueues into `module_outbox_op`.
  On drain the real resource is created and the provisional row is replaced. Editing a
  still-queued create rewrites that op instead of adding a second; deleting one drops it.
- **A shared pass is never cached.** `getSharedTicket` reads a foreign ticket without writing
  `cached_entity`, so someone else's pass cannot end up in your wallet by accident.
- **A publicly shared `.pkpass` that refuses an RDF read** (a bare artifact URL answered with 406)
  falls back to fetching the binary and parsing the pass file, so a public pass link still opens.
- **Import is off the main thread** — parsing, image extraction and blob writes all run on
  background dispatchers.
- **Sharing is online-only**, with the standard `RequiresConnection` affordance.

## 5. Extension points

- **A new import source** implements a parser in `data/passimport/`, produces a `TicketDraft`, and
  enters through the same confirm-then-write path. Add a sniffer case if it is a file type.
- **A new pass layout**: `PassLayout` in `PassCard.kt`, selected from the category.
- **A new field**: check [TICKET_VOCAB.md](TICKET_VOCAB.md) first. It is a library change (model
  plus RDF codec), then the form, the pass back, and the vocabulary table.
- **`TicketScanContributor`** claims QR payloads, deep links and open-with files, so `ScanRouter`
  routes them without the scanner knowing tickets exist. **`TicketSharedEntityUi`** contributes the
  home card, the icon and the shared-ticket routes.
- **Google Wallet save-links were supported and removed.** The JWT decode was best-effort and
  unverifiable, "skinny" JWTs carried nothing but a server reference, and the result was a draft
  with a link in the notes — a feature that looked like an import and was not. Stated here so the
  absence reads as a decision.

## 6. Tests

- `data/passimport/PkpassParserTest`, `BcbpParserTest`, `TicketFileSnifferTest` — the mapping from
  a real pass file to a draft, boarding-pass barcode decoding, and classification by magic bytes.
- `data/repo/tickets/TicketsRepositoryOfflineTest` — the queue behaviours that are easy to break: a
  provisional row appears immediately, drain replaces it with the server one, editing a queued
  create rewrites rather than duplicates, a stale provisional row is cleaned up.
- `TicketsRepositoryImagesTest` — images survive the queue, including for a ticket with no pkpass.
- `SharedTicketRepositoryTest` — a foreign ticket never touches the cache; a copy records
  `dcterms:source`, which is what makes the "already in your wallet" guard possible.
- `presentation/wallet/PassCardLogicTest`, `PassVocabularyTest`, `TicketInstantTest`,
  `WithDerivedEventStartTest` — layout selection, field vocabulary, and the date derivations that
  decide whether a pass is upcoming or past.

## 7. Specifications

- [schema.org](https://schema.org/Ticket) — `schema:Ticket` and its neighbours; the vocabulary is
  schema.org first, and every `solidshare:` mint is justified in [TICKET_VOCAB.md](TICKET_VOCAB.md).
- [Solid type index](https://github.com/solid/type-indexes) — how the tickets container is
  discovered by any Solid client rather than found at a guessed path.
- [Apple PassKit](https://developer.apple.com/documentation/walletpasses) — the `.pkpass` package
  and the web-service refresh protocol. Solid Share reads passes and refreshes them; it does not
  sign or issue them.
- [IATA Resolution 792 (BCBP)](https://www.iata.org/en/programs/passenger/common-use/) — the
  boarding-pass barcode format decoded out of pass tokens.
- [WAC](https://solidproject.org/TR/wac) / [ACP](https://solidproject.org/TR/acp) — the grants a
  ticket share writes over the ticket's container.
