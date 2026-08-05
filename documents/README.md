# Solid Share — documentation

Design documentation for the app, written for whoever has to change it next. Each page explains how
one feature actually works: its shape on the pod, its screens, what it does when the network or the
server misbehaves, and the seams a future change is expected to use. They are not tutorials and not
API dumps.

The library that talks to the pod documents itself separately, at
[androidsolidservices.erfangholami.com](https://androidsolidservices.erfangholami.com). Where a page
below stops at "the library does this", that site is where it continues.

**Read them in this order.** The first four are the layers every feature sits on; skipping them
makes the feature pages read as arbitrary. After that, any feature page stands alone.

## Start here — the layers everything else assumes

| # | Page | Read it for |
|---|---|---|
| 1 | [ARCHITECTURE.md](ARCHITECTURE.md) | The shape of the app: the four layers and what may depend on what, the eight Hilt modules, why `AuthRepository` wraps the library instead of extending it, and which of these rules a failing build enforces rather than a reviewer |
| 2 | [AUTH.md](AUTH.md) | Signing in to a pod, holding several identities at once, and why *everything* — cache, queue, sync, settings — keys on the active WebID. Also expiry as a state rather than a crash, and what a 401 does and does not mean |
| 3 | [OFFLINE.md](OFFLINE.md) | The encrypted database and the two queues every read and write goes through, what works with no connection and what refuses, the retry policy, and the one gap (conflicts) stated as a decision |
| 4 | [ERRORS.md](ERRORS.md) | How a throwable becomes a sentence a person can act on: one classifier, a cause × operation split, and the two build rules that stop any screen inventing its own wording |

## The features

| # | Page | Read it for |
|---|---|---|
| 5 | [FILES.md](FILES.md) | The pod file browser: cache-first listing, the file queue, the actions sheet, and the two decisions behind it — nothing is renamed in place, and container sizes are not recursive |
| 6 | [share.md](share.md) | Sharing end to end: View/Add/Edit as WAC and ACP grants, the on-pod given/received indexes, links and QR codes, the inbox delivery, and how the library's engine is layered beneath it. Start here for anything access-control shaped |
| 7 | [ENTITY_SHARING.md](ENTITY_SHARING.md) | Sharing *a ticket* or *a contact* rather than a file: the two-interface contract that makes a module shareable, how type travels through index, notification and link, and what a receiver's copy records |
| 8 | [NOTIFICATIONS.md](NOTIFICATIONS.md) | The bell hub: reading your pod's LDN inbox, why polling beat a live socket, typed rows, and why deleting is a pod write rather than a local dismissal |
| 9 | [CONTACTS.md](CONTACTS.md) | The address book on your pod, the two-way mirror into the phone's Contacts app, `.vcf` import/export, and duplicate review — which suggests and never merges by itself |
| 10 | [TICKETS.md](TICKETS.md) | The wallet: passes as pod resources, the open ticket-QR format any issuer can adopt, `.pkpass` import with the original kept, issuer refresh, and the five pass layouts |

## Framework and reference

| # | Page | Read it for |
|---|---|---|
| 11 | [DATA_MODULES.md](DATA_MODULES.md) | What adding data module number three actually costs. The generic cache and queue tables, the registries, and the rule the design is measured against: new files plus bindings, never edits to generic ones |
| 12 | [TICKET_VOCAB.md](TICKET_VOCAB.md) | The normative term dictionary for a ticket on a pod — every field a wallet source can yield, and the justification for each term minted under `solidshare:` instead of reused from schema.org |
| 13 | [Sharing in Solid Share.md](<Sharing in Solid Share.md>) | The original R&D standard by PourMohamad and Gholami. Historical: kept because §6 of [share.md](share.md) measures the implementation against it, criterion by criterion |

## Process

| # | Page | Read it for |
|---|---|---|
| 14 | [TESTING.md](TESTING.md) | What the suite pins and how to run it — the architecture rules with shrink-only baselines, the stateful fake pod, and the Robolectric/Room/jacoco gotchas that waste an afternoon each |
| 15 | [PUBLISHING.md](PUBLISHING.md) | Shipping to Google Play and F-Droid: the two distributions, the version-from-source rule, and the store checklists. Working document — tick as things land |
| 16 | [MODULARIZATION_PLAN.md](MODULARIZATION_PLAN.md) | The record of the plan that produced the current structure across both repos, phase by phase, with what actually landed. History, not instructions |
| 17 | [FEATURE_DOC_TEMPLATE.md](FEATURE_DOC_TEMPLATE.md) | The seven sections every feature page above follows. Start a new page by copying it |

## Conventions

- One feature per file; the file is part of the feature, not an afterthought.
- Describe what the code **does**, not what it should do one day — plans belong in a plan.
- Show real URIs and real Turtle from the running system, not invented examples.
- When behaviour is deliberately absent, say so and say why. A reader must be able to tell a
  decision from an omission.
- Cite the Solid or W3C specification wherever the code implements one, and name the places the
  implementation knowingly deviates.
- Every feature page opens with the line that links back here, so any page is one hop from the set.
