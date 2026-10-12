# NaviPlayer

NaviPlayer is a focused Android, Linux and Windows client for your own [Navidrome](https://www.navidrome.org/) music server. It keeps the familiar Subsonic-compatible backend while giving playback a native Material 3 interface, adaptive colours and artwork-led player screens.

This repository contains only client applications; server-side tooling and personal automation are intentionally out of scope.

## Features

- Sign in to an existing Navidrome account over the Subsonic API
- Material 3 interface with dynamic colours on Android 12+
- Artwork-derived colours and glow in the now-playing screen
- Background playback and system media controls through Android Media3
- Smooth wavy playback progress, seeking, queue, shuffle and repeat
- Synced and plain lyrics when supplied by the server
- Home mixes, albums, artists, playlists, search and listening history
- A dedicated loose-tracks view for files without reliable album metadata
- Local profile avatar that stays on the device and survives sign-out
- Bundled Google Sans typography for consistent rendering

### Desktop

- A wide layout designed for mouse and keyboard
- Recent albums, random recommendations, server search and album pages
- Cover artwork, queue navigation, playback and seeking
- Native `.deb` packages for Linux and `.msi` installers for Windows
- Optional Discord Rich Presence with the current song and playback time

## Requirements

- Android 8.0 (API 26) or newer
- A reachable Navidrome server and an existing user account

On the sign-in screen, enter the full server address, for example `https://music.example.com`. A LAN or Tailscale address also works. Avoid exposing an unencrypted Navidrome port directly to the public internet.

## Install

Download an APK, Linux `.deb` or Windows `.msi` from [GitHub Releases](../../releases). Android and desktop builds use separate versioned releases, each with a short explanation of the changes included in it.

Android may ask permission to install apps from your browser or file manager. Existing official NaviPlayer installs can be updated in place because release APKs keep the same signing identity.

## Build locally

Open the project in Android Studio with JDK 17, or run:

```bash
# Android APK
./gradlew :app:assembleDebug

# Run the desktop app on the current OS
./gradlew :desktop:run

# Create a native desktop installer for the current OS
./gradlew :desktop:packageDistributionForCurrentOS
```

The debug APK is written to `app/build/outputs/apk/debug/` and uses Android's standard debug signing key.

## Discord Rich Presence

Desktop Rich Presence is optional, never blocks playback, and uses NaviPlayer's built-in Discord Application ID.
It starts automatically when Discord is running. To override it with another application, launch NaviPlayer with:

```bash
NAVIPLAYER_DISCORD_APP_ID=123456789012345678 naviplayer
```

On Windows PowerShell:

```powershell
$env:NAVIPLAYER_DISCORD_APP_ID = "123456789012345678"
Start-Process NaviPlayer
```

If Discord is closed or IPC is unavailable, NaviPlayer continues normally.

## Release signing

Release builds never store a signing key or passwords in this repository. The Gradle build reads these environment variables:

- `NAVIPLAYER_KEYSTORE_PATH`
- `NAVIPLAYER_KEYSTORE_PASSWORD`
- `NAVIPLAYER_KEY_ALIAS`
- `NAVIPLAYER_KEY_PASSWORD`

The GitHub Actions workflow restores the key from the `ANDROID_KEYSTORE_BASE64` repository secret and reads the remaining values from secrets with matching names. A manual workflow run can include a custom “why this update” note; otherwise release notes are generated from commit subjects since the previous tag.

The desktop workflow builds Linux and Windows in parallel. Android tags use `v2.1.N`; desktop releases use
`desktop-v2.1.N`, so neither platform replaces releases belonging to another one.

## Privacy

Credentials and the optional profile image are stored locally on the device. The avatar is not uploaded by NaviPlayer. Music, artwork and lyrics are requested directly from the server address you provide.

## License

Source code is available under the [MIT License](LICENSE). The bundled Google Sans font files retain their Open Font License notice in `app/src/main/res/raw/google_sans_ofl.txt`.

NaviPlayer is an independent project and is not affiliated with the Navidrome project.
