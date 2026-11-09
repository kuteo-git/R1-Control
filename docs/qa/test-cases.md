# R1 Control: test-case catalogue

Derived from `docs/spec.md`, `CLAUDE.md` and every behaviour in `composeApp/src` as of
2026-11-08. Each case names how it is covered:

- **existing** `Class.method`: a test that was already in the tree.
- **new** `Class.method`: a test added by this QA pass (files `commonTest/.../qa/*.kt`
  and `jvmTest/.../data/QaWireTest.kt`). A case marked **FAILS** goes red because the
  product is wrong; its bug is in `docs/qa/bug-report-tests.md`.
- **needs device**: only a running app shows it, with the reason.
- **code read**: a defect found by reading the code, recorded as a bug, no test reaches it.

Run: `source env.sh && ./gradlew jvmTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`.
Result of this pass: 240 tests, 11 failing (all 11 are product bugs), both platform compiles green.

## 1. Address entry and validation (first run, Settings > Advanced)

| ID | Area | Preconditions | Steps | Expected | Covered by |
|---|---|---|---|---|---|
| TC-001 | Address | none saved | launch | first-run page, no tabs | existing `AppViewModelTest.noAddressMeansTheFirstRunPage` |
| TC-002 | Address | address saved | launch | tabs open, address under the title | existing `AppViewModelTest.aSavedAddressOpensTheTabs` |
| TC-003 | Address | first run | type `192.168.1.20`, Connect | saved as `192.168.1.20:8088` | existing `R1AddressTest.portDefaultsTo8088`, `AddressViewModelTest.anAnsweringSpeakerIsSavedWithTheDefaultPortAndReported` |
| TC-004 | Address | first run | type `10.25.113.151:9000` | port 9000 kept | existing `R1AddressTest.hostAndPort` |
| TC-005 | Address | first run | type `  http://192.168.1.20:8088/ ` | accepted, scheme, padding and slash dropped | existing `R1AddressTest.forgivesPaddingSchemeAndTrailingSlash` |
| TC-006 | Address | first run | type `HTTP://192.168.1.20:8088` or `Http://r1.local` | accepted like lower-case `http://` | new `QaAddressTest.aCapitalisedSchemeIsStillPlainHttp` **FAILS** (T-08) |
| TC-007 | Address | first run | type `R1.local`, `r1:80`, `phicomm-r1` | host names accepted, lower-cased | existing `R1AddressTest.hostNames`, new `QaAddressTest.ipHostnameAndPortFormsAllRead` |
| TC-008 | Address | first run | type empty or spaces | "Type the speaker's address", nothing probed | existing `R1AddressTest.rejectsEmpty`, `AddressViewModelTest.anEmptyFieldIsInvalid` |
| TC-009 | Address | first run | type `256.1.1.1`, `1.2.3`, `a b`, `/path`, `[::1]`, `https://` | BadHost, nothing probed | existing `R1AddressTest.rejectsJunkHosts`, `AddressViewModelTest.anInvalidAddressNamesTheReasonAndProbesNothing` |
| TC-010 | Address | first run | type `192.168.1.20 :8088`, `192.168. 1.20` | rejected, not guessed | new `QaAddressTest.spacesInsideTheAddressAreRejectedNotGuessed` |
| TC-011 | Address | first run | type `1.2.3.4:`, `:0`, `:65536`, `:80a`, `:8o88` | BadPort | existing `R1AddressTest.rejectsJunkPorts`, new `QaAddressTest.badPortsNameThePort` |
| TC-012 | Address | first run | type `192.168.001.020` | read as `192.168.1.20` | new `QaAddressTest.leadingZerosInAnIpAreDecimalAndNotPassedOn` **FAILS** (T-09) |
| TC-013 | Address | first run, nothing at the address | Connect | "Nothing answered at X", not saved | existing `SaveAddressTest.anAddressThatDoesNotAnswerIsNotSaved`, `AddressViewModelTest.nothingAnsweringIsNotSaved` |
| TC-014 | Address | something answers but is not an R1 (404 or not JSON) | Connect | says it is not an R1, not "nothing answered" | code read (T-23) |
| TC-015 | Address | message shown | type a character | message cleared | existing `AddressViewModelTest.typingClearsAnOldMessage` |
| TC-016 | Address | Checking | tap Save again | ignored while checking | code read, `AddressViewModel.save` guard; no defect |
| TC-017 | Address | Advanced page, tabs open | save a new reachable address | tabs talk to the new one, subtitle updates | existing `AppViewModelTest.savingAnAddressOpensTheTabsAndAChangeReplacesIt`, `HttpSpeakerRepositoryTest.aNewlySavedAddressIsUsedByTheNextCall` |
| TC-018 | Address | new address saved while the old one was offline | return to Control | new speaker's card within a second | code read (T-24) |
| TC-019 | Address | probe | Connect at an address other than the saved one | probe hits the typed address | existing `HttpSpeakerRepositoryTest.probeAsksTheGivenAddressNotTheSavedOne` |
| TC-020 | Address | iOS, host name `r1.lan` | Connect | reaches the R1 over plain HTTP | needs device: ATS decides at run time (T-25) |
| TC-021 | Address | stored | relaunch | same address read back | existing `PreferencesTest.addressRoundTripsAndStartsUnset` |

## 2. Polling, staleness, errors and unreachable

| ID | Area | Preconditions | Steps | Expected | Covered by |
|---|---|---|---|---|---|
| TC-030 | Polling | tab open | open Control | first read at once | existing `SpeakerPollerTest.readsAtTheCadenceAndKeepsGoingThroughAFailure` |
| TC-031 | Polling | playing/downloading/assistant awake/receive window | wait | 1 s cadence; idle 3 s; after failure 5 s | existing `SpeakerPollerTest.cadenceFollowsWhatTheSpeakerIsDoing` |
| TC-032 | Polling | nobody collects | leave all tabs | polling stops 3 s later | existing `SpeakerFeedTest.pollsOnlyWhileSomebodyWatchesAndStopsShortlyAfter`, `SpeakerPollerTest.stopsReadingWhenNobodyCollects` |
| TC-033 | Polling | Control open | switch tabs quickly | polling not restarted | existing `SpeakerFeedTest.aQuickTabSwitchDoesNotRestartThePolling` |
| TC-034 | Polling | slow R1 | wait | reads never overlap | existing `SpeakerPollerTest.neverReadsTwiceAtOnce` |
| TC-035 | Polling | Android, app in background | press Home, wait minutes | no requests to the R1 | needs device: Compose keeps collecting in a stopped activity (T-02) |
| TC-036 | Stale | Online | one poll fails | last card kept, banner shown | existing `ControlViewModelTest.aDroppedPollKeepsTheLastCardAndSaysSo`, `SpeakerFeedTest.afterIsAPureTransition` |
| TC-037 | Stale | Stale | speaker answers again | banner gone, new card | new `QaControlTest.aRecoveredPollClearsTheBannerAndShowsTheNewCard` |
| TC-038 | Offline | unreachable from start | open Control | an offline state on the card, banner | existing `ControlViewModelTest.anUnreachableSpeakerIsABannerAndPollingGoesOn` (banner); code read: card is blank (T-13) |
| TC-039 | Offline | Offline | speaker comes back | card appears within 5 s, banner gone | existing `ControlViewModelTest.anUnreachableSpeakerIsABannerAndPollingGoesOn` |
| TC-040 | Refresh | command sent | wait | read at once, not after the idle poll | existing `SpeakerFeedTest.refreshReadsNowAndPublishesIt`, `ControlViewModelTest.aCommandRefreshesTheCardWithoutWaitingForThePoll` |
| TC-041 | Refresh | refresh fails | wait | left to the next poll, no crash | existing `SpeakerFeedTest.aFailedRefreshIsLeftToTheNextPoll` |
| TC-042 | Race | poll in flight when a command lands | pause | card stays Paused | new `QaControlTest.aReadingStartedBeforeACommandDoesNotUndoIt` **FAILS** (T-06) |
| TC-043 | Errors | no address | any call | NotConfigured, no request | existing `HttpSpeakerRepositoryTest.noRequestWhenNoAddressIsSaved` |
| TC-044 | Errors | network down | any call | Unreachable | existing `HttpSpeakerRepositoryTest.aNetworkFailureIsUnreachable` |
| TC-045 | Errors | HTTP 500 / 404 | any call | Refused, body never in the message | existing `HttpSpeakerRepositoryTest.aServerErrorIsARefusalWithoutItsBody`, new `QaWireTest.aSpeakerThatAnswersNotFoundIsARefusalNotUnreachable` |
| TC-046 | Errors | body not JSON | any read | BadReply naming the call, not the body | existing `HttpSpeakerRepositoryTest.anUnreadableReplyIsBadReplyWithoutItsBody` |
| TC-047 | Errors | `{ok:false,error}` | write | Refused(reason) | existing `HttpSpeakerRepositoryTest.aSearchThatTheR1RefusesIsARefusal` |
| TC-048 | Secrets | any | build | no Ktor Logging plugin | existing `ArchitectureGuardTest.noRequestLogging` |
| TC-049 | Cancellation | search in flight | cancel | not a failure, nothing remembered | new `QaSearchCancellationTest.aCancelledSearchRemembersNothingAndThrowsNoFailure`, `QaMusicTest.clearingDuringASearchDropsItsAnswerAndIsNotAFailure` |

## 3. Control tab: card states

| ID | Area | Preconditions | Steps | Expected | Covered by |
|---|---|---|---|---|---|
| TC-060 | Card | first read pending | open | Connecting, volume disabled | existing `ControlViewModelTest.showsConnectingUntilTheFirstReading` |
| TC-061 | Card | all idle | read | Nothing playing | existing `NowPlayingTest.nothingWhenEverythingIsIdle` |
| TC-062 | Card | `stopped` / `idle` with a kept queue, `video_id: "null"` | read | Nothing, no next | existing `NowPlayingTest.stoppedAndIdleWithAKeptQueueAreNothing`, `MappingTest.stoppedReportsTheStringNullAsItsVideoIdAndKeepsTheQueue`; new `QaWireTest.aStoppedPlayerFromTheFixtureIsNothingWithNoNext`, `QaSmallDecisionsTest.aStoppedPlayerWithAKeptQueueHasNoNext` |
| TC-063 | Card | playing | read | cover, title, artist, seek bar, pause, stop | existing `NowPlayingTest.playingMusic`, `MappingTest.fixturesFlowThroughToTheCard` |
| TC-064 | Card | paused | read | Play glyph, position kept | existing `NowPlayingTest.pausedKeepsItsPosition` |
| TC-065 | Card | downloading, percent -1..120 | read | progress clamped 0..100, no seek, play disabled | existing `NowPlayingTest.downloadingCarriesAClampedPercentAndCannotSeek` |
| TC-066 | Card | queue with a song after the current | read | Next enabled; last or unqueued song: disabled | existing `NowPlayingTest.nextOnlyWhenTheQueueHasOneAfterTheCurrentSong`, new `QaWireTest.thePlayingFixtureHasNextOnlyIfItsSongIsNotLastInTheQueue` |
| TC-067 | Card | position past duration / negative | read | held inside 0..duration | existing `NowPlayingTest.positionIsHeldInsideTheDuration` |
| TC-068 | Card | R1 playing out to a BT speaker | read | "on <speaker name>" | existing `NowPlayingTest.playingOutToABluetoothSpeakerNamesIt` |
| TC-069 | Card | phone streaming in | read | Bluetooth-in card beats music, no seek/next | existing `NowPlayingTest.aPhoneStreamingInBeatsMusic`, `MappingTest.bluetoothIncomingFromTheFixtureWithTheSpecsEdit` |
| TC-070 | Card | incoming address not in devices | read | named by its address | existing `NowPlayingTest.anUnlistedIncomingDeviceIsNamedByItsAddress` |
| TC-071 | Card | voice awake LISTENING / SPEAKING | read | Listening… / Speaking with the last chat line | existing `NowPlayingTest.theAssistantNeedsToBeAwakeAndListeningOrSpeaking` |
| TC-072 | Card | music playing and SPEAKING awake | read | music card (measured rule) | existing `NowPlayingTest.musicOutranksTheAssistant` |
| TC-073 | Card | cover URL `=w120-h120` | show | asked at 640 px | existing `FormatTest.coverSizeIsRewrittenOnlyWhereTheHostTakesIt` |
| TC-074 | Card | seek bar clock | show | `m:ss`, `h:mm:ss` from an hour | existing `NowPlayingTest.clockFormat` |
| TC-075 | Card | Previous button | tap | either works or is not drawn | code read (T-15) |

## 4. Control tab: transport and volume pill

| ID | Area | Preconditions | Steps | Expected | Covered by |
|---|---|---|---|---|---|
| TC-080 | Transport | playing / paused | tap play-pause | pause / resume sent, card refreshed | existing `ControlViewModelTest.playPausePausesAPlayingSongAndResumesAPausedOne`, `TransportTest.toggleDependsOnThePhase` |
| TC-081 | Transport | downloading or nothing | tap play-pause | nothing sent | existing `ControlViewModelTest.playPauseDoesNothingWhileDownloadingOrWithNothingPlaying` |
| TC-082 | Transport | playing | Next, Stop | sent; failure is a banner, not a crash | existing `ControlViewModelTest.nextAndStopAreSentAndAFailureIsShownNotThrown` |
| TC-083 | Transport | command failed, speaker back | wait | banner goes away (no close button on Control) | new `QaControlTest.aFailedCommandsBannerGoesAwayOnceTheSpeakerAnswersAgain` **FAILS** (T-03) |
| TC-084 | Seek | playing | drag | no seek while dragging; one seek on release | existing `ControlViewModelTest.seekIsSentOnceOnReleaseNotWhileDragging` |
| TC-085 | Seek | released | wait 1.4 s / 1.6 s | finger's position held, then the speaker's | new `QaControlTest.theSeekBarHoldsTheFingersPositionForAMomentAfterReleaseThenFollowsTheSpeaker` |
| TC-086 | Seek | negative position | seek | sent as 0 | existing `TransportTest.seekNeverGoesBelowZeroAndFailuresAreOutcomes` |
| TC-087 | Volume | 15 steps | drag | shown and sent value snapped like the panel | existing `VolumeScaleTest.*`, `VolumeSetterTest.valuesAreSnappedToTheScaleBeforeSending` |
| TC-088 | Volume | 15 steps | drag to 2 % then 98 % | 0 and 100 at the edges, last sent is 100 | new `QaControlTest.volumeSnapsToBothEdgesAndTheReleaseValueIsTheLastSent`, `QaSmallDecisionsTest.volumeWithinHalfAStepOfAnEdgeGoesToTheEdge` |
| TC-089 | Volume | dragging | many moves | throttled, newest value wins, release value last | existing `VolumeSetterTest.throttlesADragAndTheLastValueSentIsTheReleaseValue`, `ControlViewModelTest.volumeIsSnappedToTheHardwareScaleAndTheLastSentIsTheReleaseValue` |
| TC-090 | Volume | dragging, polls arrive | hold 3 s, release | finger's value shown, held 1.5 s after release | existing `ControlViewModelTest.theShownVolumeIsTheFingersWhileDraggingAndForAMomentAfter`, `HeldValueTest.*` |
| TC-091 | Volume | set to 60 from the app, then changed on the R1 (voice, panel) | drag back to 60 | 60 sent again | new `QaControlTest.aLevelSentBeforeIsSentAgainAfterTheSpeakerMovedAway` **FAILS** (T-01) |
| TC-092 | Volume | send fails | drag | banner, same value retried next time | existing `VolumeSetterTest.aFailedSendIsReportedAndTheSameValueIsTriedAgain` |
| TC-093 | Volume | pill | tap / drag at x | percent from the finger's x, clamped | existing `ControlViewModelTest.fingerXMapsToPercentAcrossThePill` |
| TC-094 | Volume | Connecting/Offline | drag | pill disabled | existing `ControlViewModelTest.showsConnectingUntilTheFirstReading` |
| TC-095 | Volume | real R1 at night | drag | heard at the level shown | needs device: the speaker's own rounding and Bluetooth index |

## 5. Mini player

| ID | Area | Preconditions | Steps | Expected | Covered by |
|---|---|---|---|---|---|
| TC-100 | Mini | music on Music/Chat tab | look | bar with cover, title, toggle | existing `MiniPlayerViewModelTest.hiddenWhenNothingPlaysShownWhenMusicDoes` |
| TC-101 | Mini | playing / paused | tap toggle | pause / resume | existing `MiniPlayerViewModelTest.toggleFollowsThePhaseAndAPausedSongIsShownAsNotPlaying` |
| TC-102 | Mini | downloading | look | toggle disabled | existing `MiniPlayerViewModelTest.aDownloadingSongCannotBeToggled` |
| TC-103 | Mini | toggle fails | tap | a visible failure | code read (T-12) |
| TC-104 | Mini | keyboard up / Settings open | look | bar hidden | needs device: inset arithmetic |

## 6. Music tab

| ID | Area | Preconditions | Steps | Expected | Covered by |
|---|---|---|---|---|---|
| TC-110 | Fields | fresh | open | one empty field, Search disabled | existing `MusicViewModelTest.startsWithOneEmptyKeywordAndNoSearch` |
| TC-111 | Fields | one field | add, remove | never below one | existing `MusicViewModelTest.keywordFieldsAddAndRemoveButNeverBelowOne` |
| TC-112 | Fields | six fields | tap Add | capped; the button says so | existing `MusicViewModelTest.fieldsAreCappedSoTheListStaysUsable`; code read: button stays live (T-17) |
| TC-113 | Search | blank / whitespace keywords | Search | nothing sent | existing `MusicViewModelTest.aBlankSearchDoesNothing`, `SearchMusicTest.nothingToSearchMakesNoCallAndRemembersNothing`, new `QaMixAndRecentsTest.blankKeywordsAreNotKeywords` |
| TC-114 | Search | repeated keyword (case, spaces, Vietnamese) | Search | searched once, first spelling | existing `SearchMusicTest.blanksAndRepeatsAreNotSearched`, new `QaMusicTest.theSameVietnameseKeywordTwiceIsSearchedOnce` |
| TC-115 | Search | two keywords | Search | in parallel, interleaved | existing `SearchMusicTest.searchesEachKeywordAndInterleaves`, `MusicViewModelTest.searchMixesKeywordsAndShowsSearchingFirst` |
| TC-116 | Interleave | uneven / empty lists | mix | round robin, lists run out on their own | existing `MixInterleavedTest.*`, new `QaMixAndRecentsTest.noListsAtAllIsNoSongs` |
| TC-117 | Dedupe | same video in two or three keywords | mix | once, at its first round-robin place | existing `MixInterleavedTest.aSongInTwoListsKeepsItsFirstPlace`, new `QaMixAndRecentsTest.threeKeywordsWithSongsSharedAcrossThem`, `QaMusicTest.aSongFoundByTwoKeywordsIsListedOnceAtItsFirstPlace` |
| TC-118 | Search | one keyword fails | Search | songs of the others plus "could not search for X" | existing `SearchMusicTest.oneFailedKeywordDoesNotFailTheOthersAndIsNamed`, `MusicViewModelTest.failedKeywordsAreReportedNextToTheSongsFound` |
| TC-119 | Search | all fail | Search | banner with Try again, not remembered | existing `SearchMusicTest.whenEveryKeywordFailsNothingIsRemembered` |
| TC-120 | Search | no results | Search | "Nothing found" | existing preview `MusicNoResultsPreview`; needs device to look |
| TC-121 | Search | a search running | start another | the last question wins | existing `MusicViewModelTest.theLastQuestionWins` |
| TC-122 | Search | running | tap Close (clear) | Idle, not a failure, not remembered | new `QaMusicTest.clearingDuringASearchDropsItsAnswerAndIsNotAFailure` |
| TC-123 | Wire | Vietnamese keyword | search | `q` percent-encoded UTF-8 | existing `HttpSpeakerRepositoryTest.searchReadsFixtureResultsAndSendsTheKeywordEncoded`, new `QaWireTest.aVietnameseSearchKeywordIsSentPercentEncodedAsUtf8` |
| TC-124 | Wire | result rows without id | map | dropped | existing `MappingTest.searchResults` |
| TC-125 | Play | results | tap row i | whole list from row i | existing `MusicViewModelTest.tappingARowPlaysTheListFromThatRowAndAFailureIsABanner`, `PlayAndChatTest.playSendsTheWindow` |
| TC-126 | Play | 45 results | Play all | first 30, start 0 | new `QaMusicTest.playAllOfMoreThanThirtySendsTheFirstThirtyFromTheTop` |
| TC-127 | Window | 31/45 results | tap first / last / near end | window of 30 that holds the tapped song, matching the panel | existing `PlayAndChatTest.windowKeepsTheTappedSongAndThoseAboveItWhenLong`; new `QaQueueWindowTest.*`, `QaMusicTest.tappingNearTheEndOfALongListSendsTheWindowEndingThere` |
| TC-128 | Window | any length 1..70, any tap | window | tapped song is the start, window contiguous, size min(n,30) | new `QaQueueWindowTest.theTappedSongIsAlwaysTheOneThatStartsAndTheWindowIsContiguous` |
| TC-129 | Wire | Vietnamese titles | play | body UTF-8, `application/json; charset=utf-8`, empty fields as `""` | existing `HttpSpeakerRepositoryTest.playBodyIsSentAsJsonWithAnExplicitUtf8Charset`, `.playBodyBytesAreUtf8AndCarryTheWholeQueue` |
| TC-130 | Play | empty list | play | nothing sent | existing `PlayAndChatTest.playOfNothingCallsNothing` |
| TC-131 | Recents | finished search | look | newest first, keyword set + time | existing `MusicViewModelTest.aFinishedSearchJoinsTheRecentsNewestFirst`, `RecentSearchTest.newestFirst` |
| TC-132 | Recents | same set, other case/order/spaces | search | replaces the old entry, newer spelling kept | existing `RecentSearchTest.sameKeywordsIgnoringCaseAndPaddingReplaceTheOldEntry`, `.keywordOrderDoesNotMakeANewEntryAndTheNewerOrderWins`, new `QaMixAndRecentsTest.vietnameseKeywordsCompareWithoutCase` |
| TC-133 | Recents | 20 entries | new search | oldest dropped | existing `RecentSearchTest.capDropsTheOldest`, new `QaMixAndRecentsTest.aNewSearchAtTheCapDropsTheOldest` |
| TC-134 | Recents | 20 entries | repeat the oldest | moves to the top, nothing dropped | new `QaMixAndRecentsTest.repeatingTheOldestAtTheCapMovesItUpAndDropsNothing` |
| TC-135 | Recents | tap a recent | tap | fields filled, search runs | existing `MusicViewModelTest.tappingARecentFillsTheFieldsAndRunsItAgain` |
| TC-136 | Recents | stored entry without keywords | tap it | one empty field left | new `QaMusicTest.aRecentWithNoKeywordsLeavesOneFieldToTypeIn` **FAILS** (T-10) |
| TC-137 | Recents | storage | relaunch | Vietnamese keywords round trip | existing `PreferencesTest.recentSearchesRoundTripWithAccents`, new `QaMusicTest.aRecentSearchKeepsItsVietnameseKeywordsAsTyped` |
| TC-138 | Recents | corrupt store / write failure | search | empty history, results still shown | existing `PreferencesTest.aCorruptHistoryIsEmptyNotACrash`, `SearchMusicTest.aHistoryThatCannotBeWrittenDoesNotLoseTheResults` |
| TC-139 | Fields | Telex keyboard | type `Thanhf Ngaan` fast | `Thành Ngân`, caret at the end | needs device: IME composition only exists on a real keyboard (T-21) |

## 7. Chat tab

| ID | Area | Preconditions | Steps | Expected | Covered by |
|---|---|---|---|---|---|
| TC-150 | Chat | first read pending | open | spinner, not "no messages" | existing `ChatViewModelTest.isNotLoadedUntilTheFirstReadingSoEmptyIsNotShownForStillAsking` |
| TC-151 | Chat | empty log | open | "No messages yet…" | existing preview; needs device to look |
| TC-152 | Chat | log | open | oldest first, user right, time under | existing `ChatViewModelTest.showsTheConversationOldestFirstAndFollowsNewOnes` |
| TC-153 | Chat | unreachable from start | open | an offline state, not a spinner for ever | existing `ChatViewModelTest.anUnreachableSpeakerIsABannerNotAnEmptyChat` (banner); code read (T-14) |
| TC-154 | Chat | Stale then back | wait | messages kept, banner cleared | new `QaChatTest.aDroppedPollKeepsTheConversationAndRecoveryClearsTheBanner` |
| TC-155 | Send | draft with spaces, Vietnamese | Send | trimmed line sent, field cleared | existing `ChatViewModelTest.sendingClearsTheFieldAndSendsTheTrimmedLine`, new `QaChatTest.vietnameseIsSentTrimmedAndExactlyAsTyped` |
| TC-156 | Send | blank draft | Send | nothing sent, field kept | existing `ChatViewModelTest.aBlankDraftIsNotSentAndStaysInTheField`, `PlayAndChatTest.chatTrimsAndIgnoresBlank` |
| TC-157 | Send | refused | Send | text back in the field, banner | existing `ChatViewModelTest.aRefusedMessageComesBackToTheFieldWithABanner` |
| TC-158 | Send | failed, speaker back | wait | banner goes away (no close button) | new `QaChatTest.aFailedSendsBannerGoesAwayOnceTheSpeakerAnswersAgain` **FAILS** (T-03) |
| TC-159 | Wire | `+`, `&`, Vietnamese in the line | Send | arrives as typed (`+` encoded `%2B`) | existing `HttpSpeakerRepositoryTest.sayPutsTheTrimmedTextInTheQueryPercentEncoded`, new `QaWireTest.aPlusAndAnAmpersandInAChatLineReachTheSpeakerAsTyped` |
| TC-160 | Wire | Vietnamese chat in a reply with no charset | read | UTF-8 text intact | new `QaWireTest.aVietnameseChatLineInAReplyWithNoCharsetReadsAsUtf8` |
| TC-161 | Wire | unknown sender | read | assistant | existing `MappingTest.unknownKeysAreIgnoredAndAUserSenderIsTheUser` |
| TC-162 | Pinning | at the bottom | a message arrives | list follows it | needs device: scroll position (T-19 once the log holds 50) |
| TC-163 | Pinning | scrolled up | a message arrives | list left alone | needs device |
| TC-164 | Pinning | at the bottom | open the keyboard | newest message stays visible | needs device (T-20) |

## 8. Settings

| ID | Area | Preconditions | Steps | Expected | Covered by |
|---|---|---|---|---|---|
| TC-170 | Home | tabs | tap gear | list Audio, Voice, News, Bluetooth, Advanced; back returns | needs device: navigation |
| TC-171 | Audio | open | load | loudness, EQ switch, bands from `/api/state` | existing `AudioViewModelTest.loadsTheAudioSettings`, `MappingTest.settingsPart` |
| TC-172 | Audio | load fails | Try again | loads | existing `AudioViewModelTest.aFailedLoadIsShownAndCanBeRetried` |
| TC-173 | Audio | dragging loudness | drag | screen at once, one write after a pause, steps of 100, 0..2000 | existing `AudioViewModelTest.dragsMoveTheScreenAtOnceAndWriteOnceAfterTheFingerPauses`, `.loudnessStaysInItsRange` |
| TC-174 | Audio | release | lift | written at once | existing `AudioViewModelTest.liftingTheFingerWritesAtOnceSoLeavingThePageLosesNothing` |
| TC-175 | Audio | EQ switch, bands | toggle, drag | `eq_enabled`, `eq_bands` comma-joined, in range | existing `AudioViewModelTest.theEqualiserSwitchWritesAtOnce`, `.aBandWritesEveryBandsGainInOrderAndStaysInRange`, `HttpSpeakerRepositoryTest.eqBandsAreCommaJoined` |
| TC-176 | Audio | write refused | drag | banner | existing `AudioViewModelTest.aRefusedWriteIsABannerAndTheSliderStaysWhereTheFingerPutIt` |
| TC-177 | Audio | labels | look | dB as value/100, Hz/kHz | existing `FormatTest.decibels`, `.frequencies` |
| TC-178 | Audio | Vietnamese | look | loudness named as the panel names it | code read (T-22) |
| TC-179 | Voice | tts host | open | list from `<tts_host>/voice`, current checked | existing `VoiceViewModelTest.listsTheServersVoicesWithTheCurrentOne`, `HttpSpeakerRepositoryTest.voicesAreAskedAtTheTtsHost` |
| TC-180 | Voice | no tts host | open | says so, not a failure | existing `VoiceViewModelTest.noVoiceServerIsSaidNotFailed` |
| TC-181 | Voice | choose | tap | check moves, POST sent; same voice sends nothing; refusal moves it back | existing `VoiceViewModelTest.choosingMovesTheCheckAndTellsTheServer`, `.choosingTheCurrentVoiceSendsNothing`, `.aRefusalPutsTheCheckBack` |
| TC-182 | Voice | slow refusal of b, then c works | tap b, tap c | c checked | new `QaVoiceTest.aSlowRefusalDoesNotUndoALaterChoiceThatWorked` **FAILS** (T-07) |
| TC-183 | Voice | tts host unusable | open | "bad reply", not "check your wifi" | new `QaWireTest.anUnusableTtsHostIsABadReplyNotUnreachable` **FAILS** (T-11) |
| TC-184 | Voice | "Selected voice" row | tap | nothing pretends to be a button | code read (T-18) |
| TC-185 | News | open | load | enabled, time, voice, categories, server voices | existing `NewsViewModelTest.loadsTheBulletinAndOffersTheServersVoices`, `.aCurrentVoiceTheServerDoesNotListIsStillOffered`, `.anUnreachableVoiceServerStillLetsTheBulletinBeEdited` |
| TC-186 | News | steppers | tap | wrap, two digits | existing `NewsViewModelTest.steppersWrapAndKeepTwoDigits`, `NewsTimeTest.*`, new `QaSmallDecisionsTest.newsTimeReadsPaddedTextAndRejectsImpossibleTimes` |
| TC-187 | News | categories | toggle, move | order kept, ends stay | existing `NewsViewModelTest.categoriesToggleAndMoveButTheEndsStayPut` |
| TC-188 | News | Save | tap | whole draft, UTF-8 body | existing `NewsViewModelTest.saveSendsTheWholeDraftAndSaysSaved`, `HttpSpeakerRepositoryTest.newsSaveUsesTheSameCharsetBecauseItCarriesAVietnameseVoiceName` |
| TC-189 | News | edited, not saved | Phát thử | the draft on screen is what plays (panel saves first) | new `QaNewsTest.aTestPlaysWhatIsOnScreenSoTheDraftIsSavedFirst` **FAILS** (T-05) |
| TC-190 | News | refused save | Save | banner | existing `NewsViewModelTest.aRefusedSaveIsABanner` |
| TC-191 | Bluetooth | open | wait | read at once, polled 1 s, stops on leave | existing `BluetoothViewModelTest.readsAtOnceAndKeepsPollingWhileOpen`, `.leavingThePageStopsThePolling` |
| TC-192 | Bluetooth | first read fails | wait | recovers by itself | existing `BluetoothViewModelTest.aFailedFirstReadIsAFailureWithRetryByPolling`, new `QaBluetoothTest.aFailedFirstReadRecoversByItselfThroughThePoll` |
| TC-193 | Bluetooth | failed state | Try again | does something | code read (T-16) |
| TC-194 | Bluetooth | read fails then works | wait | banner gone | new `QaBluetoothTest.aDroppedReadsBannerGoesAwayWhenTheSpeakerAnswersAgain` **FAILS** (T-04) |
| TC-195 | Bluetooth | actions | enable, auto, scan, receive, pair, connect, disconnect | command then read, paths and `on=1/0` | existing `BluetoothViewModelTest.everyActionIsACommandAndThenARead`, `HttpSpeakerRepositoryTest.bluetoothWritesUseTheirPaths` |
| TC-196 | Bluetooth | forget | tap Forget, cancel / confirm | asked first | existing `BluetoothViewModelTest.forgetNeedsAConfirmationAndCancelSendsNothing` |
| TC-197 | Bluetooth | refused | action | banner | existing `BluetoothViewModelTest.aRefusedActionIsABanner` |
| TC-198 | Bluetooth | receive window | tap Receive | countdown, button off while open | needs device: R1 countdown |
| TC-199 | Advanced | restart | tap, confirm / cancel | asked first; refused is a banner | existing `AdvancedViewModelTest.restartNeedsAConfirmation`, `.aRefusedRestartIsABanner` |

## 9. Language and wire

| ID | Area | Preconditions | Steps | Expected | Covered by |
|---|---|---|---|---|---|
| TC-210 | i18n | device `vi-VN` / `en-VN` / `VI` | launch | Vietnamese / English / Vietnamese | new `QaSmallDecisionsTest.theLanguageFollowsOnlyThePrimarySubtag` |
| TC-211 | i18n | any screen | build | no literal in a composable | existing `UntranslatedGuardTest.*` |
| TC-212 | i18n | both languages | build | every word in both (compile-time) | the `Strings` interface |
| TC-213 | Arch | build | scan | dependency direction, no wire format outside data | existing `ArchitectureGuardTest.*` |
| TC-214 | Wire | fixtures | map | idle, playing, paused, stopped, BT, voices, missing fields neutral | existing `MappingTest.*` |
| TC-215 | Wire | reply declares no charset | read | decoded as UTF-8 | new `QaWireTest.aVietnameseChatLineInAReplyWithNoCharsetReadsAsUtf8` |

Total: 159 cases.
