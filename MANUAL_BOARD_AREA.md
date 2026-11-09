# Manual board area

Open **Set board area** on the floating panel while Chess.com displays the board.
Drag inside the green square to move it; drag any corner to resize it. The square
stays square and cannot leave the physical display. Match the outside edges of
the playable 8×8 board, excluding coordinates, borders, clocks, and player cards.

- **Save** stores normalized bounds privately on this device and uses them instead
  of the automatic detector. Both square sampling and arrow centers use the same
  capture-to-display transform.
- **Auto** removes the manual setting and restores the existing automatic detector.
- **Cancel** discards edits and preserves the saved mode and area.

The setting survives stopping capture and restarting the app. A proportional
resolution change is supported. Different display proportions or rotation suspend
use of the saved area and request **Set board area** or **Auto**; the saved setting
is retained so returning to its original display layout works. A rotation during
editing cancels unsaved edits. Capture resizes its existing VirtualDisplay rather
than reusing the MediaProjection permission to create another one.

Analysis pauses while editing. The capture surface is detached before the editor
is shown. Every analysis sample also briefly hides the normal arrows and panel,
waits for two display frames, and attaches the capture surface to obtain a fresh
image. The surface is detached again before the overlays return. This prevents
recognition of OpenCheps's own arrows, frame, labels, or controls, including on a
static board. Queued frames are discarded and timestamps checked. The overlay may
briefly blink while a clean frame is acquired. Results from before editing,
pause, side changes, or display changes cannot redraw stale arrows. Calibration
does not override the normal Pause setting. No images are persisted, and no
permissions, accounts, analytics, or network services are added.

## Existing detection and recognition limits

`ScreenCaptureService` reduces the screen to an 800-pixel longest edge, and
`BoardDetector` searches a further 360-pixel image. Its checker-pattern score,
integer cell sizes, coarse position steps, and limited refinement identify an
approximate checker region rather than exact board edges. Size refinement divides
its small cell-size deltas by eight using integer arithmetic, so it often makes
no size adjustment at phone capture resolutions. The source README's
synthetic examples already show vertical and size errors. System-bar/window
origins were also not explicitly reconciled when drawing. Without a failing
Chess.com screenshot/device trace, the precise contribution of each cannot be
confirmed. Manual bounds bypass the detector; both overlays now use physical
screen coordinates and account for their actual window origin. Cached move
arrows are rebuilt when board bounds change.

**Board area calibration is separate from piece-template calibration.** The
existing classifier still needs a starting position, with the correct White/Black
orientation, once per capture session and after Save/Auto changes the area.
Changing the area clears its old piece templates because their samples may have
come from the wrong squares. First show a starting position in the same theme
and layout; then open the puzzle. Recognition accuracy and side-to-move inference
retain the existing limitations. This feature does not add automatic moves or
change the chess analysis algorithm.

## Recognition after moves and app-only capture

Piece features now subtract the local square background before comparing
normalized silhouettes. Piece colour uses contrast against that background, and
templates can match either square colour. This handles last-move highlights and
pieces such as a king crossing onto a square colour without a starting template.

Any changed or uncertain position immediately clears the previous arrows and
evaluation. A failed engine query remains retryable. Two independent quiet moves
(one per side) between clean frames are recognized as two turns, so a quick
computer reply need not leave the tracker waiting for an intermediate position.
Ambiguous captures or larger jumps retain the existing stable-frame resync path;
this is still a template classifier, not a full visual chess-history reconstruction.

Android's capture chooser again offers a single app or the entire display. A
full-screen shared app is mapped using its reported content size and visible
system-bar/cutout insets. The VirtualDisplay resizes to that content size, and
manual screen bounds and arrows both use the same offset. When the shared app
is hidden, analysis waits and arrows are cleared. Floating/split-screen or other
unrecognized app dimensions pause analysis with a message instead of guessing
coordinates; use a full-screen app or entire-display capture for those layouts.

## Phone acceptance test (including Pixel 10 Pro XL)

1. Install the **arm64-v8a** debug APK, allow overlays, select White, and start
   capture. On Android 14+ choose either Chess.com as the shared app or the entire
   display. Keep Chess.com full-screen and show a starting position in your usual theme.
2. Tap **Set board area**. Verify analysis/normal arrows and panel disappear, the
   underlying board is visible, and all four handles and an 8×8 grid appear.
3. Drag the center and each corner. Check the opposite corner stays fixed on
   resize, the selection stays square, and it cannot leave the display. Align
   the playable board exactly, then **Save**.
4. Wait for piece-template calibration at the starting position. In practice
   against the computer, play e2-e4 and wait for its reply, then play another move.
   Check that arrows disappear during uncertain recognition and return for your
   next turn rather than keeping the first recommendation. Repeat with Black,
   highlights enabled, and a fast computer reply. Also test a king/queen moving
   to the opposite square colour. Open a puzzle in the same layout and confirm
   the frame and arrow endpoints align with square centers.
5. Stop and restart capture/app without clearing app data. Open **Set board area**
   and confirm the saved selection returns. Show the starting position again for
   the new session's templates.
6. Edit to a visibly different square and **Cancel**. Reopen and verify the old
   area is intact. Choose **Auto**, reopen/restart, and verify automatic detection
   remains enabled. Save another area to restore manual mode.
7. Pause, open the editor, and exercise Save/Cancel/Auto. Confirm capture stays
   paused until explicitly resumed. Change White/Black and check orientation
   and piece-template recalibration still behave as before.
8. Rotate during editing: edits should cancel, controls return, and an incompatible
   saved area must not be analyzed. In the new rotation, calibrate again or choose
   Auto. Also test portrait/landscape outside editing and a display resolution change.
9. Stop using the notification while editing, revoke overlay/capture permission,
   switch apps/Home, and restart. Check there are no orphaned windows, stuck editor,
   stale arrows, or permission-token crashes. Repeat with gesture navigation,
   camera cutout/status bars, and your preferred display/font size. With app-only
   capture, switch Home and back; analysis must wait while Chess.com is hidden.
   Repeat calibration and multiple turns in both app-only and entire-display modes.
   Check that unsupported multi-window layouts pause with a message.

Unit/Robolectric tests exercise normalized bounds, corrupt inputs, resolution and
rotation validation, edge clamping, all resize corners, real editor touch handling,
White/Black square mapping, SharedPreferences reload/clear, permission rejection,
and editor cancellation on configuration changes. Actual MediaProjection surface
composition, Stockfish execution on ARM, Chess.com recognition, and Pixel touch/
inset behavior require the phone tests above.

## Validation of this change

With JDK 17, Gradle 8.7, SDK 34, and the workflow's SHA-verified Stockfish 18
binaries, `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` passed:
The original calibration change passed 28 tests, both ARM APK builds, and lint.
The follow-up passed 53 unit/Robolectric tests, built both ARM APK splits, and
completed lint with zero errors (56 warnings). It adds highlighted-board move sequences, opposite-colour piece
templates, quick computer replies, stale-result clearing, retry after an engine
failure, overlay hiding, and app-content coordinate conversion. The editor,
overlay lifecycle, and preferences tests run on Android API 26 and 34 via Robolectric. Lint also reports
warnings, including existing UI/resource warnings and intentional physical
left/right gravity for coordinate-based overlays. `git diff --check` passes.
The Android manifest is unchanged. The phone acceptance tests remain unverified.

## APK from GitHub Actions

Open this repository's **Actions → Android CI**, select the successful run for
`feature/manual-board-area` or its pull request, and download the artifact
**opencheps-arm64-v8a-debug**. Unzip it and install the APK on the Pixel. The
**opencheps-armeabi-v7a-debug** artifact is for older 32-bit phones. The workflow
retains the Stockfish downloads, hash checks, and APK split paths, and now runs
the unit tests before publishing artifacts. If necessary, **Run workflow** lets
you choose the feature branch without merging it into `main`.
