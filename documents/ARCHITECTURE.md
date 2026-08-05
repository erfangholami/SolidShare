# Architecture

*Part of the [Solid Share documentation set](README.md).*

Solid Share is a single-module Android app in Clean Architecture with MVVM, talking to pods
exclusively through the AndroidSolidServices library. This page describes the layers, the rules
that hold them apart, and — more usefully — which of those rules are enforced by a failing build
rather than by review.

## 1. Pod shape

The app owns nothing on a pod directly. Every container, document and grant belongs to a library
feature: the data modules own `{storage}datamodule/…`, sharing owns its bookkeeping container, and
notifications own the LDN inbox. The app holds identifiers it was handed and passes them back.

That is a deliberate boundary, and it is why the library relocating every module's root under
`datamodule/` required no app change: nothing in `app/` composes a pod path from a literal.

The one shape the app does own is on the device: an SQLCipher-encrypted Room database,
`solid_cache.db`, holding the resource cache, the blob cache, the file outbox, and two module-agnostic
tables — `cached_entity` and `module_outbox_op` — shared by every data module.

## 2. Surface

### Layers

```
presentation/  →  domain/model/  →  data/repo/  →  data/local/
(Composables        (plain data        (interfaces      (DataStore /
 + ViewModels)       classes)           + impls)         Room / Authenticator)
```

Dependencies point right. A ViewModel injects a repository *interface*; a repository injects
DataStore, DAOs, and the library's managers. `domain/model` holds plain serializable data classes
with no Android or library types in them, which is what lets the same model travel from a pod row
to a Room `detailJson` column to a Compose screen without a second mapping layer.

Top-level packages beyond those four: `di/` (Hilt modules), `worker/` (WorkManager jobs),
`notification/` (system tray), `sync/` (Android account + contacts SyncAdapter), `util/`, and
`data/device/` + `data/passimport/` for device contacts and pkpass/Google Wallet parsing.

### Dependency injection

Eight Hilt modules in `di/`, split the standard way: `@Binds` inside `interface` modules for
interface-to-implementation bindings, `@Provides` inside `object` modules only for types built by
a factory we do not own.

| Module | Kind | What it binds |
|---|---|---|
| `RepositoryModule` | interface | Each repository interface to its `@Inject`-constructed implementation |
| `DataSourceModule` | interface | The two local data stores |
| `DataModuleModule` | interface | `OutboxTrigger`, and every module's `DataModuleLifecycle` into a set |
| `EntityShareModule` | interface | Every module's `SharedEntityUi`, `ReceiverPickerContributor`, `NavGraphContributor` and `ScanContributor` into sets |
| `SolidApiModule` | object | The library singletons built via `getInstance(...)` factories |
| `ApplicationModule` | object | `WorkManager` |
| `LocalModule` | object | The two `DataStore` singletons |
| `CacheModule` | object | The Room database, its migrations, and each DAO |

The multibinding modules are where the openness lives: `di/` is allowed to know every module by
name, because knowing them is its job. Nothing else generic is.

### The library boundary

`AuthRepository` is a standalone interface — it does **not** extend the library's `Authenticator`.
`AuthRepositoryImplementation` injects the `Authenticator` singleton and re-exposes only the
app's auth surface. Inject `AuthRepository` into ViewModels and the Activity; never
`Authenticator`. This keeps a library type out of the presentation layer and gives the app one
place to adapt when the library's auth surface moves — which, during the auth hardening work, it
repeatedly did.

Since library version 0.6.0 the API is all `String` IRIs. The app passes plain identifier strings
straight through and never wraps them: no `encodeUriString`, no `URI.create` at a library call
site. The library encodes at its own boundary, idempotently. `java.net.URI` appears in `app/` only
for the app's own parsing, such as reading a WebID's host.

## 3. How it flows

### Startup

`AuthenticatorImplementation` initialises asynchronously, which the navigation must not race.
`StartupViewModel` calls the suspending `authRepository.getActiveWebId()` — which internally
awaits that initialisation — and only then reads `isUserAuthorized()`, which is synchronous and by
that point accurate. It emits `null` until the coroutine completes, and `Startup.kt` holds
navigation while `null`. Calling `isUserAuthorized()` synchronously as an initial `StateFlow`
value returns `false` before init completes and sends a logged-in user to the login screen.

### Navigation

Typed serializable routes in `presentation/navigation/NavigationGraph.kt`:

```
StartUpNavItem.Launch → logged in → MainNavItem
                      → onboarded → AuthNavItem.Login
                      → otherwise → OnBoarding

MainNavItem (Scaffold + NavigationBar)
  └─ nested NavHost: Home | Files | [+ scan] | Share | Profile
```

`MainPage` takes the app-level `parentNavController` and creates its own `nestedNavController` for
the bottom tabs. Tab composables that navigate outside Main are given the parent controller.

Home is the data-module hub: a greeting plus one card per module, and those cards come from
`SharedEntityRegistry.homeCards()` rather than being listed in `Home.kt`.

### A read, end to end

1. The ViewModel collects a `Flow` from a repository.
2. The repository returns a Room `Flow` immediately — the screen paints from cache.
3. In parallel it fetches from the pod through a library manager.
4. On success it reconciles: upsert the fetched rows, then prune only `SYNCED` rows that were
   absent, and only after a fetch that raised nothing. A partial failure never deletes.
5. Room emits again and the screen updates.

## 4. Offline and failure behaviour

Described fully in [OFFLINE.md](OFFLINE.md). The architectural points:

- **The durable store is the source of truth.** Flows derive from Room; nothing important is
  reconstructed from an in-memory snapshot that can lag.
- **Every online-only surface carries one affordance.** `RequiresConnection` /
  `RequiresConnectionHint` is the single component that says so, so "what does this app do
  offline" has one answer per screen rather than a per-button guess.
- **Errors surface as messages, not exceptions.** Library results are unwrapped at the repository
  boundary, which throws `error.asException()`; one layer — `domain/error/` — turns that into a
  sentence, and no screen ever reads a throwable's own text. See [ERRORS.md](ERRORS.md).

## 5. Extension points

The rule the design is measured against: **adding a data module means writing new files and
registering them, not editing generic ones.** The seams that make that true are listed in
[DATA_MODULES.md](DATA_MODULES.md). The registries — `SharedEntityRegistry`,
`DataModuleRegistry`, `ReceiverPickerRegistry`, `NavGraphRegistry`, `ScanRouter` — are the
pattern; each is a `@Singleton` holding a Hilt `@IntoSet` set, and each replaced a hand-maintained
list or a `when` over module types.

Reusable presentation components are single-sourced and should be reused rather than re-inlined:
`ContainerView` (previewable container screen), `SharedWithSummary` / `SharedAccessGroups`,
`ResourceTypeIcon`, and `presentation/components/` (`RowDivider`, `SheetActionRow`,
`ProfileAvatar`, `ProfileHeader`, `AccountSwitcher`, `NotificationBell`, the state/banner/badge
family).

All user-facing text lives in `strings.xml`. Non-Compose code reads strings through the injected
`StringProvider`, never a raw `Context`.

## 6. Tests

`app/src/test/java/…/architecture/ArchitectureTest.kt` is a plain JVM test that walks the source
tree and asserts six rules with **shrink-only baselines** — the assertion is equality against a
known set, so fixing a violation forces you to remove it from the baseline, and introducing one
fails the build:

| Rule | Baseline |
|---|---|
| No file under `data/` imports `worker.*` | empty |
| No generic file under `presentation/` imports a module package | empty |
| No generic `presentation/` file imports a module's data layer | `presentation/sharing/PublicProfileViewModel.kt` |
| No `something.message ?: fallback` anywhere in `src/main` | empty |
| No file under `presentation/`, `worker/` or `sync/` imports `SolidError`, `SolidResultException` or `SharingException` | empty |
| No comments in production code except KDoc | empty |

The one remaining baseline entry is honest rather than tolerated: the QR-profile
add-to-contacts flow still talks to `ContactsRepository` directly, pending an add-to-module seam.

Test strategy, tooling and the Robolectric/Room/mockk gotchas are in [TESTING.md](TESTING.md).

## 7. Specifications

The app implements no wire specification itself. What it depends on is the library's conformance
to [Solid Protocol](https://solidproject.org/TR/protocol),
[Solid-OIDC](https://solidproject.org/TR/oidc), [WAC](https://solidproject.org/TR/wac),
[ACP](https://solidproject.org/TR/acp), the
[Notifications Protocol](https://solidproject.org/TR/notifications-protocol) and
[Activity Streams 2.0](https://www.w3.org/TR/activitystreams-core/).

The one wire-facing thing the app owns is its OAuth redirect: `https://solidshare.app/oauth2redirect`,
an HTTPS App Link, because some OIDC providers reject a non-`https` redirect in a Client ID
Document. It must be listed verbatim in the hosted Client ID Document's `redirect_uris` or login
is refused, and it must land on AppAuth's `RedirectUriReceiverActivity` rather than
`MainActivity` — which is why `AndroidManifest.xml` replaces that activity with an `autoVerify`
intent filter.
