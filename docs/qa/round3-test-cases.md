# Round 3 test cases (2026-11-09)

The owner's round 3: drag and X on song rows as in mytube's cast queue, playlists on the
phone (shelf, All playlists, a playlist's page with Shuffle), a Next that follows an edited
list without cutting the song, News categories by drag, ten recent searches, a Language
picker, and the wake-word switch with "end the conversation" on Control.

How each case is checked:

- **auto** names the test. New QA tests live in `composeApp/src/commonTest/.../qa/round3/`
  and `composeApp/src/jvmTest/.../qa/round3/`. Other names are the implementer's tests.
- **sim** is the iOS simulator (the coordinator runs it).
- **R1** is the real speaker, at volume 0 after dark (spec, "Night rule").

A test named `R3B_nn_...` is red on purpose. It names the bug in `bug-report-round3.md`.

## A. Editing a song list

| case | steps | expected | check |
|---|---|---|---|
| R3-01 | Results of 10. Drag row 1's handle two rows down and let go. | The song is third. One move is reported, on release. | auto `EditableRowsGestureTest.aDragOfTwoRowsMovesTheSongTwoPlaces` |
| R3-02 | Drag a handle less than half a row. | Nothing moves. | auto `EditableRowsGestureTest.aDragLessThanHalfARowMovesNothing` |
| R3-03 | Rows of unequal height (one row has the failed-play line). Drag down past the tall row, up past it, the tall row down past a normal one, and one long travel over both. | Each step is taken at half of the passed row's own height. The long travel lands two places down. | auto `EditableRowsGestureTest.aTallerRowIsPassedAtHalfItsOwnHeight`, `Round3DragGestureTest.aRowDraggedUpPassesATallerRowAtHalfItsHeight`, `.theTallRowMovesDownPastANormalRowAtHalfTheNormalRowsHeight`, `.aLongDragAcrossATallAndANormalRowLandsTwoPlacesDown`. sim at the largest text size |
| R3-04 | List of 40. Hold a row at the bottom edge, then at the top edge. | The list scrolls under the held row and it lands past what was on screen. | auto `EditableRowsGestureTest.holdingAtTheBottomEdgeScrollsAndKeepsTheRowUnderTheFinger`, `.holdingAtTheTopEdgeScrollsBackUp`. sim |
| R3-05 | Drag a row anywhere but its handle. | The page scrolls. Nothing moves. | auto `EditableRowsGestureTest.aDragOutsideTheHandleScrollsTheListAndMovesNothing` |
| R3-06 | Tap X on a row, then on the only row of a one-song list. | The first is gone. The second X is drawn disabled and does nothing. | auto `EditableRowsGestureTest.theXRemovesASongAndIsDisabledAtOneSongLeft`. sim for the disabled look |
| R3-07 | A playlist plays. X a song before the playing one, drag nothing to nowhere. | Nothing is sent to the speaker. Next is still the speaker's own. | auto `Round3NextTest.anEditBeforeThePlayingSongSendsNothingAndNextIsTheSpeakers`, `MusicStoreTest.anEditIsNeverSentUntilNextIsPressed` |
| R3-08 | Add the same song to a playlist twice through the sheet. | Once in the list. The sheet's row wears the check. The second add writes nothing. | auto `Round3PlaylistsTest.theSameSongAddedTwiceIsInThePlaylistOnceAndSavedOnce`, `SongListTest.addSkipsSongsAlreadyThereAndRepeatsWithinTheAdd` |
| R3-09 | X a result, then "Add all" into a new playlist named "  Evening   songs ". | The playlist is "Evening songs" with the results as shown, without the X'd song. | auto `Round3PlaylistsTest.addAllAfterAnXSavesTheResultsAsTheyAreShown` |

## B. Next

| case | steps | expected | check |
|---|---|---|---|
| R3-10 | Play an unedited playlist. Press Next. | `/api/media/next`. | auto `MusicStoreTest.nextSendsNextWhenTheListIsUneditedAndPlayWhenItWasEdited` |
| R3-11 | X a song after the playing one. Press Next. | `/api/media/play` from the next kept song. | auto same test, `NextMoveTest.aSongRemovedAfterThisOneResendsFromTheNextKept` |
| R3-12 | X the playing song while the speaker's queue already goes the right way. | Next is `/api/media/next`. | auto `Round3NextTest.xingThePlayingSongUsesTheSpeakersNextWhenItAlreadyGoesTheRightWay` |
| R3-13 | Play a, b from b. Add c. X b. | Next is drawn and starts c. | auto `Round3NextTest.R3B_01_xingThePlayingSongHidesNextAlthoughASongAddedAfterThePressIsStillToCome` (red, R3B-01) |
| R3-14 | Playlist of 40, played from the top. The speaker reaches s29, the window's last. X s29. | Next is drawn and sends the window from s30. | auto `Round3NextTest.R3B_01_xingTheLastSongOfTheThirtySongWindowHidesNextAlthoughTheListGoesOn` (red, R3B-01) |
| R3-15 | Playlist of 40 playing s0. Drag s35 to right after s0. Next. | A play of at most 30 that starts at s35. | auto `Round3NextTest.aSongDraggedInFromBeyondTheWindowIsWhatNextPlays` |
| R3-16 | The list's last song plays. | No Next on either card. A press sends nothing. | auto `Round3NextTest.theLastSongOfTheListHasNoNext` |
| R3-17 | Double tap Next, unedited and edited. | One song skipped. Unedited sends one `next`. Edited sends one play. | auto `Round3NextTest.aDoubleTapOnAnUneditedListSendsOneNext`, `MusicStoreTest.aDoubleTapOnNextSkipsOneSong`. R1: the R1 answers `/api/media/play` before it applies it (`ControlServer.kt:93`, `:602-605`), so a second tap within the 1 s settle can re-send the same play. Check it lands on the same song |
| R3-18 | Play an edited playlist, then ask the R1 for a song by voice. Next. | `/api/media/next`, never the playlist. At the voice queue's end, no Next. | auto `Round3NextTest.nextAfterAVoiceRequestIsTheSpeakersOwn`. R1 |
| R3-19 | Shuffle a playlist of 6. X the third shuffled song. Drag the first saved row to the fifth. Next. | Starts the shuffled second. Sends the shuffled order without the X'd song. The drag does not touch it. The saved order is never rewritten. | auto `Round3NextTest.shuffleThenXThenDragFollowsTheShuffledOrderWithoutTheRemovedSong` |
| R3-20 | On the R1: play a playlist, X and drag songs on its page while a song plays. | The song keeps playing to its end with no gap or restart. | R1 |
| R3-21 | On the R1: tap a result, read `/api/media/state`. Press Next on an unedited list. | `queue` echoes the list sent, so Next uses `/next` with no 3-6 s download. | R1 (rationale open question 2) |
| R3-22 | A reading with and without a next song. | The Control card and the mini player draw Next together. | auto `Round3ControlTest.theControlCardAndTheMiniPlayerAgreeOnNext` |
| R3-23 | Tap Shuffle and delete that playlist while the play is on the wire. Also a new search landing while a tap's play is on the wire. | Next is the speaker's own. | auto `Round3NextTest.R3B_05_aPlaylistDeletedWhileItsPlayIsOnTheWayLeavesTheCardsWithoutNext`, `.R3B_05_aSearchThatLandsWhileATapsPlayIsOnTheWayLeavesTheCardsWithoutNext` (red, R3B-05) |

## C. Playlists

| case | steps | expected | check |
|---|---|---|---|
| R3-24 | Exactly 5 playlists. | Five cards and the "New playlist" card. No More. | auto `Round3PlaylistsTest.exactlyFivePlaylistsShowFiveCardsAndNoMore` |
| R3-25 | 6 playlists, then create one. | The five newest, More at the top right. The new one is the first card. | auto `Round3PlaylistsTest.sixPlaylistsShowTheFiveNewestAndMore`. sim |
| R3-26 | No playlists. Then 1-5. | The shelf is one "New playlist" card, above the recent searches. With some, the card ends the row. | sim |
| R3-27 | More, then All playlists. | Every playlist, newest first, under a "New playlist" row. A row opens its page. | sim |
| R3-28 | Open a playlist page. | Shuffle is the filled, first button. Play all is second. Rename and delete are at the end. | sim, `MusicStoreTest.shuffleIsPinnedBySeedAndLeavesThePlaylistOrderAlone` |
| R3-29 | Shuffle with the speaker unreachable. | The page's banner says why. The order is untouched. No "Playing from" line. | auto `Round3PlaylistsTest.aRefusedShuffleIsSaidOnThePage` |
| R3-30 | Rename with "   ". | Save is disabled in the dialog. The store also keeps the old name and saves nothing. | auto `Round3PlaylistsTest.aRenameWithAnEmptyNameKeepsTheOldName`. sim for the disabled button |
| R3-31 | Delete the playing playlist from its page. | Asked first. Then the page backs out and Next is the speaker's own. | auto `Round3PlaylistsTest.deletingThePlayingPlaylistBacksThePageOutAndHandsNextToTheSpeaker`. sim for the dialog |
| R3-32 | Play from a playlist, then a song by voice. | The page no longer says "Playing from this playlist". | auto `Round3PlaylistsTest.R3B_02_aPlaylistPageStillSaysPlayingFromHereAfterTheSpeakerMovedToAVoiceRequest` (red, R3B-02) |
| R3-33 | Process death on a playlist page: the page restored before storage answers. | The page waits (nothing drawn), then shows the playlist. It never backs out. | auto `Round3PlaylistsTest.aPlaylistPageRestoredBeforeTheFirstReadWaitsAndThenShows`, `PagesTest.theStackSurvivesTheSaver`. sim: kill the app in the background on a playlist page and reopen |
| R3-34 | Play a result, then search again. | The new results show. The old list still plays and stays with the store. | auto `Round3PlaylistsTest.aNewSearchKeepsTheResultsStillPlayingEditable` |
| R3-35 | The stored playlists do not decode. | The raw value is copied to `music.playlists.backup.<time>`. The shelf says they were kept aside and starts empty. The empty start is not saved. The first playlist made leaves the copy alone. | auto `Round3PlaylistsTest.keptAsidePlaylistsStartAnEmptyShelfThatSaysSoAndCanBeUsed`, `Round3StorageTest.unreadablePlaylistsAreKeptAsideAndTheFirstSaveLeavesTheCopyAlone` |
| R3-36 | Unreadable, and the backup write fails. | The shelf says so and offers nothing. No create writes over the value. | auto `Round3StorageTest.aFailedBackupLocksTheShelfAndNothingIsWrittenOverTheValue`, `MusicStoreTest.anUnreadableStoredValueIsNeverOverwritten` |
| R3-37 | Locked shelf (R3-36). A result's add, then "New playlist" and a name. | Either no sheet, or a sheet that says it cannot save. Never a sheet that closes with the song in no playlist. | auto `Round3PlaylistsTest.R3B_03_addingToANewPlaylistWhileThePlaylistsAreUnreadableLosesTheSongsSilently` (red, R3B-03) |
| R3-38 | Start the app three times over the same unreadable value. | One backup. | auto `Round3StorageTest.R3B_04_eachLaunchCopiesTheSameUnreadableValueAsideAgain` (red, R3B-04) |
| R3-39 | A save of the playlists fails. | The line under the shelf says the change may be lost. The change stays on screen. | auto `Round3PlaylistsTest.aFailedSaveIsSaidUnderTheShelf`, `MusicStoreTest.aFailedSaveIsSaidAndTheEditStaysUntilASaveWorks` |
| R3-40 | The add sheet, then "New playlist", with the iOS keyboard up. | The name field stays above the keyboard. The top bar stays under the status bar. | sim (charter §8, `OnFocusBehavior.DoNothing`) |

## D. News

| case | steps | expected | check |
|---|---|---|---|
| R3-41 | Bulletin on. Drag the first category's handle one row down. | One move on release. One save of the bulletin. | auto `Round3DragGestureTest.aNewsCategoryMovesByItsHandleWhileTheBulletinIsOn`, `SettingsViewModelTest.aDropReordersTheCategoriesAndSavesTheBulletinOnce` |
| R3-42 | Bulletin off. Drag a handle two rows. | Rows dimmed. Nothing moves. | auto `Round3DragGestureTest.aNewsCategoryCannotBeDraggedWhileTheBulletinIsOff` |
| R3-43 | A drop the R1 refuses. | The R1's order again, and the banner. | auto `Round3SettingsTest.aRefusedDropShowsTheR1sOrderAgainAndSaysWhy` |
| R3-44 | The R1 has a category this app has no label for. Drag another past it. | It keeps its place. One save. | auto `Round3SettingsTest.anUnknownCategoryKeepsItsPlaceInTheDrop` |
| R3-45 | On the R1: reorder, then "Phát thử". | The bulletin reads in the new order. | R1 |

## E. Recent searches

| case | steps | expected | check |
|---|---|---|---|
| R3-46 | A phone with 20 stored recents opens the Music tab, then searches. | 10 shown, newest first. After the search, still 10, the new one first. | auto `Round3StorageTest.twentyStoredRecentsShowTenAndTheNextSearchKeepsTen`, `PreferencesTest.aStoredTwentyReadsAsTen` |

## F. Language

| case | steps | expected | check |
|---|---|---|---|
| R3-47 | On Settings, Language, pick Tiếng Việt, then English, then Follow the phone. | The page, its title and the tab bar change at once. The choice is stored. | auto `Round3SettingsTest.aLanguageChosenOnItsPageRetitlesThatPageAtOnce`. sim: no flash, no lost page, Back still goes to Settings |
| R3-48 | Restart with a stored choice. | The first frame is in it. | auto `Round3SettingsTest.aStoredChoiceIsTheLanguageOfTheNextStart`, `LanguageTest.theGateWaitsForTheLanguageSoNoFrameIsDrawnInTheWrongOne` |
| R3-49 | Follow the phone, then change the phone's language. | The app follows on its next start. | sim |

## G. Control: wake word and end the conversation

| case | steps | expected | check |
|---|---|---|---|
| R3-50 | `wake_enabled` absent, true, false. | Absent: nothing drawn. True and false: the switch, showing that value. | auto `Round3ControlTest.theWakeSwitchIsDrawnOnlyWhenTheR1ReportsItAndShowsItsValue`, `MappingTest.aStateWithoutWakeEnabledMapsToAbsentControls`, `HttpSpeakerRepositoryTest.aBodyWithWakeEnabledCarriesTheSwitchThroughTheSnapshot` (constructed body, the named exception) |
| R3-51 | Music playing, paused or downloading, any voice state. | No "end the conversation". | auto `Round3ControlTest.endTheConversationIsNeverOfferedOverMusic` |
| R3-52 | Bluetooth in, R1 listening. | No "end the conversation". | auto `Round3ControlTest.endTheConversationIsNeverOfferedWithBluetoothIn` |
| R3-53 | Listening beside a stopped player. | Offered. The card shows nothing. | auto `Round3ControlTest.endTheConversationIsOfferedWhileListeningBesideAStoppedPlayer` |
| R3-54 | Speaking a reply with nothing playing, and listening with nothing loaded. | Offered. | auto `Round3ControlTest.endTheConversationIsOfferedWhileTheAssistantSpeaksOrListens` |
| R3-55 | The R1 asleep (`voice_awake` false). | Not offered. | auto `Round3ControlTest.endTheConversationIsNotOfferedWhileTheR1Sleeps` |
| R3-56 | Flip the switch with the R1 unreachable, then again with it back. | It snaps back at once with the banner. The retry is sent and clears the banner. | auto `Round3ControlTest.aFailedWakeWriteSnapsBackSaysWhyAndCanBeTriedAgain` |
| R3-57 | A taken write, then a reading still showing the old value. | No flicker back inside the 1.5 s hold. | auto `Round3ControlTest.aTakenWakeWriteHoldsAgainstAnOlderReading` |
| R3-58 | Press "end the conversation". | One `/api/voice/sleep`. The button is gone for 4 s. A refusal brings it back at once. | auto `ControlVoiceTest.endSendsTheRouteOnceAndTheButtonStaysAwayUntilTheHoldIsOver`, `.aRefusedEndBringsTheButtonBackAtOnce`. R1: is 4 s enough for the stop chime |
| R3-59 | On the R1, wake word off. Say the wake word, press the button, type in Chat, Play. | The word does nothing. The other three still wake it. | R1 |
| R3-60 | Wake word off, restart the R1 app. | Still off. | R1 |
| R3-61 | On the R1, press End mid-reply. | The reply stops and the R1 goes idle. Music never stops (the button is not drawn over it). | R1 |
| R3-62 | The two writes on the wire. | `/api/wake/enable?on=1` and `on=0`, `/api/voice/sleep`. | auto `HttpSpeakerRepositoryTest.theWakeWordAndEndSessionWritesUseTheirPathsAndPinTheFlag` |

## Only a simulator or the R1 can check

- **sim:**
  - R3-03 at the largest text size, R3-04, R3-06 and R3-25 to R3-28, R3-30 and R3-31 for the dialogs.
  - R3-33 process death, R3-40 the keyboard over the sheet's dialog, R3-47 and R3-49 the language.
  - The look items in `bug-report-round3.md`, UX section.
- **R1:**
  - R3-17 the double tap against the R1's asynchronous play, R3-18, R3-20 and R3-21 the queue echo.
  - R3-45, and R3-58 to R3-61.
  - None of the R1 routes are installed yet (owner, 2026-11-09).
