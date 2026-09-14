# multiplatform-clojure

Template monorepo: one Clojure codebase targeting API, web, desktop, Android and iOS.

## Golden rule

Business logic goes in `packages/`, never in an app. Before writing code in
`apps/*/`, ask whether it belongs in one of these:

- `packages/shared` — `.cljc`, usable from the JVM API *and* every ClojureScript target.
  Schemas (malli), validation, the route table, formatting, result helpers.
- `packages/api-sdk` — `.cljc`, the only API client. JVM (`java.net.http`) and JS
  (`fetch`). Typed functions per endpoint, built on `sdk/call` and the shared route table.
- `packages/app` — `.cljs`, the client application shared by web, desktop and mobile:
  db, events, effects, subscriptions, screen view models, user-facing copy, startup.

Web and mobile differ only in the components they render with. An app may contain:
components and styles (`views.cljs`), its storage backend, and mounting its root
(`core.cljs` calling `app.app.core/start!`). Nothing else. In particular a view must not:

- hold state (no `r/atom` form state — dispatch `::events/set-auth-field` and friends),
- contain user-facing text (it comes from the screen subscription, defined in
  `app.app.copy`),
- decide behaviour (labels, enabled/disabled, which screen, what to submit).

Each screen has one view-model subscription (`::subs/auth-screen`,
`::subs/todos-screen`, …) returning everything the view renders; `::subs/screen` picks the
screen. Events take ids, not entity maps, and look the entity up in the db. Controlled
text inputs dispatch with `rf/dispatch-sync`; on React Native also call `r/flush` right
after (`change!` in the mobile views), otherwise fast typing drops characters.

The desktop app has no ClojureScript of its own — Tauri renders `apps/web/public`.

## Conventions

- Two-space indentation. No comments in code.
- Namespaced keywords for domain data: `:user/email`, `:todo/done`.
- The API returns those namespaced keywords as-is (`"todo/id"` over the wire); the
  clients read them back as namespaced keywords. Do not rename them at the boundary.
- New endpoints are declared once in `app.shared.routes/endpoints` with `:method`,
  `:path`, `:auth?` and optional `:params` / `:body` malli schemas, then implemented in
  `app.api.router`. Nothing calls the API except through `app.api-sdk.core`.
- Validation happens twice, with the same schema: in the SDK before the request is sent,
  and authoritatively in `app.api.handlers` before touching Mongo.
- SDK calls resolve, never reject, to an `app.shared.result`. Branch on `result/kind`;
  show failures with `sdk/error-message`. In re-frame use the `:api/call` effect from
  `app.app.api` with `:on-success` / `:on-failure` event vectors.
- In ClojureScript never hand domain maps to JS with a bare `clj->js`: it drops keyword
  namespaces (`:todo/id` becomes `"id"`). Use `app.api-sdk.json` or pass ids across.
- `app.shared.result` carries domain failures (`:not-found`, `:conflict`, …);
  `result/status` maps them to HTTP codes. Handlers should not invent status codes.
- Integrant owns lifecycle. A new stateful component gets an `ig/init-key`, an
  `ig/halt-key!`, and an entry in `apps/api/resources/config.edn`.

## Adding a feature end to end

1. Schema in `packages/shared/src/app/shared/schema.cljc`.
2. Endpoint in `packages/shared/src/app/shared/routes.cljc`.
3. Typed function in `packages/api-sdk/src/app/api_sdk/core.cljc`.
4. Domain namespace in `apps/api/src/app/api/` returning `result/ok` or `result/err`.
5. Handler + route in `app.api.handlers` / `app.api.router`.
6. Events, a view-model subscription and copy in `packages/app`, with a flow test in
   `packages/app/test/` (headless, against the fake API in `app.app.test-support`).
7. Components in each app that render the view model.
8. Tests: API behaviour in `apps/api/test/`, the new call in
   `app.api.sdk-contract-test` (real server, through the SDK), and SDK edge cases in
   `packages/api-sdk/test/` (`.cljc`, runs on JVM and Node).

## Running things

Everything is dockerized; use `make`, not raw `clojure`/`npx` calls.

```
make install     boot mongo, mongo-express, api, web, mobile; seed a demo account
make logs        tail everything
make api-test    kaocha against a throwaway database
make sdk-test    api-sdk tests on the jvm and on node
make app-test    shared app logic flow tests on node
make mobile-open-android   run the mobile app in expo go (emulator must be running)
make lint fmt    clj-kondo and cljfmt
make outdated    dependency report; make upgrade applies safe upgrades
```

REPL-driven work: `make api-repl` (7888), `make web-repl` (9630),
`make mobile-repl` (9632). The API dev container starts the system *and* an nREPL
server via `apps/api/dev/dev_main.clj`; `apps/api/dev/user.clj` gives you
`(go)`, `(reset)`, `(halt)` and `(conn)`.

Requires in `dev_main.clj`, `user.clj`, `app.api.main` and `app.api.fixtures` look
unused but load Integrant `defmethod`s — clj-kondo is configured to allow this in
`.clj-kondo/config.edn`. Do not delete them.

## Dependencies

Keep everything on current stable versions. Look versions up live (`make outdated`,
`npm view`, crates.io, Docker Hub), never from memory, and verify with the full suite
after upgrading. Known constraints:

- MongoDB is pinned to `mongo:8.2`: 8.0 and 8.3 exit on Linux kernel 6.19+
  (SERVER-121912). Re-check when a newer 8.x ships before moving the pin.
  Upgrading an existing dev volume across majors needs dump and restore.
- `apps/mobile`: upgrade `expo` only, then `npx expo install --fix`. Do not add a
  `babel.config.js` unless you also add `babel-preset-expo` as a devDependency; SDK 57
  does not hoist it.
- `apps/desktop/src-tauri`: `rust-version` must be at least the highest `rust_version`
  in `Cargo.lock`. With edition 2024 the resolver is MSRV-aware, so a lower value makes
  `cargo update` silently downgrade crates.

## Native builds

Docker runs the ClojureScript compiler (`mobile`) and the Expo dev server (`metro`); both
reach the device through `MOBILE_DEV_HOST` (default `localhost` plus `adb reverse`). The
containers run `npm install` on start because named `node_modules` volumes never refresh
from the image. The native link step needs host SDKs:
`make mobile-ios` (Xcode), `make mobile-android` (Android SDK), `make desktop-bundle`
(Rust). CI builds the three desktop targets on separate runners because Tauri cannot
cross-compile them.

## Renaming

`make rename NAME=acme` rewrites `app.*` namespaces, moves source directories and
updates bundle identifiers. Run it once, early.
