# NaviPlayer

NaviPlayer is a focused Android client for your own [Navidrome](https://www.navidrome.org/) music server. It keeps the familiar Subsonic-compatible backend while giving playback a native Material 3 interface, adaptive colours and a compact, artwork-led now-playing screen.

This repository contains only the Android application; server-side tooling and personal automation are intentionally out of scope.

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

## Requirements

- Android 8.0 (API 26) or newer
- A reachable Navidrome server and an existing user account

On the sign-in screen, enter the full server address, for example `https://music.example.com`. A LAN or Tailscale address also works. Avoid exposing an unencrypted Navidrome port directly to the public internet.

## Install

Download an APK from [GitHub Releases](../../releases). Each automated build has its own versioned release and a short explanation of the changes included in it.

Android may ask permission to install apps from your browser or file manager. Existing official NaviPlayer installs can be updated in place because release APKs keep the same signing identity.

## Build locally

Open the project in Android Studio with JDK 17, or run:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/` and uses Android's standard debug signing key.

## Release signing

Release builds never store a signing key or passwords in this repository. The Gradle build reads these environment variables:

- `NAVIPLAYER_KEYSTORE_PATH`
- `NAVIPLAYER_KEYSTORE_PASSWORD`
- `NAVIPLAYER_KEY_ALIAS`
- `NAVIPLAYER_KEY_PASSWORD`

The GitHub Actions workflow restores the key from the `ANDROID_KEYSTORE_BASE64` repository secret and reads the remaining values from secrets with matching names. A manual workflow run can include a custom “why this update” note; otherwise release notes are generated from commit subjects since the previous tag.

## Privacy

Credentials and the optional profile image are stored locally by Android. The avatar is not uploaded by NaviPlayer. Music, artwork and lyrics are requested directly from the server address you provide.

## License

Source code is available under the [MIT License](LICENSE). The bundled Google Sans font files retain their Open Font License notice in `app/src/main/res/raw/google_sans_ofl.txt`.

NaviPlayer is an independent project and is not affiliated with the Navidrome project.
