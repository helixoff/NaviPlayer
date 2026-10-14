# NaviPlayer work queue

Keep this list short and actionable. Completed work belongs in Git history and
`PROJECT_CONTEXT.md`, not in an ever-growing checklist.

## P0 — validate the Windows fixes

- [ ] Run the packaged Windows app with Discord Desktop already running.
- [ ] Start a track and confirm title, artist, artwork, playing state, paused
      state, and elapsed listening time appear correctly in Discord Rich Presence.
- [ ] Confirm Play/Pause, Next, Previous, and Stop hardware keys work while the
      NaviPlayer window is unfocused.
- [ ] Check behavior when another media application is open. Decide whether
      NaviPlayer should pass handled media-key events down the Windows hook chain
      or consume them to prevent two players responding.
- [ ] Verify reconnection when Discord starts after NaviPlayer.
- [ ] In the packaged desktop app, focus a song row and confirm Space only
      pauses/resumes the current track; confirm Enter submits Search and spaces
      can still be typed in its field.
- [ ] Compare playback on Windows and Linux before/after the coalesced 4 Hz UI
      progress updates, including while synced lyrics are open.

## P0 — validate Android appearance controls

- [ ] Run `:app:assembleDebug` with an Android SDK installed; the 2026-10-14
      environment had JDK 22 but no Android SDK.
- [ ] On Android 11 and Android 12+, verify System/Light/Dark, all custom
      accents, Dynamic color fallback, and AMOLED surfaces across relaunches.

## P0 — restore reliable Windows packaging

- [ ] Reproduce `:desktop:packageMsi` outside the Codex sandbox.
- [ ] Diagnose jpackage/WiX exit code 216. Capture verbose jpackage and WiX logs.
- [ ] Confirm the resulting MSI installs per-user, launches, upgrades, and
      uninstalls cleanly without breaking the existing application identity.

## P1 — diagnostics and resilience

- [ ] Expose optional integration status in a small diagnostics area or bounded
      log: Discord connected/disconnected, Windows media hook installed/failed,
      MPRIS available/unavailable, webhook last success/failure.
- [ ] Add focused tests for Discord frame encoding so header and payload can
      never regress to separate writes.
- [ ] Add tests around queue navigation triggered from non-Compose/native threads.

## P1 — webhook security review

- [ ] Decide whether the external now-playing service truly needs reusable
      Navidrome token/salt headers.
- [ ] Prefer a scoped, revocable webhook credential or server-side proxy design.
- [ ] Document retention, authentication, TLS expectations, and failure behavior.
- [ ] Ensure logs and UI errors never expose Navidrome authentication material.

## Maintenance rule

Whenever one of these items is completed, update `PROJECT_CONTEXT.md`, remove or
replace the item here, record the exact verification performed, and commit the
notes together with the code.
