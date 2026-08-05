# Notifications

*Part of the [Solid Share documentation set](README.md).*

When somebody shares something with you, changes what you can do with it, or takes it back, that
arrives as a document in your pod's inbox — not as a push message from a server we run. Solid
Share reads that inbox and shows it behind a bell. There is no notifications tab and no per-item
system notification for shares: one badge, one hub, and the app syncs itself from what it finds.

The wire format, the inbox discovery and the anti-impersonation checks belong to the library and
are documented at
[androidsolidservices.erfangholami.com/build/notifications](https://androidsolidservices.erfangholami.com/build/notifications/).
This page is the app's half.

## 1. Pod shape

The app owns no notification storage of its own. Everything is the LDN inbox that the WebID
profile advertises, holding Activity Streams 2.0 activities: `as:Offer` when a share is made,
`as:Update` when its access level changes, `as:Undo` when it is revoked, plus request and decision
activities. `ensureInbox(webId)` creates and advertises one if the profile has none.

Two app-side pieces of state are local and per-account: the "last seen" timestamp that drives the
unread badge, and the system-notification channel registrations.

## 2. Surface

### Repository

`data/repo/notifications/NotificationsRepository.kt`:

| Verb | What it does |
|---|---|
| `listFeed(webId)` | The hub's list: every activity mapped to a `NotificationItem` |
| `listNotifications` / `listRequests` | The typed subsets behind the tabs |
| `deleteNotification(webId, uri)` | Removes one activity from the inbox |
| `compactInbox(webId, olderThanIso)` | Bulk cleanup; returns how many went |
| `ensureInbox(webId)` | Creates and advertises an inbox when the profile lacks one |
| `sendOffer` / `sendUndo` / `sendRequest` / `sendReject` | The outgoing side |

`NotificationsBadgeStore` is a `@Singleton` holding the unread count as a `StateFlow`, computed by
combining the fetched items with the stored last-seen timestamp. It ensures each account's inbox
exactly once (`ensuredInboxes`) so opening the bell does not re-probe on every visit.

### Screens

`presentation/notifications/`: `NotificationsPage` with **All / Unread / Requests** tabs and
per-item delete, `NotificationsViewModel`, `NotificationsBadgeViewModel`, and the two bell
components (`NotificationBell`, `TopBarNotificationBell`) used across top bars.

## 3. How it flows

### Something arrives

1. `NotificationPollingWorker` runs every 15 minutes (unique periodic work, `KEEP` policy,
   enqueued from `SolidShareApplication.onCreate`).
2. It asks the library to sync received shares from the inbox. The library collapses the inbox to
   the newest event per `(owner, resource)` pair — grant wins on a tie — so a resource shared,
   updated and re-shared produces one row, not three.
3. The badge store refreshes and the bell's count changes.
4. Opening the hub lists the feed; opening it also updates the last-seen timestamp, which is what
   clears the badge.
5. The Share tab reloads on `ON_RESUME`, so a share that auto-synced from the inbox is present
   when you switch to it.

### A typed share

An `as:Offer` for a data-module entity carries an RDF description of its object: the entity's
`rdf:type` and `schema:name`. The app reads that into `NotificationItem.resourceType` and renders
a typed chip — "Ticket", "Contact" — beside the row, and routes the tap through
`SharedEntityRegistry.forType(resourceType)`. Nothing in the notifications code names a module;
an unknown type simply renders without a chip and opens generically.

### Sending

Outgoing activities are written by the sharing flows rather than by the notifications screens:
creating a share sends an Offer, changing access sends a **separate** `as:Update` (never a
re-Offer), and revoking sends an `as:Undo`. Requests and rejections come from the confirm-access
flow when you ask an owner for access to something you cannot read.

## 4. Offline and failure behaviour

- **The hub is online-only.** The inbox is not cached locally, so with no connection the page
  shows the offline state rather than a stale list. This is a deliberate asymmetry with the data
  modules: an inbox is a queue of things that just happened, and showing a stale one is worse than
  showing none.
- **Polling is network-constrained**, so the worker does not wake to fail.
- **A missing inbox is created**, not reported as an error.
- **An append-only inbox** — one you may POST to but not read or delete from — is normal for a
  stranger's pod. Sending still works; only your own inbox is listed.
- **An unresolvable actor** leaves the row rendered with the raw WebID rather than hiding it. A
  notification you cannot attribute is still information.
- **Deleting** is per-item from the row's action, plus `compactInbox` for bulk cleanup. Deletion
  is on the pod, not local dismissal, because the inbox is the state.

### Posting system notifications

`POST_NOTIFICATIONS` is requested once after login rather than at first launch, through the
`rememberPermissionGate` seam. Workers that want to post check `NotificationHelper.canPost` first,
so a denied permission degrades to silence rather than to a crash.

## 5. Extension points

- **Typed rows** come from `SharedEntityRegistry`; a new data module gets its chip and its
  destination by registering, with no edit here.
- **`NotificationItem`** is the app's own model, mapped from the library's `ShareNotification`. A
  new activity type is added by extending that mapping, and unknown types already fall through to
  a generic row rather than being dropped.
- **The badge** is a single `StateFlow` on `NotificationsBadgeStore`; any surface that wants a
  count observes it rather than counting for itself.

## 6. Tests

The behaviours worth pinning are on the library side, because that is where the collapsing rule
and the actor gate live: that the inbox collapses to the newest event per owner-and-resource pair,
that a grant wins a tie, and that a foreign actor's profile is fetched publicly rather than with
credentials. On the app side the mapping to `NotificationItem` — particularly that an unknown
`resourceType` degrades to a generic row instead of throwing — is the piece a refactor is most
likely to break.

## 7. Specifications

- [Solid Notifications Protocol](https://solidproject.org/TR/notifications-protocol) — the
  framework.
- [Linked Data Notifications](https://www.w3.org/TR/ldn/) — the inbox, its discovery and its POST
  semantics.
- [Activity Streams 2.0](https://www.w3.org/TR/activitystreams-core/) and its
  [vocabulary](https://www.w3.org/TR/activitystreams-vocabulary/) — `as:Offer`, `as:Update`,
  `as:Undo`.
- The subscription channel specs (WebSocket, EventSource, Streaming HTTP, Webhook) are **not**
  used by the app. Polling every 15 minutes was chosen instead: it works on every pod server
  regardless of which channels it implements, and it survives the process being killed, which a
  held socket does not. This is a decision, not an omission.
