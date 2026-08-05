# Errors

*Part of the [Solid Share documentation set](README.md).*

> When something goes wrong, the person using Solid Share sees a sentence about *their* situation —
> "Couldn't create the folder. solidcommunity.net is out of space." — never a status code, a stack
> frame, or a library diagnostic. One layer does that translation for the whole app: a throwable is
> classified once into an `AppError`, and rendered once by combining that cause with the
> `AppOperation` the user was attempting.

## 1. Pod shape

This feature owns nothing on the pod. It reads the failures that pod interactions produce and turns
them into words. The vocabulary it interprets is the Solid Protocol's own: HTTP status semantics,
WAC/ACP authorization outcomes, and the `ldp:inbox` discovery failures of the Notifications
Protocol.

## 2. Surface

**App** — everything lives in `app/src/main/java/com/erfangholami/solidshare/domain/error/`:

| File | What it is |
|---|---|
| `AppError.kt` | The vocabulary of causes. 36 variants, each describing *why*, never *what was attempted*. Also `AppErrorException` / `asException()` for app-raised preconditions |
| `AppOperation.kt` | The vocabulary of attempts. One enum entry per user-visible action, each carrying its own headline `@StringRes` |
| `AppErrorMapper.kt` | The single throwable → `AppError` classifier, plus `hostOf()` |
| `ErrorPresenter.kt` | `(AppError, AppOperation) → UiError`. The message catalogue |
| `UiError.kt` | What a screen holds: `title`, `message`, `action`, `summary`, and the `AppError` behind them. Plus `ErrorAction` |
| `LocalErrorPresenter.kt` | Composition-local presenter, for the two sheets that own their own submit state |
| `Cancellation.kt` | `rethrowIfCancellation()` — `catch (e: Exception)` also catches coroutine cancellation |

Rendering is shared too: `presentation/components/ErrorViews.kt` has `UiErrorBanner` (inline,
dismissible, carries the action) and `UiErrorState` (full-slot, for a screen that has nothing to
show).

**Library** — the app consumes, and never re-derives, the classification the library already made:

```kotlin
public sealed class SolidError { val code: SolidErrorCode; val message: String; … }
public class SolidResultException(public val error: SolidError) : Exception(…)
```

`SolidError.message` is explicitly a developer string. `AppErrorMapper` reads the *type*, not the
message; the message survives only as `AppError.diagnostic`, which is never displayed.

## 3. How it flows

1. A repository unwraps a `SolidResult.Failure` and throws `error.asException()`. Repositories no
   longer mint app-specific exception types — there is one channel.
2. A ViewModel catches, and calls
   `errors.present(e, AppOperation.CREATE_FOLDER, subject = folderName, origin = containerUrl)`.
3. `AppErrorMapper` walks up to five links of the cause chain looking for something it recognises:
   `AppErrorException`, `SolidResultException`, `SharingException`, then platform exceptions.
   `origin` tells it which host to name and whether the work was local (`content:` / `file:`).
4. `ErrorPresenter` looks for an operation-specific override, falls back to the per-cause sentence,
   and returns a `UiError` whose `action` is the single most useful recovery.
5. The screen renders it — `UiErrorBanner`, `UiErrorState`, or `summary` in a snackbar — and wires
   the action it can actually perform.

Two refinements happen in the mapper that no lower layer can make:

- **Offline versus unreachable.** The library reports both as a network error. Only the app knows
  whether the device has a link, which decides between "You're offline" and "Couldn't reach
  solidcommunity.net — your device is online, so the pod may be down."
- **Statuses the protocol leaves to the server.** 413 and 507 reach the app as a generic unexpected
  response but mean specific things: too large, and pod out of space.

### Why cause and operation are separate

A message needs both halves — what you were doing, and why it failed. Storing them together would
mean `operations × causes` strings. Keeping them apart costs `operations + causes`, and the join
happens at render time:

```
title    ← AppOperation.CREATE_FOLDER   "Couldn't create the folder"
message  ← AppError.PodStorageFull      "Your pod on solidcommunity.net is out of space. Free some up and try again."
summary  = "Couldn't create the folder. Your pod on solidcommunity.net is out of space. Free some up and try again."
```

A dozen pairs mean something sharper together than apart, and those are explicit overrides in
`ErrorPresenter.override()` — being refused a *share you are creating* is about ownership ("Only the
owner of this item can change who has access to it"), while being refused a *share you are
receiving* is about asking ("You don't have access to this yet. Alice can share it with you", with
a **Request access** button).

## 4. Offline and failure behaviour

The layer *is* the failure behaviour. What it guarantees:

- **A throwable's own `message` never reaches a screen.** It is kept as `AppError.diagnostic` for
  logs and telemetry. An `ArchitectureTest` rule forbids the `something.message ?: fallback` idiom
  anywhere in `src/main`.
- **Cancellation is not a failure.** `rethrowIfCancellation()` runs first in every reporting catch
  block, so navigating away from a loading screen no longer flashes an error.
- **Every error offers at most one recovery**, chosen by the layer rather than by each screen:
  `Retry` for transient causes, `SignIn` for a dead session, `RequestAccess` when the pod disclosed
  an owner. A screen that cannot perform an action passes no handler and the button is not drawn;
  `allowRetry = false` suppresses a retry the caller has no way to run.
- **Access denied and access unverified stay distinct.** A definitive WAC/ACP "no" says you need
  permission; a transient one says "Your pod didn't give a clear answer about your access. Try again
  in a moment." Telling a user they lack access when the pod merely stumbled is the worst failure
  mode this layer prevents.
- **Notification delivery failures are separated from grant failures**, matching the library: a
  share's WAC write succeeding while the receiver's inbox is unreachable is not a failed share, and
  `RecipientUnreachable` says so — "…hasn't set up an inbox, so SolidShare can't notify them. Send
  them the link another way."

## 5. Extension points

**A new cause** — add a variant to `AppError`. The `when` in `ErrorPresenter.wordingFor()` is
exhaustive, so the compiler refuses to build until it has a sentence. Nothing else changes.

**A new screen or feature** — add an `AppOperation` entry with its own `titleRes`. It carries
everything the layer needs; no other file is touched. Every existing cause immediately renders
correctly under the new operation, and `ErrorPresenterTest` proves it.

**A new pairing that reads badly** — add a branch to `ErrorPresenter.override()`. This is the only
place operation and cause are allowed to know about each other.

**A new failure source** — teach `AppErrorMapper.recognize()` about the type. Repositories, workers
and the sync adapter need no changes.

## 6. Tests

`app/src/test/java/com/erfangholami/solidshare/domain/error/`:

- `AppErrorMapperTest` (16) — pins the decisions, not the plumbing: offline vs unreachable is
  re-decided against live connectivity; 401 is a dead session rather than a denial; 413/507/408 are
  refined out of the library's generic bucket; a stale ACL is distinct from a stale resource; a
  wrapped cause is still found; `ENOSPC` is called out; `content:` work is not reported as a network
  problem. Plus `hostOf` against the URL shapes pods actually produce, including IPv6 and userinfo.
- `ErrorPresenterTest` (13) — that the headline/cause split composes, that sharing refusals point at
  ownership while receiving refusals offer **Request access**, that a denial with no known owner
  never offers a request that cannot be sent, that sign-in failures do not tell the user to sign in,
  and that a throwable's own text never survives into `message` or `summary`. The last test renders
  **every cause under every operation** and asserts each is a finished sentence with no leaked
  format specifier — the safety net for both extension points above.

`ArchitectureTest` adds two rules that keep the layer the only path:

- *no screen builds its own message out of a throwable* — the `.message ?:` idiom is banned outright.
- *library error types stop at the data layer* — nothing under `presentation/`, `worker/` or `sync/`
  may import `SolidError`, `SolidResultException` or `SharingException`.

## 7. Specifications

- [Solid Protocol](https://solidproject.org/TR/protocol) — the status semantics the mapper reads;
  §Reading and Writing Resources for 409/412, §Authorization for the 401 vs 403 distinction.
- [Web Access Control](https://solidproject.org/TR/wac) and
  [Access Control Policy](https://solidproject.org/TR/acp) — the two backends behind
  `PermissionDenied` and `UnsupportedAccessControl`. Servers that expose only ACP surface as
  "…controls access in a way SolidShare doesn't support yet".
- [Solid Notifications Protocol](https://solidproject.org/TR/notifications-protocol) — `ldp:inbox`
  discovery, behind `RecipientUnreachable`, `RecipientInboxRefused` and `NotificationNotDelivered`.
- [Solid Security Considerations](https://solid.github.io/security-considerations/) — why
  `InsecureConnection` does not offer a retry button, and why `AuthenticityCheckFailed` reports a
  dropped notification rather than silently hiding it.

**Known deviation.** The Solid Protocol reserves 401 for missing or invalid credentials and 403 for
an authenticated agent who is not authorized, but pods differ in practice. The app therefore keeps
two paths: a bare 401 becomes `SessionExpired` (sign in again), while a 401 or 403 on a *resource
read* is turned into `SolidError.AccessDenied` by `FileRepositoryImplementation.accessAwareError`
before it reaches the mapper, because on a foreign pod that is what it almost always means.
