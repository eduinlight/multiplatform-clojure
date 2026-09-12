# Desktop (Tauri v2)

The desktop shell renders the exact ClojureScript bundle produced by `apps/web`.
No view code is duplicated: `frontendDist` points at `apps/web/public`, and
`devUrl` points at the shadow-cljs dev server, so hot reload works in the
desktop window too.

## Requirements on the host

Tauri compiles native code, so these run on the host rather than in Docker:

- Rust toolchain (`rustup`)
- Linux: `libwebkit2gtk-4.1-dev`, `libgtk-3-dev`, `libayatana-appindicator3-dev`, `librsvg2-dev`, `patchelf`
- macOS: Xcode Command Line Tools
- Windows: MSVC Build Tools + WebView2 runtime

## Commands

```
make desktop-dev          # dev window against the shadow-cljs dev server
make desktop-build        # release binary for the current host
make desktop-bundle       # installers (.dmg/.msi/.AppImage/.deb) for the host
```

Cross-compiling desktop bundles for all three OSes from one machine is not
supported by Tauri; `.github/workflows/desktop.yml` builds each on its own
runner instead.
