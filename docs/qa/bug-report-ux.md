# R1 Control: UX issues

Reviewed 2026-11-08 on an iPhone 16e simulator (iOS 26.5), Debug build of the
working tree, against the real R1 at 10.25.113.209:8088. Half the review ran
with the simulator in Vietnamese, half in English. A second pass ran at the
"Accessibility XL" text size. Nothing that writes to the R1 was pressed.
Playing, downloading, Bluetooth-in, assistant, offline and dialog states that
could not be produced safely on the device were rendered from the real
`*Content` composables in a throwaway JVM `ImageComposeScene` (1170x2532 px,
density 3). Those screenshots are named `ux-render-*.png`. The JVM renders use
the desktop font, so they show layout and wording, not exact iOS pixels.

Each issue says how it was found. "Seen" means seen on the simulator or in a
render. "Code" means read from the source and not exercised, usually because
exercising it would write to the R1.

Screenshots are in `docs/qa/screens/`. File and line numbers are as of this
review, under `composeApp/src/commonMain/kotlin/com/r1control/app/` unless a
path says otherwise.

Severity: blocker = a user gets stuck or cannot do the task; major = the
task works but misleads, loses work, or is unsafe; minor = noticeable and
annoying; polish = fit and finish.

Totals: 1 blocker, 9 major, 22 minor, 9 polish (41 issues).

---

## Blocker

### U-01. Chat: the keyboard hides the field, pushes the page under the status bar, and cannot be closed

- **Where:** Chat tab, tapping "Nhắn cho loa" / "Message the speaker".
- **What the user sees:** The whole page slides up. The R1 title and gear go
  off the top, and the newest bubbles sit under the clock and battery. The
  bottom ~14 pt of the text field and the send button are under the
  keyboard's suggestion bar. The tab bar is hidden while the keyboard is up.
  Tapping the messages, scrolling them, or tapping anywhere else does not
  close the keyboard. The only ways out are to send the message (the
  keyboard's Return key is Send) or to kill the app. Seen, twice.
- **What it should be:** The field and send button sit directly on top of
  the keyboard. The top bar stays put. A tap on the messages or a downward
  drag closes the keyboard and brings the tab bar back. The spec's Chat
  section expects a text field and a send button at the bottom that work
  like the web panel's chat drawer.
- **Evidence:** `ux-vi-17-chat-keyboard.png`, `ux-vi-18-chat-keyboard-stuck.png`.
  The accessibility tree at that moment puts the field at y 475..523 and the
  send button at y 473..525, with the keyboard's top at y 509. `ui/App.kt:113`
  hides the tab bar while the IME is up, `ui/App.kt:119` pads the column by the
  IME, and nothing clears focus on an outside tap. The upward pan is likely
  Compose iOS's own "keep the focused field above the keyboard" behaviour
  added on top of `imePadding()`, since `MainViewController.kt:9` uses the
  default `ComposeUIViewController` configuration. That last point is a guess,
  not traced.

---

## Major

### U-02. The volume pill does not exist for VoiceOver

- **Where:** Control tab, volume pill.
- **What the user sees:** VoiceOver finds an image labelled "Âm lượng" and a
  text "0%". There is no adjustable element, so a VoiceOver user cannot change
  the volume at all. Seen in the accessibility tree.
- **What it should be:** One adjustable element ("Âm lượng, 40%") that swipe
  up and down step through the R1's 15 volume steps, as the seek slider
  already does.
- **Evidence:** `ux-vi-04-control-idle.png`. `ui/control/ControlScreen.kt:359-397`
  has pointer input only, with no semantics.

### U-03. One accidental tap on the volume pill jumps to that level, up to 100%

- **Where:** Control tab, volume pill.
- **What the user sees:** A tap anywhere on the pill sets the volume to the
  finger's x position at once. A tap near the right end while scrolling or
  reaching for the tab bar sets the speaker to 90-100%. At night that is the
  failure the spec's "Night rule" exists for. Code (not exercised, it writes).
- **What it should be:** The spec asks for "drag horizontally anywhere on it
  to set the level (like Google Home)". A tap alone should not write. If a tap
  must do something, it should step by one hardware step towards the finger.
- **Evidence:** `ui/control/ControlScreen.kt:379-385` (`detectTapGestures` calls
  `onDrag` then `onDone`). The drag start at line 370 also jumps to the
  finger's position before any movement.

### U-04. Unreachable speaker: the Control tab looks like a real, empty, silent speaker

- **Where:** Control tab when the R1 cannot be reached (wrong address, phone
  off the house wifi, R1 restarting).
- **What the user sees:** A red banner "Không nối được với loa. Điện thoại có
  đang dùng wifi nhà không?" above a media card with no text at all, a seek
  bar and greyed transport buttons, and a volume pill that says "0%". The
  "0%" reads as a fact about the speaker. The banner has no Try again and no
  way to reach the address setting. Seen (render).
- **What it should be:** One state that says the speaker is not answering,
  names what to check (same wifi, speaker powered, address), and offers
  "Thử lại" and "Đổi địa chỉ" (opening Settings, Nâng cao). The volume pill
  shows no number while nothing is known.
- **Evidence:** `ux-render-control-offline.png`, `ux-render-control-offline-en.png`.
  `ui/control/ControlViewModel.kt:63-64` maps Offline to `ControlState.Initial`
  (volume 0, card Connecting); `ui/control/ControlScreen.kt:182` draws an empty
  string for Connecting; `ui/control/ControlScreen.kt:101` draws the banner
  with no action.

### U-05. A failed command leaves a red banner on the Control tab for good

- **Where:** Control tab, after any play, pause, next, stop, seek or volume
  call that fails.
- **What the user sees:** "Không nối được với loa…" or "Loa từ chối…" stays
  above the card even after the speaker answers every poll again and
  playback is visibly fine. It goes away only after another command
  succeeds. The banner has no close button. Code (not exercised, it writes).
- **What it should be:** The banner clears when the next poll succeeds or
  when the user closes it.
- **Evidence:** `ui/control/ControlViewModel.kt:127` sets `commandProblem`, and
  only a later command resets it; `dismissProblem()` at line 132 exists but
  `ControlContent` never calls it (`ui/control/ControlScreen.kt:101`). The
  Bluetooth page has the same shape: a failed poll sets `problem`, and a
  successful `read()` never clears it (`ui/settings/BluetoothViewModel.kt:87-101`).

### U-06. Chat when the speaker is unreachable spins for ever

- **Where:** Chat tab, speaker unreachable.
- **What the user sees:** The red banner plus a loading spinner that never
  stops, and an active-looking text field. "Still asking" and "cannot ask"
  look the same. Seen (render).
- **What it should be:** The failure in place of the spinner, with Try again,
  and the field disabled until the speaker answers.
- **Evidence:** `ux-render-chat-offline.png`. `ui/chat/ChatViewModel.kt:40` maps
  Offline to `loaded = false`, which `ui/chat/ChatScreen.kt:78` draws as
  loading.

### U-07. News settings: edits are lost on Back, and "Phát thử" does not play what is on screen

- **Where:** Settings, Bản tin.
- **What the user sees:** The page looks like every other settings page, with
  switches that seem to apply at once (the Audio and Bluetooth switches do).
  Here nothing is written until "Lưu", which is below the fold on an iPhone
  16e. Back discards every edit without a word. "Phát thử" reads the bulletin
  with the settings saved on the speaker, not the voice or topics just
  chosen, and it stops any music that is playing without warning (charter §8,
  "Play a news test stops the music"). The "Đã lưu" note stays until the next
  edit. Seen (layout) and code (behaviour).
- **What it should be:** Either write each change at once like the other
  pages, or make the draft obvious: a Save button pinned at the bottom, Save
  disabled until something changed, and a "Bỏ thay đổi?" question on Back.
  "Phát thử" should use the shown settings or say it uses the saved ones, and
  warn when music is playing.
- **Evidence:** `ux-vi-08-settings-news.png`, `ux-vi-09-settings-news-bottom.png`,
  `ux-render-news-saved.png`. `ui/settings/NewsViewModel.kt:96-106` (`save` sends
  the draft, `test` sends nothing); `ui/settings/NewsScreen.kt:124-130`.

### U-08. "Độ vang" is the wrong word for loudness, and the boost has no warning

- **Where:** Settings, Âm thanh, first slider.
- **What the user sees:** "Độ vang" means echo or reverberation. The slider is
  the R1's loudness enhancer (`loudness_mb`). A Vietnamese reader will expect
  an echo effect. The hint "Giúp tiếng nhỏ dễ nghe hơn." reads as a free win,
  while the web panel warns that +3 dB already distorted the built-in speaker
  (the R1 under test is at 10 dB). Seen.
- **What it should be:** "Độ to" (the web panel's word) or "Tăng âm lượng",
  and a hint that says high values can crackle on the built-in speaker.
- **Evidence:** `ux-vi-06-settings-audio.png`. `ui/i18n/Strings.kt:342`.
  The panel wording is in `/Volumes/Data2/git/xiaozhi-android/app/src/main/assets/control.html:580`.

### U-09. No swipe-back on settings pages

- **Where:** Every settings page (Âm thanh, Giọng đọc, Bản tin, Bluetooth, Nâng
  cao) and Settings itself.
- **What the user sees:** A swipe from the left edge does nothing; only the
  arrow in the corner goes back. Pages also switch with no slide, so there is
  no sense of depth. Seen twice through idb's synthetic swipe; confirm on a
  phone, since idb's swipe may not reach the system edge recogniser.
- **What it should be:** The iOS edge swipe goes back one level, with the
  page sliding as in every iOS app and in Google Home.
- **Evidence:** `ux-vi-06-settings-audio.png` (unchanged after the swipe).
  `ui/App.kt:115-117` handles back with `BackHandler`; settings pages are a
  plain index swap with no transition (`ui/App.kt:130-133`).

### U-10. Tapping a song gives no feedback that anything happened

- **Where:** Music tab, a result row or "Phát tất cả".
- **What the user sees:** The row shows the default ripple and nothing else.
  The user stays on the Music tab. Nothing marks the tapped row as the one
  playing. The mini player appears only once the R1 reports the song, after
  the 3-6 s download, and while downloading it shows a greyed play glyph with
  no "Đang chuẩn bị…" text. A failure is a banner at the top of the list,
  possibly scrolled away. Nothing in the row says that a tap plays it on the
  speaker (YouTube Music's row has a menu; this one has neither a play glyph
  nor a menu). Code and render (not exercised, a tap writes).
- **What it should be:** At once, the tapped row shows a playing or loading
  marker and the mini player appears with "Đang chuẩn bị…". The playing song
  stays marked in the list. A failure shows next to the row or as a toast.
- **Evidence:** `ux-vi-13-music-results.png`, `ux-render-miniplayer-downloading.png`,
  `ux-render-music-play-failed.png`. `ui/music/MusicViewModel.kt:89-94` sets only
  a problem; `ui/music/MusicScreen.kt:229-252` has no state for "playing" or
  "starting".

---

## Minor

### U-11. The keyboard never closes on an outside tap, anywhere

- **Where:** Nâng cao (address), Music keywords, first run.
- **What the user sees:** Tapping outside the field removes the caret but
  leaves the keyboard up, covering the bottom half. Only the keyboard's own
  Done or Search key closes it. Seen on Nâng cao.
- **What it should be:** A tap outside any field closes the keyboard.
- **Evidence:** No `clearFocus` on outside taps in `ui/App.kt` or the screens.

### U-12. English left in the Vietnamese interface

- **Where:** Âm thanh ("Equalizer"); any refusal from the R1 ("Loa từ chối:
  queue busy"); Bluetooth adapter errors ("Connection refused (status=133)");
  "app" in "Khởi động lại app trên loa"; the iOS Local Network permission
  prompt (English only).
- **What it should be:** "Bộ chỉnh âm (EQ)"; refusals and adapter errors put
  into words by the app with the raw reason hidden or after a Vietnamese
  sentence; "ứng dụng"; an `InfoPlist.strings` for `vi`.
- **Evidence:** `ux-vi-06-settings-audio.png`, `ux-render-music-play-failed.png`,
  `ux-render-bt-adapter-error.png`, `ux-vi-11-settings-advanced.png`.
  `ui/i18n/Strings.kt:343,354,426`; `ui/settings/BluetoothScreen.kt:138`;
  `iosApp/iosApp/Info.plist:37-38`.

### U-13. The big card stays black for seconds while the large cover loads

- **Where:** Control tab, a song starts.
- **What the user sees:** A black card with the title, for several seconds,
  while the mini player already shows the small cover. Seen.
- **What it should be:** Show the 120 px cover (already cached) at once and
  swap in the 640 px one when it arrives.
- **Evidence:** `ux-vi-14-control-playing-cover-loading.png` then
  `ux-vi-15-control-playing-cover.png`. `ui/control/ControlScreen.kt:120-131`
  (no placeholder).

### U-14. "Previous" is always drawn and never works

- **Where:** Control tab, left of the seek bar.
- **What the user sees:** A greyed previous button on every card. The R1 has
  no previous, so it can never light. Seen.
- **What it should be:** Draw it only when it can work. If it is kept for the
  Google Home look, make a tap restart the song (seek 0).
- **Evidence:** `ui/control/ControlScreen.kt:348` (onClick `{}`).

### U-15. Search and "Thêm từ khoá": the primary action looks secondary

- **Where:** Music tab.
- **What the user sees:** With an empty field, "Tìm" is a grey pill with grey
  text and "Thêm từ khoá" is a wide pill with white text. The secondary action
  is the louder one. While searching, the button's spinner is dark on dark
  and nearly invisible, next to a second spinner in the list. Seen.
- **What it should be:** A search field with a magnifier and the keyboard's
  Search key as the main path (as in `docs/design/music-browse.png`), "Tìm"
  visibly primary, one spinner.
- **Evidence:** `ux-vi-12-music-empty.png`, `ux-render-music-searching.png`.
  `ui/components/Components.kt:141,147`.

### U-16. Adding a keyword does not focus the new field, and mixing is unexplained

- **Where:** Music tab, "Thêm từ khoá".
- **What the user sees:** A second field appears, but typing still goes into
  the first. The new field has the same hint as the first. The only line
  that explains mixing ("Thêm từ khoá để trộn kết quả") disappears once there
  are recent searches. Seen.
- **What it should be:** Focus the new field. Hint the second one differently
  ("Thêm ca sĩ hoặc bài khác để trộn"), or keep a one-line explanation above
  the fields.
- **Evidence:** `ux-vi-12-music-empty.png`, `ux-vi-19-music-recents.png`.
  `ui/music/MusicScreen.kt:124`, `ui/music/MusicViewModel.kt:60-62`.

### U-17. Results header breaks at large text, and its X is ambiguous

- **Where:** Music tab, results header.
- **What the user sees:** At Accessibility XL, "Kết quả" wraps to two lines
  beside the X and "Phát tất cả". At every size the X looks like the X that
  removes a keyword, but it clears the results. Seen.
- **What it should be:** Header on one line ("Phát tất cả" can drop below the
  title), and the clear action labelled ("Xoá kết quả") or moved into the
  search field.
- **Evidence:** `ux-vi-21-music-results-largetext.png`, `ux-vi-13-music-results.png`.
  `ui/music/MusicScreen.kt:189-197`.

### U-18. Long artist lists hide the duration

- **Where:** Music results rows.
- **What the user sees:** "Thanh Ngân, Gia Tiến, Mai Tiến Đạt,…" with the
  duration cut off; at large text most rows lose it. Seen.
- **What it should be:** Duration in its own trailing column, or before the
  artist.
- **Evidence:** `ux-render-music-partial-failure.png`, `ux-vi-21-music-results-largetext.png`.
  `ui/music/MusicScreen.kt:240`.

### U-19. Sliders are unlabelled for VoiceOver

- **Where:** Seek bar, loudness, five EQ bands.
- **What the user sees (VoiceOver):** "slider, 1000.0" for an EQ band; the
  seek bar has no label. Seen in the accessibility tree.
- **What it should be:** "60 Hz, +10 dB", "Vị trí, 1:41 trên 2:23".
- **Evidence:** `ui/components/Components.kt:450-483` (no semantics);
  `ui/settings/AudioScreen.kt:122-136`.

### U-20. Touch targets under 48 dp

- **Where:** "Phát tất cả" (40 dp, `ui/components/Components.kt:187`); banner
  actions "Thử lại" and "Đóng" (about 36 dp, `ui/components/Components.kt:283`);
  Bluetooth device buttons (40 dp, `ui/settings/BluetoothScreen.kt:195-196`);
  previous and next (44 dp, `ui/control/ControlScreen.kt:348,350`). Code, and
  the layout matches in the screenshots.
- **What it should be:** 48 dp minimum, as the charter requires.

### U-21. Settings home: two titles, three names, no current values

- **Where:** Settings.
- **What the user sees:** A top bar "Cài đặt" and, below it, a headline "Loa
  R1", then "Phicomm R1". The main page calls the same speaker "R1". The rows
  show no current value (which voice, which Bluetooth output), and there are
  no group headers. Seen.
- **What it should be:** As in `docs/design/settings.png`: only a close/back
  control on top, one large speaker name, the model under it, grouped rows
  ("Chung", "Tính năng"), and a second line with the current value where it
  helps ("Giọng đọc: Adam bựa", "Bluetooth: chưa nối").
- **Evidence:** `ux-vi-05-settings-home.png`, `ux-en-03-settings-home.png`.
  `ui/settings/SettingsHome.kt:50-67`.

### U-22. Voice page lists the current voice twice and cannot preview

- **Where:** Settings, Giọng đọc.
- **What the user sees:** "Giọng đang dùng: Adam bựa ✓" and again "Adam bựa ✓"
  at the top of "Tất cả giọng". There is no way to hear a voice before
  choosing it, and choosing one shows nothing beyond the moved check mark.
  Seen.
- **What it should be:** The voice once, checked. A small play button per
  voice for a sample (if the TTS host can provide one), and a short "Đã đổi
  giọng" confirmation.
- **Evidence:** `ux-vi-07-settings-voice.png`. `ui/settings/VoiceScreen.kt:68-75`.

### U-23. Bluetooth page reads like a technician's panel

- **Where:** Settings, Bluetooth.
- **What the user sees:** Every device shows its MAC address. "Xoá" (forget)
  has the same weight as "Nối" (connect) and reads as "delete". Nothing says
  which device is the current output except a small dot. "Nhận nhạc từ điện
  thoại" has no explanation of what happens next. The countdown reads
  "Điện thoại tìm tên Phicomm_R1_2E2B… còn 87 giây". Seen and render.
- **What it should be:** Name only (MAC behind a detail), "Đang phát qua loa
  này" on the active device, "Bỏ ghép đôi" as a quiet text action, and
  instructions such as "Trên điện thoại, mở Bluetooth và chọn
  Phicomm_R1_2E2B (còn 87 giây)".
- **Evidence:** `ux-vi-10-settings-bluetooth.png`, `ux-en-05-settings-bluetooth.png`,
  `ux-render-bt-receiving.png`. `ui/i18n/Strings.kt:411,414`.

### U-24. Bluetooth "Thử lại" does nothing

- **Where:** Settings, Bluetooth, when the first read fails.
- **What the user sees:** "Thử lại" under the failure. Pressing it does
  nothing; the page recovers only through its own 1 s poll. Code.
- **What it should be:** Wire it to a read, or drop the button and say the
  page retries by itself.
- **Evidence:** `ux-render-bt-failed.png`. `ui/settings/BluetoothScreen.kt:72`
  passes `{}` as `onRetry`.

### U-25. Address field: wrong keyboard, autocorrect, hint that looks like a value

- **Where:** First run and Nâng cao.
- **What the user sees:** A letters keyboard (URL type) for an IP address,
  autocorrect suggestions such as "years", "days", "25009", and a grey
  "192.168.1.20:8088" hint that looks like a prefilled address. Seen.
- **What it should be:** A numbers-and-punctuation keyboard, autocorrect off,
  and a hint that reads as an example ("Ví dụ: 192.168.1.20").
- **Evidence:** `ux-vi-01-firstrun.png`, `ux-vi-02-firstrun-keyboard.png`,
  `ux-vi-03-firstrun-unreachable.png`. `ui/address/AddressScreen.kt:62`.

### U-26. First-run "unreachable" does not say what to try

- **Where:** First run, wrong or unreachable address.
- **What the user sees:** "Không có gì trả lời ở 10.25.113.250:8088." Nothing
  about the speaker being on, the phone being on the house wifi, or iOS's
  Local Network permission, which, if denied, makes every address fail the
  same way (a guess; the simulator does not ask). Seen.
- **What it should be:** "Không thấy loa ở địa chỉ này. Kiểm tra loa đang bật,
  điện thoại đang dùng wifi nhà, và R1 Control được phép truy cập mạng cục bộ
  (Cài đặt › Quyền riêng tư)."
- **Evidence:** `ux-vi-03-firstrun-unreachable.png`. `ui/i18n/Strings.kt:433`.

### U-27. Chat times have no date

- **Where:** Chat tab.
- **What the user sees:** Yesterday's 22:19 bulletin lines sit above today's
  00:03 lines with nothing between them, and every bubble repeats its time.
  Seen.
- **What it should be:** Day separators ("Hôm qua", "Hôm nay") and one time
  per group of lines from the same minute.
- **Evidence:** `ux-vi-18-chat-keyboard-stuck.png`. `ui/chat/ChatScreen.kt:174-181`,
  `ui/i18n/ClockTime.kt`.

### U-28. Disabled send button is almost invisible

- **Where:** Chat tab, empty field.
- **What the user sees:** A dark arrow on a dark circle, contrast 1.30:1
  (computed from the tokens), so the send control seems missing. Seen.
- **What it should be:** The muted grey arrow (`OnGroundMuted`, about 5.6:1 on
  the card).
- **Evidence:** `ux-vi-16-chat.png`. `ui/chat/ChatScreen.kt:101-102`.

### U-29. White flash on every cold start

- **Where:** App launch.
- **What the user sees:** A fully white screen before the dark app appears.
  Seen (the centre pixel of the launch capture is 255,255,255).
- **What it should be:** A launch screen in the ground colour `#141416`.
- **Evidence:** `ux-en-07-launch-white.png`. `iosApp/iosApp/Info.plist:39-40`
  (`UILaunchScreen` is an empty dict).

### U-30. Mini player hides failures and the downloading state

- **Where:** Mini player on Music and Chat.
- **What the user sees:** While a song downloads, a greyed play glyph with no
  word. A pause that fails does nothing visible. Code and render.
- **What it should be:** "Đang chuẩn bị…" in place of the artist while
  downloading, and a failure shown somewhere.
- **Evidence:** `ux-render-miniplayer-downloading.png`.
  `ui/music/MiniPlayerViewModel.kt:57-63` ignores the outcome.

### U-31. Recent searches have no time and cannot be removed

- **Where:** Music tab, "Tìm gần đây".
- **What the user sees:** Keyword sets only. The spec says each is saved with
  its time, like YouTube Music's history, and there is no way to remove one.
  Seen.
- **Evidence:** `ux-vi-19-music-recents.png`. `ui/music/MusicScreen.kt:157-174`.

### U-32. Tab bar flickers away after the keyboard closes

- **Where:** After Connect on first run, and after a search.
- **What the user sees:** For a moment the tab bar is missing, then it
  appears. Seen twice (live views, not saved).
- **What it should be:** The bar stays; hiding it with the keyboard is what
  makes it blink.
- **Evidence:** `ui/App.kt:113,141`.

---

## Polish

### U-33. The back arrow keeps its pressed circle on the next page

- **Where:** Every settings page reached after pressing back.
- **What the user sees:** A grey circle stays behind the back arrow, as if it
  were still pressed. Seen.
- **Evidence:** `ux-vi-07-settings-voice.png`, `ux-vi-08-settings-news.png`,
  `ux-vi-10-settings-bluetooth.png`, `ux-vi-11-settings-advanced.png`.
  `ui/App.kt:120-129` keeps one `ScreenTopBar` across pages, so the ripple
  outlives the page.

### U-34. Idle card says nothing about what to do; five strings are unused

- **Where:** Control tab, idle.
- **What the user sees:** "Chưa phát gì" alone. The translated hint "Chọn bài
  ở tab Nhạc, hoặc nói chuyện với loa." exists but is never shown.
  `nothingPlayingHint`, `thisSpeaker`, `equalizerBands`, `searching` and
  `appName` are defined in both languages and never read. Code.
- **Evidence:** `ux-vi-04-control-idle.png`. `ui/i18n/Strings.kt`.

### U-35. Volume pill at 0%: no sliver, no mute glyph

- **Where:** Control tab, volume 0.
- **What the user sees:** An empty pill with a normal speaker glyph. Nothing
  hints that it can be dragged. In `docs/design/control-playing.png` a thin
  fill edge stays visible at 3%. Seen.
- **Evidence:** `ux-vi-04-control-idle.png`. `ui/control/ControlScreen.kt:387,392`.

### U-36. Stop is a bare square that reads as a status icon

- **Where:** Control card, top right.
- **What the user sees:** A small white square with no circle, next to a
  large round pause button. Stop ends the queue, but nothing confirms it.
  Seen.
- **Evidence:** `ux-vi-15-control-playing-cover.png`. `ui/control/ControlScreen.kt:254`.

### U-37. News page does not dim when the bulletin is off

- **Where:** Bản tin with "Bản tin hằng ngày" off.
- **What the user sees:** Time, voice and topics stay fully active. Disabled
  move arrows (contrast about 2.1:1) look nearly the same as enabled ones. The
  label "Giờ" is also used for hours. The steppers move the hour by 1 and the
  minute by 5, so 06:30 to 21:45 is 18 taps. Seen.
- **Evidence:** `ux-vi-08-settings-news.png`. `ui/settings/NewsScreen.kt:94-109,136-157`.

### U-38. Audio page alignment and dimming

- **Where:** Âm thanh.
- **What the user sees:** The loudness slider starts at the left margin while
  the band sliders start after a 72 dp label, so the two columns do not line
  up. With EQ off the sliders dim, but the band names and dB values do not.
  "Độ vang" is a grey section label while "Equalizer" is a white row title,
  though they are peers. Seen.
- **Evidence:** `ux-vi-06-settings-audio.png`, `ux-render-audio-eq-off.png`.
  `ui/settings/AudioScreen.kt:76-106`.

### U-39. Vietnamese wording that reads as translated

- `errorBadReply` "Loa trả lời mà không đọc được." → "Loa trả lời không đúng
  dạng." (`ui/i18n/Strings.kt:425`)
- `btConnect` / `btDisconnect` "Nối" / "Ngắt" → "Kết nối" / "Ngắt kết nối"
- `newsTime` "Giờ" → "Giờ phát" (`ui/i18n/Strings.kt:345`)
- `searchResults` "Kết quả" is fine; `clear` "Xoá" next to it is ambiguous
  (see U-17)

### U-40. Chat empty state: centred icon, left-aligned text

- **Where:** Chat tab, no messages.
- **Evidence:** `ux-render-chat-empty.png`. `ui/chat/ChatScreen.kt:119-124` (no
  `textAlign`).

### U-41. Dialogs are Material's, not the app's look

- **Where:** Restart and Forget confirmations.
- **What the user sees:** A Material `AlertDialog` with text buttons. It
  works, but it is the one piece that is neither iOS's alert nor Google
  Home's sheet. Code; the JVM render caught it mid-fade, so look at it on the
  device.
- **Evidence:** `ux-render-advanced-restart-confirm.png`,
  `ux-render-bt-forget-dialog.png`. `ui/components/Components.kt:410-429`.
