# NaviPlayer project context

Last updated: 2026-10-13

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
