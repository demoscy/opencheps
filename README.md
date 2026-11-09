# OpenCheps

An Android chess overlay for puzzles, study and bot practice. MediaProjection
captures a chessboard; local piece recognition and Stockfish 18 produce analysis
and arrows. No automatic moves, cloud recognition, analytics or account.

## Current-position recognition

Open an existing puzzle or practice position directly. The bundled offline
[fenshot](https://github.com/scoriiu/fenshot) tile model replaces opening-position
template training. There is no “show starting position” requirement. Use a normal
2D piece theme; uncertain squares or invalid king/pawn arrangements reject the
frame. Two matching recognized frames are required before adopting a position.
Recognition confidence and sanity checks cannot guarantee correct analysis.

- **I play White / Black** selects the side to analyze and defaults the board
  orientation to that side at the bottom.
- **Turn** explicitly sets whose turn it is in an existing position. Subsequent
  recognized moves update it; arrows appear only for the selected analysis side.
- **White below / Black below** flips board coordinates independently of turn.
- The circular **Read current position** button restarts position tracking from
  the displayed pieces, defaulting the turn to “I play”. Correct Turn if needed.
- **Pause / Resume / Stop** retain their existing functions.

A still image cannot reliably provide turn, castling rights or en-passant history.
The existing FEN builder supplies no castling/en-passant rights. An unrelated
puzzle/history jump can require Read current position and Turn selection.

## Compact controls and settings

**Minimize** replaces the floating panel with a draggable 48 dp OpenCheps bubble.
Tap the bubble to expand it; drag the panel title or bubble to reposition it.
Minimizing keeps analysis, arrows and the existing Pause state. The bubble stays
minimized across background transitions and rotation in the current session.
Controls are clamped away from system bars/cutouts; the expanded panel scrolls
when the display is short. A new capture session starts with the expanded panel.

**Settings** opens the existing OpenCheps app without stopping MediaProjection.
The overlay hides and analysis pauses while the app screen is visible. Change
think time, strength, move choices, study line or side, then tap **Return to board**
or leave the app to resume. Normal Pause is preserved; changing the selected side
uses the same orientation/position reset as the panel side toggle. Other settings
retain turn and board orientation. Starting a second capture is disabled while a
session is active. No new permissions or background service starts are needed.

## Study controls

**Move choices** selects **one to three candidate arrows**, defaulting to three.
Existing saved choice counts are honored. Green marks the selected move; amber
and blue show distinct ranked alternatives when the position has enough moves.

Enable **Limit practice strength** and choose **1320–3190 Elo** to use Stockfish
18's native `UCI_LimitStrength` / `UCI_Elo`. Disable it for full strength. This is
Stockfish's approximate calibration scale, not a Chess.com rating, and is not
human imitation or a concealment feature. Think time only controls search time;
its former device-dependent Elo estimates are removed. Settings persist locally.
The cap applies to the green selected move; other arrows are analysis alternatives.

**Show a 3-move study line** displays up to six half-moves (three for each side),
with White/Black labels, following the green arrow. This is separate from the
number of candidate arrows and is not a prediction of actual replies. It can
be shorter near mate/game end or if the
search yields less. The strength cap applies to the selected next move; the rest
is an illustrative engine variation, not independently Elo-limited play per ply.
The line clears with the old evaluation on pause or uncertain/changed positions.

A strength-limited Stockfish `bestmove` can differ from its top `info ... pv`
line. The app uses the actual selected move. With the study line enabled, a second search
restricted to that move obtains its own continuation, capped at 500 ms extra.
Handicap is disabled for that fixed-root continuation, then reapplied for the
next move selection. Complete same-root variations are retained when a later
report is truncated. The strongest candidate never replaces the chosen move.

For quicker suggestions, set **Think time** to **100–300 ms** and turn off
**Show a 3-move study line**. Disabling the line skips its extra search entirely;
one-to-three next-move choices remain available. Short searches may miss tactics
and still produce strong moves. Think time is the engine's search budget, not
total response time: capture, recognition, two matching frames and scheduling
also add delay. Changing time or the line setting refreshes the same position.

## Manual board area

**Set board area** opens a draggable square with four resize handles and an 8×8
grid. Match the playable board exactly, excluding outside coordinates and borders.
**Save** remembers normalized bounds. Up to eight different layouts are retained;
confident recognition chooses between compatible saved crops, favoring the active
crop on near ties. **Auto** clears all manual areas and restores the existing
automatic detector. **Cancel** preserves settings. Recognition crops and arrow
coordinates use the same selected square and capture-to-display transform.

Each different Chess.com layout may need calibration once. New unknown layout
bounds are not inferred from the piece model. Rotation/aspect validation prevents
using a saved portrait rectangle in an incompatible layout. See
[calibration, diagnosis and phone tests](MANUAL_BOARD_AREA.md).

## Capture

Android's screen-capture chooser supports Chess.com only or the entire screen.
Keep the shared app full-screen. Captured content dimensions and system-bar
insets map app-only content back to the physical overlay screen. Unknown
split/floating layouts pause instead of guessing. Hidden shared content clears
arrows and waits.

The calibration editor pauses analysis and detaches capture. App-only capture
with a recognized content viewport avoids hiding other-app overlays. Entire-screen
capture uses masked change probes while idle, then hides OpenCheps briefly for
clean recognition frames after a change or for confirmation. A 15-second clean
check covers obscured changes, so some brief blinking remains in this path.
Screenshots stay in memory. No new permissions or runtime network requests.

## Detection and recognition

The existing automatic checker-pattern detector uses reduced-resolution grayscale
integral images, a coarse multiscale search and local refinement. It identifies
approximate edges, which can be vertically offset or the wrong size. Manual
calibration bypasses it. The piece recognizer does not change automatic bounds.

The offline model reads 64 grayscale 32×32 tiles. It rejects any tile below 0.70
confidence; king/pawn checks and stable-frame confirmation filter further errors.
Black perspective flips placement and arrow mapping, not piece colours. Move
tracking supports ordinary moves, captures, castling changes and some quick
computer replies; ambiguous jumps clear stale analysis and request a fresh read.
Unsupported 3D/cartoon themes and animation can still fail.

**Check board** opens a paused, memory-only preview of the last clean board crop,
with its age and recognition result. It identifies the weakest square and its
confidence, or a rejected king/pawn arrangement. Close it to resume; normal Pause
remains in effect. The preview uses a separate bitmap and detaches capture before
appearing, so its controls never become recognition input. It does not save images
or relax acceptance checks. A matching calibration grid does not by itself prove
that the captured crop, scaled pieces or theme will pass recognition.

## Stockfish 18

Bundled as `app/src/main/jniLibs/<abi>/libstockfish.so`. The CI workflow downloads the official Stockfish 18 Android binaries from the `sf_18` release and SHA256-verifies them:

| ABI | Source file | SHA256 |
|---|---|---|
| `arm64-v8a` | `stockfish-android-armv8` | `cc3b3b74466b6d85cdc0f8cfdad4111c6a44bd0698f4eab96fea7b359a54d3d4` |
| `armeabi-v7a` | `stockfish-android-armv7-neon` | `672fbd7533d15fc70dae82df52c1185236975d059a585aa65eda960f1a3266ea` |

Both are statically linked PIE ELFs from NDK r27c, ~114 MB each on disk with the NNUE embedded. Why ship as `lib*.so` instead of an asset? Because Android extracts files matching `lib/<abi>/lib*.so` into `nativeLibraryDir` with executable permissions on a partition that allows `execve`. If you put it in `assets/`, you have to copy it to `filesDir` at first launch, `chmod +x` it, and on some manufacturer ROMs the data partition is `noexec` and the whole thing falls over. The `.so` approach is bulletproof.

Engine settings configured in `StockfishEngine.start`:
```
setoption name Threads value 2
setoption name Hash value 48
setoption name MultiPV value 1
setoption name Move Overhead value 100
```

Stockfish uses two threads and a 48 MB hash. The default search time is 1500 ms,
configurable from 100 to 3000 ms. With the study line enabled, at most another
500 ms obtains the selected move's continuation when needed; with it off there
is no follow-up search. MultiPV changes to the requested choice count for
each analysis. Elo limiting is a separate setting. Stockfish's
strength calibration cannot be converted directly into your online account rating.

## Build, tests and APKs

GitHub Actions builds ARM64 and ARMv7 debug APKs for pull requests to main, pushes
to main, or manual workflow runs. The workflow downloads and verifies Stockfish.
Gradle preBuild also runs `python3 tools/prepare_piece_model.py --tests` to fetch
SHA256-verified, pinned offline model weights, test fixtures and runtime notices.
The model and licenses are bundled in the APK; the app never downloads them.
Python 3 uses only its standard library. ONNX Runtime is pinned to 1.23.2.

Local builds require JDK 17, Android SDK 34, Gradle 8.7, and the Stockfish binaries
at `app/src/main/jniLibs/<abi>/libstockfish.so` as installed by the workflow. Run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Tests include actual offline inference on Chess.com and Black-side screenshots,
a sparse nonstarting position, rejected unsupported styles, coordinate transforms,
manual profile migration/persistence/selection, move tracking, clean-frame policy,
and editor/overlay lifecycle. Physical capture composition, ARM execution and
phone touch behavior still need the acceptance tests in MANUAL_BOARD_AREA.md.

Download **opencheps-arm64-v8a-debug** from the successful Actions run's Artifacts
for Pixel 10 Pro XL. Unzip and install the APK. The armeabi-v7a artifact is for
older 32-bit phones. No merge into main is needed to run the feature workflow.

## License

App code: MIT. Stockfish 18: GPL-3.0. Offline fenshot model and its preprocessing:
MIT (SORTINO LABS S.R.L.; preprocessing also credits Sameer Ansari's
`tensorflow_chessbot`). ONNX Runtime: MIT (Microsoft). Attribution, licenses and
runtime third-party notices accompany the model in APK assets.
