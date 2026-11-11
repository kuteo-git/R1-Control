# R1 Control: spec

The owner wants a phone app (iPhone and Android) that controls the household's Phicomm R1
smart speaker the way the Google Home app controls a Nest speaker: see what is playing and
control it, turn the volume with a swipe, find music and play it on the speaker, and reach the
speaker's settings behind a gear. Today the only control surface is a web panel served by the R1
itself (`http://<r1>:8088/`, source: `/Volumes/Data2/git/xiaozhi-android/app/src/main/assets/control.html`
and `.../control/ControlServer.kt`). That panel is the reference for every API call and every
setting; this app is not a clone of it, it is the everyday remote.

Design references are in `docs/design/` (Google Home speaker page idle and playing, Google Home
device settings, YouTube Music list, Apple Music browse). Dark, Google Home's look: near-black
ground, large rounded cards, a pill volume bar whose fill is the level.

## Decisions already made (do not reopen)

- Kotlin Multiplatform + Compose Multiplatform, Android + iOS. Same toolchain and version set as
  `/Volumes/Data2/mytube-app` (read its `gradle/libs.versions.toml` and CLAUDE.md §4 before
  changing any version; `source /Volumes/Data2/mytube-app/env.sh` style env file).
- Clean architecture by dependency direction, as mytube's CLAUDE.md §3: `domain` imports no Ktor,
  serialization or Compose; DTOs live in `data`; no nullable in domain or ViewModels; no DI
  framework (one composition root); `ArchitectureGuardTest` in jvmTest.
- Interface language: Vietnamese and English through a `Strings` interface (mytube CLAUDE.md §7). A
  missing translation must not compile. It follows the phone by default, and Settings, Language lets
  the owner choose Tiếng Việt or English instead (stored on the phone, applied at once).
- **A news bulletin is not distinguished from a conversation.** The R1 reports both as
  `voice_state = SPEAKING`. The card shows one "assistant is speaking" state for both.
- Music "mix" menu is later. MVP mixes multi-keyword results by interleaving.

## Screens

Top bar like Google Home: speaker name (from `/api/bt/state.adapter_name`? no: use a fixed
"R1" unless a better name exists; show the R1 address under it in small text), a gear that opens
Settings.

### Tab 1: Control

1. A media card at the top (Google Home's). It shows, by priority:
   - **Bluetooth in**: a device is streaming into the R1 (`bt.incoming != ""`): device name, a
     Bluetooth glyph. No seek or next (the R1 cannot control the phone).
   - **Music**: `media.state` is `playing`, `paused` or `downloading`: cover (`cover_url`),
     title, artist, a seek bar (`position_s`/`duration_s`, drag to seek, `/api/media/seek`),
     play/pause, next (only when the playing list, or else the speaker's queue, has a next song: one
     decision for both cards, see "Lists and playlists"), stop. Downloading shows progress
     (`download_percent`). If the R1 is playing out to a Bluetooth speaker
     (`bt.connected != ""`), say "on <speaker name>".
   - **Assistant**: `voice_state` is `LISTENING` or `SPEAKING` (and `voice_awake`): "Listening…"
     or "Speaking", with the last chat line (`state.chat`, newest last).
   - **Nothing playing** (image `control-idle.png`).
   The decision is one pure function over the three polled states, tested from fixtures.
2. Below it the volume pill: drag horizontally anywhere on it to set the level (like Google
   Home), tap +/- not required. Shows the percent. The R1 has `volume_steps` (15) hardware steps;
   snap the shown/sent value to a step exactly like the panel: `pct -> idx = round(pct*steps/100)
   -> pct = round(idx*100/steps)`. Send `/api/set?key=volume&value=<pct>` throttled while
   dragging and once on release; the last value sent must be the release value.

3. Under the pill, only when the R1 reports `wake_enabled` in `/api/state` (a build with both new
   routes below; an older build has neither, and nothing is drawn):
   - **Wake word** (Từ đánh thức), a switch with a one-line subtitle: `POST /api/wake/enable?on=1|0`.
     When off, the hardware button, typed text, Play and the news alarm still wake the R1. The
     switch shows the finger's value until the R1 has caught up, and snaps back if it refuses.
   - **End the conversation** (Kết thúc trò chuyện), a button: `POST /api/voice/sleep`. It is drawn
     only in a voice session (the Assistant card, or listening beside a stopped player), **never
     while music plays or Bluetooth is in**, and not over a stale reading (music may have started
     since): the R1's sleep path flushes playback. After a press it reads "Ending…" (Đang kết
     thúc…), not pressable, for up to 4 s, because the R1 answers before it has ended the session;
     it goes as soon as a reading shows the session closed.

### Tab 2: Music

1. A multi-keyword search in **one text field**: keywords are separated by a comma (the
   full-width "，" a Vietnamese keyboard types is accepted too), trimmed, blanks dropped. Each
   keyword is searched separately (`/api/media/search?q=`, 15 results each), in
   parallel; the results are **interleaved** (first of each, then second of each, ...) with
   duplicates (same `video_id`) dropped, keeping the first occurrence. Pure functions, tested
   (`keywordsIn`, `mixInterleaved`).
2. Below: the mixed result list (YouTube Music row style: cover, title, artist, duration). Tap a
   row: play the whole list on the R1 starting at that row. A "Play all" button. The row of the
   song on the speaker (playing or paused, matched by `video_id`) is lit in the theme's accent
   (tinted row, accent title) as well as wearing the speaker glyph on its cover.
3. Recent searches (like YouTube Music's history): each finished multi-keyword search is saved on
   the phone (keyword set + time), newest first, de-duplicated by keyword set, capped (10; a longer
   stored list is cut when it is read). Tap
   one to run it again: it fills the field as "a, b, c" and runs. Stored with the app's local
   settings as the keyword list.

4. **Lists and playlists**, kept on the phone. Every list of songs (the results, a playlist) is edited
   the same way, as in mytube's cast queue: a handle at the start of a row drags it (the list
   scrolls under a row held near an edge), an X at the end removes it (disabled at the last song),
   and a row's add button puts it into a playlist. A song is in a list once: adding one that is
   already there does nothing. Edits are shown at once and sent to the speaker as **what follows
   the song playing** (`/api/media/queue`), which it takes when that song ends: the song playing is
   never interrupted, and a removed song is not played when the one before it ends on its own. An R1
   built before that call refuses it quietly. Next re-sends the edited list from the next song
   (`/api/media/play`) when the speaker's own queue would go somewhere else, and uses the speaker's
   `/api/media/next` when it would already go the right way; the app's one Next, `MusicStore.next`,
   is what both cards press.
   - A shelf of playlists above the recent searches: at most five cards, a trailing "New playlist"
     card, and "More" (top right) to the **All playlists** page when there are more than five.
   - A **playlist's page**: songs (editable, no add button), a prominent **Shuffle** (plays the
     playlist in a random order from its first song and never rewrites the saved order), Play all,
     rename and delete (asking first). It says "Playing from this playlist" only while the speaker
     plays a song sent from it. An empty one has **Find songs**, to the Music tab's search with the
     field focused. The Music pages keep the mini player.
   - "Add all" on the results, and a sheet on each row, add songs to a playlist or start a new one.
   - Playlists are stored as JSON on the phone. A stored value that does not read is copied to
     `music.playlists.backup.<time>` before anything is saved (once: the same value is not copied
     again at the next start), and the shelf says so; it is never silently replaced by an empty
     list. If the copy fails the playlists are locked: no add, "Add all" or "New playlist" is
     drawn, and one line under the results says why.
   - A screen reader moves a row with "Move up" and "Move down" on its handle.

**Mini player** (Music and Chat tabs, and the Music pages, while music plays): cover, title, artist and
`position / duration` (`1:23 / 4:05`), play/pause, and Next, drawn when the Control card's is.
Tapping it, or the queue button on the Control card, opens the **Now playing** page: the songs playing (the
phone's list in its playing order, or the speaker's own queue read only), the current one lit; a tapped row plays
from there in the same order. Settings pages open on the values of the shared reading, with no loading frame.

### Tab 3: Chat

Like the web panel's chat drawer: the conversation with the speaker as bubbles (user right,
assistant left, time under each), newest at the bottom, a text field and a send button at the
bottom. The list is `/api/state.chat` (`[{sender: "user"|"assistant", text, time(epoch ms)}]`,
the R1 keeps only the recent ones), polled while the tab is open; keep the list pinned to the
bottom unless the user has scrolled up. Opening the tab focuses the field (the keyboard is up as
the tab arrives); a hide-keyboard button sits beside send while the field has focus, and
dragging the list closes the keyboard. Sending is `POST /api/say?text=<text>` (trimmed, blank
ignored, field cleared on send); the speaker answers aloud and the answer arrives through the
next polls. Empty state: "no messages yet, message the speaker below". Spoken conversations and
typed ones share the same list.

### Settings (gear)

A Google Home-style list (image `settings.png`), titled "Settings", with no header block: each
row shows its current value. It leads to:
- **Âm thanh / Audio**: loudness slider `loudness_mb` 0..2000 step 100 (shown as dB, value/100),
  equalizer switch `eq_enabled`, five band sliders (`eq.freqs`, range `eq.min..eq.max`,
  `eq_bands` comma-joined).
- **Giọng đọc / Voice**: the TTS voice list from `<tts_host>/voice` (GET returns
  `{voice, voices[]}`), set with `POST <tts_host>/voice?name=<name>`. `tts_host` comes from
  `/api/state`.
- **Bản tin / News**: enabled, time (HH:mm), voice (same list), categories ordered and toggled
  (`news_categories`, keys tech/world/society/weather/power; labels in the panel's NEWS_LABELS),
  saved together with `POST /api/news/save` body `{enabled,time,voice,categories:[{key,enabled}]}`.
  Categories are reordered by dragging their handle (one save on drop; no delete: the five keys are
  fixed);
  "Phát thử" = `POST /api/news/test`.
- **Bluetooth**: a menu (`/api/bt/*`) with the adapter switch (`/api/bt/enable`) and two
  sub-pages, each showing only what its task needs:
  - **Phát ra loa Bluetooth / Play to a Bluetooth speaker** (the R1 plays out to a speaker):
    status, scan, the speakers (pair / connect / disconnect / forget), auto-reconnect.
  - **Nhận nhạc từ điện thoại / Play from your phone** (the R1 receives): "let a phone connect"
    (`/api/bt/receive`, countdown `receive_seconds_left`), who is streaming in, the paired phones
    (forget).
  A device is a speaker or a phone/source, never both (`outputDevices` / `inputDevices`).
- **Ngôn ngữ / Language**: Follow the phone (default), Tiếng Việt, English; each in its own words.
- **Nâng cao / Advanced**: the R1 address typed as `ip:port` (default port 8088), like mytube's
  server address entry: validate, test with `/api/state`, save on the phone. Restart the R1 app:
  `POST /api/restart` behind a confirmation.

## The R1 API (read from ControlServer.kt and measured on the device, 2026-11-07)

Plain HTTP on the LAN, no auth. Every call answers JSON. Writes are POST.

- `GET /api/state`: many keys; this app reads `volume`, `volume_steps`, `eq_enabled`,
  `eq_bands`, `eq{freqs,min,max}`, `loudness_mb`, `voice_state` (IDLE/LISTENING/SPEAKING/...),
  `voice_awake`, `chat[{sender,text,time}]`, `news_*`, `tts_host`. It also carries secrets
  (masked keys) and the household's Home Assistant entity list: never log or show the raw body.
- `GET /api/media/state`: `{state: idle|downloading|playing|paused|stopped, video_id, title,
  artist, cover_url, duration_s, position_s, download_percent, queue[{video_id,title,artist,
  thumbnail,duration}]}`.
- `GET /api/bt/state`: see fixture; `incoming` is the address streaming in, `connected` the
  speaker the R1 plays out to, `devices[]` with `streaming_in`, `connected`, `bonded`, `audio`,
  `phone`.
- `POST /api/set?key=<k>&value=<v>`: answers `{ok}`.
- `POST /api/media/search?q=`: `{ok, results[{video_id,title,artist,duration,thumbnail}]}` or
  `{ok:false,error}`.
- `POST /api/media/play` body `{items:[{video_id,title,artist,duration,thumbnail}], start_index}`.
  **Measured: the body MUST be sent with `Content-Type: application/json; charset=utf-8`.**
  Without the charset the R1 (NanoHTTPD) decodes it as ASCII and every Vietnamese title becomes
  `Th��nh T��m S��m H���i` on the speaker and in the assistant's spoken line. A test must pin the
  header.
- `POST /api/media/seek?position_s=<int>`, `/api/media/next`, `/pause`, `/resume`, `/stop`.
- `GET /api/state` also carries `wake_enabled` (round 3 build only; absent on older builds).
  `POST /api/voice/sleep` ends the voice session and `POST /api/wake/enable?on=1|0` sets the wake
  word; the R1 reads anything but `on=1` as off. Neither is on the real R1 yet: no captured fixture
  has `wake_enabled`, so its "present" test body is `state_idle.json` plus the key, a named exception.
- `POST /api/news/save`, `POST /api/news/test`, `POST /api/restart`.
- `/api/bt/state|enable?on=|scan/start|scan/stop|pair?addr=|connect?addr=|disconnect?addr=|forget?addr=|auto?on=|receive`.

Measured on the device while capturing fixtures: after `/api/media/play`, state goes
`downloading` (percent 0..100, ~3-6 s) then `playing`; `position_s` advances by whole seconds;
pause keeps position; stop leaves `state: stopped`, empty title, the queue kept.

Fixtures in `docs/fixtures/` are verbatim responses from the R1 (state trimmed to the keys above
on purpose). Wire-mapping tests copy from them; do not invent bodies. A "Bluetooth in" body is
`bt_idle.json` with `"incoming":"F4:4E:FD:7A:0C:92"` and that device's `"streaming_in":true`
(measured earlier the same day on the device, name `UGREEN-BT305`).

## Night rule

When anything is played on the real R1 for testing in the evening or at night, set
`volume` to 0 first and restore it afterwards.
