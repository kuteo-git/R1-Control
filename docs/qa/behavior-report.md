# R1 Control: runtime behaviour report

QA of the running app against the real Phicomm R1 (`10.25.113.209:8088`), on the iPhone 16e
simulator (`C6825263-6297-40D0-A994-72E4AFC851E7`), build from derived data
`/Volumes/Data2/dev/dd-r1control-qa-behavior` (pre-fix code). Bugs are filed in
`bug-report-behavior.md` as B-##.

Two runs produced this report.

- **Run 1** (2026-11-08 00:16 to 00:44, screenshots `behavior-01` to `behavior-86`). It stopped
  on a rate limit and left no notes. Its results below are read from the screenshots, and
  where a screenshot cannot prove a result the row says so.
- **Run 2** (2026-11-08 18:17 to 18:49, screenshots `behavior-87` to `behavior-155`). The
  coordinator stopped it at 18:49 because the household started listening to music on the R1.
  Areas it could not finish are marked "not run".

Screenshots live in `docs/qa/screens/`. The file name says what each one shows. Times in
file names are seconds after the action.

## How run 2 tested

- **R1 truth** was read with `curl` (`/api/media/state`, `/api/bt/state`, and only the needed
  keys of `/api/state` through `jq`; the TTS server's `GET /voice`).
- **Taps** went through `idb ui tap` at points read from a 390-pt-wide copy of each
  screenshot. The accessibility tree of this Compose scene is empty, so nothing could be
  found by label.
- **Unreachable on demand.** A small TCP relay on the Mac (`10.25.113.151:18088` to
  `10.25.113.209:8088`) was saved as the app's address. The relay could forward, reset
  every connection (`reset`, a refused or unreachable speaker), accept and never answer
  (`hang`, a Wi-Fi drop), or delay each new connection (`slow:6`). The R1 itself was never
  disconnected. The first version only applied its mode to new connections, and the app
  keeps one keep-alive connection, so one volume tap meant to fail reached the R1 (see
  "Incidents"). From 18:28:51 on, the relay dropped live connections on every mode change.
- **Latency** comes from screenshots taken at known offsets, from `curl` sampled every
  250 ms, or from pixel sampling of the banner area every 1.5 s.

## Results by area

| Area | What was done | What the app showed | R1 truth | Latency | Result | Evidence |
|---|---|---|---|---|---|---|
| First run, empty | Connect with an empty field | "Type the speaker's address." | n/a | at once | pass | 01, 02 |
| First run, bad text | `10.25.113.300` | "That is not an address. Use a form like 192.168.1.20." | n/a | at once | pass | 03, 04 |
| First run, dead host | `10.25.113.233` | "Checking…", then "Nothing answered at 10.25.113.233:8088." | nothing at that IP | a few s | pass | 05, 06 |
| First run, not an R1 | `10.25.113.151:8002` (the TTS server) | "Nothing answered at …" although a server answered | TTS server answers HTTP | a few s | fail (B-13, same as T-23) | 07 |
| First run, real R1 | `10.25.113.209` | Control with "Nothing playing", 0% | idle, volume 0 | about 1 s | pass | 08, 09 |
| Relaunch | kill, launch | card filled by 0.4 s | idle | 0.4 s | pass | 10, 11 |
| Address kept over kill | kill and relaunch (run 2, twice) | header and data from the stored address (`.233` at 18:21, the relay at 18:33) | stored in `r1.address` | at once | pass | 87, 113 |
| Music, one keyword | "trinh cong son", Search | spinner, then 15 mixed results | `/api/media/search` | 1 to 2 s | pass | 12 to 15 |
| Music, tap a row | tap "Cho Đời Chút Ơn" | no visible change for 1 s, then mini player | `downloading` then `playing` | about 1 s to the mini player | pass, but no feedback (U-10) | 16, 17 |
| Control, playing | open Control | cover, title, artist, seek bar, pause, next, stop | `playing` | at once | pass | 18 |
| Seek | drag the seek bar, tap the seek bar | position moved to the finger | `position_s` followed | under 1 s | pass | 19, 20 |
| Song end / getting ready | next song downloading | "Getting ready… 0%" with a bar | `downloading` | at once | pass | 21 |
| Pause, resume | pause, then play | play glyph, then pause glyph; position kept | `paused`, `playing` | under 1 s | pass | 22, 23 |
| Next | tap next | next song, 0:00 | queue advanced | under 1 s | pass | 24, 25 |
| Stop | tap stop | "Listening…" with a stale "Đang tải bài …" line, not "Nothing playing" | `stopped`, `voice_state` LISTENING | at once | fail (B-12) | 26 |
| Last in queue | play the last result | next glyph still drawn | queue has no next | n/a | not proven: the screenshot cannot show whether next is disabled | 27, 28 |
| Foreground (Control) | background, return | position resumes, 0:29 then 0:32 | playing | 0.3 s | pass | 29, 30 |
| Volume tap | tap at 7% | 7% | `volume` 7 | under 0.5 s | pass | 31 |
| Volume, outside change | volume set to 0 with `curl` | pill followed to 0% | 0 | within one poll | pass | 32 |
| Volume, tap the same level again | tap 7% again after the outside change | pill shows 0% again, nothing sent | stays 0 | n/a | fail (B-04, same as T-01) | 33 |
| Volume drag | drag to 13%, back to 0 | 13%, then 0% | followed | under 0.5 s | pass | 34, 35 |
| Music, several keywords | three fields, "trinh cong son", "khanh ly" | interleaved results, duplicates dropped | two searches | 1 to 2 s | pass | 36 to 39 |
| Recent searches | clear results | recent list, newest first, de-duplicated | phone only | at once | pass | 40, 45 |
| Removed fields | remove keyword fields | fields gone | n/a | at once | pass | 41 |
| Vietnamese typing | Telex keys typed by `idb` | raw "suwowng trawngs" in the field | n/a | n/a | inconclusive: `idb` sends raw keys past the Telex input method, so this neither proves nor clears T-21 | 42 |
| Vietnamese search | "Sương trắng" | correct results, correct recent entry | UTF-8 kept | 1 to 2 s | pass | 43, 44 |
| No results | "7=3" (run 1), "qqzxjvkwpyq" (run 2) | results both times | the R1 returned 15 results for nonsense | n/a | not testable: YouTube returns something for any query | 46, 146 |
| Search, then switch tab while loading | relay `slow:6`, search "son tung", go to Control, come back after 8 s | results waiting on return | search answered | 6 s (relay) | pass | 147 to 149 |
| Chat open | open the tab | history, newest at the bottom | `chat` list | at once | pass | 47, 48 |
| Chat, keyboard | tap the field | page pushed under the status bar, newest lines hidden | n/a | n/a | fail (same as U-01, T-20) | 49 |
| Chat, send | "what time is it", "thanks" | bubble, field cleared, answer arrives | user line within 1 s, answer within seconds | about 1 s | pass | 49 to 54 |
| Chat, close the keyboard | tap outside, swipe down | keyboard stays | n/a | n/a | fail (same as U-11, U-01) | 51, 52 |
| Chat, scrolled up | scroll up, new lines arrive | stays where the reader is | n/a | n/a | pass | 55, 56 |
| Chat, blank | spaces only | send disabled | nothing sent | n/a | pass | 57 |
| Chat, long | a 300-character line | one-line field scrolls sideways; bubble wraps; reply "Được" | received whole | about 1 s | pass | 58, 59 |
| Chat, foreground | background 20 s, return | list caught up, pinned to the bottom | new lines | 3 s | pass | 151, 152 |
| Settings home | gear | five rows | n/a | at once | pass | 60 |
| Audio, values | open | 10 dB, EQ on, +10 / 0 / -6 / 0 / +8 dB | `loudness_mb` 1000, `eq_bands` 1000,0,-600,0,800 | under 1.5 s | pass | 61, 113 |
| Audio, loudness drag | drag to 15 dB | 15 dB | 1500 (sampled every 250 ms) | written within 1 s of release | pass | 62, 63, 114 |
| Audio, outside change | `loudness_mb` set to 1000 with `curl` while the page is open | still 15 dB after 8 s | 1000 | never | fail (B-06) | 66, 115 |
| Audio, EQ switch | off, on | switch follows | `eq_enabled` false, then true | under 250 ms | pass | 64, 116, 117 |
| Audio, band drag | 3.6 kHz to +5 dB | +5 dB | `eq_bands[3]` 500 | under 1 s | pass | 65, 118 |
| Audio, reopen after restore | back, open | original values | restored | under 1.5 s | pass | 119 |
| Audio, write while unreachable | relay `reset`, flip EQ off | switch off, banner; after the R1 is back the switch still says off | `eq_enabled` true all along | n/a | fail (B-05) | 120, 121 |
| Voice list | open | "Trúc Ly Nam" checked, 27 voices | `GET /voice` "Trúc Ly Nam" | under 1 s | pass | 122, 123, 126 |
| Voice, choose | Mai Anh, Trúc Ly, Trúc Ly Nam | check mark moves | the server followed each, exactly | under 1 s | pass | 124, 125, 127 |
| News, values | open | off, 09:15, Thiền Tâm Đức, topics T/T/F/F/T in panel order | same | under 2 s | pass | 70, 128 |
| News, edit and Save | hour up, minute down, Mai Anh, Weather on, Power cut moved up, Save | "Saved" | `news_time` 10:10, `news_voice` Mai Anh, order tech/world/society/power/weather, weather on | under 1 s | pass | 129 to 133 |
| News, buttons after Save | look at the page after Save | the "Saved" line pushes the two buttons half off the bottom until the page is scrolled | n/a | n/a | fail, cosmetic (B-16) | 72, 132, 133 |
| News, Play a test | at volume 0 | "The speaker is reading a bulletin." | `voice_state` SPEAKING within 1 s | under 1 s | pass | 73, 134 |
| News, outside change | original settings restored with `curl` while the page is open | page still shows 10:10 and Mai Anh | 09:15, Thiền Tâm Đức | never | fail (B-06) | 135 |
| News, unsaved edit and Back | Weather on, Back, reopen | edit gone, no warning | weather off | n/a | fail (B-11, same as U-07) | 136, 137 |
| Bluetooth, values | open | on, nothing connected, auto-reconnect on, two paired speakers | same; ten unpaired non-audio devices hidden, as the web panel does | under 2 s | pass | 74, 138 |
| Bluetooth, scan | Scan for devices | "Looking for devices…", disabled | `scanning` true within 1 s, false after about 57 s | under 1 s | pass | 75, 139 to 141 |
| Bluetooth, receive | Receive from phone | "Search for Phicomm_R1_2E2B on your phone… 118 s left", counting down | `receive_seconds_left` 119, then 107 | matches within 1 s | pass | 76, 142, 143 |
| Bluetooth, R1 back after a drop | relay `reset`, then `up` | banner stays 10 s after the R1 is back, countdown live | answering | never cleared | fail (B-09, same as T-04) | 144, 145 |
| Bluetooth, foreground | background 15 s, return | same page, same state | same | 1 s | pass | 153 |
| Advanced, page | open | address, Save, Restart, version 0.1.0 | n/a | at once | pass | 77 |
| Advanced, dead IP | `10.25.113.233`, Save | "Checking…", then "Nothing answered at 10.25.113.233:8088." The old address stays | nothing saved | 3 to 5 s (run 2), about 11 s (run 1) | pass | 78, 79 |
| Advanced, `;` for `:` | `10.25.113.151;18088` | "That is not an address." | n/a | at once | pass | 96 |
| Advanced, new address | the relay address, Save | "Saved"; Control on the new address within 0.5 s, banner gone | answering | 0.5 s | pass (T-24 did not show, because the old address was offline) | 97 to 100 |
| Advanced, keyboard | after Save | keyboard stays up | n/a | n/a | fail (same as U-11) | 80, 98 |
| Advanced, back to `10.25.113.209:8088` | | | | | not run: stopped by the coordinator. The stored address was set back to `10.25.113.209:8088` with `simctl spawn defaults write` (no UI) | n/a |
| Unreachable from the start | stored address `.233`, launch | banner within 3 s; blank card with play glyph, seek bar, prev and next; 0% | nothing answers | 3 s | fail (B-07, same as T-13, U-04) | 87 to 89 |
| Every tab and page, unreachable from the start | Chat, Music, Audio, Voice, News, Bluetooth | Chat: banner over a spinner that never ends. Music: no sign until a search. Settings pages: "Could not reach the speaker" + Try again | | Chat never settles | Chat fails (B-08, same as T-14, U-06); the rest pass | 90 to 95 |
| R1 unreachable mid-use, refused | relay `reset` on Control | banner within 1 s, card kept | | 1 s | pass | 101, 102 |
| Command while unreachable | volume tap at 13% | 13% for about 1.5 s, then 0%; nothing reached the R1 | 0 | n/a | pass for the send; see the next row | 103, 103b |
| Banner after the R1 is back | relay `up` | "Could not reach the speaker" still there at 2 s, 15 s, after a good volume write, after a tab switch, after backgrounding | answering | never | fail (B-02, same as T-03, U-05) | 104 to 108 |
| Chat send while unreachable | keyboard up, relay `reset`, Enter | text silently back in the field; the banner is above the screen | nothing received | n/a | fail (B-03) | 109 to 111 |
| Chat send after the R1 is back | Enter | sent once, field cleared | one "offline test two" line, answered | 1.5 s | pass | 112 |
| R1 unreachable mid-use, no answer | relay `hang` on Control with a song playing | card frozen at 0:51, live-looking controls, banner only after 9 to 12 s | answering through another path | 9 to 12 s | fail, minor (B-10) | 154 |
| Recovery from no answer | relay `up` | banner gone, card at 1:42 | playing | under 3 s | pass | 155 |
| Background and foreground | Control (run 1), Music, Chat, Bluetooth (run 2) | state kept, data caught up | | 0.3 to 3 s | pass | 29, 30, 150 to 153 |
| Rotation | | | | | not run. `Info.plist` allows portrait only, so the app does not rotate on iPhone | n/a |

## What only the real iPhone can show

The simulator does not enforce Local Network privacy, does not suspend networking the way a
locked phone does, and was never on a different Wi-Fi. None of the items below were observed.
Each is reasoning from the build settings and from Apple's documented behaviour.

- **Local Network permission on a fresh install (likely to fail; filed as B-01).** iOS 14+
  shows "R1 Control would like to find and connect to devices on your local network" the first
  time the app opens a connection to a LAN address. `Info.plist` carries
  `NSLocalNetworkUsageDescription`, so the prompt appears. That first connection is the
  first-run "Connect" probe (`GET /api/state`). My understanding of Apple's TN3179 is that a
  connection made before the user answers does not get through: the default `URLSession`
  configuration (`waitsForConnectivity = false`, which Ktor's Darwin engine uses) fails the task
  instead of waiting. If so, the owner's first tap on Connect ends in "Nothing answered at
  10.25.113.209:8088." while the alert is still on screen, and only a second tap works. If the
  owner taps "Don't Allow", every request fails the same way. Every screen then says "Could
  not reach the speaker. Is the phone on the house wifi?", which is wrong, and nothing points
  to Settings > Privacy & Security > Local Network. The cause is that `R1Http.call` turns
  every exception into `SpeakerException.Unreachable` and `Strings.describe` has one message
  for it. **To check on the iPhone:** delete the app, reinstall, type the address, tap
  Connect once, and watch what the field says while the alert is up.
- **ATS.** `NSAllowsLocalNetworking` is set. Plain HTTP to an IP literal works, which is how
  the owner connects. A host name such as `r1.lan` would be blocked (T-25). No new issue.
- **Background.** `Info.plist` has no background modes. The app is suspended once
  backgrounded, polling stops, and on return the next poll runs. On the simulator this
  showed no error flash (150 to 152). On a phone, sockets kept alive across a suspension
  are often dead on return. `URLSession` usually retries an idempotent `GET` once on a lost
  connection, so a short banner flash on return is possible but unproven.
- **Different Wi-Fi or mobile data.** This behaves like B-02 and B-07. The banner wording fits.
- **Orientation.** Portrait only. `TARGETED_DEVICE_FAMILY` is `1,2` (iPad too) with portrait
  only and no `UIRequiresFullScreen`. That doesn't matter for the owner's iPhone.

## Incidents during run 2 (the R1 was touched in ways not planned)

- **18:27:50, volume 7 for about 37 s.** The first relay applied `reset` only to new
  connections. The app's keep-alive connection carried a volume tap meant to fail, so the R1
  went from 0 to 7. Nothing was playing and the assistant was idle, so no sound came out. I
  set the volume back to 0 at about 18:28:27 and rewrote the relay to drop live connections.
- **18:28 to 18:45, a volume watchdog.** A loop set the volume to 0 whenever it read
  anything else. Between 18:44:05 and 18:44:26 it reset someone else's changes nine times
  (13, 20, 27, 33). Those were not from this app, which was not touched at those moments. At
  the same time `VolumeSetter.kt` changed in the working tree, which suggests a fixer agent
  testing volume on the real R1 (a guess). The coordinator stopped the watchdog.
- **18:39:02 to about 18:46:00, the news test bulletin.** "Play a test" started at volume 0
  (checked first). The bulletin ran about seven minutes. While the other party raised the
  volume (up to 33 at 18:45:4x), the bulletin was audible in the house. I set the volume to
  0 once at 18:45:49. The bulletin ended at about 18:46:00. This is the "news test speaks"
  risk the charter notes.
- **18:46 on, the household's music.** A song started that neither this run nor the app
  started ("Cẩm Ly | Thương Áo Bà Ba", then "Liên Khúc Rumba"), at 27 and later 40. On the
  coordinator's instruction, nothing more was sent to the R1 after 18:48:40.
- **Working tree changed during the run.** Other agents edited
  `/Volumes/Data2/git/r1-control` in place (for example `VolumeSetter.kt`, and a new
  `CommandProblem` in `ControlViewModel.kt` and `ChatViewModel.kt`). The app under test was
  built before that. Code locations in `bug-report-behavior.md` refer to the code as read at
  18:20.

## R1 values changed in run 2, before and after

| Value | Before | Changed to | Restored | Final (GET, 23:11) |
|---|---|---|---|---|
| `volume` | 20 | 0 (18:18); 7 by accident (18:27:50 to about 18:28:27) | not restored to 20, on the coordinator's order ("40 is their own level") | 40 (household) |
| `loudness_mb` | 1000 | 1500 (app drag, 18:34:08) | 1000 (`curl`, 18:34:36) | 1000 |
| `eq_enabled` | true | false (18:35:03) | true (app, 18:35:06) | true |
| `eq_bands` | 1000,0,-600,0,800 | 1000,0,-600,500,800 (18:35:18) | 1000,0,-600,0,800 (`curl`, 18:35:2x) | 1000,0,-600,0,800 |
| TTS voice (`GET :8002/voice`) | Trúc Ly Nam | Mai Anh, then Trúc Ly | Trúc Ly Nam (app, 18:37:10) | Trúc Ly Nam |
| News | off, 09:15, Thiền Tâm Đức, tech T / world T / society F / weather F / power T | off, 10:10, Mai Anh, tech T / world T / society F / power T / weather T (18:38:24) | identical (`POST /api/news/save` with the recorded body, checked with `diff`, 18:39:3x) | identical to before |
| Chat | | one line, "offline test two" (18:33:04), answered aloud at volume 0 | n/a | n/a |
| Bluetooth | on, auto on, nothing connected | one scan (18:40:47, about 57 s), one receive window (18:42:12, 120 s). No pair, connect, disconnect or forget | both ended on their own | Kitchen speaker connected by the household; the device list now holds only that entry |
| Media | idle | nothing played by run 2 | n/a | playing "Liên Khúc Rumba" (household), left alone |

The TTS voice was "Adam bựa" in run 1's screenshots (67, 68, at 00:39). Run 1 chose
"Trúc Ly" (69). At 18:17 it was "Trúc Ly Nam". Who changed it in between is not known.

## App state left on the simulator

- Stored address `r1.address` = `10.25.113.209:8088`, written with
  `xcrun simctl spawn … defaults write` after the coordinator stopped UI driving. Run 1 had
  left `10.25.113.233:8088` there, written the same way.
- The relay is stopped.
- The app is still running on Settings > Advanced with `10.25.113.233` typed and "Nothing
  answered". It reads the address from settings on every request, so it talks to the R1
  again.
- Two recent searches were added: "son tung" and "qqzxjvkwpyq".
