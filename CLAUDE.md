# R1 Control: Project Charter

> Source of truth for architectural decisions. If a change contradicts this
> file, update this file rather than quietly going another way. The product
> spec is `docs/spec.md`; its "Decisions already made" are not reopened here.
> The toolchain, version set and most rules are cloned from
> `/Volumes/Data2/mytube-app` (its CLAUDE.md is the longer version of §3, §4, §8, §9).

## 1. What this is

A phone app (Android + iOS, Kotlin Multiplatform + Compose Multiplatform) that
controls the household's Phicomm R1 smart speaker the way the Google Home app
controls a Nest speaker: see what plays, control it, turn the volume with a
swipe, find music and play it on the speaker, reach its settings behind a gear.
The R1 serves a web panel and a JSON API on `http://<r1>:8088`; the panel's
source (`/Volumes/Data2/git/xiaozhi-android/app/src/main/assets/control.html`,
`.../control/ControlServer.kt`) is the reference for every call. This app is
the everyday remote, not a clone of the panel.

## 2. Hard constraints

| | |
|---|---|
| Platforms | Android phone + iOS. No desktop, no TV |
| Reach | **House wifi only.** The R1 speaks plain HTTP, no TLS, no auth. The address is DHCP, typed by hand as `ip:port` (default 8088) |
| Secrets | `/api/state` carries masked keys and the household's Home Assistant device names. **Never log or show a raw body.** No Ktor `Logging` plugin (guarded); exceptions carry a call name, never a body |
| Everything on Data2 | Source and toolchain. The internal disk is nearly full |
| Testing the real R1 | Evening or night: set `volume` to 0 first and restore it afterwards (spec, "Night rule") |

## 3. Architecture

Clean architecture by direction of dependency. `ArchitectureGuardTest` fails
the build when an arrow points the wrong way.

```
composeApp/src/
  commonMain/kotlin/com/r1control/app/
    domain/model/        entities and pure decisions. No Ktor, serialization, Compose
    domain/repository/   the ports, and SpeakerException
    domain/usecase/      poller, search, play, chat, save-address, volume throttle
    data/remote/         Ktor client, mappers; data/remote/dto holds the wire shapes
    data/local/          SettingsDataSource (one implementation per platform)
    data/repository/     implementations of domain/repository
    ui/i18n/ ui/theme/ ui/    Strings, theme (Tokens, Radius, Space), App shell
    ui/control music chat settings address/   XScreen(viewModel) -> XContent(state, actions)
    ui/components/ ui/icons/  shared pieces; every glyph is an ImageVector in R1Icons
    AppContainer.kt      the composition root
  androidMain/ iosMain/  entry points, settings store, device language
  commonTest/            domain and view model tests (pure, fakes: `testing/FakeSpeaker`)
  jvmTest/               guards, wire mapping and HTTP tests (read docs/fixtures)
iosApp/                  Swift entry point, hand-written .xcodeproj
docs/fixtures/           verbatim R1 replies; the single source for wire tests
```

- **No `@Serializable` outside `data`.** Domain types are not the R1's JSON.
- **Nothing in `domain` or a ViewModel is nullable. DTOs are.** Absence is
  decided once, in the mapper, and named (`hasTtsHost`, `SavedAddress.Unset`,
  `AddressInput.Invalid`), never `.isEmpty()` scattered across screens.
- **Failures are `SpeakerException`** (`NotConfigured`, `Unreachable`,
  `Refused`, `BadReply`), thrown by `SpeakerRepository`, carried by use-case
  outcomes, put into words by `Strings.describe`.
- **No DI framework.** One composition root, `AppContainer`.
- **The card is one pure function**: `nowPlayingOf(snapshot)` (Bluetooth in,
  then music, then assistant, then nothing).
- **Polling**: `SpeakerPoller.snapshots()` is a cold flow, so a screen shares it
  with `stateIn(scope, WhileSubscribed)` and it stops when nobody looks.
- **Volume** goes through `VolumeSetter` (trailing throttle, release value is
  always last) and `VolumeScale.snap`.

## 4. Versions

Read `gradle/libs.versions.toml` before changing anything: it is mytube-app's
set (Kotlin 2.3.20 pinned by Compose 1.11.0, AGP 8.11.1, Gradle 8.14, Ktor
3.5.2, lifecycle 2.10.0) with its reasons. AGP 9.x and anything "newest
generation" from androidx demand AGP 9.1 and fail; take the version below.
One release number lives in five places: `versionName`, `versionCode`,
`CFBundleShortVersionString`, `CFBundleVersion`, and `APP_VERSION`, which
Settings shows (`VersionGuardTest`).

## 5. Language

Code, identifiers, comments and commit messages in English. Display strings
only go through `Strings` (`EnglishStrings`, `VietnameseStrings`, chosen in
Settings > Language: follow the phone by default, Tiếng Việt or English, stored
on the phone, read before the first frame). A property added to `Strings` stops both languages from
compiling until supplied. Formatters take `Strings`. Conversation with the
owner is in Vietnamese.

## 6. Testing

| layer | where | what it catches |
|---|---|---|
| Unit | `commonTest` | pure decisions, use cases against fakes |
| Wire | `jvmTest` | mappers over `docs/fixtures`; requests through Ktor `MockEngine` |
| Guard | `jvmTest` | source scans (`ArchitectureGuardTest`, `UntranslatedGuardTest`, `HapticGuardTest`); `jvmTest` declares `src` as an input so a guard actually reruns |

View models are plain classes that take a `CoroutineScope` (the activity's
`viewModelScope` for the tabs, one `rememberCoroutineScope` per settings page)
and are tested with `runTest` and `backgroundScope`. A text field's text is its
own `StateFlow`, never routed through a combined state.

Test bodies for wire mapping are copied from the running R1, never invented.
Regression tests go red on the reported symptom first. Guards were each proven
to fail before they were believed.

**Verification command:**

```sh
source env.sh && ./gradlew jvmTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```

Nothing is called done because it compiled: UI is looked at on the simulator,
and anything that touches the speaker is checked against the real R1.

## 7. Toolchain

`source env.sh` (JDK 21, Android SDK, Gradle home and Kotlin/Native on
`/Volumes/Data2/dev`). iOS: `iosApp/iosApp.xcodeproj` is hand-written, the
framework is static; simulator builds need
`-destination 'platform=iOS Simulator,id=<udid>'` and a `-derivedDataPath`
under `/Volumes/Data2/dev/`. Android allows cleartext HTTP to any host
(`network_security_config.xml`); iOS allows local networking
(`NSAllowsLocalNetworking`) and carries a local-network usage description.

## 8. Rules learned

- **Send every request body as `application/json; charset=utf-8`.** Measured:
  without the charset the R1's NanoHTTPD decodes the body as ASCII and every
  Vietnamese title becomes `Th��nh T��m` on the speaker and in the assistant's
  spoken line. The same holds for `/api/news/save` (a Vietnamese voice name).
  The content type is `JsonUtf8` in `R1Http.kt`, built as a `TextContent` so no
  plugin rewrites it, and pinned by `HttpSpeakerRepositoryTest`.
- **A stopped player reports `video_id: "null"` (the string).** The mapper
  turns it into empty; `stopped` and `idle` with a kept queue are "nothing".
- **The panel caps a play queue at 30**, windowed around the tapped song
  (`queueAround`); 15 results per keyword times several keywords exceeds it.
- **Music outranks the assistant on the card, and must.** Measured 2026-11-07 at
  volume 0: while a song plays the R1 reports `voice_state: SPEAKING`,
  `voice_awake: true` for the whole song (music goes out through the same voice
  playback). An assistant-first rule would show every song as "speaking". After
  `stop` it reports `LISTENING`, awake, for a while.
- **Reply bodies are decoded as UTF-8 whatever the R1 declares** (it declares
  no charset).
- **A name that shadows a member wins silently.** `url { build() }` called
  `URLBuilder.build()` instead of the parameter and every request went to
  `localhost`; the tests, not the compiler, found it. Name lambda parameters
  so no builder member can claim them.
- **One shared reading of the speaker.** `SpeakerFeed` polls only while a
  screen collects `view` (and 3 s after the last one leaves), keeps the last
  snapshot when a poll fails (`Stale`), and `refresh()` reads once after a
  command. Measured on the device: pause, resume and next are applied by the R1
  before it answers, so an immediate read already shows them; without the
  refresh the card lagged by the idle poll interval (3 s).
- **The finger owns a control until the R1 has caught up.** Volume and seek
  position keep the finger's value for 1.5 s after release (`HeldValue`).
  Measured: after a drag to 7% the R1 reported 7 within half a second; the
  hold is for the poll that was already in flight.
- **Text needs a content colour from a `Surface`.** A bare `Box` under
  `MaterialTheme` leaves `LocalContentColor` black and every `Text` without an
  explicit colour is invisible on the near-black ground. `App` wraps everything
  in `Surface`.
- **Material's `Slider` is a tall bar on a thick track** in this version; the
  design's is a round thumb on a thin track, so `R1Slider` draws its own thumb
  and track (still Material's gestures and semantics).
- **`WindowInsets.isImeVisible` does not exist in common code** and
  `androidx.compose.ui.tooling.preview.Preview` does not resolve on the jvm
  target: use `WindowInsets.ime.getBottom(density) > 0` and
  `org.jetbrains.compose.ui.tooling.preview.Preview`.
- **`advanceUntilIdle` ignores `backgroundScope`.** A view model built on it
  needs `advanceTimeBy` plus `runCurrent`, and an event exactly at the boundary
  needs one millisecond more.
- **The R1's cover is a 120 px thumbnail** (`=w120-h120-l90-rj`); the card
  asks the image host for 640 px by rewriting that suffix (`coverAtSize`).
- **"Play a news test" stops the music** that is playing (measured): the card
  falls to Nothing, then to the assistant while the bulletin is read.
- **A slider tap writes.** Measured: one tap on the loudness slider changed
  `loudness_mb` 1000 -> 1200 on the real speaker at once (write path proven);
  restore a value you touch with `POST /api/set?key=loudness_mb&value=<old>`.
- **Chat lines the R1 writes for songs are `assistant` lines** ("Đang tải bài
  ... của ..."); the user's typed line comes back as `user` within a second.
- **Readings apply in the order they started, not the order they land.**
  `SpeakerFeed` (and Audio, News) number each read; `restart()` drops reads
  still in flight from an old address. A slow poll otherwise put Playing back
  over a pause.
- **Nothing reads the speaker while the app is hidden.** A stopped activity and
  a backgrounded iOS app keep their collectors; polled reads wait in
  `ForegroundSpeaker`, fed from the lifecycle by `App`. Commands always go.
- **A command's failure clears itself** (`CommandProblem`): at least 4 s so it
  can be read, then until the speaker answers again. In Chat with the keyboard
  up the failure is said above the field (`sendProblem`), where the eye is.
- **A read gets 2 s, a command keeps 10.** Measured 2026-11-08: GETs answer in
  30-220 ms; with a 10 s timeout a dropped wifi left a live-looking card for
  9-12 s. A search is the R1 asking YouTube and keeps 10 s.
- **Listening next to a `stopped` player is nothing.** After stop or the end
  of a queue the R1 reopens its mic in the same session; only a timed-out
  session clears the player to `idle` (read from its `VoiceAssistant`).
- **A settings page shows the speaker's value, not the one that failed.**
  Audio and News poll every 3 s while open and hold an edit (`HeldValue`)
  until the R1 took it; a failed write lets go at once. News saves as it is
  edited; "Phát thử" saves first, as the panel does.
- **A screen's first frame comes from the source, not a `stateIn` initial.**
  Chat's fixed "asking" initial flashed a spinner on the first tab visit;
  collect a cold flow with `initial = vm.current()`.
- **iOS slides the whole Compose view to a focused field** unless
  `onFocusBehavior = OnFocusBehavior.DoNothing`; with `imePadding` both
  applied and the top bar went under the status bar. `requestFocus()` raises
  the keyboard, `clearFocus()` lowers it.
- **Chat rows are keyed by the message, never its index** (the R1 keeps 50
  lines, so indexes move), and the list is `reverseLayout`.
- **A text field's `value` can arrive a keystroke late**: a text the field
  reported itself is an echo, not an outside change (`R1TextField`).
- **A URL built from an R1 answer (`tts_host`) is parsed before the request.**
  Inside `R1Http.call` every throw is Unreachable.
- **On an iPhone, "unreachable" names Local Network** (`phoneAsksForLocalNetwork`):
  a refused permission fails exactly like being off the wifi, and first run
  checks again when the app comes back to the front.
- **A list's songs have one owner (`MusicStore`); the playing list is a pointer
  into it, never a copy.** An edit is told to the speaker only at Next, so the
  song playing is never cut. One Next (`MusicStore.next` over `nextMove`);
  `OneNextGuardTest` fails on a second caller of `SpeakerRepository.next`.
- **What the speaker plays now is read from the reading, never from what was
  last sent** (`playsFrom`, `nextMove`); a pointer is re-checked when its send
  lands (`sentFrom`), since a delete or a search can arrive mid-send.
- **An unreadable stored value is never saved over**: playlists that do not
  decode are copied aside once (judged by content), or the shelf locks.
- **A drag that reorders a lazy list pins the scroll after each move**
  (`requestScrollToItem`); only a touch-pointer `ImageComposeScene` test shows
  it (`EditableRowsGestureTest`).
- **End the conversation (`/api/voice/sleep`) stops music on the R1**, which
  reports SPEAKING for a whole song: drawn only by `canEndSession`, never over a
  Stale reading. `wake_enabled` absent means an R1 build without both routes,
  and the R1 reads anything but `on=1` as off.
- **The Now playing page answers "which songs are playing"** (`nowPlayingPageOf`):
  the app's list in its playing order (`FromApp`, read through `playsFrom`, so a
  cleared search still has it), else the speaker's own queue read only
  (`FromSpeaker`), else nothing. A tapped row plays from its place in that same
  order (`MusicStore.playFrom`), never a new shuffle.
- **Settings draw from the feed.** `SpeakerSnapshot.settings` comes from the
  `/api/state` body the feed already reads; Home and the pages open on it, with
  no Loading frame, and refresh behind it. The voice list is remembered by
  `VoiceCache` (the one owner, warmed from the first snapshot that names a voice
  server). A page that writes tells the feed to read again.
- **Every touch is felt, through one seam.** `Haptic` names the six sensations
  (Tap, On, Off, Tick, Knock, Lift); `LocalHaptics` carries the one
  `rememberPlatformHaptics()` from `App` (`View.performHapticFeedback` on
  Android, Apple's generators prepared once on iOS). Presses go through
  `tappable`, switches through `R1Switch`, sliders through `R1Slider`
  (`HapticGuardTest`). A value is felt by notch (`notchFeel`): a tick past
  one, a knock at either end, one tick however far a fast finger jumped. The
  volume's notches are the speaker's own steps; the seek bar is not felt.
