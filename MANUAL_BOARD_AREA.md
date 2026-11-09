# Board calibration and current-position recognition

Use OpenCheps for puzzles, study of completed positions, and practice against bots.
There is no move execution. The app has no account, analytics, cloud recognition,
or Internet permission. Screenshots remain in memory and are never saved/uploaded.

## On the phone

1. Open an existing Chess.com puzzle, without returning to its starting position.
   Use a normal 2D piece theme. Start OpenCheps and share Chess.com as a full-screen
   app, or share the entire display. Select the side you want to analyze with
   **I play: WHITE / BLACK**.
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

## Practice strength and study line

The overlay always shows one next-move arrow. In OpenCheps settings, optionally
check **Limit practice strength**, then select 1320–3190 Elo on Stockfish 18's
approximate calibration scale. It is not a Chess.com rating or human model.
Uncheck to restore full strength; the selected practice value is remembered.
Think time no longer displays an invented Elo estimate.

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
the line and check its first move equals the only arrow, White/Black alternate,
and the panel remains readable. Pause or move to an unclear board and confirm
both old evaluation and continuation clear. Restart to check settings persist.

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

Castling rights and en-passant history cannot be recovered from a still image;
the existing FEN builder uses `-` for both. Incorrect turn selection, unsupported
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
- Rotation during/outside editing, resolution/font changes, Home/back transitions,
  notification Stop, permission revocation and restart; no orphaned windows or
  stale results. Unsupported multi-window capture should pause safely.

Validation on 2026-11-09: all 90 unit/Robolectric tests passed; both ARM debug APK
splits built; lint completed with zero errors and 51 warnings. `git diff --check`
passed. APK inspection confirmed the verified model/licenses, the expected ABI
libraries, no test screenshots, and no added Internet permission. These results
are desktop build/test evidence, not a physical-device acceptance result.

The production Java UCI adapter was also run against the official SHA256-verified
Stockfish 18 Linux AVX2 binary (release archive SHA256
`536c0c2c0cf06450df0bfb5e876ef0d3119950703a8f143627f990c7b5417964`).
It confirmed the actual 1320–3190 range, full/1320/1800/3190/full strength
transitions, six-half-move lines rooted in each selected move, and an empty
result in checkmate. This verifies the host protocol, not ARM execution or
an empirical Elo rating. Unit tests additionally cover handicap reapplication,
truncated reports, selected-root preservation, settings changes during search,
legacy multi-arrow preferences, promotion formatting and continuation clearing.

Physical MediaProjection composition, ARM inference/Stockfish, Chess.com animation
and Pixel 10 Pro XL touch/inset behavior cannot be confirmed by desktop tests.

## APK from GitHub Actions

Push the feature branch to update its pull request, then open **Actions → Android
CI → successful run → Artifacts → opencheps-arm64-v8a-debug**. Download, unzip and
install the APK on the Pixel. The armeabi-v7a artifact is for older 32-bit phones.
**Run workflow** can select the feature branch without merging it into main.
