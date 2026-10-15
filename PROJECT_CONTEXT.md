# NaviPlayer project context

Last updated: 2026-10-14

This file is the durable handoff for humans and coding agents. Update it with
every meaningful change so losing a chat does not mean losing the project state.

## Product

NaviPlayer is a Navidrome/Subsonic client with two Gradle modules:

- `app/`: Android client built with Kotlin, Jetpack Compose, Media3, OkHttp, and
  Coil. Minimum Android API 26, target API 35.
- `desktop/`: Windows and Linux client built with Kotlin/JVM, Compose Desktop,
  JavaFX Media, OkHttp, and Coil. It produces MSI and DEB distributions.

The desktop and Android applications share concepts and API behavior but do not
currently share a common Kotlin module.

## Important files

- `desktop/src/main/kotlin/app/naviplayer/desktop/Main.kt`: application entry
  point and ownership of process-lifetime services.
- `desktop/src/main/kotlin/app/naviplayer/desktop/DesktopPlayer.kt`: JavaFX
  playback, queue, state notifications, Discord updates, and webhook updates.
- `desktop/src/main/kotlin/app/naviplayer/desktop/DiscordPresence.kt`: direct
  implementation of Discord local IPC without the deprecated native RPC DLL.
- `desktop/src/main/kotlin/app/naviplayer/desktop/MprisService.kt`: Linux MPRIS,
  global media keys, playerctl, and desktop media widgets.
- `desktop/src/main/kotlin/app/naviplayer/desktop/WindowsMediaKeyService.kt`:
  Windows low-level keyboard hook for Play/Pause, Next, Previous, and Stop.
- `desktop/src/main/kotlin/app/naviplayer/desktop/PlaybackWebhookReporter.kt`:
  periodic now-playing delivery.
- `desktop/src/main/kotlin/app/naviplayer/desktop/SubsonicClient.kt`: desktop
  Navidrome/Subsonic API client and authenticated media URLs.
- `.github/workflows/build.yml`: signed Android APK releases.
- `.github/workflows/desktop.yml`: Windows/Linux desktop packaging and releases.

## Runtime ownership

`DesktopRuntime` owns exactly one `SessionStore`, `DiscordPresence`,
`PlaybackWebhookReporter`, `DesktopPlayer`, `MprisService`, and
`WindowsMediaKeyService`. This prevents Compose recomposition from leaking old
players, native hooks, threads, or IPC connections.

Shutdown order matters: external control services are closed before the player,
then webhook and Discord resources are released.

## Recent work and decisions

### Reliable now-playing artwork — 2026-10-14

- The public `/api/now-playing` response advertised `/api/now-playing/art`, but
  that artwork route returned HTTP 404 while clients were posting private
  Navidrome cover URLs. An external server cannot fetch those URLs when the
  Navidrome instance is reachable only over LAN or Tailscale.
- Android and Desktop webhook reporters now fetch artwork locally, bound the
  download to 2 MiB, resize it to at most 192 px, encode it as JPEG, and put a
  cached data URI in the existing `albumArtUrl` field. This keeps the receiving
  API contract unchanged while making the image self-contained.
- Artwork conversion and webhook delivery run off the playback/UI threads.
  Each reporter caches the current cover, and failures remain optional: a
  missing or unsupported image sends `null` and never interrupts playback.
- Private Navidrome cover URLs, including their query-string credentials, are
  no longer included in the JSON body. The existing authentication headers are
  unchanged for compatibility with the webhook endpoint.

Verification performed on Windows:

- The live public API was read without credentials: `/api/now-playing` returned
  an artwork proxy URL while that proxy returned HTTP 404, confirming the
  observed placeholder failure.
- `:desktop:compileKotlin` succeeded with JDK 22. The Kotlin daemon could not
  write to the host profile in the managed environment, so Gradle completed via
  its in-process fallback compiler.
- Android compilation and end-to-end posting remain unverified locally because
  no Android SDK or test Navidrome session is available.

### Desktop responsiveness, keyboard controls, and appearance — 2026-10-14

- Desktop Home no longer waits for the complete (up to 6,000-track) library
  before first paint. Home recommendations and Library metadata load
  independently, and the stable shuffled all-tracks queue is fetched lazily the
  first time All tracks or Loose tracks is opened.
- JavaFX playback callbacks now publish Compose state on the AWT UI thread.
  Progress updates are coalesced to four per second and ignore sub-150 ms
  changes, keeping decoder callbacks light and reducing UI churn on Windows and
  Linux. Queue navigation is serialized on the same UI thread.
- Desktop search submits with Enter. An app-level Space shortcut consumes the
  focused row/button event and toggles the current MediaPlayer, so it cannot
  accidentally start a different track. Space remains available while typing
  in the search field.
- Desktop appearance now persists six accents, comfortable/compact density,
  motion effects, ambient glow, and deep-night/AMOLED surfaces.
- Android appearance now persists System/Light/Dark mode, Dynamic/Coral/Violet/
  Blue/Mint accents, and an AMOLED dark-surface option. Controls live on the
  profile screen and apply immediately.

Verification performed on Windows:

- `:desktop:compileKotlin` succeeded with JDK 22.
- `:desktop:createDistributable` succeeded and produced the Windows app image.
- `git diff --check` succeeded before the commit.
- Android source was not compiled because no Android SDK is installed in the
  local environment; this remains a required CI/device check.

### Windows media keys and Discord RPC — 2026-10-13

Commit baseline: `75eccb8 Fix Windows media keys and Discord RPC`.

- Added JNA Platform `5.15.0` to the desktop module.
- Added a Windows-only `WH_KEYBOARD_LL` hook for virtual media keys `0xB0` to
  `0xB3`. Handlers are dispatched to the AWT event queue and events continue to
  the Windows hook chain.
- Fixed Discord IPC framing. Discord's Windows named pipe is message-oriented,
  so the 8-byte little-endian header and JSON payload must be written in one
  `OutputStream.write` call. Separate writes happened to work with Unix stream
  sockets but broke Windows RPC.
- Discord remains optional and reconnects on later presence refreshes when the
  client was not running initially.

Verification performed on Windows:

- `:desktop:compileKotlin` succeeded.
- `:desktop:createDistributable` succeeded, and the Windows app image contained
  the JNA jars/native resources.
- `:desktop:packageMsi` reached WiX linking but `light.exe`/jpackage exited with
  Windows code 216 in the local Codex environment. Direct `light.exe -?` worked.
  This packaging failure is still unresolved and may be environment-specific.
- End-to-end Discord presence and physical media-key behavior were not tested
  because Discord was not running during the build session.

### Desktop playback work — 2026-10-11 to 2026-10-12

Recent commits introduced one persistent playback runtime, clean shutdown,
desktop updater controls, MPRIS, Discord listening-time tracking, stable shuffle
queues, random shuffle starting positions, package identity fixes, and automatic
Discord RPC refresh.

### Now-playing webhook — 2026-10-12

Android and Desktop report playback roughly every 15 seconds to:

`https://feepiwakura.lol/api/now-playing`

Headers contain reusable Navidrome authentication material:

- `X-Navidrome-User`
- `X-Navidrome-Token`
- `X-Navidrome-Salt`

Payload fields include source, playing state, track identity and metadata,
artwork URL, progress, and duration. Failures are intentionally swallowed so the
webhook cannot block playback. This is a security-sensitive design decision and
should eventually be replaced or hardened; see `TODO.md`.

## Builds and releases

- Android local build: `./gradlew :app:assembleDebug`
- Desktop development run: `./gradlew :desktop:run`
- Current-platform installer: `./gradlew :desktop:packageDistributionForCurrentOS`
- Android releases use tags shaped like `v2.1.N`.
- Desktop releases use tags shaped like `desktop-v2.1.N`.
- The desktop Gradle module uses JVM toolchain 22; CI currently packages DEB and
  MSI artifacts.
- Discord application ID defaults to `1558612549287682078` and can be overridden
  by `NAVIPLAYER_DISCORD_APP_ID`.

## Recovery checklist

When resuming without the previous chat:

1. Read `AGENTS.md`, this file, and `TODO.md`.
2. Run `git status --short --branch` and inspect the last 10 commits.
3. Confirm which platform is in scope and whether Discord/Navidrome are available
   for live verification.
4. Continue from the first unchecked high-priority task in `TODO.md`, unless the
   user gives a different task.
5. Update these notes before committing the next meaningful change.
