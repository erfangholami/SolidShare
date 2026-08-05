# Authentication, sessions and accounts

*Part of the [Solid Share documentation set](README.md).*

Signing in to Solid Share means signing in to a pod: you pick a provider, the provider
authenticates you in a browser tab, and the app comes back holding tokens bound to your WebID. The
app can hold several such identities at once and switches between them wholesale — every screen,
cache and queue is scoped to the active WebID. This page covers the app's half; the token
lifecycle, DPoP and the refresh machinery live in the library and are documented at
[androidsolidservices.erfangholami.com/build/auth](https://androidsolidservices.erfangholami.com/build/auth/).

## 1. Pod shape

Authentication reads the WebID profile document and writes nothing. What it needs from the profile
is the OIDC issuer (`solid:oidcIssuer`), the storage roots (`pim:storage`) and the display fields
the app shows in the account switcher. Everything else about a session — tokens, DPoP keys, the
account list — lives on the device in the library's encrypted profile store, never on the pod.

The one hosted artefact the app owns is its Client ID Document, served from the website repo at
`https://solidshare.app/clientid.jsonld`. Its `redirect_uris` must list the app's redirect
**verbatim** or providers refuse the login.

## 2. Surface

### `AuthRepository`

`AuthRepository` is a standalone interface; it does **not** extend the library's `Authenticator`.
`AuthRepositoryImplementation` injects the `Authenticator` singleton plus `AuthLocalDataStore` and
re-exposes only what the app needs. Inject `AuthRepository` into ViewModels and the Activity —
never `Authenticator`. That rule is what kept the app compiling through the library's auth
rework: the adaptation happens in one file.

| Member | What it gives you |
|---|---|
| `activeWebIdFlow`, `activeProfileFlow` | The current identity and its profile |
| `loggedInProfilesFlow`, `expiredProfilesFlow` | The account switcher's two lists |
| `isAuthorizedFlow`, `isUserAuthorized()` | Whether anyone is signed in |
| `getActiveWebId()` | **Suspending.** Awaits authenticator initialisation, then answers |
| `createAuthenticationIntent(…)` / `createAuthenticationIntentWithOidcIssuer(…)` | Builds the browser intent |
| `submitAuthorizationResponse(intent)` | Completes the exchange, returns the WebID |
| `setActiveWebId`, `removeProfile`, `removeAllProfiles` | Account switching and sign-out |
| `getStorages`, `ownsResource`, `oidcIssuerHost` | Profile-derived facts used across the app |
| `hasRefreshableSession(webId)` | Whether this session can survive a token expiry |

### Screens

`presentation/onboard/` holds the first-run introduction and `presentation/login/` the provider
list and the login screen. `presentation/startup/` decides where a launch lands.
`presentation/main/ProfileViewModel` owns account switching and sign-out, and
`presentation/components/AccountSwitcher.kt` holds the reusable `AccountRow` / `AddAccountRow`.

## 3. How it flows

### Startup routing

`AuthenticatorImplementation` initialises asynchronously, and the navigation must not race it.
`StartupViewModel`:

1. calls the suspending `authRepository.getActiveWebId()`, which internally awaits initialisation;
2. then reads `isUserAuthorized()`, which by that point is synchronous and accurate;
3. emits `null` until that coroutine finishes, and `Startup.kt` does `?: return@LaunchedEffect` to
   hold navigation while it is `null`.

Do **not** call `isUserAuthorized()` synchronously as a `StateFlow`'s initial value. It returns
`false` before initialisation completes, and a signed-in user lands on the login screen.

### Login

1. `LoginViewModel.login()` calls
   `createAuthenticationIntentWithOidcIssuer(clientName, issuerUrl, REDIRECT_URI)`, which suspends
   and returns `Pair<Intent?, String?>`.
2. The composable launches the intent through `rememberLauncherForActivityResult`.
3. The result goes to `handleAuthResult(intent?)`, which calls `submitAuthorizationResponse(...)`.
4. On `LoginState.Success` navigation tries `popBackStack(MainNavItem, inclusive = false)` first
   and falls back to `navigate(MainNavItem) { popUpTo(graph.id, inclusive = true) }`. The two
   branches are what make first login and add-account both land correctly.

The redirect URI is `https://solidshare.app/oauth2redirect`, an HTTPS App Link. It is HTTPS
because some providers reject a non-`https` redirect in a Client ID Document. Because the library
launches auth through AppAuth's `getAuthorizationRequestIntent`, the redirect must land on
AppAuth's `RedirectUriReceiverActivity` and not on `MainActivity` — so `AndroidManifest.xml`
replaces that activity (`tools:node="replace"`) with an `autoVerify` intent filter for
`solidshare.app/oauth2redirect`, verified by the existing `.well-known/assetlinks.json`. The
`appAuthRedirectScheme` placeholder remains defined but unused, since the custom-scheme filter is
the one being replaced.

### Switching accounts

`setActiveWebId(webId)` is the whole switch. Every module scopes its cache, its queue, its device
sync sources and its settings by WebID, so the surface changes wholesale with no per-feature
reset. Sharing deep links use this too: `ChooseReceiverDialog` picks the account, switches to it,
then continues into the confirm-access flow, which is why a link received on the wrong account is
a two-tap fix rather than a sign-out.

### Signing out

`ProfileViewModel.logout()` removes the profile, clears the file cache and file outbox, and calls
`dataModules.clearCache(webId)` — the registry fan-out, so no module is named. The contacts
SyncAdapter's Android account and its device mirror are removed alongside.

## 4. Offline and failure behaviour

### Expiry is a state, not a crash

A session whose tokens can no longer be refreshed moves from `loggedInProfilesFlow` to
`expiredProfilesFlow`. The app keeps the account visible and marked, keeps its Android account and
contacts mirror in place, and asks for a re-login when you use it. It does not silently drop the
identity, because dropping it would also drop the device mirror and any queued writes belonging to
it.

`SolidShareApplication.watchSessionExpiry` observes both lists and reports each *newly* expired
WebID once. The first emission is deliberately skipped — `previous == null` returns early — so a
cold start with an already-expired account does not report an expiry that happened days ago.

### Sessions with no refresh token

Some providers only issue a refresh token when the user ticks a "remember me" box. Without one the
session dies at the first access-token expiry and no refresh can save it. `hasRefreshableSession`
exposes this, and `AuthAnalytics.loginNoRefreshToken` records it at login, which is what makes an
otherwise baffling "logged out after five minutes" report diagnosable.

### A 401 is not automatically an expiry

This is the failure mode that cost the most sessions, and it is fixed in the library rather than
here, but the app's behaviour depends on it. A bare 401 from a **foreign** pod — reading someone
else's shared resource you are not authorized for — is an authorization outcome, not a token
problem. Treating it as an expiry triggered a refresh, and a burst of them triggered rate limiting
and, in the worst case, revocation of the whole token family. The library now classifies the
`WWW-Authenticate` challenge (`AuthChallenge`) and only refreshes when the challenge names a token
problem, or when a bare 401 comes from the identity's own origin.

### Telemetry

`telemetry/AuthAnalytics.kt` reports auth events to Firebase Analytics and Crashlytics:
`solid_login_success` (with whether a refresh token came back), `solid_login_no_refresh`,
`solid_login_failed`, `solid_session_expired` (issuer plus a truncated error code),
`solid_kicked_to_login` (with the trigger) and `solid_startup_route`. Issuers are recorded as a
**host**, never a full WebID, and error codes are truncated to the part before the first colon —
enough to group failures, not enough to identify a person.

## 5. Extension points

- **Provider list** — `getListOfPodServers()` returns the built-in providers; the login screen
  also accepts a typed issuer URL, so a provider that is not listed still works.
- **The library seam** — everything the app knows about sessions arrives through `AuthRepository`.
  A library-side change to session modelling should require edits in
  `AuthRepositoryImplementation` and nowhere else. If it does not, that is the defect worth
  fixing.
- **Telemetry** — `AuthAnalytics` is a plain injectable class; the library reports into it via its
  `Telemetry` sink, so neither side has a hard dependency on Firebase.

## 6. Tests

Auth is the area where tests earn their keep, and most of them live in the library rather than
here, since that is where the state machine is. On the app side the useful pins are the startup
ordering (that routing waits for `getActiveWebId()`) and the account-scoping invariants that the
module repository tests cover implicitly by keying everything on WebID.

The library's `AuthChallengeTest` is the one to read before touching refresh behaviour: it encodes
the decisions — a nonce challenge is not an expiry, a named token problem refreshes anywhere, an
`insufficient_scope` never refreshes, and a bare 401 refreshes only on the identity's own origin.

Firebase needs `FirebaseApp.initializeApp` under Robolectric, or analytics-touching tests fail on
a missing default app.

## 7. Specifications

- [Solid-OIDC](https://solidproject.org/TR/oidc) and the
  [Solid-OIDC Primer](https://solidproject.org/TR/oidc-primer) — the authentication mechanism.
- [RFC 9449 (DPoP)](https://datatracker.ietf.org/doc/html/rfc9449) — proof-of-possession, applied
  to every authenticated request by the library.
- [Solid WebID Profile](https://solid.github.io/webid-profile/) — the profile fields read at login.
- [Solid Security Considerations](https://solid.github.io/security-considerations/) — the threat
  model behind the anti-impersonation checks and the refusal to trust an actor's self-description.
- [HTTPSig for Solid](https://solid.github.io/httpsig/) — not implemented. Named so a reader can
  tell a decision from an omission: Solid-OIDC with DPoP is the only mechanism supported.
