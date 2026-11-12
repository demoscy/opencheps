package com.opencheps.vision;

import com.opencheps.util.Side;

import java.util.ArrayList;
import java.util.List;

public class MoveAnalyzer {

    public enum Kind { NONE, QUIET, CAPTURE, CASTLE, EN_PASSANT, PROMOTION, TWO_PLIES, UNKNOWN }

    public static class Result {
        public final Kind kind;
        public final Side mover;
        public final int changeCount;
        public Result(Kind kind, Side mover, int changeCount) {
            this.kind = kind; this.mover = mover; this.changeCount = changeCount;
        }
    }

    /** A quick computer reply can leave two independent quiet moves between samples. */
    public static Result analyze(char[][] prev, char[][] curr, Side expectedFirstMover) {
        Result single = analyze(prev, curr);
        if (single.kind != Kind.UNKNOWN || prev == null || curr == null || expectedFirstMover == null) return single;
        char[] from = new char[2], to = new char[2];
        int changed = 0;
        for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++) {
            char before = prev[r][f] == 0 ? '.' : prev[r][f];
            char after = curr[r][f] == 0 ? '.' : curr[r][f];
            if (before == after) continue;
            changed++;
            if (before != '.' && after == '.') {
                int side = Character.isUpperCase(before) ? 0 : 1;
                if (from[side] != 0) return single;
                from[side] = before;
            } else if (before == '.' && after != '.') {
                int side = Character.isUpperCase(after) ? 0 : 1;
                if (to[side] != 0) return single;
                to[side] = after;
            } else return single; // Captures/ambiguous overlapping moves still need resynchronization.
        }
        if (changed == 4 && from[0] != 0 && from[1] != 0 && from[0] == to[0] && from[1] == to[1]) {
            Side lastMover = expectedFirstMover == Side.WHITE ? Side.BLACK : Side.WHITE;
            return new Result(Kind.TWO_PLIES, lastMover, changed);
        }
        return single;
    }

    public static Result analyze(char[][] prev, char[][] curr) {
        if (prev == null || curr == null) return new Result(Kind.UNKNOWN, null, 0);

        List<int[]> emptied = new ArrayList<>();
        List<int[]> filled = new ArrayList<>();
        List<int[]> replaced = new ArrayList<>();
        int changes = 0;

        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                char p = prev[r][f] == 0 ? '.' : prev[r][f];
                char c = curr[r][f] == 0 ? '.' : curr[r][f];
                if (p == c) continue;
                changes++;
                if (p == '.' && c != '.') filled.add(new int[]{r, f, c});
                else if (p != '.' && c == '.') emptied.add(new int[]{r, f, p});
                else replaced.add(new int[]{r, f, p, c});
            }
        }

        if (changes == 0) return new Result(Kind.NONE, null, 0);

        if (emptied.size() == 1 && filled.size() == 1 && replaced.isEmpty()) {
            char piece = (char) emptied.get(0)[2];
            char dest  = (char) filled.get(0)[2];
            Side mover = Character.isUpperCase(piece) ? Side.WHITE : Side.BLACK;
            if (Character.isUpperCase(piece) != Character.isUpperCase(dest)) {
                return new Result(Kind.UNKNOWN, null, changes);
            }
            boolean isPromotion = (Character.toLowerCase(piece) == 'p'
                    && Character.toLowerCase(dest) != 'p');
            return new Result(isPromotion ? Kind.PROMOTION : Kind.QUIET, mover, changes);
        }

        if (emptied.size() == 1 && filled.isEmpty() && replaced.size() == 1) {
            char attacker = (char) emptied.get(0)[2];
            int[] rep = replaced.get(0);
            char victim   = (char) rep[2];
            char newPiece = (char) rep[3];
            Side mover = Character.isUpperCase(attacker) ? Side.WHITE : Side.BLACK;
            boolean attackerIsWhite = Character.isUpperCase(attacker);
            boolean victimIsWhite   = Character.isUpperCase(victim);
            boolean newIsWhite      = Character.isUpperCase(newPiece);
            if (attackerIsWhite == victimIsWhite) return new Result(Kind.UNKNOWN, null, changes);
            if (newIsWhite != attackerIsWhite)    return new Result(Kind.UNKNOWN, null, changes);
            boolean isPromotion = (Character.toLowerCase(attacker) == 'p'
                    && Character.toLowerCase(newPiece) != 'p');
            return new Result(isPromotion ? Kind.PROMOTION : Kind.CAPTURE, mover, changes);
        }

        // Special moves must match their exact square changes, in either board orientation.
        if (changes == 3 || changes == 4) {
            for (Side side : Side.values()) {
                for (boolean flipped : new boolean[]{false, true}) {
                    char[][] before = flipped ? FenBuilder.flip(prev) : prev;
                    char[][] after = flipped ? FenBuilder.flip(curr) : curr;
                    if (changes == 4 && isCastle(before, after, side))
                        return new Result(Kind.CASTLE, side, changes);
                    if (changes == 3 && isEnPassant(before, after, side))
                        return new Result(Kind.EN_PASSANT, side, changes);
                }
            }
        }

        return new Result(Kind.UNKNOWN, null, changes);
    }

    private static boolean isCastle(char[][] before, char[][] after, Side side) {
        int row = side == Side.WHITE ? 7 : 0;
        char king = side == Side.WHITE ? 'K' : 'k', rook = side == Side.WHITE ? 'R' : 'r';
        if (before[row][4] != king) return false;
        for (boolean kingSide : new boolean[]{true, false}) {
            int corner = kingSide ? 7 : 0, kingTo = kingSide ? 6 : 2, rookTo = kingSide ? 5 : 3;
            if (before[row][corner] != rook || !empty(before[row][kingTo]) || !empty(before[row][rookTo])
                    || !kingSide && !empty(before[row][1])) continue;
            char[][] expected = copy(before);
            expected[row][4] = expected[row][corner] = '.';
            expected[row][kingTo] = king;
            expected[row][rookTo] = rook;
            if (same(expected, after)) return true;
        }
        return false;
    }

    private static boolean isEnPassant(char[][] before, char[][] after, Side side) {
        int row = side == Side.WHITE ? 3 : 4, to = side == Side.WHITE ? 2 : 5;
        char pawn = side == Side.WHITE ? 'P' : 'p', victim = side == Side.WHITE ? 'p' : 'P';
        for (int f = 0; f < 8; f++) for (int dest : new int[]{f-1, f+1}) {
            if (dest < 0 || dest > 7 || before[row][f] != pawn
                    || before[row][dest] != victim || !empty(before[to][dest])) continue;
            char[][] expected = copy(before);
            expected[row][f] = expected[row][dest] = '.';
            expected[to][dest] = pawn;
            if (same(expected, after)) return true;
        }
        return false;
    }

    private static char[][] copy(char[][] board) {
        char[][] result = new char[8][];
        for (int r = 0; r < 8; r++) result[r] = board[r].clone();
        return result;
    }
    private static boolean empty(char piece) { return piece == '.' || piece == 0; }
    private static boolean same(char[][] a, char[][] b) {
        for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++) {
            if (a[r][f] != b[r][f] && !(empty(a[r][f]) && empty(b[r][f]))) return false;
        }
        return true;
    }
}
