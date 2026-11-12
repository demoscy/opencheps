# Board calibration and current-position recognition

Use OpenCheps for puzzles, study of completed positions, and practice against bots.
There is no move execution. The app has no account, analytics, cloud recognition,
or Internet permission. Screenshots remain in memory and are never saved/uploaded.

## On the phone

1. Open an existing Chess.com puzzle, without returning to its starting position.
   Use a normal 2D piece theme. Start OpenCheps and share Chess.com as a full-screen
   app, or share the entire display. Select the side you want to analyze with
   **White/Black selection** (the pawn icon in the overlay).
2. Tap **Set board area**. Drag the square or its corners to match the exact outer
   edges of the playable 8×8 board, excluding outside labels, clocks and borders.
   **Save** remembers this layout. **Cancel** changes nothing. **Auto** clears all
   remembered manual areas and restores the automatic detector.
3. Check **White below / Black below** matches the board's orientation; tap to flip
   the mapping if necessary. This is independent of whose turn it is.
4. Check **Turn: White / Black** matches the actual position; tap to change it.
   On a fresh arbitrary position the default is the selected “I play” side.
   The exact opening setup defaults to White's turn. A still image cannot reveal
   the turn or move history reliably. Arrows appear only for the selected side.
5. Wait for two matching, confident frames and analysis. Move in a puzzle or bot
   practice and verify old arrows disappear, then return for your next turn.
   A quick pair of independent quiet moves is supported. Ambiguous jumps clear
   the old analysis and ask you to tap the circular **Read current position**
   button. It reads the position on screen and defaults the turn to “I play”;
   correct Turn afterward if necessary. It never requests the starting setup.
6. Switch between puzzle and bot layouts. Calibrate each different layout once.
   Up to eight normalized areas are remembered across restarts; the recognizer
   selects a compatible, confident saved crop, with near ties favoring the active
   crop. A crop still must pass piece-confidence and position checks. If no crop
   fits, **Set board area** in that layout. This does not infer unknown new board
   bounds, and recognition confidence is not a guarantee of correctness.

## Bubble and settings during capture

Tap the **OpenCheps logo** to minimize or expand controls, and drag the logo to
move them. The minimized strip is 152×56 dp with three 48 dp touch targets:
logo, selected White/Black pawn and the blue **Read current position** button.
Tap the pawn to switch sides; re-read works without expanding. It retains normal
Pause and does not hide analysis arrows. The strip hides for clean entire-screen
captures; only its current bounds are masked in change probes. Rotation clamps
it to the new usable display. The smaller expanded panel uses a gear for Settings,
Pause/Stop icons and borderless actions, and scrolls on short screens.

Tap the **gear** to bring the existing app screen forward. Capture permission
remains active while analysis and overlays pause. Change settings and use
**Return to board**, Back or switch apps. A compositor delay, fresh-frame check
and epoch barrier prevent the settings screen or obsolete results entering
analysis. The current pause/turn/orientation are preserved unless the selected
analysis side changes, which resets position/orientation as the panel toggle does.
The app disables Start while a capture session exists and updates its controls if
capture stops. Rotation/recreation and quick reentry cancel older resume tasks.

Phone checks:

- Tap the logo while running and paused. Drag it to all edges; tap to expand. Arrows
  continue only when running, and a drag/cancel should not toggle the panel. Use
  the minimized pawn and blue read button; verify side persistence and fresh reads.
- Rotate with the bubble visible. Expand in landscape and check scrolling and
  Stop/Settings access. Test large display/text sizes and navigation gestures.
- Open Settings without stopping capture. Change think time, choices, strength,
  line and side; Return to board and check the changes. Confirm no new capture
  permission dialog, stale arrows or analysis of the settings page.
- Repeat with normal Pause enabled; it must remain paused. Try rapid reopen/Back,
  Home, rotation in Settings, notification Stop and overlay permission revocation.
  No bubble/panel should remain after Stop. A new session starts expanded.

## Practice strength and study line

In OpenCheps settings, **Move choices** selects one to five candidate arrows,
defaulting to three. Saved older choice counts are retained. Green is the highest
displayed score, followed by amber, blue, purple and pink. **★** marks the engine's
selected move and can be on a lower-scoring color when strength is limited.
One-choice mode keeps that Elo-selected move and colors it green. Fewer arrows appear if
there are fewer legal choices or the search returns fewer candidates.

Optionally check **Limit practice strength**, then select 1320–3190 Elo on Stockfish 18's
approximate calibration scale. It is not a Chess.com rating or human model.
Uncheck to restore full strength; the selected practice value is remembered.
Think time no longer displays an invented Elo estimate.
The cap applies to the ★ selected move; additional arrows are analysis alternatives.

Enable **Show a 3-move study line** for up to three moves per side, alternating
White/Black across six half-moves. It is one possible continuation, not a forecast.
It can end earlier near mate or when search provides less. Elo limiting applies
to the chosen next move; future plies illustrate an engine variation rather than
independently weakened play at each step. A restricted follow-up search, at most
500 ms extra, keeps the line rooted in Stockfish's chosen move even when that
move differs from its strongest analysis candidate. Handicap is disabled for
this fixed-root continuation and reapplied on the next selection; complete
same-root variations survive later truncated engine reports. No automatic moves or
concealment behavior is added.

Phone checks: change the practice rating and return to the same position; verify
its old recommendation clears/recalculates. Toggle back to full strength. Enable
the line and check its first move equals the ★ selected arrow, White/Black alternate,
and the panel remains readable. Pause or move to an unclear board and confirm
both old evaluation and continuation clear. Restart to check settings persist.
Change Move choices between one and five on the same stationary position and
verify the recommendation refreshes, distinct arrows appear, and the line still
follows ★. Relaunch to verify the choice count persists.

For quicker puzzle or bot suggestions, set **Think time** to **100–300 ms** and
disable **Show a 3-move study line**. The default remains 1500 ms; existing saved
times are retained, with invalid values clamped to 100–3000 ms. The optional line
performs its follow-up search only when enabled. Changing either setting refreshes
an unchanged position, and a result using obsolete settings is discarded. Three
candidate arrows are still available at the shorter search time. Capture, model
inference, two matching frames and scheduling remain part of the total latency.
The 100 ms setting is not a promise of a 100 ms response or human-like play.

Phone speed check: on the same stationary puzzle/bot board, compare 1500 ms with
100 ms and the line off. Then enable the line and verify it follows ★;
disable it again and verify it clears. Change time during a search and confirm
the next result reflects the new value. Check persistence after reopening settings.

## Engine scores on arrows

Each arrow chip shows its own engine score, with a matching colored outline.
**+0.80** favors the selected analysis side, **-1.25** favors its opponent,
**M3** is a mating score and **-M2** a losing mate score. **—** means no score was
reported. The star denotes the selected move; with Elo limiting it is not
necessarily the strongest candidate. Changing board orientation alone does not
reverse scores. The expanded panel retains the coordinate move and score.

Phone check: enable five candidates and compare their chip scores and colors. Check a mate
line and Black-side analysis. Shared-origin chips should separate when the board
has space, remain readable near edges, and clear with analysis on pause, settings
or uncertain recognition. Move-quality labels such as “Great” are not inferred.

## Check a rejected board

If an exactly calibrated, stationary board is still rejected, tap **Check board**.
This displays the last clean captured crop, its age, and the recognition reason.
It does not take a fresh screenshot while open. Check that the crop contains the
entire playable board with no clipped row, clock, border or OpenCheps controls.
An uncertain-square message names the weakest square using the current board
orientation, gives its confidence and counts squares below the 70% requirement.
Other messages identify a missing/extra king, invalid layout or absent crop.
These reports distinguish crop problems from piece recognition; they do not fix
an unsupported theme or guarantee that a confident prediction is correct.

For diagnosis, open a puzzle, bot board or completed position in the failing
layout, let it settle, then check the preview and report its exact message and
whether its crop matches. A screenshot of that preview can help reproduce the
failure. Nothing is saved or sent by OpenCheps. Capture is detached before the
preview appears; **Close board check** resumes only if normal Pause was off.
Rotation, Stop and permission rejection close it safely.

## Why the old version failed

The automatic detector scores approximate checker patterns at reduced resolution.
Its coarse search, integer cell sizes, limited refinement, and a size-refinement
integer division can yield vertical/size errors. One manual rectangle also cannot
represent every Chess.com layout. App-only capture excludes system bars, whereas
calibration and overlays use physical screen coordinates; these origins must be
reconciled. Without a device trace the contribution of each cannot be measured.

The previous classifier explicitly learned labeled templates from the 32 pieces
at the opening setup. Removing its “show starting position” prompt alone would
have assigned incorrect piece labels. It is replaced with the small bundled
fenshot tile CNN (MIT), running locally through ONNX Runtime. Each crop becomes
64 upright grayscale 32×32 tiles; any square below 0.70 confidence rejects the
frame. Valid king/pawn counts and two matching frames are required before adopting
a position. These are sanity checks, not complete chess legality validation.
No labels are copied from an earlier frame, and no piece identity is silently
repaired from the expected move. Unsupported 3D/stylized themes can be rejected.

Both recognition and move arrows use the same selected square and the same
capture-to-display transform. Saved areas validate normalized bounds, aspect and
rotation. Incompatible orientations are retained for when the old layout returns,
but are never applied to the new orientation. Rotation cancels unsaved editor
changes. Capture resizes the existing VirtualDisplay rather than creating a
second display from a used MediaProjection grant.

## Capture and lifecycle

Analysis is paused and the capture surface detached before the calibration editor
or captured-board preview
appears. Removal is followed by a compositor delay and fresh timestamp checks.
Normal Pause remains in effect after Save, Auto or Cancel. Epoch checks suppress
old results after editing, pause, side changes, rotation and capture visibility
changes. Cleanup closes the ONNX session, engine, display and reader on the worker.

A recognized app-only content viewport excludes OpenCheps's other-app windows,
so it can be analyzed without hiding the overlay. For entire-display or ambiguous
full-display-sized capture, visible frames are change probes only. Actual arrow,
label, frame and panel pixels are masked out of the comparison with the last clean
frame. A change, pending confirmation or engine retry requests a clean hidden read
instead of hiding every 500 ms while idle. A clean check at least every 15 seconds
covers a change obscured entirely by overlays. Thus some brief blinking remains
in this path; keep the floating panel outside the board. Probe frames never enter
the piece classifier. Unknown split/floating app layouts pause rather than guess
an offset. Hidden shared content clears arrows and waits until visible again.

## Limits

Castling rights and en-passant history cannot be recovered from a still image.
They are now tracked from observed moves: the standard starting position grants
`KQkq`, home king/rook movement or rook capture revokes rights, and a pawn double
step sets an en-passant target for one reply when a capture is possible. A fresh
mid-game position defaults to no known rights. **Special moves** allows explicit
confirmation for that position, with home-piece/target validation. Stockfish
still rejects castling through check or blocked paths and en passant exposing
the king. Rights stay in capture-session memory; they are not persisted across
unrelated games. Re-reading/rotating the same canonical position preserves them;
a different re-read position discards them. Missed history, including a king or
rook leaving and returning while unseen, cannot be inferred from identical pixels. Incorrect turn selection, unsupported
themes, animation, capture scaling or a wrong crop can still prevent or mislead
analysis. Moving to an unrelated puzzle/history position can require **Read current
position** and Turn selection. This is not automatic move-history reconstruction.

## Build and verification

JDK 17, SDK 34, Gradle 8.7 and the workflow's SHA-verified Stockfish binaries are
required. `preBuild` runs `python3 tools/prepare_piece_model.py --tests`: it fetches
pinned, SHA256-verified model weights, reference test fixtures and runtime notices
at build time. Python uses only its standard library. None of this downloads on
the phone. Licenses/attribution accompany the model in APK assets. ONNX Runtime
is pinned to 1.23.2; the ARM native libraries have 16 KiB ELF LOAD alignment.

Run `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Tests cover real
model inference on a Chess.com midgame, Black perspective and a sparse position;
unsupported-style rejection; preprocessing/order; saved-layout selection and
persistence; startup from an existing puzzle; move sequences and uncertain jumps;
clean-read scheduling; rasterized overlay masking; and the existing coordinate,
editor, permission and lifecycle cases. Test fixtures are not packaged in the APK.

Phone acceptance also requires checking:

- Correct crop and square-center arrows in puzzles and bot practice, White and
  Black, with highlights and several moves. Restart directly in a nonstarting
  position and verify saved puzzle/bot layouts and Turn controls.
- Idle blinking reduction in both sharing modes; fresh reads after moving a
  piece along an arrow or moving the panel; no old arrows on an unclear board.
- Save/Cancel/Auto while paused; cancellation retains turn and active crop.
- Check board from both running and paused states; inspect the crop/reason, close
  it, and verify pause/turn are preserved. Rotate or Stop while it is open and
  confirm no preview window remains.
- Rotation during/outside editing, resolution/font changes, Home/back transitions,
  notification Stop, permission revocation and restart; no orphaned windows or
  stale results. Unsupported multi-window capture should pause safely.

Validation on 2026-11-11: all 162 unit/Robolectric tests passed; both ARM debug APK
splits built; lint completed with zero errors and 44 warnings. `git diff --check`
passed. APK inspection confirmed the verified model/licenses, the expected ABI
libraries, no test screenshots, and no added Internet permission. These results
are desktop build/test evidence, not a physical-device acceptance result.

The production Java UCI adapter was also run against the official SHA256-verified
Stockfish 18 Linux AVX2 binary (release archive SHA256
`536c0c2c0cf06450df0bfb5e876ef0d3119950703a8f143627f990c7b5417964`).
It confirmed one/two/three distinct choices, strength-limited primary preservation,
the actual 1320–3190 range, full/1320/1800/3190/full strength
transitions, six-half-move lines rooted in each selected move, and an empty
result in checkmate. This verifies the host protocol, not ARM execution or
an empirical Elo rating. Unit tests additionally cover handicap reapplication,
truncated reports, selected-root preservation, settings changes during search,
saved one-to-five choice counts, promotion formatting and continuation clearing.
Preview tests cover crop ownership, pause/turn preservation, rotation cancellation
and permission rejection. Recognition diagnostics retain rejected predictions for
inspection while keeping them excluded from engine input. The exact cause of the
reported stationary Chess.com rejection has not been reproduced on a connected
device. The user reports successful Daily and browser capture/analysis, while
affected native-app modes produce black boards in both Check board and normal
Android screenshots, including with entire-screen sharing. This suggests
mode-specific capture protection or rendering; no particular Android restriction
has been confirmed. Calibration cannot restore pixels absent from capture.

Fast-search checks with the same production adapter and official host Stockfish 18
returned legal, distinct root moves in 125–146 ms for 100 ms searches at full,
1800 and 1320 strength, versus 1536 ms for a 1500 ms search on one test position.
Recorded UCI commands confirmed no fixed-root follow-up when the study line was
off. These are host engine measurements, not phone response-time guarantees or
move-quality benchmarks. Unit tests cover the optional follow-up, missing-PV
fallback, fast-time persistence and settings changes before/during analysis.

Bubble/settings tests cover tap versus drag/cancel, 48 dp measured bounds,
screen-edge/resize clamping, scrolling on short displays, clean-frame visibility,
the minimized capture mask, explicit Settings launch, capture/publication guards,
pause/turn preservation and stale resume cancellation. Activity tests use a
simulated projection token to verify retaining a session across settings and
recreation and disabling a second capture. Real MediaProjection and phone gestures
still require the device checks above; no new permissions were added.

Physical MediaProjection composition, ARM inference/Stockfish, Chess.com animation
and Pixel 10 Pro XL touch/inset behavior cannot be confirmed by desktop tests.

## APK from GitHub Actions

Push the feature branch to update its pull request, then open **Actions → Android
CI → successful run → Artifacts → opencheps-arm64-v8a-debug**. Download, unzip and
install the APK on the Pixel. The armeabi-v7a artifact is for older 32-bit phones.
**Run workflow** can select the feature branch without merging it into main.

Compact-panel checks also verify logo tap/drag, the minimized pawn/read controls,
individual arrow scores, mate sign, unavailable-score fallback and score-chip
separation/edge clamping. A native-rendered 360 dp phone fixture measures the
expanded panel at 296×190 dp for the sample state and the strip at 152×56 dp,
with 48 dp header targets. Synthetic UI previews are written to build/reports/
ui-preview during tests; they contain no phone capture and are not APK assets.
Device checks remain necessary for touch, font scaling and overlay composition.

The November 11 follow-up draws all shafts/heads before opaque score boxes,
retains matching color borders, and expands placement searches for five chips.
Native raster tests verify that a later collinear arrow cannot cover an earlier
score/background/border, plus five-chip separation near a board edge. Ranking
tests cover finite scores, mate distance/sign, unavailable scores, ties and an
Elo-selected move that differs from green. Preferences/activity tests cover the
new five-choice limit and persistence while preserving the three-choice default.
The production UCI adapter also returned five distinct legal choices from official
Stockfish 18 at full strength and with Elo limiting, four choices after switching
settings, and one Elo-selected choice. These host checks do not verify phone
performance or guarantee five moves in terminal/constrained positions.

Device acceptance: show two candidates in the same direction, then five choices;
check that every score box stays above lines and retains its matching border.
Verify that green has the highest displayed score. Keep Elo limiting on and check
that ★, the panel move and Selected continuation still follow the chosen move,
including after switching back to one choice.


## Special-move acceptance checks

The FEN builder previously hardcoded `- -`, which made castling/en-passant
suggestions impossible even when the image transition was recognized. The capture
service now passes session rights into every engine FEN and its analysis cache.
The new **Special moves** screen uses the same detach/remove/250 ms grace flow as
calibration. Save invalidates analysis without resetting turn, orientation or
normal Pause; Cancel and rotation do not change the rights. No new permissions,
network services or workflow changes were added.

On a browser study/practice board:

1. Start at the standard initial position. After opening moves clear the king's
   path, check **Special moves**: relevant castling rights remain checked. A
   castle may be suggested when legal and among the engine's displayed choices;
   availability does not force the engine to prefer it.
2. Make O-O and O-O-O in separate test positions, for White and Black; repeat
   with Black below. Tracking should accept the four-square change and continue.
3. Move a king/rook away and back; its right must stay unchecked. Capture a home
   rook; its right must also disappear.
4. With White pawn e5 and Black pawn d7, observe ...d5. White can receive e5d6
   en passant. A different White reply expires d6. Repeat with Black pawn d4,
   White pawn e2 and e4 to make d4e3 available to Black.
5. Open a mid-game castling/en-passant position directly. Set **Turn** correctly,
   open **Special moves**, confirm the known rights/target, Save and verify the
   suggestions refresh without moving a piece. Try Cancel, rotation, Pause and
   Stop while editing. Settings must not be applied to a different position.

Desktop verification also uses the production FEN builder and UCI adapter against
real Stockfish 18: all four castles survive the UCI path when legal, both observed
double steps yield en-passant suggestions, absent rights remove those legal moves,
and castling through check / pinned en passant are rejected. Physical Android
capture and the dropdown/window behavior still require phone acceptance testing.
