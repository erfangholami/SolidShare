# The Solid Share ticket vocabulary

*Part of the [Solid Share documentation set](README.md). The feature that uses it is
[TICKETS.md](TICKETS.md).*

The normative term dictionary for a ticket on a pod. It is a **superset** of every field a
wallet source can yield, so the vocabulary is never the reason data gets dropped. The active
acquisition paths today are `.pkpass`/`.pkpasses` import (with BCBP decoding of boarding-pass
barcodes), the Solid Share ticket QR format, and manual entry; the remaining rows are
deliberate headroom so other writers can use the same vocabulary:

| Source | Fields it can yield | Covered here |
|---|---|---|
| Apple Wallet `.pkpass` *(active)* | 104 semantic tags + 6 structured types + the pass-level keys that carry data | yes |
| IATA BCBP (boarding-pass barcode) *(active, inside pkpass tokens)* | 43 items | yes |
| Bare barcode / QR *(active)* | payload + symbology | yes |
| Google Wallet save-link (inline JWT) *(headroom)* | event/venue/seat/date/barcode | yes |
| UIC 918-3 / FCB (European rail barcode) *(headroom)* | issuing, traveller, reservation, open-ticket, pass | yes |
| PDF / screenshot (barcode + OCR) *(headroom)* | whatever OCR yields | yes, via `solidshare:detail` |

**Every property is optional.** A ticket that lacks a term simply omits the triple; the Kotlin
model reads it back as `null` or an empty list. Only `schema:name` (the display title) is required.

Two rules governed the design:

1. **schema.org first.** A term is minted under `solidshare:` only where schema.org demonstrably
   has nothing. Each mint below is justified. (Verified against schema.org's full RDF dump —
   17,949 triples — not by reading doc pages.)
2. **Never mint against a superseded term, and prefer core over `pending.schema.org`.** Pending
   terms can change or vanish; pod data outlives them. Where the only schema.org candidate is
   pending, that is called out.

Namespaces:

```turtle
@prefix schema:     <https://schema.org/> .
@prefix solidshare: <https://solidshare.app/ns#> .
@prefix dcterms:    <http://purl.org/dc/terms/> .
@prefix xsd:        <http://www.w3.org/2001/XMLSchema#> .
```

---

## 1. Document shape

One ticket is one document (`{tickets}/{uuid}/ticket`, extension-less — the URI never encodes a
representation), living in its own container next to its binary attachments. The primary subject
`#this` is **always** a `schema:Ticket` — this keeps the identity, the type-index registration
(a `solid:instance` for `schema:Ticket` pointing at the tickets index, itself typed
`solidshare:TicketIndex`) and the index rows consistent, and means any Solid app looking for
tickets finds one.

What the ticket *admits you to* hangs off a **reservation envelope**. schema.org's `Ticket` has no
outbound link to its event or journey — the only link is `Reservation.reservedTicket` pointing
*inward* — so the reservation points at the ticket, which is the direction schema.org intends. A
minted `solidshare:reservation` provides the forward link so readers don't need a reverse scan.

```
#this        schema:Ticket              the ticket itself (always present)
├─ #reservation   schema:*Reservation   booking envelope — travel & events
│  └─ #trip       schema:Flight | TrainTrip | BusTrip | BoatTrip
│     ├─ #from    schema:Airport | TrainStation | BusStop | BoatTerminal
│     └─ #to      "
│  └─ #event      schema:Event | MusicEvent | SportsEvent | ScreeningEvent | TheaterEvent
│     └─ #venue   schema:Place
├─ #seat1…n       schema:Seat           repeatable — pkpass semantics.seats[] is a list
├─ #barcode1…n    solidshare:Barcode    repeatable — pkpass barcodes[] is a list
├─ #issuer        schema:Organization | schema:Airline
├─ #holder        schema:Person
├─ #membership    schema:ProgramMembership   loyalty / frequent flyer
├─ #style         solidshare:PassStyle       colours + images, for faithful re-render and export
├─ #detail1…n     solidshare:Detail          label/value catch-all — the no-data-lost guarantee
├─ #relevance1…n  solidshare:RelevantLocation
└─ #wifi1…n       solidshare:WifiNetwork
```

`#this` is a `schema:Ticket` even for a loyalty card or coupon; those simply have no
`#reservation`, and carry `#membership` or the offer terms instead.

The reservation type is chosen from the category, and is the *only* place the category is
load-bearing — `solidshare:category` is retained as a cheap UI grouping key, but it is now
derivable from the RDF type rather than the source of truth.

| `solidshare:category` | `#reservation` type | `#reservation schema:reservationFor` |
|---|---|---|
| `EVENT`, `CINEMA` | `schema:EventReservation` | `schema:Event` (or `ScreeningEvent`, `MusicEvent`, `SportsEvent`, `TheaterEvent`, `Festival`) |
| `FLIGHT` | `schema:FlightReservation` | `schema:Flight` |
| `TRAIN` | `schema:TrainReservation` | `schema:TrainTrip` |
| `BUS` | `schema:BusReservation` | `schema:BusTrip` |
| `BOAT` | `schema:BoatReservation` ⚠ *pending* | `schema:BoatTrip` ⚠ *pending* |
| `LODGING` | `schema:LodgingReservation` | `schema:LodgingBusiness` |
| `LOYALTY`, `COUPON`, `GENERIC` | *(none)* | *(none)* |

---

## 2. `#this` — the ticket (`schema:Ticket`)

| Term | Range | Meaning |
|---|---|---|
| `schema:name` | Text | **Required.** Display title. |
| `schema:description` | Text | Free-text notes. |
| `schema:ticketNumber` | Text | The ticket's own number — *distinct from* the booking reference. |
| `schema:ticketToken` | Text | The primary barcode payload, mirrored here for schema.org consumers. Authoritative copy lives on `#barcode1`. |
| `schema:ticketedSeat` | `schema:Seat` | **Repeatable.** |
| `schema:issuedBy` | `schema:Organization` | → `#issuer`. |
| `schema:underName` | `schema:Person` | → `#holder`. |
| `schema:dateIssued` | xsd:dateTime | |
| `schema:totalPrice` | Text | Raw lexical value. |
| `schema:priceCurrency` | Text | ISO-4217. |
| `schema:validFrom` | xsd:dateTime | |
| `schema:validThrough` | xsd:dateTime | Also fed by pkpass `expirationDate`. |
| `schema:image` | URL | Single generic image slot (all `Thing`s have one). |
| `solidshare:reservation` | `schema:Reservation` | **Mint.** Forward link; schema.org only provides the inverse. |
| `solidshare:barcode` | `solidshare:Barcode` | **Mint.** Repeatable. |
| `solidshare:category` | Text | `EVENT FLIGHT TRAIN BUS BOAT CINEMA LODGING LOYALTY COUPON GENERIC` |
| `solidshare:source` | Text | `MANUAL SCAN PKPASS GOOGLE_WALLET BCBP UIC PDF IMAGE LINK` |
| `solidshare:artifact` | URL | The original imported file kept verbatim in the ticket's container. |
| `solidshare:artifactVerified` | xsd:boolean | **Mint.** Did the artifact's issuer signature verify? |
| `solidshare:logoImage` `iconImage` `stripImage` `thumbnailImage` `footerImage` `backgroundImage` | URL | **Mint.** The stored pass images in the ticket's container; server-managed like `artifact` (survive an update). |
| `solidshare:voided` | xsd:boolean | **Mint.** Redeemed / cancelled. pkpass `voided`. |
| `solidshare:style` | `solidshare:PassStyle` | **Mint.** |
| `solidshare:detail` | `solidshare:Detail` | **Mint.** Repeatable. |
| `solidshare:relevantLocation` | `solidshare:RelevantLocation` | **Mint.** Repeatable. |
| `solidshare:relevantDate` | xsd:dateTime | **Mint.** When the pass should surface. |
| `solidshare:wifiNetwork` | `solidshare:WifiNetwork` | **Mint.** Repeatable. pkpass `wifiAccess`. |
| `solidshare:silenceRequested` | xsd:boolean | **Mint.** |
| `solidshare:serialNumber` | Text | **Mint.** The issuer's own pass serial (pkpass `serialNumber`). |
| `solidshare:groupingIdentifier` | Text | **Mint.** Groups related passes (multi-leg trips). |
| `solidshare:organizationName` | Text | **Mint.** pkpass `organizationName` when no richer issuer node exists. |
| `dcterms:created` / `dcterms:modified` | xsd:dateTime | Pod resource timestamps. |

---

## 3. `#reservation` — the booking envelope

| Term | Range | Meaning |
|---|---|---|
| `schema:reservedTicket` | `schema:Ticket` | → `#this`. The canonical schema.org link. |
| `schema:reservationFor` | Thing | → `#trip` or `#event`. |
| `schema:reservationId` | Text | **The booking reference / PNR / confirmation number.** |
| `schema:reservationStatus` | `schema:ReservationStatusType` | `ReservationConfirmed` \| `ReservationPending` \| `ReservationHold` \| `ReservationCancelled` |
| `schema:bookingTime` | xsd:dateTime | |
| `schema:modifiedTime` | xsd:dateTime | |
| `schema:underName` | `schema:Person` | |
| `schema:broker` | `schema:Organization` | The selling agent. (Core; `bookingAgent` is superseded.) |
| `schema:programMembershipUsed` | `schema:ProgramMembership` | → `#membership`. |
| `schema:totalPrice` / `schema:priceCurrency` | Text | |
| `schema:boardingGroup` | Text | |
| `schema:passengerSequenceNumber` | Text | BCBP check-in sequence number. |
| `schema:passengerPriorityStatus` | Text | e.g. Gold, Silver. |
| `schema:securityScreening` | Text | e.g. Priority. |
| `schema:checkinTime` / `schema:checkoutTime` | xsd:dateTime | `LodgingReservation` only. |
| `schema:numAdults` / `schema:numChildren` | xsd:integer | `LodgingReservation` only. |
| `solidshare:boardingZone` | Text | **Mint.** |
| `solidshare:fareClass` | Text | **Mint.** `fare` has **zero hits** across schema.org. Airline booking class (Y, J), rail fare product, pkpass `ticketFareClass`. |
| `solidshare:compartmentCode` | Text | **Mint.** BCBP compartment code (cabin class). |
| `solidshare:passengerStatus` | Text | **Mint.** BCBP passenger status. |
| `solidshare:baggageTag` | Text | **Mint.** Repeatable. BCBP baggage tag licence-plate numbers. |
| `solidshare:baggageAllowance` | Text | **Mint.** |
| `solidshare:fastTrack` | xsd:boolean | **Mint.** |
| `solidshare:electronicTicket` | xsd:boolean | **Mint.** BCBP e-ticket indicator. |
| `solidshare:specialServiceRequest` | Text | **Mint.** Repeatable. IATA SSR codes. |
| `solidshare:passengerCapability` | Text | **Mint.** Repeatable. |
| `solidshare:documentsVerified` | xsd:boolean | **Mint.** |
| `solidshare:tariff` | Text | **Mint.** UIC fare/tariff descriptor. |
| `solidshare:validityRegion` | Text | **Mint.** UIC zone/line/region validity, for tickets with no station pair. |

> **Note on domains.** `boardingGroup`, `passengerSequenceNumber`, `passengerPriorityStatus` and
> `securityScreening` are declared on `FlightReservation`. schema.org's `domainIncludes` is
> explicitly **non-constraining**, so using them on a `TrainReservation` or `BusReservation` is
> permitted, and preferable to fragmenting the vocabulary with parallel minted terms.

---

## 4. `#trip` — the journey (`schema:Flight` | `TrainTrip` | `BusTrip` | `BoatTrip`)

This is the two-ended thing the old model could not express.

| Term | Range | Applies to | Meaning |
|---|---|---|---|
| `schema:departureTime` | xsd:dateTime | all | **The live / effective time** — updated on delay. |
| `schema:arrivalTime` | xsd:dateTime | all | Live / effective. |
| `schema:provider` | `schema:Organization` | all | The operating carrier. ⚠ *pending* in schema.org — but `Flight.carrier` is **superseded by it**, so it is the only forward-looking option. |
| `schema:flightNumber` | Text | Flight | |
| `schema:departureAirport` / `schema:arrivalAirport` | `schema:Airport` | Flight | |
| `schema:departureGate` / `schema:arrivalGate` | Text | Flight | |
| `schema:departureTerminal` / `schema:arrivalTerminal` | Text | Flight | |
| `schema:aircraft` | Text | Flight | |
| `schema:boardingPolicy` | `schema:BoardingPolicyType` | Flight | `GroupBoardingPolicy` \| `ZoneBoardingPolicy` |
| `schema:trainNumber` / `schema:trainName` | Text | TrainTrip | |
| `schema:departureStation` / `schema:arrivalStation` | `schema:TrainStation` | TrainTrip | |
| `schema:departurePlatform` / `schema:arrivalPlatform` | Text | TrainTrip | |
| `schema:busNumber` / `schema:busName` | Text | BusTrip | |
| `schema:departureBusStop` / `schema:arrivalBusStop` | `schema:BusStop` | BusTrip | |
| `solidshare:originalDepartureTime` | xsd:dateTime | all | **Mint.** Originally scheduled. schema.org has **no scheduled-vs-actual pair anywhere** (`delay` and `actual` are zero hits); `Trip.departureTime` is single-valued. Convention: the schema.org term holds the live value, the minted `original*` term is frozen at issue — matching Apple. |
| `solidshare:originalArrivalTime` | xsd:dateTime | all | **Mint.** |
| `solidshare:boardingTime` | xsd:dateTime | all | **Mint.** `Event.doorTime` is Event-only; `Flight.webCheckinTime` is when check-in *opens*, not boarding. |
| `solidshare:originalBoardingTime` | xsd:dateTime | all | **Mint.** |
| `solidshare:departurePlatform` / `solidshare:arrivalPlatform` | Text | Bus, Boat | **Mint.** `BusTrip` has no platform/gate term at all. |
| `solidshare:transitStatus` | Text | all | **Mint.** e.g. On Time, Delayed. |
| `solidshare:transitStatusReason` | Text | all | **Mint.** e.g. Thunderstorms. |
| `solidshare:vehicleName` | Text | all | **Mint.** e.g. a boat's name. |
| `solidshare:vehicleNumber` | Text | all | **Mint.** Aircraft registration / train set number. |
| `solidshare:vehicleType` | Text | all | **Mint.** e.g. aircraft model. |
| `solidshare:coachNumber` | Text | Train | **Mint.** Rail carriage / car number (pkpass `carNumber`, UIC `coach`). |
| `solidshare:serviceBrand` | Text | Train, Bus | **Mint.** e.g. ICE, TGV. UIC `serviceBrand`. |
| `solidshare:duration` | xsd:duration | all | **Mint.** `Trip` has no duration term. |

### `#from` / `#to` — the stops

| Term | Range | Meaning |
|---|---|---|
| `schema:name` | Text | Airport / station / stop name. |
| `schema:address` | Text | |
| `schema:geo` | `schema:GeoCoordinates` | |
| `schema:iataCode` | Text | `schema:Airport` only. |
| `solidshare:stopCode` | Text | **Mint.** Non-air station code (UIC station code, local carrier code). |
| `solidshare:cityName` | Text | **Mint.** Display city, distinct from the station name. |
| `solidshare:timeZone` | Text | **Mint.** tz database ID, e.g. `Europe/Amsterdam`. Needed to render a departure time correctly abroad. |
| `solidshare:securityProgram` | Text | **Mint.** Repeatable. e.g. TSA PreCheck. |

Gate, terminal and platform live on `#trip`, not here — that is schema.org's own split (the gate
belongs to the journey, the airport is a place).

---

## 5. `#event` and `#venue`

| Term | Range | Meaning |
|---|---|---|
| `schema:name` | Text | |
| `schema:startDate` / `schema:endDate` | xsd:dateTime | |
| `schema:doorTime` | xsd:dateTime | When admission commences. |
| `schema:eventStatus` | `schema:EventStatusType` | `EventScheduled` \| `EventRescheduled` \| `EventPostponed` \| `EventCancelled` \| `EventMovedOnline` |
| `schema:previousStartDate` | xsd:dateTime | The only reschedule-tracking term in schema.org. |
| `schema:location` | `schema:Place` | → `#venue`. |
| `schema:performer` | `schema:Person` \| `schema:Organization` | Repeatable. |
| `schema:organizer` | `schema:Organization` | |
| `schema:duration` | xsd:duration | |
| `schema:homeTeam` / `schema:awayTeam` | `schema:SportsTeam` | `SportsEvent` only. |
| `schema:workPresented` | `schema:Movie` | `ScreeningEvent` only — the film. |
| `solidshare:genre` | Text | **Mint.** `Event` is not a `CreativeWork`, so it has no `genre`. |
| `solidshare:sportName` | Text | **Mint.** `SportsEvent.sport` is *pending*. |
| `solidshare:leagueName` / `solidshare:leagueAbbreviation` | Text | **Mint.** No schema.org term. |
| `solidshare:teamAbbreviation` | Text | **Mint.** On the team node. |
| `solidshare:admissionLevel` | Text | **Mint.** e.g. General Admission, VIP. |
| `solidshare:admissionLevelAbbreviation` | Text | **Mint.** e.g. GA, VIP. |
| `solidshare:gatesOpenTime` | xsd:dateTime | **Mint.** |
| `solidshare:boxOfficeOpenTime` | xsd:dateTime | **Mint.** |
| `solidshare:parkingOpenTime` | xsd:dateTime | **Mint.** |
| `solidshare:fanZoneOpenTime` | xsd:dateTime | **Mint.** |
| `solidshare:venueOpenTime` / `solidshare:venueCloseTime` | xsd:dateTime | **Mint.** |
| `solidshare:tailgatingAllowed` | xsd:boolean | **Mint.** |
| `solidshare:dateUnannounced` / `solidshare:dateUndetermined` | xsd:boolean | **Mint.** TBA / TBD events (pkpass `EventDateInfo`). |

`#venue` is a `schema:Place`: `schema:name`, `schema:address` (→ `schema:PostalAddress`),
`schema:geo`, `schema:telephone`, plus minted `solidshare:room`, `solidshare:entrance`,
`solidshare:entranceGate`, `solidshare:entranceDoor`, `solidshare:entrancePortal`,
`solidshare:regionName` — none of which schema.org provides for a venue.

---

## 6. `#seat` (`schema:Seat`) — repeatable

| Term | Range | Meaning |
|---|---|---|
| `schema:seatNumber` | Text | |
| `schema:seatRow` | Text | (Not `rowNumber` — that does not exist.) |
| `schema:seatSection` | Text | |
| `schema:seatingType` | Text | Type/class of seat. |
| `solidshare:seatIdentifier` | Text | **Mint.** |
| `solidshare:seatLevel` | Text | **Mint.** |
| `solidshare:seatAisle` | Text | **Mint.** |
| `solidshare:seatDescription` | Text | **Mint.** e.g. "A flat bed seat". |
| `solidshare:seatSectionColor` | Text | **Mint.** CSS rgb triple. |
| `solidshare:coach` | Text | **Mint.** UIC coach for this specific place. |

---

## 7. `#barcode` (`solidshare:Barcode`) — repeatable

Entirely minted. **`schema:Barcode` exists but is an `ImageObject`** — a *picture* of a barcode,
with zero own properties. Its `encodingFormat` is a MIME type (`image/png`), **not a symbology**.
A vocabulary-wide search found `symbology`, `aztec`, `pdf417` and `qrcode` all at **zero hits**.
This is a genuine, confirmed hole in schema.org.

| Term | Range | Meaning |
|---|---|---|
| `solidshare:payload` | Text | The exact bytes the issuer encoded. **Sacred** — re-rendered verbatim so gate scanners read an identical barcode. |
| `solidshare:symbology` | Text | `QR_CODE AZTEC PDF_417 CODE_128 CODE_39 CODE_93 EAN_13 EAN_8 UPC_A UPC_E ITF CODABAR DATA_MATRIX` |
| `solidshare:encoding` | Text | IANA charset of the payload, e.g. `iso-8859-1`. Round-tripping this wrong corrupts the barcode. |
| `solidshare:altText` | Text | Human-readable fallback shown beneath the barcode. |
| `solidshare:rotating` | xsd:boolean | **Mint.** Flags a payload known to rotate server-side (Ticketmaster SafeTix, AXS). Such a token is a snapshot and **will not scan later** — the UI must say so rather than show a dead barcode. |

---

## 8. `#issuer`, `#holder`, `#membership`

`#issuer` — `schema:Organization` (or `schema:Airline`, which adds `schema:iataCode`):
`schema:name`, `schema:logo`, `schema:url`, `schema:telephone`, `schema:email`.
`schema:logo`'s domain includes `Organization` but **not** `Ticket` — which is exactly why the
issuer logo hangs here and the pass images hang off `#style`.

`#holder` — `schema:Person`: `schema:name`, `schema:givenName`, `schema:familyName`,
`schema:additionalName`, `schema:honorificPrefix`, `schema:honorificSuffix`, plus minted
`solidshare:phoneticName` (pkpass `PersonNameComponents.phoneticRepresentation`) and
`solidshare:nickname`.

`#membership` — `schema:ProgramMembership`: `schema:programName`, `schema:membershipNumber`,
`schema:hostingOrganization`, plus minted `solidshare:membershipStatus` (tier, e.g. Gold),
`solidshare:pointsBalance` (xsd:decimal) and `solidshare:balance` + `solidshare:balanceCurrency`
(a store card's redeemable value). schema.org's `membershipPointsEarned` is *pending* **and**
means points *earned*, not a running balance — so it cannot carry this.

---

## 9. `#style`, `#detail`, `#relevance`, `#wifi`

`#style` — `solidshare:PassStyle`. Entirely minted; schema.org models no presentation.
Lets the wallet re-render the pass faithfully **and** export it back to `.pkpass`:
`solidshare:foregroundColor`, `solidshare:backgroundColor`, `solidshare:labelColor`,
`solidshare:stripColor`, `solidshare:footerBackgroundColor`, `solidshare:logoText`,
`solidshare:logoSymbolName`. The pass **images** are not style properties: they are stored
binaries in the ticket's container, linked from `#this` (`solidshare:logoImage` …) so they are
server-managed like the artifact and survive updates.

`#detail` — `solidshare:Detail`. **The no-data-lost guarantee.** Anything an importer reads but
cannot map to a term above lands here verbatim rather than being discarded:
`solidshare:label`, `solidshare:value`, `solidshare:placement`
(`HEADER PRIMARY SECONDARY AUXILIARY BACK ADDITIONAL`), `solidshare:order` (xsd:integer).
This is where pkpass's free-text `backFields`, OCR'd PDF lines, and BCBP's airline-individual-use
blob end up.

`#relevance` — `solidshare:RelevantLocation`: `schema:geo`, `solidshare:maxDistance`
(xsd:integer, metres), `solidshare:relevantText`.

`#wifi` — `solidshare:WifiNetwork`: `solidshare:ssid`, `solidshare:password`.

---

## 10. Worked example — the bus ticket

```turtle
<#this> a schema:Ticket ;
    schema:name          "Amsterdam → Berlin" ;
    schema:ticketNumber  "TKT-0042" ;
    schema:ticketToken   "MSBYRUZBTiBHSE9MQU1J…" ;
    schema:ticketedSeat  <#seat1> ;
    schema:issuedBy      <#issuer> ;
    schema:underName     <#holder> ;
    schema:totalPrice    "24.99" ; schema:priceCurrency "EUR" ;
    solidshare:category  "BUS" ;
    solidshare:source    "PKPASS" ;
    solidshare:artifact  <artifact.pkpass> ;
    solidshare:logoImage <logo.png> ;
    solidshare:iconImage <icon.png> ;
    solidshare:reservation <#reservation> ;
    solidshare:barcode   <#barcode1> ;
    solidshare:style     <#style> ;
    dcterms:created      "2026-07-14T09:15:00Z"^^xsd:dateTime .

<#reservation> a schema:BusReservation ;
    schema:reservedTicket    <#this> ;
    schema:reservationFor    <#trip> ;
    schema:reservationId     "PNR7X2Q" ;
    schema:reservationStatus schema:ReservationConfirmed ;
    solidshare:fareClass     "Standard" .

<#trip> a schema:BusTrip ;
    schema:busNumber        "042" ;
    schema:busName          "FlixBus 042" ;
    schema:provider         <#issuer> ;
    schema:departureBusStop <#from> ;
    schema:arrivalBusStop   <#to> ;
    schema:departureTime    "2026-08-02T08:15:00+02:00"^^xsd:dateTime ;
    schema:arrivalTime      "2026-08-02T14:40:00+02:00"^^xsd:dateTime ;
    solidshare:departurePlatform "B3" ;
    solidshare:duration          "PT6H25M"^^xsd:duration .

<#from>  a schema:BusStop ; schema:name "Amsterdam Sloterdijk" ;
         solidshare:cityName "Amsterdam" ; solidshare:timeZone "Europe/Amsterdam" .
<#to>    a schema:BusStop ; schema:name "Berlin ZOB" ;
         solidshare:cityName "Berlin" ;    solidshare:timeZone "Europe/Berlin" .
<#seat1> a schema:Seat ; schema:seatNumber "12A" .
<#issuer> a schema:Organization ; schema:name "FlixBus" ; schema:logo <logo.png> .
<#holder> a schema:Person ; schema:name "Erfan Gholami" .

<#barcode1> a solidshare:Barcode ;
    solidshare:payload   "MSBYRUZBTiBHSE9MQU1J…" ;
    solidshare:symbology "AZTEC" ;
    solidshare:encoding  "iso-8859-1" ;
    solidshare:altText   "PNR7X2Q" .

<#style> a solidshare:PassStyle ;
    solidshare:backgroundColor "rgb(115,196,63)" .
```

---

## 11. Summary of minted terms

schema.org covers roughly 60 of the properties above. The gaps below are **confirmed absent** from
schema.org and are the complete set of `solidshare:` mints — grouped by why they had to exist.

| Group | Terms | Why schema.org can't carry it |
|---|---|---|
| Barcode | `Barcode`, `payload`, `symbology`, `encoding`, `altText`, `rotating`, `barcode` | `schema:Barcode` is an image class; symbology is a zero-hit term |
| Delay tracking | `originalDepartureTime`, `originalArrivalTime`, `boardingTime`, `originalBoardingTime`, `transitStatus`, `transitStatusReason` | no scheduled-vs-actual pair exists; `doorTime` is Event-only |
| Bus / boat | `departurePlatform`, `arrivalPlatform` | `BusTrip` has no gate/platform term |
| Journey extras | `vehicleName`, `vehicleNumber`, `vehicleType`, `coachNumber`, `serviceBrand`, `duration` | no `Trip`-level equivalents |
| Stops | `stopCode`, `cityName`, `timeZone`, `securityProgram` | only `Airport.iataCode` exists |
| Fare | `fareClass`, `compartmentCode`, `tariff`, `validityRegion` | `fare` = zero hits vocabulary-wide |
| Boarding / BCBP | `boardingZone`, `passengerStatus`, `baggageTag`, `baggageAllowance`, `fastTrack`, `electronicTicket`, `specialServiceRequest`, `passengerCapability`, `documentsVerified` | none exist |
| Event extras | `genre`, `sportName`, `leagueName`, `leagueAbbreviation`, `teamAbbreviation`, `admissionLevel`, `admissionLevelAbbreviation`, `gatesOpenTime`, `boxOfficeOpenTime`, `parkingOpenTime`, `fanZoneOpenTime`, `venueOpenTime`, `venueCloseTime`, `tailgatingAllowed`, `dateUnannounced`, `dateUndetermined` | `Event` is not a `CreativeWork` (no genre); venue timings and admission levels have no terms |
| Venue | `room`, `entrance`, `entranceGate`, `entranceDoor`, `entrancePortal`, `regionName` | `Place` has none of these |
| Seat | `seatIdentifier`, `seatLevel`, `seatAisle`, `seatDescription`, `seatSectionColor`, `coach` | `schema:Seat` has only 4 properties |
| Loyalty | `membershipStatus`, `pointsBalance`, `balance`, `balanceCurrency` | `membershipPointsEarned` is *pending* and means "earned", not "balance" |
| Presentation | `PassStyle`, `foregroundColor`, `backgroundColor`, `labelColor`, `stripColor`, `footerBackgroundColor`, `logoText`, `logoSymbolName` | schema.org models no presentation |
| Attachments | `logoImage`, `iconImage`, `stripImage`, `thumbnailImage`, `footerImage`, `backgroundImage` | stored pass images in the ticket's container, linked from `#this`; `schema:logo`'s domain excludes `Ticket` |
| Discovery | `TicketIndex` | types the tickets index the type-index `solid:instance` registration points at |
| Catch-all | `Detail`, `label`, `value`, `placement`, `order` | the guarantee that no extracted field is ever dropped |
| Relevance | `RelevantLocation`, `maxDistance`, `relevantText`, `relevantDate`, `WifiNetwork`, `ssid`, `password`, `silenceRequested` | none exist |
| Housekeeping | `reservation`, `category`, `source`, `artifact`, `artifactVerified`, `voided`, `serialNumber`, `groupingIdentifier`, `organizationName`, `phoneticName`, `nickname` | app-level bookkeeping and the forward link schema.org omits |

---

## 12. The namespace IRI

The vocabulary is minted under **`https://solidshare.app/ns#`** — the domain the project owns and
verifies (App Links, `assetlinks.json`, the OAuth redirect, the ticket QR format all live there),
so the namespace IRI can dereference to the vocabulary it names. The sharing pipeline
(`solidshare:mode`, `solidshare:Share`, …) uses the same namespace. No migration of existing data
is carried: `solidshare.app` is treated as the vocabulary's home from the start.
