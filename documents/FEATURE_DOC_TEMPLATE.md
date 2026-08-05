# <Feature name>

*Part of the [Solid Share documentation set](README.md).*

> One paragraph, plain language: what this feature is for the person using the app, and the one
> sentence a reader should remember about how it is built.

## 1. Pod shape

The containers and RDF this feature owns, as a tree, plus a real Turtle excerpt. State which
vocabulary each term comes from and flag anything minted under `solidshare:`. If the feature owns
nothing on the pod, say that instead — it is useful information.

## 2. Surface

**App:** the screens and what each one can do.
**Library:** the API this feature calls, with the exact signatures that matter.

Name the files a reader should open first.

## 3. How it flows

The two or three paths that matter, end to end — the happy path and the ones that bite. Prefer a
numbered walk-through over prose. Say which component owns each step.

## 4. Offline and failure behaviour

What happens with no network, what queues and what refuses; how errors surface to the user; what
each distinct failure means. If something is deliberately online-only, say why.

## 5. Extension points

What a future change is expected to plug into: the seams, the registries, the descriptors. If
adding one more of something (module, provider, receiver kind) requires editing a generic file,
that is a defect worth naming here.

## 6. Tests

Where the tests live and what they pin — especially the ones that encode a decision rather than a
behaviour, since those are the ones a refactor will be tempted to delete.

## 7. Specifications

The Solid / W3C documents this implements, with links, and any place the implementation knowingly
deviates.
