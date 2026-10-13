# NaviPlayer agent instructions

These instructions apply to the whole repository. They exist so work can be
continued safely even when the previous chat is unavailable.

## Start every task by recovering context

1. Read `README.md`, `PROJECT_CONTEXT.md`, and `TODO.md`.
2. Inspect `git status --short --branch` and the latest commits before editing.
3. Treat the code and Git history as the source of truth when a note is stale.
4. Do not discard, reset, or overwrite unrelated local changes.

## Keep the project memory current

After any non-trivial change, update `PROJECT_CONTEXT.md` and `TODO.md` in the
same commit. Record:

- what changed and why;
- important design decisions and assumptions;
- verification that was actually performed;
- remaining bugs, risks, and the best next step;
- platform-specific behavior that the next agent may not be able to reproduce.

Do not write chat transcripts. Preserve only durable engineering context.

## Project constraints

- NaviPlayer has Android and Desktop targets. Avoid breaking one while working
  on the other, even when the request is platform-specific.
- Keep native integrations tied to the single `DesktopRuntime` lifetime. Do not
  create media players, Discord clients, hooks, or webhook reporters during
  Compose recomposition.
- Gate platform-specific code explicitly. Linux media integration uses MPRIS;
  Windows media-key integration uses WinAPI through JNA.
- Android requires JDK 17 or newer. Desktop uses the Gradle JVM toolchain 22.
- Prefer graceful degradation for optional integrations: Discord, MPRIS,
  Windows media keys, updates, and the now-playing webhook must never prevent
  local playback.

## Security and privacy invariants

- Never commit credentials, GitHub tokens, signing files, Navidrome passwords,
  generated credential stores, or files under `work/`.
- The now-playing webhook currently sends Navidrome username, token, and salt in
  request headers to `https://feepiwakura.lol/api/now-playing`. Treat these as
  secrets: never log them or expose them in errors. Do not change this contract
  without documenting the migration in `PROJECT_CONTEXT.md`.
- Keep release signing material in environment variables/GitHub secrets only.

## Verification expectations

Run the smallest relevant checks and report exactly what ran.

- Desktop compile: `./gradlew :desktop:compileKotlin`
- Desktop runnable image: `./gradlew :desktop:createDistributable`
- Desktop installer: `./gradlew :desktop:packageDistributionForCurrentOS`
- Android debug APK: `./gradlew :app:assembleDebug`

When the required JDK, Android SDK, Discord client, Navidrome server, or native
packager is unavailable, record the unverified behavior in `TODO.md` rather than
claiming it works.

## Git hygiene

- Keep commits focused and use imperative English commit subjects, matching the
  existing history.
- Stage explicit paths so local caches and unrelated work cannot slip in.
- Do not force-push, rewrite shared history, or delete user work.
- Before handing off, run `git diff --check` and report any remaining untracked
  or modified files.
