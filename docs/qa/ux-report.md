# R1 Control: UX review

Reviewed 2026-11-08 on a dedicated iPhone 16e simulator (iOS 26.5), Debug build
of the working tree, against the real R1 at 10.25.113.209:8088. The first half
ran with the simulator in Vietnamese (the owner's phone), the second half in
English. Both ran at default text size, with a pass at Accessibility XL. No
control that writes to the R1 was pressed. States that need a write or an
unreachable speaker (offline, downloading, Bluetooth in, assistant speaking,
failures, dialogs) were rendered from the real `*Content` composables in a
throwaway JVM `ImageComposeScene` outside the repository (`ux-render-*.png`).
Those renders use the desktop font, so read them for layout and wording, not
exact pixels.

Every issue named here is filed with an ID, severity and evidence in
[`bug-report-ux.md`](bug-report-ux.md). Totals: 1 blocker, 9 major, 22 minor,
9 polish.

## Summary

The look is close to Google Home. The ground colour, the card radius and
height, the volume pill's size and fill, and the playing card with the cover
behind the title all match `docs/design/control-*.png` well. The Vietnamese
text is mostly natural, and Dynamic Type scales without breaking the Control
tab.

What makes it feel buggy is behaviour at the edges:

1. Chat's keyboard traps the user (U-01).
2. The volume pill is unsafe to tap and invisible to VoiceOver (U-02, U-03).
3. Failure states either mislead or never go away (U-04, U-05, U-06).
4. News edits vanish on Back (U-07).
5. Nothing confirms that a tapped song is on its way (U-10).

Visual fidelity is not the problem. Feedback and state are.

## Theme and consistency

- **Works.** One ground (`#141416`), one card colour, 28 dp card radius, and
  one accent across all tabs and settings pages. Title sizes match the
  reference (28 sp bar title, 32 sp settings headline). Body text is 17 sp.
  Muted text on the ground is 7.35:1, the accent percentage on the volume fill
  is 4.88:1, and error text on its container is 7.93:1 (computed from the
  tokens), so the main text passes WCAG AA.
- **Does not work.** Disabled states are too faint to read as controls: the
  send glyph is 1.30:1 (U-28) and disabled move arrows are about 2.1:1
  (U-37). Material pieces show through in a few places: dialogs (U-41),
  switches, and the tab bar's pill indicator. Page changes have no
  transition, and there is no iOS swipe-back (U-09). A white launch screen
  flashes before the dark app (U-29).
- **Icons** are one consistent outline set. The News row's icon reads as a
  calendar or table more than a newspaper, and Stop is a bare square (U-36).

## First run

Screens: `ux-vi-01-firstrun.png`, `ux-vi-02-firstrun-keyboard.png`,
`ux-vi-03-firstrun-unreachable.png`.

- **Works.** Clear title, one field, one button. An empty submit says "Hãy
  nhập địa chỉ của loa." A bad address is checked before saving, with a
  spinner in the button and "Đang kiểm tra…". The keyboard does not cover the
  button.
- **Does not work.** The hint looks like a prefilled value. The keyboard is
  the URL letters layout with autocorrect suggestions (U-25). The unreachable
  message does not say what to check, and the iOS Local Network prompt is
  English only (U-26, U-12). After Connect the tab bar blinks in (U-32).

## Control tab

Reference: `docs/design/control-idle.png`, `docs/design/control-playing.png`.
Screens: `ux-vi-04-control-idle.png`, `ux-vi-14-control-playing-cover-loading.png`,
`ux-vi-15-control-playing-cover.png`, `ux-en-01-control-playing.png`,
`ux-vi-20-control-largetext.png`, `ux-render-control-*.png`.

- **Fidelity.** Very close to the reference. The idle card has the radial
  grey glow, the play-in-circle glyph and "Chưa phát gì" in the same place,
  with the transport row at the bottom. The playing card puts the cover full
  bleed under a dark scrim, with the title and artist on the left, a round
  dark pause button on the right, and the seek bar between previous and next.
  The pill is 72 dp tall with a blue-grey fill, as in the reference.
  Differences from the reference, all reasonable: a music glyph and a Stop
  square in the card's top row, and times under the seek bar.
- **Works.** Long Vietnamese titles wrap to two lines and then ellipsize
  (`ux-render-control-long-title-paused.png`). Downloading shows "Đang chuẩn
  bị… 37%" with a bar. Output to a Bluetooth speaker shows "trên
  UGREEN-BT305" in the accent. The assistant card shows "Đang trả lời" with
  the last line in three lines. Bluetooth in shows "Đang phát từ iPhone của
  Lực". At Accessibility XL the card grows and nothing overlaps.
- **Does not work.** VoiceOver cannot change the volume (U-02). A tap on the
  pill writes an absolute level (U-03). Unreachable looks like an empty
  speaker at "0%" with no way to retry or fix the address (U-04). A failed
  command leaves its banner for good (U-05). The large cover shows black for
  seconds although the small one is cached (U-13). Previous is permanently
  disabled (U-14). The idle card does not say what to do, though the
  translated hint exists (U-34). At 0% the pill gives no hint it can be
  dragged (U-35). The seek bar has no VoiceOver label (U-19).
- **Affordance.** The pill has no thumb, which matches Google Home, but this
  app has no onboarding. A one-time "Kéo để chỉnh âm lượng" hint, or the
  reference's thin fill edge at low levels, would help.

## Music tab

Reference: `docs/design/music-list.png` (YouTube Music list),
`docs/design/music-browse.png` (Apple Music search).
Screens: `ux-vi-12-music-empty.png`, `ux-vi-13-music-results.png`,
`ux-vi-19-music-recents.png`, `ux-vi-21-music-results-largetext.png`,
`ux-en-06-music-results.png`, `ux-render-music-*.png`,
`ux-render-miniplayer-downloading.png`.

- **Fidelity.** Result rows follow YouTube Music: a 56 dp square cover with
  8 dp radius, a 17 sp title, and "artist · duration" in muted text. The
  outlined "Phát tất cả" pill matches the reference's "Play all". The mini
  player above the tab bar matches the reference's placement. The search area
  does not follow Apple Music's capsule with a magnifier. It is a plain card
  field and a pair of pill buttons, which takes about 40% of the screen once
  two keywords are in.
- **Works.** Vietnamese diacritics type and show correctly. Multi-keyword
  results interleave. A failed keyword is named inline ("Không tìm được
  'bolero'") while the others still show. "Không tìm thấy" covers no
  results. Recent searches re-run with one tap. Titles ellipsize on one line,
  as in the reference.
- **Does not work.** Nothing confirms that a tapped row is playing or loading,
  and nothing in the row says a tap plays it on the speaker (U-10). The
  primary and secondary buttons have their weights swapped, and the busy
  spinner is invisible (U-15). A new keyword field does not take focus, and
  mixing is not explained once recents exist (U-16). The results header wraps
  at large text, and its X is ambiguous (U-17). Long artist lists hide the
  duration (U-18). Recents have no time and no remove (U-31). The keyboard
  does not close on an outside tap (U-11).

## Chat tab

Screens: `ux-vi-16-chat.png`, `ux-vi-17-chat-keyboard.png`,
`ux-vi-18-chat-keyboard-stuck.png`, `ux-render-chat-empty.png`,
`ux-render-chat-offline.png`.

- **Works.** Bubbles are clear: the user on the right in the accent, the
  speaker on the left in the card colour, with a time under each. The list
  opens at the newest message. The empty state says "Chưa có tin nhắn. Nhắn
  cho loa ở bên dưới." as the spec asks.
- **Does not work.** The keyboard covers part of the field and send button,
  shoves the page under the status bar, and cannot be dismissed (U-01,
  blocker). Offline spins for ever (U-06). There are no day separators
  (U-27). The disabled send button is nearly invisible (U-28). The empty
  state's text is left-aligned under a centred icon (U-40). The mini player
  plus the tab bar plus the top bar leave little room for messages on a
  6.1-inch phone.

## Settings

Reference: `docs/design/settings.png`.
Screens: `ux-vi-05-settings-home.png`, `ux-en-03-settings-home.png`,
`ux-vi-06-settings-audio.png`, `ux-en-04-settings-audio.png`,
`ux-vi-07-settings-voice.png`, `ux-vi-08/09-settings-news*.png`,
`ux-vi-10-settings-bluetooth.png`, `ux-en-05-settings-bluetooth.png`,
`ux-vi-11-settings-advanced.png`, `ux-vi-22-news-largetext.png`,
`ux-render-*` for Bluetooth, Audio, News, Voice and Advanced states.

- **Gear and navigation.** The gear is top right on all three tabs, where
  Google Home puts it, with a 48 dp target. Settings replaces the tab bar.
  The back arrow returns one level, and from Settings back to the tab.
  There is no edge swipe and no slide (U-09). The pressed circle of the back
  arrow carries onto the next page (U-33).
- **Settings home vs reference.** Same structure (big name, model, round
  device badge, rows with leading icons). It adds a second title bar, uses
  three names for one speaker, and has no group headers and no current
  values on rows (U-21).
- **Âm thanh.** Clear sliders with live dB and Hz values. The Vietnamese label
  for loudness is wrong, and the boost has no distortion warning (U-08).
  "Equalizer" is untranslated (U-12). The sliders are unlabelled for
  VoiceOver (U-19). The alignment and dimming are uneven (U-38).
- **Giọng đọc.** The list is easy to scan and the current voice is checked.
  The current voice appears twice, and voices cannot be previewed (U-22).
  The "no voice server" text is clear (`ux-render-voice-noserver.png`).
- **Bản tin.** Readable at large text. The draft-and-Save model clashes with
  every other page. Back loses edits, and "Phát thử" plays the saved settings
  and stops music (U-07). The page does not dim when the bulletin is off
  (U-37).
- **Bluetooth.** All the panel's functions are present, and status sentences
  are plain ("Chưa nối thiết bị nào. Tiếng ra loa trong máy."). Too technical
  in places: MAC addresses, "Xoá" for forget, an unexplained "Receive from
  phone", and raw adapter errors in English (U-23, U-12). "Thử lại" does
  nothing (U-24). Device buttons are 40 dp (U-20).
- **Nâng cao.** The address form is shared with first run, so it validates and
  checks before saving. Restart asks first and then reports "Đang khởi động
  lại. Vài giây nữa loa sẽ sẵn sàng." The keyboard stays up after an outside
  tap (U-11).

## Cross-cutting checks

| Check | Result |
|---|---|
| Safe areas, notch, home indicator | Respected on every screen except Chat with the keyboard up (U-01). |
| Keyboard over fields | First run, Music and Nâng cao are fine; Chat is covered (U-01). The keyboard never closes on an outside tap (U-11). |
| Touch targets 48 dp | Most icon buttons are 48 dp. Exceptions are listed in U-20. |
| Empty, loading, error states | Music and Audio say what happened and offer Try again. Control and Chat offline do not (U-04, U-06). Bluetooth's Try again is dead (U-24). |
| Feedback after actions | Voice (check moves), News (Đã lưu), Restart (message) and address (Đã lưu) give feedback. Playing a song (U-10), mini player toggle (U-30) and failed commands (U-05) do not. |
| Vietnamese wording | Mostly natural. Wrong word for loudness (U-08). English leaks in (U-12). A few stiff phrases (U-39). Units: "dB", "Hz", "kHz" and "giây" are fine. |
| Dynamic Type | Honoured. The Control and News pages hold at Accessibility XL. The Music results header breaks (U-17) and rows lose the duration (U-18). |
| VoiceOver | The volume pill is missing (U-02). Sliders are unlabelled (U-19). Tabs, gear and transport buttons are labelled in the current language. |
| Contrast (WCAG) | Main text passes AA (numbers under "Theme and consistency"). Disabled controls are too faint to recognise (U-28, U-37). Artist text over a bright cover measured 4.58:1 on the tested cover. A lighter cover would go lower, because the scrim is only 35% at the top. |

## Method notes

- Simulator `R1QA-UX` (created for this review and deleted at the end). App
  built with `xcodebuild -derivedDataPath /Volumes/Data2/dev/dd-r1control-qa-ux`.
- The R1 was read only with `GET /api/media/state`, and with `GET /api/state`
  piped through `jq` to the volume and voice keys. No raw body was printed.
- Edge swipe was driven through idb's synthetic swipe. Confirm U-09 on a
  phone before fixing.
- The offline states could not be produced on the simulator: the app kept a
  cached address after its preferences file was edited. They come from the
  JVM renders of the same composables with `SpeakerView.Offline`-shaped
  state.
