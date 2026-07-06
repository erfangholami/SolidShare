# Contacts in Solid Share

Solid Share treats the pod as the user's canonical address book — but the app itself is a
**viewer, not an editor**. Contacts flow into the pod from real sources (Google, vCard files,
scanned Solid profiles) and flow out to the places they are useful (the phone's Contacts app, the
sharing receiver picker, vCard exports). There are no create/edit forms in the app.

Implementation split:

- **AndroidSolidServices** (`api` ≥ 0.6.0): the contacts data module, redesigned around role
  interfaces — `SolidContactsDataModule` is a facade exposing `books: AddressBookStore`,
  `contacts: ContactStore`, `groups: GroupStore` (factory `getInstance`). The writable state of a
  contact is one immutable snapshot, `ContactData`, built with a Kotlin DSL
  (`contactData { name {…}; phone("+31…", PhoneType.CELL); address(AddressType.HOME) {…};
  webId("https://…#me") }`, `buildUpon {}` for derived edits). Reads return `SolidContact`
  (uri/etag/modified/photoUri + data). `ContactStore.findByWebId` scans the user's books for a
  WebId-typed URL — the duplicate check behind "add scanned profile".
- **Solid Share app**: read-only list + detail UI (`presentation/contacts/`), a contacts settings
  page, the sync stack (`sync/`), and vCard I/O (`util/VCardReader`, `util/VCardWriter`).

## 1. Pod data model (W3C vCard ontology, SolidOS layout)

```
{storage}contacts/{bookUuid}/
├── index.ttl#this        ← vcard:AddressBook (dc:title, nameEmailIndex, groupIndex)
├── people.ttl            ← contact list index (cached vcard:fn per contact)
├── groups.ttl            ← group list index
├── Person/{uuid}/
│   ├── index.ttl#this    ← vcard:Individual
│   └── photo.jpg         ← photo binary (vcard:hasPhoto target)
└── Group/{Name}.ttl      ← vcard:Group
```

Covered vCard parameters per contact: `fn`, structured name (`hasName` — family/given/additional/
prefix/suffix), `nickname`, **typed** telephones (`hasTelephone` nodes classed `vcard:Cell/Home/
Work/Fax/Pager/Voice/Text/Video/TextPhone`), **typed** emails (`hasEmail` + `Home/Work`),
**postal addresses** (`hasAddress` → `vcard:Address` + `Home/Work`; street-address, locality,
region, postal-code, country-name, post-office-box), `bday`, `anniversary`, `organization-name`,
`organization-unit`, `role`, `title`, `note`, typed URLs (`url` nodes incl. `vcard:WebId`),
`hasUID` (a `urn:uuid:` is assigned on create), and `hasPhoto`. Untyped legacy nodes still parse
(they read as OTHER); OTHER entries are written untyped for byte-compatibility with older writers.
Address books are registered in the private/public **type index** for cross-app discovery.

## 2. Where contacts come from (the only write paths)

1. **Sync with Google** (settings): the chosen Google account(s) on the device are **fully
   mirrored to the pod, deletions included** — Google is the source of truth for those contacts.
   The engine tracks each Google contact in a local mapping (`ContactsSyncPrefs`:
   rawContactId → pod URI + field/photo hashes, stored per WebID and account). Per sync pass:
   new Google contacts are created in the default book (after a dedupe check that *adopts*
   an existing matching pod contact instead of duplicating), changed ones are updated (typed
   phones/emails, addresses, nickname, birthday/anniversary, org/department, photo), deleted
   ones are deleted from the pod, and a pod copy that was deleted out-of-band is **re-created**
   (mirror semantics — remove the contact in Google to make it stay gone).
2. **Import from .vcf** (settings): tolerant vCard 2.1/3.0/4.0 parser (folded lines,
   QUOTED-PRINTABLE, typed TEL/EMAIL/ADR, ORG name;unit, BDAY/ANNIVERSARY, inline base64 PHOTO);
   exact duplicates (name + first phone/email) are skipped.
3. **Add a scanned Solid profile**: scanning a profile QR opens the public profile page, whose
   person-add action checks `findByWebId` first — "already in your contacts" vs "added" — and
   otherwise stores name/emails/phones/organization/role plus the WebID as a `vcard:WebId` URL.
   Your own logged-in WebIDs are refused.
4. **Delete** (contact page ⋮): allowed as the single destructive action; deleting a
   Google-tracked contact warns that it will reappear unless also removed in Google.

Everything else in the app is read-only; the phone-side mirror rows remain editable (see below).

## 3. Android account & sync topology

- One **Android account per logged-in WebID** (type `com.erfangholami.solidshare`), reconciled
  automatically from the logged-in profiles; signing out removes the account and its mirrored
  contacts. Pod contacts appear in any contacts app under that account (per-account visibility).
- One SyncAdapter run = three phases, in order:
  1. **Google pass** (device Google rows → pod, mapping-driven, described above).
  2. **Upload pass** (SolidShare-account rows → pod): the mirror stays **two-way** — edits or
     deletions made to the SolidShare-account rows in other apps push back to the pod
     (`SOURCE_ID` = contact URI, `SYNC1` book URI, `SYNC2` fields hash, `SYNC3` photo key).
  3. **Download pass** (pod → SolidShare-account rows): typed data rows (phone/email types,
     structured postal, nickname, anniversary, department), groups (`Groups.SOURCE_ID` = group
     URI) and memberships, photos re-fetched only when changed; vanished pod contacts/groups
     are removed locally.
- Triggers: automatic upload syncs on local edits, 4-hour periodic sync, expedited sync on
  contacts-page open / settings "Sync now" / after every in-app write. Google-row edits don't
  wake our adapter directly — the periodic and on-open syncs pick them up.
- Conflict note: with both Google mirroring and the two-way mirror active, last-write-wins per
  source; an edit made on a SolidShare mirror row of a Google-tracked contact holds only until
  that contact next changes in Google.

## 4. Sharing from contacts

The create-share sheet's receiver field has a contacts button: a picker lists pod contacts that
carry a WebID (name + WebID), and choosing one fills the receiver — connecting the address book
to the WAC/ACP grant flow.

## 5. Export

Settings → "Export to .vcf" writes every pod contact as vCard 3.0 (typed TEL/EMAIL/ADR, NICKNAME,
ANNIVERSARY, ORG name;unit, embedded photos) via the system file picker.
