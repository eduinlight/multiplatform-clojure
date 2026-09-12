# multiplatform-clojure

Template monorepo: one Clojure codebase targeting API, web, desktop, Android and iOS.

## Golden rule

Business logic goes in `packages/`, never in an app. Before writing code in
`apps/*/`, ask whether it belongs in one of these:

- `packages/shared` — `.cljc`, usable from the JVM API *and* every ClojureScript target.
  Schemas (malli), validation, the route table, formatting, result helpers.
- `packages/client` — `.cljs`, HTTP calls built from `app.shared.routes`.
- `packages/ui` — `.cljs`, the entire re-frame layer: db, events, effects, subs.

Only view rendering is allowed to differ per platform:
`apps/web/src/app/web/views.cljs` (hiccup/DOM) and
`apps/mobile/src/app/mobile/views.cljs` (React Native components).
Both consume the same `app.ui.events` and `app.ui.subs`. If you find yourself writing
an event handler or subscription inside an app, move it to `packages/ui`.

The desktop app has no ClojureScript of its own — Tauri renders `apps/web/public`.

## Conventions

- Two-space indentation. No comments in code.
- Namespaced keywords for domain data: `:user/email`, `:todo/done`.
- The API returns those namespaced keywords as-is (`"todo/id"` over the wire); the
  clients read them back as namespaced keywords. Do not rename them at the boundary.
- New endpoints are declared once in `app.shared.routes/endpoints`, then implemented in
  `app.api.router`. Clients address endpoints by id, never by string path.
- Validation happens twice, with the same schema: optimistically in `app.ui.events`
  before the request, authoritatively in `app.api.handlers` before touching Mongo.
- `app.shared.result` carries domain failures (`:not-found`, `:conflict`, …);
  `result/status` maps them to HTTP codes. Handlers should not invent status codes.
- Integrant owns lifecycle. A new stateful component gets an `ig/init-key`, an
  `ig/halt-key!`, and an entry in `apps/api/resources/config.edn`.

## Adding a feature end to end

1. Schema in `packages/shared/src/app/shared/schema.cljc`.
2. Endpoint in `packages/shared/src/app/shared/routes.cljc`.
3. Domain namespace in `apps/api/src/app/api/` returning `result/ok` or `result/err`.
4. Handler + route in `app.api.handlers` / `app.api.router`.
5. Events and subs in `packages/ui`.
6. Views in each app.
7. Tests in `apps/api/test/` — shared logic is tested on the JVM even though the
   clients are the main consumers.

## Running things

Everything is dockerized; use `make`, not raw `clojure`/`npx` calls.

```
make install     boot mongo, mongo-express, api, web, mobile; seed a demo account
make logs        tail everything
make api-test    kaocha against a throwaway database
make lint fmt    clj-kondo and cljfmt
```

REPL-driven work: `make api-repl` (7888), `make web-repl` (9630),
`make mobile-repl` (9632). The API dev container starts the system *and* an nREPL
server via `apps/api/dev/dev_main.clj`; `apps/api/dev/user.clj` gives you
`(go)`, `(reset)`, `(halt)` and `(conn)`.

Requires in `dev_main.clj`, `user.clj`, `app.api.main` and `app.api.fixtures` look
unused but load Integrant `defmethod`s — clj-kondo is configured to allow this in
`.clj-kondo/config.edn`. Do not delete them.

## Native builds

Docker runs the ClojureScript compiler and Metro. The native link step needs host SDKs:
`make mobile-ios` (Xcode), `make mobile-android` (Android SDK), `make desktop-bundle`
(Rust). CI builds the three desktop targets on separate runners because Tauri cannot
cross-compile them.

## Renaming

`make rename NAME=acme` rewrites `app.*` namespaces, moves source directories and
updates bundle identifiers. Run it once, early.
