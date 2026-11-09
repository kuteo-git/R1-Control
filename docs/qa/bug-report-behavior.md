# R1 Control: runtime bug report

Bugs seen on the running app (iPhone 16e simulator, pre-fix build) against the real R1, or,
for B-01, reasoned from the build settings for the real iPhone. Areas and pass results are
in `behavior-report.md`. Screenshots are `docs/qa/screens/behavior-NN-*.png`.

"Same as T-xx / U-xx" means `bug-report-tests.md` or `bug-report-ux.md` already has it. Those
entries add only what runtime showed beyond the earlier report. Code locations refer to the
code as read at 18:20 on 2026-11-08, the code the tested app was built from. Other agents
edited the tree afterwards.

The test harness is described in `behavior-report.md`. A TCP relay at `10.25.113.151:18088`
could cut, hang or slow the path to the R1 without touching the R1.

16 bugs: 0 blocker, 4 major, 12 minor.

## Major

### B-01. On a fresh iPhone install the first Connect probably fails, and a refused Local Network permission reads as "not on the house wifi"

- **Status.** Inferred, not observed. The simulator does not enforce Local Network privacy.
  New (not in T or U; T-25 is ATS for host names, a different cause).
- **Steps.** On the iPhone: delete R1 Control, install it, type `10.25.113.209`, tap Connect.
  Then, in a second attempt, tap "Don't Allow" on the Local Network alert.
- **Expected.** The app waits for the user's answer, or says it needs Local Network access
  and points to Settings > Privacy & Security > Local Network.
- **Actual (reasoned).** The probe is the app's first LAN connection, so it triggers the
  system alert. My reading of Apple's TN3179 is that a connection opened before the user
  answers does not get through, and the default `URLSession` configuration Ktor's Darwin
  engine uses (`waitsForConnectivity = false`) fails the task instead of waiting. The field
  then says "Nothing answered at 10.25.113.209:8088." under the alert, and only a second tap
  works. After "Don't Allow", every tab says "Could not reach the speaker. Is the phone on
  the house wifi?" while the phone is on the house Wi-Fi, and nothing names the setting
  that fixes it.
- **Evidence.** `iosApp/iosApp/Info.plist` has `NSLocalNetworkUsageDescription` and
  `NSAllowsLocalNetworking`, so the alert is shown. `data/remote/R1Http.kt` (`call`) turns
  every exception into `SpeakerException.Unreachable(e)`. `ui/i18n/Strings.kt` maps
  `Unreachable` to one sentence (`errorUnreachable`). The owner's "too many bugs" began on
  the iPhone, where this is the first thing that happens.
- **Suspected location.** `R1Http.call` (no distinction for a path refused by Local Network
  privacy, reported by `NSURLErrorDomain` -1009 with an unsatisfied path), `FirstRunScreen`
  (no retry once permission is granted), `Strings.describe`.
- **To confirm.** The steps above on the iPhone, watching the field while the alert is up.

### B-02. "Could not reach the speaker" stays after the speaker is back; with nothing playing only killing the app clears it

- **Same as T-03 / U-05.** Runtime adds: (1) a failed *volume* tap sets the banner too, and a
  later *successful* volume write does not clear it; (2) with nothing playing, the Control
  tab offers no command that clears it, so it survives tab switches and backgrounding and
  goes only when the app is killed; (3) its wording is the "unreachable" one, so the user
  reads it as "the speaker is still down".
- **Steps.** Control tab, nothing playing, address through the relay. Cut the relay
  (`reset`), wait for the banner, tap the volume pill at 13% (y≈477; the banner moves the
  pill down). Restore the relay. Wait 15 s. Tap the pill at 0% (succeeds). Switch to Music
  and back. Background for 8 s and return.
- **Expected.** The banner goes on the first good reading after the speaker answers.
- **Actual.** The card updates live ("Nothing playing", 0%) but the banner stays at 2 s, at
  15 s, after the good volume write, after the tab switch and after backgrounding.
- **Evidence.** `behavior-101` to `behavior-103b` (cut, tap), `behavior-104-back-2s`,
  `behavior-105-back-15s`, `behavior-106-banner-cleared-by-volume-0` (still there),
  `behavior-107-banner-after-tab-switch`, `behavior-108-banner-after-background`. R1 truth
  during the cut: `{"volume":0}`. Nothing was received.
- **Suspected location.** `ui/control/ControlViewModel.kt`. `commandProblem` is set by the
  `VolumeSetter` failure callback and by `run`, and cleared only by the next `run`
  (play/pause/next/stop/seek) or `dismissProblem`, which no screen calls. `VolumeSetter` has
  no success callback.

### B-03. A chat message that fails to send while the keyboard is up gives no visible sign

- **New as a runtime result.** It combines U-01 (the keyboard pushes the page under the
  status bar) with the Chat half of T-03 (the send banner).
- **Steps.** Chat tab. Tap the field, type "offline test two". Cut the relay (`reset`), wait
  4 s, press Enter (the send key). Watch for 4 s. Restore the relay, wait 10 s.
- **Expected.** A visible "not sent" next to the field or the message, and the text kept for
  a retry.
- **Actual.** The text silently returns to the field. The error banner is drawn at the top of
  the page, which the keyboard has pushed above the screen, so nothing on screen says the
  send failed. To the user it looks as if Enter did nothing. Pressing Enter again after the
  R1 is back sends it once (`behavior-112`). The R1 received exactly one line, so there is no
  duplicate.
- **Evidence.** `behavior-109-chat-send-while-cut-1s`, `behavior-110-chat-send-while-cut-4s`,
  `behavior-111-chat-back-10s`, `behavior-112-chat-send-after-back-1.5s`. R1 chat afterwards:
  `{"sender":"user","text":"offline test two"}` once.
- **Suspected location.** `ui/chat/ChatScreen.kt` (`ProblemBanner` at the top of the
  column), `ui/App.kt` (the `imePadding` column that pushes the top bar off screen, U-01),
  `ui/chat/ChatViewModel.kt` (`send` puts the text back with no per-message state).

### B-04. Tapping a volume level the app sent before does nothing once the speaker has moved

- **Same as T-01.** Runtime confirms it on the real R1 (run 1). Being fixed (`VolumeSetter.kt`
  changed in the tree during run 2).
- **Steps.** Control. Tap the pill at 7% (the R1 goes to 7). Set the volume to 0 another
  way (`curl`). The pill follows to 0%. Tap 7% again.
- **Expected.** 7 is sent, the R1 goes to 7.
- **Actual.** Nothing is sent. The pill shows 7% for the 1.5 s hold, then 0% again.
- **Evidence.** `behavior-31-volume-tap-7`, `behavior-32-volume-follows-external-0`,
  `behavior-33-volume-retap-not-sent`.
- **Suspected location.** `domain/usecase/VolumeSetter.kt`, `lastSent` (as read at 18:20).

## Minor

### B-05. Audio page keeps showing a setting the write failed to make

- **New.**
- **Steps.** Settings > Audio, EQ on. Cut the relay (`reset`). Flip Equalizer off. Restore the
  relay and wait 5 s.
- **Expected.** The switch goes back to On (the R1's value) with the error, or shows it was
  not applied.
- **Actual.** The switch stays Off, the bands are dimmed, and the banner stays, while the R1
  still has `eq_enabled: true`. The page now tells the user the equaliser is off when it
  is on. The same applies to the loudness and band sliders, which are moved before the write.
- **Evidence.** `behavior-120-eq-toggle-while-cut`, `behavior-121-eq-toggle-after-back-5s`.
  R1 truth after: `{"eq_enabled":true}`.
- **Suspected location.** `ui/settings/AudioViewModel.kt`. `onEqEnabled`, `onLoudness` and
  `onBand` call `update` first, and `attempt` sets only `problem` on failure.

### B-06. Audio and News pages never re-read the speaker, so they show values it no longer has

- **New.**
- **Steps.** (a) Audio: drag loudness to 15 dB. Set `loudness_mb` back to 1000 another way
  (`curl`, the web panel, another phone). Wait. (b) News: save 10:10 / Mai Anh in the app,
  then restore 09:15 / Thiền Tâm Đức another way.
- **Expected.** The open page follows the speaker (as Control, Chat and Bluetooth do), or at
  least on returning to it.
- **Actual.** Audio shows 15 dB eight seconds later while the R1 has 10 dB. News still shows
  10:10 and Mai Anh. A tap on Save from that page would write the stale draft back.
- **Evidence.** `behavior-115-audio-stale-8s-after-external-restore`,
  `behavior-135-news-page-stale-after-external-save` (run 1 saw the same:
  `behavior-66-audio-stale-after-external-change`).
- **Suspected location.** `AudioViewModel` and `NewsViewModel` read `speaker.settings()` once,
  in `init { load() }`. `BluetoothViewModel` polls, these two do not.

### B-07. Unreachable from the start: the card is a blank player with live-looking controls

- **Same as T-13 / U-04.** Runtime adds: the banner comes within 3 s for a refused or
  unanswered host. The card under it draws a play glyph, a seek bar, previous and next, and
  the volume pill says 0%, all from no data.
- **Steps.** Stored address `10.25.113.233:8088` (nothing there). Launch.
- **Evidence.** `behavior-87-dead-from-start-3s`, `behavior-88-…-12s`, `behavior-89-…-22s`.

### B-08. Chat spins for ever when the speaker is unreachable from the start

- **Same as T-14 / U-06.** Runtime: the banner and a spinner that never stops, 15 s after
  opening.
- **Evidence.** `behavior-94-chat-dead-from-start-15s`.

### B-09. Bluetooth page banner stays after the speaker is back

- **Same as T-04.** Runtime: after a cut and restore, the receive countdown keeps updating
  (94 s, then 77 s) under a "Could not reach the speaker" banner that does not clear.
- **Evidence.** `behavior-144-bt-cut-4s`, `behavior-145-bt-back-10s`.

### B-10. A Wi-Fi drop takes 9 to 12 s to show, and meanwhile the card looks live

- **New.**
- **Steps.** Control tab, a song playing. The R1 stops answering (relay `hang`: connections
  accepted, never answered).
- **Expected.** Within a poll or two (about 2 s), the card says the reading is old.
- **Actual.** No change for 9 s (banner area sampled every 1.5 s: unchanged at 3, 6 and 9 s;
  banner at 12 s). The position freezes (0:51) under a pause button and a live-looking seek
  bar, and taps wait the same 10 s before failing. Recovery after the R1 answers again is
  under 3 s.
- **Evidence.** `behavior-154-control-hang-40s`, `behavior-155-control-hang-recovered-3s`.
  Run 1 saw the same: no banner at 8 s, banner at 18 s (`behavior-81`, `behavior-82`).
- **Suspected location.** `data/remote/R1Http.kt`: `requestTimeoutMillis = 10_000` is the only
  signal. The poller has no staleness clock of its own.

### B-11. News edits are lost on Back without a word

- **Same as U-07.** Runtime: Weather turned on, Back, reopen, Weather is off again. No prompt.
- **Evidence.** `behavior-136-news-unsaved-edit`, `behavior-137-news-reopened-edit-lost`.

### B-12. After Stop, or when a song ends, the card says "Listening…" with a stale song line

- **New.**
- **Steps.** Play a song from Music. On Control, tap Stop (or let the last song end).
- **Expected.** "Nothing playing".
- **Actual.** "Listening…" with "Đang tải bài Cho Đời Chút Ơn của Hoàng Yến Chibi" (an old
  assistant line) for many seconds, while nobody spoke to the speaker. It reads as if the
  speaker's microphone were open.
- **Evidence.** `behavior-26-control-after-stop`, and after the queue ended
  `behavior-31` to `behavior-35` (all at 00:30, "Listening…" with "Đang tải bài interlude
  (diễm xưa)…").
- **Cause.** The R1 reports `voice_state: LISTENING` and `voice_awake: true` for a while after
  `stop` (charter §8, "Music outranks the assistant"). `nowPlayingOf` in
  `domain/model/NowPlaying.kt` then shows the assistant with the last chat line, which is
  the R1's own "loading song" line.

### B-13. First run says "Nothing answered" when something did answer

- **Same as T-23.** Runtime: `10.25.113.151:8002` (the TTS server, which answers HTTP) gives
  "Nothing answered at 10.25.113.151:8002."
- **Evidence.** `behavior-07-firstrun-not-r1`.

### B-14. The keyboard stays up after Save and after sending, and cannot be closed

- **Same as U-11 (and U-01 for Chat).** Runtime: Advanced after Save; Chat after a tap outside
  and a swipe down. In Chat the tab bar is gone while the keyboard is up, so the only way out
  found was killing the app.
- **Evidence.** `behavior-80-advanced-saved`, `behavior-98-advanced-relay-saved`,
  `behavior-51-chat-tap-outside`, `behavior-52-chat-swipe-down`.

### B-15. Previous is drawn on every card and never works

- **Same as T-15 / U-14.**
- **Evidence.** `behavior-09-control-first`, `behavior-18-control-playing`.

### B-16. News: the "Saved" line pushes Play a test and Save half off the screen

- **New, cosmetic.**
- **Steps.** Settings > News, scroll to the bottom, tap Save (or Play a test).
- **Actual.** The status line appears above the buttons and pushes them so that only their top
  half shows above the screen edge. The page scrolls, so they can be reached.
- **Evidence.** `behavior-72-news-saved`, `behavior-132-news-saved`,
  `behavior-133-news-saved-scrolled`, `behavior-134-news-test-started`.
- **Suspected location.** `ui/settings/NewsScreen.kt`. The status `Text` is inserted above the
  buttons in a `verticalScroll` column with no scroll-to-keep-in-view.

## Looked at and not filed

- **Telex typing (T-21).** `behavior-42` shows "suwowng trawngs" in the field, but `idb` types
  raw keys past the Vietnamese input method. Not a proof either way.
- **Bluetooth list hides ten unpaired devices.** The R1 reports 12 devices and the app shows the
  two paired speakers. The web panel filters the same way (`audio || bonded`).
- **No "stop scan" control.** The panel has none either. The scan ended on its own after about 57 s.
- **EQ bands move in 0.5 dB steps** where the panel uses 1 dB. The R1 accepts both and the spec
  gives no step.
- **Empty search results** cannot be reached on the real R1: it returned 15 results for
  "qqzxjvkwpyq".
- **`;` typed for `:`** in the address field is an `idb` key-mapping artefact. The app rejects
  it correctly (`behavior-96`).
