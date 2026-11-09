# R1 Control: QA bug report

QA pass of 2026-11-08, code level only: no simulator, nothing sent to the real R1.
Every failing test named here is in the tree and red today; run
`source env.sh && ./gradlew jvmTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
(240 tests, 11 failing, both compiles green). "Needs device" marks a bug read from
the code whose effect only a running app can confirm. Sorted by severity. No blocker
was found.

| ID | Severity | Title | Proof |
|---|---|---|---|
| T-01 | major | Volume drag to a level the app sent earlier is dropped once the speaker moved | failing test |
| T-02 | major | Android keeps polling the R1 in the background | code read, needs device |
| T-03 | major | Control and Chat error banners never clear after the speaker is back | 2 failing tests |
| T-04 | major | Bluetooth page error banner never clears after the speaker is back | failing test |
| T-05 | major | "Phát thử" plays the last saved bulletin, not the one on screen | failing test |
| T-06 | minor | A slow poll puts the old card back after play/pause | failing test |
| T-07 | minor | A slow refused voice undoes a later choice that worked | failing test |
| T-08 | minor | `HTTP://` (capital scheme) rejected as a bad address | failing test |
| T-09 | minor | IP with leading zeros saved verbatim | failing test, needs device for the effect |
| T-10 | minor | A stored recent search with no keywords leaves Music with no field | failing test |
| T-11 | minor | An unusable `tts_host` is reported as "could not reach the speaker" | failing test |
| T-12 | minor | Mini player play/pause failure is silent | code read |
| T-13 | minor | Control card is blank when the speaker is unreachable from the start | code read |
| T-14 | minor | Chat spins for ever when the speaker is unreachable from the start | code read |
| T-15 | minor | Previous button is drawn and never works | code read |
| T-16 | minor | Bluetooth "Try again" does nothing | code read |
| T-17 | minor | "Add a keyword" stays live at six fields and does nothing | code read |
| T-18 | minor | "Selected voice" row is a button that does nothing | code read |
| T-19 | minor | Chat stops following new messages once the R1's log is full | code read, needs device |
| T-20 | minor | Opening the keyboard hides the newest chat messages | code read, needs device |
| T-21 | minor | R1TextField can reset the field under a Telex composition | code read, needs device |
| T-22 | minor | Vietnamese "Độ vang" for loudness means reverb | code read |
| T-23 | minor | Address check says "Nothing answered" when something did | code read |
| T-24 | minor | After a new address is saved, tabs show the old speaker's reading | code read |
| T-25 | minor | iOS blocks plain HTTP to host names like `r1.lan` that the parser accepts | code read, needs device |

## Major

### T-01. Volume drag to a level the app sent earlier is dropped once the speaker moved

- **Steps.** Control tab, music playing. Drag the pill to 60 % and release. Change the
  volume on the R1 another way (voice "tăng âm lượng", the web panel, a news test).
  The card shows the new level, say 20 %. Drag the pill back to 60 % and release.
- **Expected.** 60 % is sent; the speaker plays at 60 %.
- **Actual.** Nothing is sent. The pill shows 60 % for 1.5 s, then snaps back to 20 %.
  With 15 hardware steps there are only 16 possible values, so a repeat is common.
- **Failing test.** `QaControlTest.aLevelSentBeforeIsSentAgainAfterTheSpeakerMovedAway`:
  `the second drag to 60 was never sent expected:<[60, 60]> but was:<[60]>`.
- **Evidence.** `domain/usecase/VolumeSetter.kt:31-33`: `lastSent` lives as long as the
  view model (the process) and a value equal to it is skipped, whatever the speaker
  now reports.
- **Likely cause.** Dedupe against the setter's own last write instead of the speaker's
  current level, never reset.

### T-02. Android keeps polling the R1 in the background

- **Steps.** Android, play music, open Control (or Chat, or Settings > Bluetooth).
  Press Home and leave the phone for ten minutes.
- **Expected.** No requests to the R1 while the app is not visible.
- **Actual (inferred).** `/api/state`, `/api/media/state` and `/api/bt/state` every
  second while music plays (3 s idle), for as long as the process lives; the Bluetooth
  page adds `/api/bt/state` every second.
- **Evidence.** Every screen collects with plain `collectAsState()` (`ui/App.kt:142`,
  `ui/control/ControlScreen.kt:75`, `ui/chat/ChatScreen.kt:57`,
  `ui/settings/BluetoothScreen.kt:66`); nothing in `androidMain` or `ui` reads the
  lifecycle (grep for `Lifecycle` finds nothing). A stopped activity keeps its
  composition, so the collectors stay subscribed and `SpeakerFeed`
  (`domain/usecase/SpeakerFeed.kt:59-71`) keeps polling on `subscriptionCount > 0`.
  `BluetoothViewModel.kt:42-47` loops in the page scope the same way.
- **Needs device.** Confirm with the R1's request count or `adb shell dumpsys netstats`
  with the app in the background.
- **Likely cause.** Collection is not lifecycle-aware (no `collectAsStateWithLifecycle`
  or equivalent stop on `ON_STOP`).

### T-03. Control and Chat error banners never clear after the speaker is back

- **Steps.** Control: lose wifi for a moment, tap Next (fails), wifi back. Chat: send a
  message while the wifi drops, wifi back.
- **Expected.** The banner goes once the speaker answers again; neither banner has a
  close button.
- **Actual.** "Could not reach the speaker. Is the phone on the house wifi?" stays
  while the card and chat update normally, until another command (or send) succeeds.
  A failed volume send (`VolumeSetter` `onFailure`) sets the same sticky banner.
- **Failing tests.** `QaControlTest.aFailedCommandsBannerGoesAwayOnceTheSpeakerAnswersAgain`
  and `QaChatTest.aFailedSendsBannerGoesAwayOnceTheSpeakerAnswersAgain`:
  `30 s of good readings later the banner still says unreachable expected:<None> but was:<Failed(...Unreachable...)>`.
- **Evidence.** `ui/control/ControlViewModel.kt:57,127` set `commandProblem`; it is cleared
  only by `run` of a later command or by `dismissProblem` (`:132`), which no screen calls:
  `ui/control/ControlScreen.kt:101` draws `ProblemBanner` with no action.
  `ui/chat/ChatViewModel.kt:59,63` keep `sendProblem` until the next send;
  `ui/chat/ChatScreen.kt:74` has no action either.
- **Likely cause.** A command's failure is kept as screen state with no way out other
  than another command.

### T-04. Bluetooth page error banner never clears after the speaker is back

- **Steps.** Settings > Bluetooth. Drop the wifi for a second, then restore it.
- **Expected.** The page keeps updating and the banner goes.
- **Actual.** The list updates every second underneath a banner that still says the
  speaker cannot be reached, until some action succeeds.
- **Failing test.** `QaBluetoothTest.aDroppedReadsBannerGoesAwayWhenTheSpeakerAnswersAgain`:
  `five good reads later the page still says unreachable expected:<None> but was:<Failed(...Unreachable...)>`.
- **Evidence.** `ui/settings/BluetoothViewModel.kt:89-90`: a good read sets only
  `bluetooth`; `problem` is set by a failed read at `:96` and cleared only by `act`
  at `:82`.
- **Likely cause.** The read path never clears the problem it sets.

### T-05. "Phát thử" plays the last saved bulletin, not the one on screen

- **Steps.** Settings > News. Turn a topic on (or reorder, or change the voice). Do not
  tap Save. Tap "Phát thử".
- **Expected.** The bulletin read is the one on screen. The reference panel saves
  first: `control.html:985-997`, "testing must reflect what's currently on screen ...
  otherwise 'Phát thử' before ever hitting Save just silently does nothing (the server
  has no config yet for this device)".
- **Actual.** Only `POST /api/news/test` is sent. The old topics are read, or, for a
  device never saved, nothing is read while the screen says "The speaker is reading a
  bulletin."
- **Failing test.** `QaNewsTest.aTestPlaysWhatIsOnScreenSoTheDraftIsSavedFirst`:
  `the edited draft was never saved: [testNews]`.
- **Evidence.** `ui/settings/NewsViewModel.kt:103-106`.
- **Likely cause.** Test was ported as the bare endpoint without the panel's save-first step.

## Minor

### T-06. A slow poll puts the old card back after play/pause

- **Steps.** Music playing. Tap pause while a poll is on its way (1 s cadence).
- **Expected.** The card shows Paused and stays Paused.
- **Actual.** The refresh shows Paused, then the poll that left before the pause lands
  and puts Playing back for up to one poll interval (about 1 s). Window is the poll's
  round trip, so it is rare on a fast LAN and common on a slow one.
- **Failing test.** `QaControlTest.aReadingStartedBeforeACommandDoesNotUndoIt`:
  `the slow poll put Playing back on the card expected:<Paused> but was:<Playing>`.
- **Evidence.** `domain/usecase/SpeakerFeed.kt:68-69` merges poll and refresh results
  and applies them in arrival order, whatever order they were read in.
- **Likely cause.** Readings carry no start order, so an older reading can overwrite a newer one.

### T-07. A slow refused voice undoes a later choice that worked

- **Steps.** Settings > Voice. Tap voice b (the TTS server is slow and refuses it), then
  at once tap voice c (accepted).
- **Expected.** c is checked; the server uses c.
- **Actual.** When b's refusal arrives the check goes back to the original voice a,
  while the server uses c.
- **Failing test.** `QaVoiceTest.aSlowRefusalDoesNotUndoALaterChoiceThatWorked`:
  `the server took c, the screen shows a expected:<[c]> but was:<[a]>`.
- **Evidence.** `ui/settings/VoiceViewModel.kt:59,69`: each choice restores the `before`
  it captured, not the state now.
- **Likely cause.** No "last choice wins" guard on the optimistic update.

### T-08. `HTTP://` (capital scheme) rejected as a bad address

- **Steps.** First run, type or paste `HTTP://192.168.1.20:8088` (or `Http://r1.local`).
- **Expected.** Accepted like `http://`.
- **Actual.** "That is not an address."
- **Failing test.** `QaAddressTest.aCapitalisedSchemeIsStillPlainHttp`:
  `HTTP://192.168.1.20:8088. Expected ... AddressInput$Valid, actual ... AddressInput$Invalid`.
- **Evidence.** `domain/model/R1Address.kt:25` (`removePrefix("http://")` is case
  sensitive), then `:27` rejects anything with `://`.
- **Likely cause.** Case-sensitive scheme strip.

### T-09. IP with leading zeros saved verbatim

- **Steps.** First run, type `192.168.001.020`.
- **Expected.** Read as `192.168.1.20`, or rejected.
- **Actual.** Saved as `192.168.001.020:8088` and sent to the platform as is. iOS
  resolves numeric hosts with `inet_aton`, which reads `020` as octal 16, so the app
  would talk to `192.168.1.16`; Android's stricter `inet_pton` refuses it as a number
  and tries DNS (guess, needs device).
- **Failing test.** `QaAddressTest.leadingZerosInAnIpAreDecimalAndNotPassedOn`:
  `expected:<192.168.1.20:8088> but was:<192.168.001.020:8088>`.
- **Evidence.** `domain/model/R1Address.kt:47-48` checks each label's value but `:41`
  keeps the text.
- **Likely cause.** Validated but not normalised.

### T-10. A stored recent search with no keywords leaves Music with no field

- **Steps.** A history entry with an empty keyword list exists (an older or half-written
  store reads that way: `RecentSearchDto.keywords` defaults to empty). Tap it.
- **Expected.** At least one keyword field remains (spec: "at least one").
- **Actual.** The field list becomes empty; the only way back is "Add a keyword".
- **Failing test.** `QaMusicTest.aRecentWithNoKeywordsLeavesOneFieldToTypeIn`:
  `expected: [""] but was: []`.
- **Evidence.** `ui/music/MusicViewModel.kt:79-81`;
  `data/repository/SettingsPreferencesRepository.kt:16,36-37` lets such an entry through.
- **Likely cause.** `searchAgain` assigns the keywords without the one-field floor.

### T-11. An unusable `tts_host` is reported as "could not reach the speaker"

- **Steps.** The R1 reports a `tts_host` Ktor cannot parse. Open Settings > Voice.
- **Expected.** "The speaker answered with something unreadable" (BadReply, as the code
  intends).
- **Actual.** "Could not reach the speaker. Is the phone on the house wifi?"
- **Failing test.** `QaWireTest.anUnusableTtsHostIsABadReplyNotUnreachable`:
  `got Unreachable. Expected ... SpeakerException$BadReply`.
- **Evidence.** `data/repository/HttpVoiceRepository.kt:40-47` throws `BadReply` inside
  the URL builder lambda, which runs inside `client.request`; `data/remote/R1Http.kt:69-70`
  wraps every exception there as `Unreachable`.
- **Likely cause.** The URL is built inside the guarded request instead of before it.

### T-12. Mini player play/pause failure is silent

- **Steps.** Music tab, mini player showing. Wifi drops. Tap its play/pause.
- **Expected.** A visible failure, as on the Control tab.
- **Actual.** Nothing happens and nothing is said.
- **Evidence.** `ui/music/MiniPlayerViewModel.kt:57-63` drops the `CommandOutcome`;
  `MiniState` has no problem field.
- **Likely cause.** Outcome ignored.

### T-13. Control card is blank when the speaker is unreachable from the start

- **Steps.** Start the app with the R1 off.
- **Expected.** The card says the speaker cannot be reached (or "Nothing playing").
- **Actual.** A banner, then a card with an empty title (`""`), a disabled slider and a
  0 % disabled pill, for as long as it stays offline.
- **Evidence.** `ui/control/ControlViewModel.kt:64` maps `Offline` to
  `ControlState.Initial` (card `Connecting`); `ui/control/ControlScreen.kt:144,182` draws
  `""` for Connecting. Existing `ControlViewModelTest.anUnreachableSpeakerIsABannerAndPollingGoesOn`
  pins the Connecting card.
- **Likely cause.** No offline card state.

### T-14. Chat spins for ever when the speaker is unreachable from the start

- **Steps.** Start the app with the R1 off, open Chat.
- **Expected.** "Still asking" ends in a failure state.
- **Actual.** Banner plus an endless spinner.
- **Evidence.** `ui/chat/ChatViewModel.kt:40` (`Offline` is `loaded = false`);
  `ui/chat/ChatScreen.kt:78` shows `LoadingBox` for `!loaded`.
- **Likely cause.** Offline is folded into "not loaded yet".

### T-15. Previous button is drawn and never works

- **Steps.** Control tab, any card.
- **Expected.** Nothing drawn that is not wired.
- **Actual.** A Previous button, always disabled, with an empty click handler.
- **Evidence.** `ui/control/ControlScreen.kt:189,312,348`.
- **Likely cause.** Copied from the design image; the R1 has no previous command.

### T-16. Bluetooth "Try again" does nothing

- **Steps.** Settings > Bluetooth with the R1 off. Tap "Try again".
- **Expected.** Reads again.
- **Actual.** Nothing (the 1 s poll recovers on its own, so the button is dead weight).
- **Evidence.** `ui/settings/BluetoothScreen.kt:72` passes `{}` as `onRetry`.
- **Likely cause.** No retry wired.

### T-17. "Add a keyword" stays live at six fields and does nothing

- **Steps.** Music tab, add fields up to six, tap "Add a keyword" again.
- **Expected.** Disabled at the cap.
- **Actual.** Looks enabled, does nothing.
- **Evidence.** `ui/music/MusicScreen.kt:124` (no `enabled`), `ui/music/MusicViewModel.kt:61`.
- **Likely cause.** Cap enforced only in the view model.

### T-18. "Selected voice" row is a button that does nothing

- **Steps.** Settings > Voice, tap the row under "Selected voice".
- **Expected.** Not clickable.
- **Actual.** Ripple, nothing happens; announced as a radio button.
- **Evidence.** `ui/settings/VoiceScreen.kt:70` (`onClick = {}`), `VoiceRow` is
  `clickable(role = Role.RadioButton)` at `:88`.
- **Likely cause.** Reused the list row for a label.

### T-19. Chat stops following new messages once the R1's log is full

- **Steps.** Use the speaker until its conversation log holds 50 lines (a day of song
  requests). Keep Chat open at the bottom; a new line arrives.
- **Expected.** The newest message is fully in view.
- **Actual (inferred).** The count stays 50, so the "scroll to the newest" effect never
  runs again; the list keeps its index and the new, possibly taller, last bubble can sit
  half under the input. A reader who scrolled up drifts one message per new line.
- **Evidence.** `ui/chat/ChatScreen.kt:133` keys the effect on `messages.size`;
  `:148` has no item keys; the R1 caps the log at 50
  (`xiaozhi-android/.../domain/voice/ConversationLog.kt:7,15`).
- **Needs device.** Scroll position only shows on screen.
- **Likely cause.** "New message" is detected by count.

### T-20. Opening the keyboard hides the newest chat messages

- **Steps.** Chat at the bottom, tap the field.
- **Expected.** The newest message stays above the keyboard.
- **Actual (inferred).** The list keeps its first visible item while its viewport
  shrinks, so the last messages go under the keyboard until a new one arrives.
- **Evidence.** `ui/chat/ChatScreen.kt:131-139`: pinning reacts only to a size change
  or a user scroll; `ui/App.kt:119` shrinks the content with `imePadding()`.
- **Needs device.**
- **Likely cause.** No re-pin when the viewport changes.

### T-21. R1TextField can reset the field under a Telex composition

- **Steps.** Type fast with a Telex keyboard (`Thanhf Ngaan`) in a Music field or Chat.
- **Expected.** `Thành Ngân`, caret at the end, no lost keystroke.
- **Actual (guess).** When the field recomposes with a `value` older than the latest
  keystroke, the effect sees `value != reported` and replaces the whole
  `TextFieldValue`, dropping the IME composition and moving the caret to the end; the
  next value then restores the text.
- **Evidence.** `ui/components/Components.kt:228-235`. The fields in
  `ui/music/MusicScreen.kt:105` are composed by position without `key`, so removing a
  middle field hands one field's internal state to the next.
- **Needs device.** Composition and timing exist only with a real IME.
- **Likely cause.** External-change detection compares against a value that may be stale.

### T-22. Vietnamese "Độ vang" for loudness means reverb

- **Steps.** Vietnamese phone, Settings > Âm thanh.
- **Expected.** "Độ to", as the panel names it (`control.html:579`).
- **Actual.** "Độ vang" (vang is echo/reverb), a setting this app does not have.
- **Evidence.** `ui/i18n/Strings.kt:342`.
- **Likely cause.** Mistranslation.

### T-23. Address check says "Nothing answered" when something did

- **Steps.** Type the address of a router or another web server (it answers 404 or
  HTML).
- **Expected.** "That is not an R1" (the outcome carries `Refused` or `BadReply`).
- **Actual.** "Nothing answered at X."
- **Evidence.** `ui/address/AddressScreen.kt:87` and `ui/i18n/Strings.kt:294,433` ignore
  `AddressStatus.Unreachable.error`.
- **Likely cause.** One message for three kinds of failure.

### T-24. After a new address is saved, tabs show the old speaker's reading

- **Steps.** Settings > Advanced, save a different R1's address, go back to Control or
  Chat.
- **Expected.** The new speaker's card and chat.
- **Actual.** The old speaker's last reading (presented as live) until the next poll,
  up to 5 s if the old one was offline.
- **Evidence.** `ui/AppViewModel.kt:48-50` changes only the gate; `SpeakerFeed`
  (`domain/usecase/SpeakerFeed.kt:53`) keeps its state and is not refreshed.
- **Likely cause.** Address change does not reset the shared reading.

### T-25. iOS blocks plain HTTP to host names like `r1.lan` that the parser accepts

- **Steps.** iPhone, type a router host name such as `r1.lan` or `r1.home.arpa`.
- **Expected.** Works like the IP, or is refused with a reason.
- **Actual (guess).** `NSAllowsLocalNetworking` exempts IP addresses, `.local` and
  unqualified names only, so App Transport Security blocks the request and the screen
  says "Nothing answered".
- **Evidence.** `iosApp/iosApp/Info.plist` (`NSAppTransportSecurity` ->
  `NSAllowsLocalNetworking`); `domain/model/R1Address.kt:50-54` accepts any dotted name.
- **Needs device.**
- **Likely cause.** ATS exception narrower than the accepted address forms.

## Checked and not a bug

- Loudness truncation `(mb / 100) * 100` (`AudioViewModel.kt:54`): every tick the 0..2000
  slider produces is an exact multiple of 100 in float (checked in `jshell` for all 21
  ticks), so no wrong step reaches the R1.
- `queueAround` drops the songs above the tapped one in a long list; the panel does the
  same (`control.html:1556-1564`), so it matches the reference.
- Bluetooth scan never stopped by the app: Android's discovery ends itself.
- Empty `news_categories`: the R1 always reports its default five
  (`xiaozhi-android/.../data/Settings.kt:182-185`).
