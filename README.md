# multiplatform-clojure

A template for starting Clojure projects that ship to **API, web, desktop, Android and iOS**
from one monorepo, sharing everything except the view layer.

The whole development environment is dockerized and driven by `make`.

```
make install
```

That single command boots MongoDB, mongo-express, the API, the web watcher and the
mobile bundler, then seeds a demo account.

| Service | URL | Notes |
|---|---|---|
| API | http://localhost:8080/api/v1/health | Ring + reitit + Integrant |
| Web | http://localhost:8280 | shadow-cljs dev server, hot reload |
| mongo-express | http://localhost:8082 | database browser |
| API nREPL | `localhost:7888` | `make api-repl` |
| Web nREPL | `localhost:9630` | `make web-repl` |
| Mobile nREPL | `localhost:9632` | `make mobile-repl` |

Demo account: `demo@example.com` / `demo12345`

## What is actually shared

The point of the template is that platform-specific code is a thin shell.

```
packages/
  shared/    .cljc  schemas, validation, routes, formatting          -> api + web + desktop + mobile
  api-sdk/   .cljc  typed API client for the JVM and JavaScript       -> api tests + web + desktop + mobile
  app/       .cljs  the client app: state, events, screen view models,
                    user-facing copy, startup                         -> web + desktop + mobile

apps/
  api/       .clj   the only place that talks to MongoDB
  web/       .cljs  DOM components, CSS, localStorage, React root
  mobile/    .cljs  React Native components, styles, SecureStore, AppRegistry
  desktop/   rust   Tauri shell that renders the web build verbatim
```

Web and mobile run the same application; they only differ in the components they render
with. Everything else lives in `packages/app`:

- **Startup.** `app.app.core/start!` installs the API client and a storage backend,
  loads the saved session and boots. An app passes `{:storage {:get :set :del}}` (sync or
  promise-returning) and mounts its root.
- **Screens as data.** One subscription per screen returns everything the view shows:
  `::subs/screen` (`:loading`, `:auth` or `:todos`), `::subs/auth-screen` (title, fields,
  error, submit and switch labels) and `::subs/todos-screen` (user, summary, draft, list
  state, items).
- **Behaviour as events.** Form input, mode switching, submit (including double-submit
  protection), todo create, toggle and delete, sign out.
- **Copy.** Every string lives in `app.app.copy`, ready for translation.

So `apps/web/src/app/web/views.cljs` and `apps/mobile/src/app/mobile/views.cljs` only map
those view models to `[:input]` or `TextInput`, `[:button]` or `TouchableOpacity`. A new
feature is built and tested once in `packages/app`; each app then adds components for it.
The package's tests (`make app-test`) drive whole user flows headlessly — start, log in,
create, toggle, delete, sign out — against an in-memory fake API.

A worked example: `app.shared.format/summarize` computes the "3 of 5 tasks done" label.
It is a `.cljc` file, so the API can use it in tests, the web renders it in a `<p>`, and
React Native renders it in a `<Text>` — one implementation, three consumers.

## api-sdk

`packages/api-sdk` is the one way to talk to the API, from any runtime. It is `.cljc`:
`java.net.http` on the JVM, `fetch` in browsers, React Native, Tauri and Node.

```clojure
(require '[app.api-sdk.core :as sdk]
         '[app.shared.result :as result])

(def api (sdk/client {:base-url "http://localhost:8080"}))

(let [session (result/value @(sdk/login api {:email "demo@example.com" :password "demo12345"}))
      api (sdk/with-token api (:token session))]
  @(sdk/create-todo api {:title "from the JVM"})
  (result/value @(sdk/list-todos api)))
```

On the JVM every call returns a `CompletableFuture` (so `@` works); in ClojureScript it
returns a `js/Promise`. Either way it **resolves, never rejects**, to an
`app.shared.result`:

| Outcome | `result/kind` |
|---|---|
| 2xx | `result/ok?` is true, body in `result/value` |
| input fails the shared malli schema (no request sent) | `:invalid` |
| protected endpoint called without a token (no request sent) | `:unauthorized` |
| 400 / 401 / 403 / 404 / 409 | `:invalid` `:unauthorized` `:forbidden` `:not-found` `:conflict` |
| 5xx | `:internal` |
| connection refused, DNS, timeout | `:network` |

`sdk/error-message` turns any failure into a human-readable string, including
per-field validation errors (`"email must be a valid email; password must be at least 8
characters"`), so every client shows the same wording.

Typed functions: `health`, `register`, `login`, `me`, `list-todos`, `create-todo`,
`update-todo`, `delete-todo`. They are thin wrappers over `(sdk/call client op request)`,
which reads method, path, auth requirement and request schemas from
`app.shared.routes/endpoints`. A custom `:transport` function can be passed to
`sdk/client` for tests or mocks.

In re-frame, `packages/app` exposes it as an effect:

```clojure
{:api/call {:op :todo/update
            :token token
            :params {:id id}
            :body {:done true}
            :on-success [::saved]
            :on-failure [::failed]}}
```

The SDK's tests run on both platforms (`make sdk-test`), and
`apps/api/test/app/api/sdk_contract_test.clj` drives a real server through the SDK so
the client and API cannot drift apart.

## Requirements

Docker is enough for the API, web and mobile bundler. Native builds need host tooling:

- **Desktop**: Rust 1.88+. Linux also needs `libwebkit2gtk-4.1-dev`, `libgtk-3-dev`,
  `libayatana-appindicator3-dev`, `librsvg2-dev`, `patchelf`.
- **Android**: Android SDK (compileSdk 36, Android 7+ devices) and JDK 17–21; Android
  Studio's bundled JDK works.
- **iOS**: macOS with Xcode 26.4+ (iOS 16.4+ devices).

Metro and the ClojureScript compiler run in Docker; the native compile/link step cannot,
because it needs the platform SDKs. `make mobile-dev` keeps the bundler in a container and
`make mobile-ios` / `make mobile-android` drive the host toolchain against it.

## Commands

```
make help              list every target
make install           boot the environment and seed a demo account
make up / down         start / stop containers
make logs              tail all container logs
make reinstall         wipe volumes and start over

make api-repl          connect an nREPL to the running api
make api-test          run the test suite against a throwaway database
make api-image         build the production api image
make sdk-test          api-sdk tests on the jvm and on node
make app-test          shared app logic tests on node

make web-dev           web watcher in the foreground
make web-build         optimized web bundle
make web-repl          browser-connected cljs repl

make mobile-dev        mobile bundler in the foreground
make mobile-prebuild   generate native ios/android projects
make mobile-ios        build and run on ios
make mobile-android    build and run on android

make desktop-dev       tauri window against the web dev server
make desktop-bundle    installers for the current host

make lint fmt test     clj-kondo, cljfmt, kaocha
make outdated          report outdated dependencies everywhere
make upgrade           apply safe dependency upgrades
make rename NAME=acme  rename the template's namespaces
```

## Versions

The template tracks current stable releases.

| Layer | Version |
|---|---|
| JVM (dev images, production image, CI) | Java 25 LTS (Temurin) |
| Clojure / ClojureScript | 1.12.6 / 1.12.145 |
| shadow-cljs | 3.5.1 |
| Reagent / re-frame | 2.0.1 / 1.4.7 |
| React (web) | 19.3 |
| Expo / React Native / React (mobile) | SDK 57 / 0.86.3 / 19.2.3 |
| Tauri | 2.11 |
| Node | 24 LTS |
| MongoDB | 8.2 |

`make outdated` reports what is behind across Clojure, npm, Expo, Cargo and GitHub
Actions. `make upgrade` applies the safe upgrades and prints the manual steps for the
rest. `renovate.json` opens weekly update PRs once the
[Renovate app](https://github.com/apps/renovate) is installed on the repository.

Deliberate holds, all encoded in `renovate.json`:

- **MongoDB 8.2**, not `8`/latest: 8.0 and 8.3 refuse to start on Linux kernel 6.19+
  (SERVER-121912), which is what current Docker Desktop runs.
- **Mobile React / React Native versions are owned by Expo.** Bump `expo`, then run
  `npx expo install --fix`; never bump `react-native` on its own.
- **Babel 7** on mobile until `babel-preset-expo` supports Babel 8.

## Desktop strategy

The desktop app is **Tauri v2**, not Electron. `frontendDist` points at
`apps/web/public`, so the desktop window renders the exact ClojureScript bundle the
browser gets — no third view implementation, and binaries are a few MB rather than
~150MB. In development `devUrl` points at the shadow-cljs dev server, so hot reload works
inside the desktop window too.

Tauri cannot cross-compile all three desktop targets from one machine, so
`.github/workflows/desktop.yml` builds Linux, macOS and Windows on their own runners.

## Making it yours

```
make rename NAME=acme
```

This rewrites the `app.*` namespaces to `acme.*`, moves the source directories, and
updates the bundle identifiers. Review with `git diff --stat`, then change
`JWT_SECRET` in `.env` before deploying anything.

## Layout

```
.
├── apps
│   ├── api          Clojure service: Ring, reitit, Integrant, MongoDB
│   ├── web          ClojureScript SPA: shadow-cljs, Reagent, re-frame
│   ├── mobile       ClojureScript RN app: shadow-cljs, Expo
│   └── desktop      Tauri v2 shell around the web build
├── packages
│   ├── shared       .cljc used by every target, including the JVM
│   ├── api-sdk      typed API client, JVM (java.net.http) and JS (fetch)
│   └── app          client app logic shared by web, desktop and mobile
├── scripts          seed and rename helpers
├── docker-compose.yml
└── Makefile
```
