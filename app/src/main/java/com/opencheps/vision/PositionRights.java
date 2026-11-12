package com.opencheps.vision;

import com.opencheps.util.Side;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Session-only history in canonical a8..h1 coordinates. Never infer rights from home pieces alone. */
public final class PositionRights {
    private static final String[] START = {"rnbqkbnr", "pppppppp", "........", "........",
            "........", "........", "PPPPPPPP", "RNBQKBNR"};
    private char[][] previous;
    private String castles = "", ep = "-";

    public String castling() { return castles.isEmpty() ? "-" : castles; }
    public String enPassant() { return ep; }

    /** Re-read/flip keeps history only for the exact same canonical position. */
    public void synchronize(char[][] board, Side turn) {
        if (!same(previous, board)) {
            castles = isStart(board) ? "KQkq" : "";
            ep = "-";
            remember(board);
        }
        castles = allowedCastling(board, castles);
        if (!enPassantTargets(board, turn).contains(ep)) ep = "-";
    }

    public void advance(char[][] board, Side nextTurn, MoveAnalyzer.Result move) {
        if (previous == null || move.kind == MoveAnalyzer.Kind.UNKNOWN || move.mover == null) {
            synchronize(board, nextTurn);
            return;
        }
        // Losing home occupancy revokes the right permanently, including a captured home rook.
        castles = allowedCastling(board, castles);
        ep = "-"; // An en-passant opportunity lasts exactly one reply.
        if (move.kind == MoveAnalyzer.Kind.QUIET || move.kind == MoveAnalyzer.Kind.TWO_PLIES) {
            int from = move.mover == Side.WHITE ? 6 : 1;
            int to = move.mover == Side.WHITE ? 4 : 3;
            int through = (from + to) / 2;
            char pawn = move.mover == Side.WHITE ? 'P' : 'p';
            for (int f = 0; f < 8; f++) {
                if (previous[from][f] == pawn && empty(board[from][f])
                        && empty(previous[to][f]) && board[to][f] == pawn
                        && empty(previous[through][f]) && empty(board[through][f])) {
                    ep = "" + (char) ('a' + f) + (8 - through);
                    break;
                }
            }
        }
        remember(board);
        if (!enPassantTargets(board, nextTurn).contains(ep)) ep = "-";
    }

    /** Only explicit user confirmation can grant rights in an arbitrary mid-game position. */
    public boolean configure(char[][] board, Side turn, String castling, String target) {
        String requested = "-".equals(castling) ? "" : castling;
        if (requested == null || !requested.matches("K?Q?k?q?")
                || !allowedCastling(board, requested).equals(requested)
                || !enPassantTargets(board, turn).contains(target)) return false;
        castles = requested;
        ep = target;
        remember(board);
        return true;
    }

    /** Path clearance and attacked squares are left to Stockfish's legal move generator. */
    public static String allowedCastling(char[][] board, String requested) {
        StringBuilder result = new StringBuilder();
        if (board[7][4] == 'K') {
            if (requested.indexOf('K') >= 0 && board[7][7] == 'R') result.append('K');
            if (requested.indexOf('Q') >= 0 && board[7][0] == 'R') result.append('Q');
        }
        if (board[0][4] == 'k') {
            if (requested.indexOf('k') >= 0 && board[0][7] == 'r') result.append('k');
            if (requested.indexOf('q') >= 0 && board[0][0] == 'r') result.append('q');
        }
        return result.toString();
    }

    /** Plausible target squares only; the user must confirm the opponent's last double step. */
    public static List<String> enPassantTargets(char[][] board, Side turn) {
        List<String> result = new ArrayList<>();
        result.add("-");
        int pawnRow = turn == Side.WHITE ? 3 : 4;
        int targetRow = turn == Side.WHITE ? 2 : 5;
        int sourceRow = turn == Side.WHITE ? 1 : 6;
        char opponent = turn == Side.WHITE ? 'p' : 'P';
        char capturer = turn == Side.WHITE ? 'P' : 'p';
        for (int f = 0; f < 8; f++) {
            if (board[pawnRow][f] == opponent && empty(board[targetRow][f]) && empty(board[sourceRow][f])
                    && (f > 0 && board[pawnRow][f-1] == capturer || f < 7 && board[pawnRow][f+1] == capturer)) {
                result.add("" + (char) ('a' + f) + (8 - targetRow));
            }
        }
        return result;
    }

    private void remember(char[][] board) {
        previous = new char[8][];
        for (int r = 0; r < 8; r++) previous[r] = board[r].clone();
    }
    private static boolean empty(char piece) { return piece == '.' || piece == 0; }
    private static boolean isStart(char[][] board) {
        for (int r = 0; r < 8; r++) if (!Arrays.equals(board[r], START[r].toCharArray())) return false;
        return true;
    }
    private static boolean same(char[][] a, char[][] b) {
        if (a == null || b == null) return false;
        for (int r = 0; r < 8; r++) if (!Arrays.equals(a[r], b[r])) return false;
        return true;
    }
}
