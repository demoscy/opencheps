package com.opencheps.engine;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import com.opencheps.util.Prefs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class StockfishEngine {
    public static class Pv {
        public final String uci;
        public final List<String> moves;
        public final int cp;
        public final boolean mate, hasScore;
        public Pv(String uci, int cp, boolean mate) { this(Collections.singletonList(uci), cp, mate, true); }
        public Pv(List<String> moves, int cp, boolean mate) { this(moves, cp, mate, true); }
        private Pv(List<String> moves, int cp, boolean mate, boolean hasScore) {
            if (moves.isEmpty()) throw new IllegalArgumentException("Empty PV");
            this.moves = Collections.unmodifiableList(new ArrayList<>(moves));
            this.uci = moves.get(0);
            this.cp = cp; this.mate = mate; this.hasScore = hasScore;
        }
    }

    private static final String TAG = "OC/Engine";
    private Process proc;
    private BufferedWriter in;
    private BufferedReader out;
    private volatile boolean ready;
    private int currentElo = -1, currentMultiPv;

    public synchronized void start(Context ctx) throws Exception {
        if (ready) return;
        ApplicationInfo ai = ctx.getApplicationInfo();
        File bin = new File(ai.nativeLibraryDir, "libstockfish.so");
        if (!bin.exists()) throw new RuntimeException("Stockfish native lib missing at " + bin);
        ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath());
        pb.redirectErrorStream(true);
        pb.directory(ctx.getFilesDir());
        proc = pb.start();
        in = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
        out = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        send("uci");
        waitFor("uciok", 6000);
        send("setoption name Threads value 2");
        send("setoption name Hash value 48");
        send("setoption name Move Overhead value 100");
        send("setoption name Ponder value false");
        setMultiPv(1);
        setStrength(0);
        ready = true;
        Log.i(TAG, "Stockfish 18 ready: " + bin.getAbsolutePath());
    }

    private void setStrength(int requestedElo) throws Exception {
        int elo = requestedElo <= 0 ? 0 : Prefs.clampElo(requestedElo);
        if (elo == currentElo) return;
        send("setoption name Skill Level value 20");
        if (elo > 0) send("setoption name UCI_Elo value " + elo);
        send("setoption name UCI_LimitStrength value " + (elo > 0));
        send("isready");
        waitFor("readyok", 6000);
        currentElo = elo;
    }

    private void setMultiPv(int choices) throws Exception {
        if (choices == currentMultiPv) return;
        send("setoption name MultiPV value " + choices);
        currentMultiPv = choices;
    }

    public synchronized List<Pv> analyze(String fen, int movetimeMs, int elo) throws Exception {
        return analyze(fen, movetimeMs, elo, 1);
    }

    /** The actual selected move first, then distinct ranked alternatives when requested. */
    public synchronized List<Pv> analyze(String fen, int movetimeMs, int elo, int requestedChoices) throws Exception {
        return analyze(fen, movetimeMs, elo, requestedChoices, true);
    }

    public synchronized List<Pv> analyze(String fen, int movetimeMs, int elo, int requestedChoices,
                                         boolean includeContinuation) throws Exception {
        if (!ready) throw new IllegalStateException("engine not started");
        int choices = Math.max(1, Math.min(3, requestedChoices));
        setMultiPv(choices);
        setStrength(elo);
        int time = Prefs.clampThinkMs(movetimeMs);
        send("ucinewgame");
        send("isready");
        waitFor("readyok", 6000);
        Search result = search(fen, time, null);
        if (result.move == null) return Collections.emptyList();
        Pv selected = result.lines.get(result.move);
        if (includeContinuation && (selected == null || selected.moves.size() < 6 && !selected.mate)) {
            // Strength limiting may pick a different move from the reported top PV.
            // Restrict a second search to that chosen move; never replace it with PV #1.
            // Handicap selection can truncate PVs even with only one root move. Analyze
            // the fixed chosen move at full strength; the next query reapplies its Elo.
            setStrength(0);
            // A previous transposition-table hit may report a truncated PV. Clear it
            // before asking for a displayable line from this fixed root.
            send("setoption name Clear Hash");
            Search continuation = search(fen, Math.min(time, 500), result.move);
            if (!result.move.equals(continuation.move))
                throw new IllegalStateException("Restricted search changed the selected move");
            Pv extended = continuation.lines.get(result.move);
            if (extended != null) selected = extended;
        }
        if (selected == null) selected = new Pv(Collections.singletonList(result.move), 0, false, false);
        List<Pv> choicesFound = new ArrayList<>();
        choicesFound.add(selected);
        for (String move : result.ranking.values()) {
            if (choicesFound.size() == choices) break;
            boolean duplicate = false;
            for (Pv candidate : choicesFound) if (candidate.uci.equals(move)) duplicate = true;
            Pv alternative = result.lines.get(move);
            if (!duplicate && alternative != null) choicesFound.add(alternative);
        }
        return choicesFound;
    }

    private static class Search {
        String move;
        final Map<String, Pv> lines = new HashMap<>();
        final Map<Integer, String> ranking = new TreeMap<>();
    }

    private Search search(String fen, int time, String onlyMove) throws Exception {
        send("position fen " + fen);
        send("go movetime " + time + (onlyMove == null ? "" : " searchmoves " + onlyMove));
        Search result = new Search();
        long deadline = System.currentTimeMillis() + time + 8000;
        while (true) {
            String line = readLine(deadline);
            if (line.startsWith("bestmove ")) {
                String move = line.split("\\s+")[1];
                if (isMove(move)) result.move = move;
                else if (!"0000".equals(move) && !"(none)".equals(move))
                    throw new IllegalStateException("Invalid engine bestmove");
                return result;
            }
            Pv pv = parsePv(line);
            if (pv != null) {
                int rank = 1;
                String[] words = line.split("\\s+");
                for (int i = 0; i < words.length - 1; i++) if ("multipv".equals(words[i])) {
                    try { rank = Integer.parseInt(words[i+1]); } catch (NumberFormatException e) { rank = 0; }
                }
                if (rank >= 1 && rank <= currentMultiPv) result.ranking.put(rank, pv.uci);
                Pv previous = result.lines.get(pv.uci);
                // Later bound/TT reports can shorten a valid variation. Keep a complete
                // line for this same root move; retain its corresponding score as well.
                if (previous == null || pv.mate || pv.moves.size() >= previous.moves.size())
                    result.lines.put(pv.uci, pv);
            }
        }
    }

    static Pv parsePv(String line) {
        if (!line.startsWith("info ") || line.startsWith("info string ")) return null;
        String[] tokens = line.trim().split("\\s+");
        int cp = 0;
        boolean mate = false, hasScore = false;
        for (int i = 1; i < tokens.length - 1; i++) {
            if ("score".equals(tokens[i]) && i + 2 < tokens.length) {
                try {
                    int value = Integer.parseInt(tokens[i+2]);
                    if ("cp".equals(tokens[i+1])) { cp = value; hasScore = true; }
                    else if ("mate".equals(tokens[i+1])) {
                        mate = true; hasScore = true;
                        cp = value > 0 ? 100000 - value : -100000 - value;
                    }
                } catch (NumberFormatException ignored) { return null; }
            }
            if ("pv".equals(tokens[i])) {
                List<String> moves = new ArrayList<>();
                for (int j = i + 1; j < tokens.length; j++) {
                    if (!isMove(tokens[j])) return null;
                    if (moves.size() < 6) moves.add(tokens[j]);
                }
                return moves.isEmpty() ? null : new Pv(moves, cp, mate, hasScore);
            }
        }
        return null;
    }

    private static boolean isMove(String move) {
        return move != null && move.matches("[a-h][1-8][a-h][1-8][qrbn]?");
    }

    public synchronized void stop() {
        ready = false;
        try { if (in != null) { send("stop"); send("quit"); in.close(); } } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        if (proc != null) proc.destroy();
        proc = null;
    }

    private void send(String cmd) throws Exception {
        in.write(cmd); in.write("\n"); in.flush();
    }

    private String readLine(long deadline) throws Exception {
        while (System.currentTimeMillis() < deadline) {
            if (out.ready()) {
                String line = out.readLine();
                if (line == null) throw new EOFException("Stockfish output closed");
                return line;
            }
            if (proc != null && !proc.isAlive()) throw new EOFException("Stockfish exited");
            Thread.sleep(5);
        }
        throw new java.io.IOException("Stockfish response timed out");
    }

    private void waitFor(String token, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!readLine(deadline).equals(token)) { /* ignore preceding UCI output */ }
    }
}
