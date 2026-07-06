# Tickets (Wallet) in Solid Share

Solid Share stores wallet-style tickets and passes (event tickets, boarding passes, cinema
tickets, loyalty cards, coupons…) as plain Solid resources on the user's own pod. This document
specifies the data model, the container layout, the open **Solid Share ticket QR format** issuers
can adopt for one-tap "add to wallet", and how imports from existing wallet artifacts work.

The implementation lives in two places:

- **AndroidSolidServices** (`api` ≥ 0.6.0): `SolidTicketsDataModule` — CRUD + index + typeIndex
  registration (`api/…/datamodule/tickets/`), models in `shared/model/tickets/`, RDF codecs in
  `shared/rdf/tickets/`.
- **Solid Share app**: `TicketsRepository` (thin delegation), the Wallet UI
  (`presentation/wallet/`), barcode rendering (`util/BarcodeRenderer.kt`, ZXing), scanning
  (ML Kit, all common symbologies), and the pass importers (`data/passimport/`).

## 1. Pod layout

```
{storage}tickets/                     ← container, bootstrapped on first ticket
├── index.ttl                         ← tickets index (one cached row per ticket)
├── {uuid}.ttl                        ← one RDF document per ticket (subject #this)
└── {uuid}.pkpass                     ← optional original imported artifact
```

The container is registered in the user's **type index** as a `solid:instanceContainer` for
`schema:Ticket` (private index by default), so any Solid app can discover the user's tickets.
Listing reads every registered container's `index.ttl` — a single GET per container.

## 2. Ticket resource model

One ticket is one document, primary subject `{doc}#this`, typed `schema:Ticket`
(namespace `https://schema.org/`). Wallet-specific terms come from the Solid Share namespace
`https://solidshare.com/ns#` (prefixed `solidshare:` below). Sub-entities are fragment nodes of
the same document.

```turtle
<#this>  a schema:Ticket ;
    schema:name              "Coldplay — Music of the Spheres" ;
    schema:description       "Gate opens 18:00" ;
    schema:ticketNumber      "TKT-0042" ;
    schema:ticketToken       "c3RhZGl1bS10aWNrZXQ…" ;          # exact barcode payload
    solidshare:barcodeFormat "AZTEC" ;                          # symbology of the token
    solidshare:category      "EVENT" ;
    schema:issuedBy          <#issuer> ;
    schema:underName         <#underName> ;
    schema:ticketedSeat      <#seat> ;
    schema:totalPrice        "89.50" ;
    schema:priceCurrency     "EUR" ;
    schema:dateIssued        "2026-06-01T10:00:00Z"^^xsd:dateTime ;
    solidshare:event         <#event> ;
    schema:validFrom         "2026-07-14T17:00:00Z"^^xsd:dateTime ;
    schema:validThrough      "2026-07-14T23:59:00Z"^^xsd:dateTime ;
    solidshare:source        "PKPASS" ;                         # MANUAL|SCAN|PKPASS|GOOGLE_WALLET
    solidshare:artifact      <{uuid}.pkpass> ;                  # original imported file
    dcterms:created          "2026-07-02T09:15:00Z"^^xsd:dateTime ;
    dcterms:modified         "2026-07-02T09:15:00Z"^^xsd:dateTime .

<#issuer>    a schema:Organization ; schema:name "Ticketmaster" .
<#underName> a schema:Person ;       schema:name "Erfan Gholami" .
<#seat>      a schema:Seat ;
    schema:seatNumber "27" ; schema:seatRow "F" ; schema:seatSection "B12" .
<#event>     a schema:Event ;
    schema:name      "Music of the Spheres Tour" ;
    schema:startDate "2026-07-14T19:30:00Z"^^xsd:dateTime ;
    schema:endDate   "2026-07-14T23:00:00Z"^^xsd:dateTime ;
    schema:location  <#place> .
<#place>     a schema:Place ;
    schema:name "Johan Cruijff ArenA" ; schema:address "Arena Boulevard 1, Amsterdam" .
```

Enumerations (stored as string literals, unknown values tolerated on read):

| Term | Values |
|---|---|
| `solidshare:category` | `EVENT FLIGHT TRAIN BUS CINEMA LOYALTY COUPON GENERIC` |
| `solidshare:barcodeFormat` | `QR_CODE AZTEC PDF_417 CODE_128 CODE_39 CODE_93 EAN_13 EAN_8 UPC_A UPC_E ITF CODABAR DATA_MATRIX NONE` |
| `solidshare:source` | `MANUAL SCAN PKPASS GOOGLE_WALLET` |

**`schema:ticketToken` is sacred**: it is the exact payload the issuer encoded and is re-rendered
verbatim (via ZXing, in the symbology recorded by `solidshare:barcodeFormat`) so gate scanners
read the identical barcode.

## 3. Tickets index

`{container}index.ttl` caches one row per ticket so the wallet list renders from a single GET
(the `people.ttl` idiom of the contacts module):

```turtle
<{uuid}.ttl#this> a schema:Ticket ;
    schema:name         "Coldplay — Music of the Spheres" ;
    solidshare:category "EVENT" ;
    schema:startDate    "2026-07-14T19:30:00Z"^^xsd:dateTime ;
    solidshare:issuer   "Ticketmaster" ;
    schema:validThrough "2026-07-14T23:59:00Z"^^xsd:dateTime .
```

The data module rewrites the row on every create/update/delete.

## 4. The Solid Share ticket QR format ("add to wallet")

Any issuer can offer one-tap add-to-pod by encoding a ticket in one of two equivalent forms:

**Link form** (doubles as an App Link — scanning with any camera app opens Solid Share):

```
https://solidshare.app/t#<base64url(JSON)>
```

**Bare JSON form** (any QR with this marker):

```json
{
  "solidshare": "ticket",
  "v": 1,
  "title": "Coldplay — Music of the Spheres",
  "category": "EVENT",
  "token": "c3RhZGl1bS10aWNrZXQ…",
  "format": "AZTEC",
  "number": "TKT-0042",
  "holder": "Erfan Gholami",
  "issuer": "Ticketmaster",
  "price": "89.50",
  "currency": "EUR",
  "description": "Gate opens 18:00",
  "event": {
    "name": "Music of the Spheres Tour",
    "start": "2026-07-14T19:30:00Z",
    "end": "2026-07-14T23:00:00Z",
    "venue": "Johan Cruijff ArenA",
    "address": "Arena Boulevard 1, Amsterdam"
  },
  "seat": { "section": "B12", "row": "F", "number": "27" },
  "validFrom": "2026-07-14T17:00:00Z",
  "validThrough": "2026-07-14T23:59:00Z",
  "issued": "2026-06-01T10:00:00Z"
}
```

Rules:

- `title` is required; everything else is optional.
- `token` is the payload the venue scanner expects; `format` names its symbology (defaults to
  `QR_CODE` when a token is present). Dates are ISO-8601.
- Unknown fields are ignored (`v` allows evolution).
- The link form is `base64url` (no padding required) of the UTF-8 JSON, carried in the URI
  fragment — it never reaches the solidshare.app server.
- Parser: `TicketQrCodec` (`data/repo/tickets/TicketQrCodec.kt`). Recognized by the universal
  scanner (the bottom-bar **+**), the Wallet scanner, and clicked links (App Links, `/t` path).

## 5. Importing existing wallet artifacts

- **Apple Wallet `.pkpass`** (`PkpassParser`): the file is unzipped, `pass.json` is mapped
  best-effort (description→title, organizationName→issuer, serialNumber→number, first
  `barcodes[]` entry→token+format, pass style→category, `relevantDate`→event start,
  `expirationDate`→valid-until, field labels/values→notes). The **original `.pkpass` bytes are
  preserved on the pod** as `solidshare:artifact` next to the ticket. Entry points: Wallet →
  Import pass file, or opening/sharing a `.pkpass` with Solid Share from any app.
  Signatures are *not* verified — import is transcription, not validation.
- **Google Wallet save-links** (`GoogleWalletParser`): links of the form
  `https://pay.google.com/gp/v/save/<JWT>` are recognized when scanned or pasted; the JWT payload
  is base64-decoded **without signature verification** and the first pass object
  (`eventTicketObjects`, `flightObjects`, …) is mapped best-effort. "Skinny" JWTs that only
  reference server-side objects degrade to a draft with the link in the notes.

Every import lands in the pre-filled ticket form for the user to confirm before anything is
written to the pod.

## 6. Sharing

Tickets are ordinary pod resources, so the existing Solid Share sharing pipeline applies
unchanged: the ticket detail page's share action opens Manage access for the ticket document,
grants land in WAC/ACP as usual, and the receiver gets the standard notification.
