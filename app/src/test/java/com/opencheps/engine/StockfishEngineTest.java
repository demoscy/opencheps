package com.opencheps.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StockfishEngineTest {
    private final String fen = "6k1/8/8/8/8/8/5PPP/6K1 w - - 0 1";
    private final StringWriter commands = new StringWriter();
    private StockfishEngine engine(String transcript) {
        StockfishEngine engine = new StockfishEngine();
        ReflectionHelpers.setField(engine, "in", new BufferedWriter(commands));
        ReflectionHelpers.setField(engine, "out", new BufferedReader(new StringReader(transcript)));
        ReflectionHelpers.setField(engine, "ready", true);
        return engine;
    }
    @Test public void selectedWeakMoveAndContinuationCannotBeReplacedWithTheTopCandidate() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\n"
                + "info depth 12 score cp 80 pv g1f1 g8f8 f2f3\n"
                + "bestmove g2g3 ponder g8f8\nreadyok\n"
                + "info depth 11 score cp 12 pv g2g3 g8f8 g1f1 f8e8 f2f3 e8d8\n"
                + "bestmove g2g3\n");
        List<StockfishEngine.Pv> result = engine.analyze(fen, 1500, 1800);
        assertEquals(1, result.size());
        assertEquals("g2g3", result.get(0).uci);
        assertEquals(Arrays.asList("g2g3", "g8f8", "g1f1", "f8e8", "f2f3", "e8d8"), result.get(0).moves);
        assertEquals(12, result.get(0).cp);
        assertTrue(commands.toString().contains("setoption name UCI_Elo value 1800\n"));
        assertTrue(commands.toString().contains("setoption name UCI_LimitStrength value true\n"));
        assertTrue(commands.toString().contains("go movetime 500 searchmoves g2g3\n"));
        assertTrue(commands.toString().indexOf("UCI_LimitStrength value false")
                < commands.toString().indexOf("searchmoves g2g3"));
        assertFalse(commands.toString().contains("MultiPV value 3"));
    }
    @Test public void fullStrengthRestoresTheOptionAndDoesNotNeedAnExtraSearch() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\n"
                + "info depth 20 score cp 31 pv f2f3 g8f8 g1f1 f8e8 f3f4 e8d8\nbestmove f2f3\n");
        ReflectionHelpers.setField(engine, "currentElo", 1800);
        StockfishEngine.Pv result = engine.analyze(fen, 300, 0).get(0);
        assertEquals("f2f3", result.uci);
        assertTrue(commands.toString().contains("UCI_LimitStrength value false"));
        assertFalse(commands.toString().contains("searchmoves"));
    }
    @Test public void shallowSelectedLineIsExtendedWithoutSelectingADifferentFirstMove() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\n"
                + "info depth 1 score cp 12 pv g2g3\nbestmove g2g3\nreadyok\n"
                + "info depth 12 score cp 18 pv g2g3 g8f8 g1f1 f8e8 f2f3 e8d8\nbestmove g2g3\n");
        StockfishEngine.Pv result = engine.analyze(fen, 300, 1320).get(0);
        assertEquals("g2g3", result.uci);
        assertEquals(6, result.moves.size());
        assertTrue(commands.toString().contains("searchmoves g2g3"));
    }
    @Test public void terminalPositionDoesNotReuseAnEarlierInfoMove() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\ninfo depth 1 score cp 0 pv g1f1\nbestmove (none)\n");
        assertTrue(engine.analyze(fen, 300, 0).isEmpty());
    }
    @Test public void missingPvRetainsChosenMoveWithoutInventingAnEvaluationOrReplies() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\nbestmove f2f3\nreadyok\nbestmove f2f3\n");
        StockfishEngine.Pv result = engine.analyze(fen, 300, 1320).get(0);
        assertEquals(Arrays.asList("f2f3"), result.moves);
        assertFalse(result.hasScore);
    }
    @Test public void requestedEloIsClampedAndBrokenOutputCannotReturnAStalePv() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\ninfo depth 10 score cp 30 pv f2f3\n");
        try { engine.analyze(fen, 300, 500); fail("EOF must fail the query"); }
        catch (java.io.EOFException expected) { }
        assertTrue(commands.toString().contains("UCI_Elo value 1320"));
    }
    @Test public void laterTruncatedReportDoesNotEraseACompleteLineForTheChosenMove() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\n"
                + "info depth 12 score cp 31 pv f2f3 g8f8 g1f1 f8e8 f3f4 e8d8\n"
                + "info depth 13 score cp 32 lowerbound pv f2f3\nbestmove f2f3\n");
        StockfishEngine.Pv result = engine.analyze(fen, 300, 0).get(0);
        assertEquals(6, result.moves.size());
        assertEquals(31, result.cp); // Score and variation come from the same report.
        assertFalse(commands.toString().contains("searchmoves"));
    }
    @Test public void handicapIsReappliedForTheNextSelectionAfterAFullStrengthContinuation() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\nbestmove g2g3\nreadyok\n"
                + "info depth 10 score cp 18 pv g2g3 g8f8 g1f1 f8e8 f2f3 e8d8\nbestmove g2g3\n"
                + "readyok\nreadyok\ninfo depth 10 score cp 18 pv f2f3 g8f8 g1f1 f8e8 f3f4 e8d8\nbestmove f2f3\n");
        assertEquals("g2g3", engine.analyze(fen, 500, 1800).get(0).uci);
        assertEquals("f2f3", engine.analyze(fen, 500, 1800).get(0).uci);
        String sent = commands.toString();
        assertEquals(2, sent.split("UCI_LimitStrength value true", -1).length - 1);
        assertTrue(sent.lastIndexOf("UCI_LimitStrength value true") > sent.indexOf("searchmoves g2g3"));
    }
    @Test public void restrictedSearchCannotSilentlyReplaceTheChosenMove() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\nbestmove g2g3\nreadyok\nbestmove f2f3\n");
        try { engine.analyze(fen, 300, 1800); fail("Changed root must fail the query"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("selected move")); }
    }
    @Test public void fullPvSupportsPromotionsMateShortLinesAndRejectsMalformedTokens() {
        StockfishEngine.Pv pv = StockfishEngine.parsePv("info depth 15 score mate 2 pv a7a8q h8h7 a8h8");
        assertNotNull(pv);
        assertTrue(pv.mate);
        assertEquals(99998, pv.cp);
        assertEquals(Arrays.asList("a7a8q", "h8h7", "a8h8"), pv.moves);
        assertNull(StockfishEngine.parsePv("info string score cp 20 pv e2e4"));
        assertNull(StockfishEngine.parsePv("info depth 15 score cp 20 pv e2e4 invalid"));
        assertNull(StockfishEngine.parsePv("info depth 15 score cp wrong pv e2e4"));
    }
    @Test public void threeChoicesKeepActualHandicappedMoveFirstAndThenRankedAlternatives() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\n"
                + "info depth 12 multipv 1 score cp 80 pv f2f3 g8f8 g1f1 f8e8 f3f4 e8d8\n"
                + "info depth 12 multipv 2 score cp 40 pv g2g3 g8f8 g1f1 f8e8 f2f3 e8d8\n"
                + "info depth 12 multipv 3 score cp 10 pv h2h3 g8f8 g1f1 f8e8 f2f3 e8d8\n"
                + "bestmove h2h3\n");
        List<StockfishEngine.Pv> result = engine.analyze(fen, 500, 1800, 3);
        assertEquals(3, result.size());
        assertEquals("h2h3", result.get(0).uci);
        assertEquals("f2f3", result.get(1).uci);
        assertEquals("g2g3", result.get(2).uci);
        assertTrue(commands.toString().contains("MultiPV value 3"));
    }
    @Test public void duplicateReportsDoNotInventAdditionalChoices() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\n"
                + "info multipv 1 score cp 20 pv f2f3 g8f8 g1f1 f8e8 f3f4 e8d8\n"
                + "info multipv 2 score cp 20 pv f2f3 g8f8 g1f1 f8e8 f3f4 e8d8\n"
                + "info multipv 3 score cp 10 pv g2g3 g8f8 g1f1 f8e8 f2f3 e8d8\n"
                + "bestmove f2f3\n");
        List<StockfishEngine.Pv> result = engine.analyze(fen, 500, 0, 99);
        assertEquals(2, result.size());
        assertTrue(commands.toString().contains("MultiPV value 3"));
    }

    @Test public void fastSearchKeepsChosenMoveAndAlternativesWithoutSearchingForHiddenReplies() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\n"
                + "info multipv 1 score cp 40 pv f2f3 g8f8 g1f1 f8e8 f3f4 e8d8\n"
                + "info multipv 2 score cp 10 pv g2g3\nbestmove h2h3\n");
        List<StockfishEngine.Pv> result = engine.analyze(fen, 100, 1800, 3, false);
        assertEquals(3, result.size());
        assertEquals("h2h3", result.get(0).uci);
        assertEquals(Arrays.asList("h2h3"), result.get(0).moves);
        assertFalse(result.get(0).hasScore);
        assertEquals("f2f3", result.get(1).uci);
        assertEquals("g2g3", result.get(2).uci);
        assertTrue(commands.toString().contains("go movetime 100\n"));
        assertFalse(commands.toString().contains("searchmoves"));
        assertFalse(commands.toString().contains("UCI_LimitStrength value false"));
    }

    @Test public void fastShallowLineReturnsImmediatelyAndClampsTheRequestedTime() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\n"
                + "info depth 1 score cp 12 pv g2g3 g8f8\nbestmove g2g3\n");
        StockfishEngine.Pv result = engine.analyze(fen, 0, 0, 1, false).get(0);
        assertEquals(Arrays.asList("g2g3", "g8f8"), result.moves);
        assertEquals(12, result.cp);
        assertTrue(commands.toString().contains("go movetime 100\n"));
        assertFalse(commands.toString().contains("searchmoves"));
    }

    @Test public void enablingContinuationStillExtendsTheSameMoveAtTheFastestSetting() throws Exception {
        StockfishEngine engine = engine("readyok\nreadyok\n"
                + "info depth 1 score cp 12 pv g2g3\nbestmove g2g3\nreadyok\n"
                + "info depth 12 score cp 18 pv g2g3 g8f8 g1f1 f8e8 f2f3 e8d8\nbestmove g2g3\n");
        StockfishEngine.Pv result = engine.analyze(fen, 100, 1320, 1, true).get(0);
        assertEquals("g2g3", result.uci);
        assertEquals(6, result.moves.size());
        assertTrue(commands.toString().contains("go movetime 100 searchmoves g2g3\n"));
    }
}
