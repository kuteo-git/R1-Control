# Round 3 bug report (2026-11-09)

The owner's round 3 adds editable song lists, playlists on the phone with Shuffle, a Next that
follows an edited list without cutting the song, News by drag, ten recents, a Language picker,
and the wake-word switch with "end the conversation". This report lists what QA found wrong.
The cases are in `round3-test-cases.md`.

Each red test is named after its bug and stays red until the bug is fixed. Every test outside
`qa/round3` is green. Paths below are under `composeApp/src/commonMain/kotlin/com/r1control/app/`
unless they say otherwise.

## Bugs

### R3B-01. Removing the playing song can lose Next while songs are still to come (medium)

- **Symptom.** A playlist plays. The owner removes the song now playing. Next disappears from
  both cards, although the list still has songs after it. Two ways to get there:
  - A song added after the play started (an Add appends it).
  - A list longer than 30, when the speaker has reached the last song of the 30 it was sent.
  The speaker then runs into related songs, not the owner's list.
- **Evidence.**
  - `Round3NextTest.R3B_01_xingThePlayingSongHidesNextAlthoughASongAddedAfterThePressIsStillToCome`
    fails with "c is after the X'd song, but Next is not drawn".
  - `Round3NextTest.R3B_01_xingTheLastSongOfTheThirtySongWindowHidesNextAlthoughTheListGoesOn`
    fails with "ten songs are still to come, but Next is not drawn".
- **Suspected root cause.** `domain/model/Playing.kt:99-102`.
  - When the playing song is gone from the list, the next song is "the first song *sent* after
    it that survives".
  - If no song after it was sent, the anchor is `sequence.size`, and that reads as the end.
  - Sent ids are only the 30-song window, and they are fixed at the press. Songs added later, or
    beyond the window, can never be found this way.
  - The rationale's own table row ("the playing song itself X'd") has the gap. The fixer should
    find the playing song's place another way, for example the last surviving song sent *before*
    it, and then go one past.

### R3B-02. A playlist's page says "Playing from this playlist" when it is not (low)

- **Symptom.** The owner plays a playlist, then asks the R1 for a song by voice. The playlist's
  page still says "Đang phát từ danh sách này". The same happens after the speaker stops, or when
  it has run past the list into related songs.
- **Evidence.** `Round3PlaylistsTest.R3B_02_aPlaylistPageStillSaysPlayingFromHereAfterTheSpeakerMovedToAVoiceRequest`
  fails with "the speaker plays a song asked for by voice, yet the page says it plays from this
  playlist".
- **Suspected root cause.**
  - `domain/usecase/MusicStore.kt:75` answers "is this the list last *sent*".
  - `ui/music/PlaylistViewModel.kt:76` draws that as "playing".
  - The reading is not consulted. `nextMove` already asks the right question at `Playing.kt:92`:
    is the speaker's `video_id` among the sent ids.

### R3B-03. Adding to a new playlist while the playlists cannot be read drops the songs silently (medium)

- **Symptom.** The stored playlists did not decode, and the copy aside failed. The shelf says so
  and offers nothing. But every result row still has its add button, and "Add all" is still
  drawn. The sheet still offers "New playlist". The owner names it and taps Create. The sheet
  closes, and the songs are in no playlist. Nothing says why. This is a control that is drawn
  but does nothing.
- **Evidence.** `Round3PlaylistsTest.R3B_03_addingToANewPlaylistWhileThePlaylistsAreUnreadableLosesTheSongsSilently`
  fails with "the sheet took the name and closed; the song is in no playlist and nothing says why".
- **Suspected root cause.**
  - `ui/music/PlaylistPicker.kt:63` always draws "New playlist".
  - `ui/music/MusicViewModel.kt:232-251` opens the sheet and closes it on a name whatever the
    playlists are.
  - `domain/usecase/MusicStore.kt:190` ignores a create unless the playlists are Readable, and
    returns an id anyway.
  - The same applies while the first read is still Loading. That window is short.

### R3B-04. Every launch copies the same unreadable playlists aside again (low)

- **Symptom.** None on screen. The kept-aside start is never saved, because it is empty and the
  first value is dropped. So the stored value stays unreadable, and each start writes one more
  `music.playlists.backup.<time>` with the same content. This goes on until the owner creates a
  playlist. A large value grows SharedPreferences and NSUserDefaults with every start.
- **Evidence.** `Round3StorageTest.R3B_04_eachLaunchCopiesTheSameUnreadableValueAsideAgain`
  fails with "three starts left 3 copies of one value".
- **Suspected root cause.**
  - `data/repository/SettingsPreferencesRepository.kt:119-123` copies on every unreadable read.
    It does not check whether that raw value is already backed up.
  - `domain/usecase/MusicStore.kt:333-337` drops the first value (`drop(1)`), so the empty start
    never replaces the bad value.

### R3B-05. A play still on the wire can leave the pointer naming a gone list, and Next vanishes (low)

- **Symptom.** Both cards lose Next while the speaker's own queue has a next song. Two ways:
  - The owner taps Shuffle or a row and deletes that playlist before the R1 answers.
  - The owner taps a result, then a new search lands before the R1 answers.
  The window is the R1's reply time: 30-220 ms normally, up to 10 s on bad wifi.
- **Evidence.**
  - `Round3NextTest.R3B_05_aPlaylistDeletedWhileItsPlayIsOnTheWayLeavesTheCardsWithoutNext`
    fails with "the speaker's own queue has b next, but Next is not drawn".
  - `Round3NextTest.R3B_05_aSearchThatLandsWhileATapsPlayIsOnTheWayLeavesTheCardsWithoutNext`
    fails with "the speaker plays a, b, c and b is next, but Next is not drawn".
- **Suspected root cause.** `domain/usecase/MusicStore.kt:291-293` writes `Playing.FromList(ref, ...)`
  after the await, without checking that `ref` still exists.
  - `delete` (`:203-212`) and `showResults` (`:144-156`) keep the invariant only for a pointer
    that was already set.
  - A pointer to a gone list reads as an empty list. `nextMove` then answers None rather than the
    speaker's fallback (`Playing.kt:94-105`).

### R3B-06. A blank name is refused in two create paths but not in the third (low, no symptom today)

- **Evidence.**
  - `ui/music/MusicViewModel.kt:247-248` refuses a blank name.
  - `ui/music/MusicViewModel.kt:258` (`onNewPlaylist`) and `ui/music/PlaylistsScreen.kt:66`
    create with whatever `cleanPlaylistName` returns, an empty name included.
- **Note.** Today the dialog's Create is disabled for a blank name (`ui/components/Components.kt:498`),
  so no owner can reach it. The rule lives in the dialog and in one of three callers. The next
  caller that skips the dialog makes an "Untitled playlist" by accident.

### R3B-07. "End the conversation" is drawn over a stale reading (low, code read)

- **Symptom (inferred).** Wifi drops, and the card is Stale. The button is still drawn from the
  last good reading, and a press sends `/api/voice/sleep`.
  - If music started since that reading (a voice request), the R1's sleep path flushes it.
  - The charter's guard "a stale press must not stop a song" holds for the reading in hand,
    which is stale here.
- **Evidence.** `ui/control/ControlViewModel.kt:118` builds the voice row from the Stale
  snapshot. `:195-197` guards only on that row.
- **Note.** Not reproduced. Whether the R1 can start music between a failed poll and a press is
  for the real speaker.

## UX

Judged against the owner's words ("as in the mytube cast queue", "the shelf above recent
searches", "Shuffle") and mytube's feel.

- **UX-01. The shelf is gone while results show.** `ui/music/MusicScreen.kt:172-179` draws the
  shelf only in Idle. After a search, reaching a playlist means Clear first. Recents behave the
  same, so this is consistent. But the shelf was asked for as a way into playlists, and today
  it hides when songs are on screen to add.
- **UX-02. The mini player over a Music page drops the page stack.** `ui/App.kt:217` taps to
  Control with `PageStack.Tabs`, so Back cannot return to the playlist the owner was editing.
  mytube's rule is that Back returns to the place somebody was on.
- **UX-03. A result row has little room left for the title.** Each row has a handle (40 dp), a
  cover (56), the add button (48) and the X (48), inside a 16 dp gutter. On a 390 pt phone
  about 140 dp is left for the title and the artist (an estimate from the sizes in
  `ui/components/EditableRows.kt:201` and `ui/music/SongRows.kt`). Long Vietnamese titles will
  cut early. mytube's queue row has no add button. Look on the simulator.
- **UX-04. Shuffle is prominent, and right.** It is the filled first button, with Play all
  tonal beside it (`ui/music/PlaylistScreen.kt:147-148`). Rename and delete are quiet icons at
  the end, and delete asks first. No change needed.
- **UX-05. The X at one song is disabled, not hidden**, as in mytube (`EditableRows.kt:306`).
  On a one-result search it may read as broken. Check the disabled tint on the simulator.
- **UX-06. "End the conversation" vanishes for 4 s after a press, then can come back** if the
  R1 still reports the session (`ControlViewModel.kt:221`). With no feedback in between, the
  owner may read a return as "it did not work". A pressed or "ending..." state would say more.
- **UX-07. Reordering has no path without a finger drag.** The News arrows are gone
  (`NewsViewModel.kt:102-106`). The handle is a Button for a screen reader but has no move-up
  or move-down action, and the same holds for songs. mytube has the same gap.
- **UX-08. A new, empty playlist's page has no way in to fill it.** The hint says to search and
  use a song's add button (`playlistEmptyHint`). That is right, but the page offers no step
  towards it.
- **UX-09. With 1 to 5 playlists there is no All playlists page.** This is the owner's rule
  (More only above five), so it is not a bug. With five, the sixth playlist made pushes the
  oldest off the shelf, and More then appears.

## What only a simulator or the R1 can check

- **Simulator:**
  - The shelf's look, empty and full.
  - The All playlists page.
  - The disabled X and Create.
  - The add sheet with the iOS keyboard (`ModalBottomSheet` plus a `Dialog`).
  - Process death on a playlist page.
  - The language switch redrawing the whole app with no flash.
  - Drag at the largest text size.
  - UX-03.
- **Real R1:** none of the round-3 routes are installed yet.
  - Does `media.queue` echo what was posted (rationale question 2)? If not, every Next re-sends
    and costs a 3-6 s download.
  - Is a double tap during the R1's asynchronous `/api/media/play` (`ControlServer.kt:602-605`)
    still one song?
  - Is the 4 s end hold long enough?
  - Wake word off: are wake detections ignored while the button, typing, Play and the news alarm
    still wake it?
  - Does the setting survive an R1 app restart?
  - The News reading order after a drag.
