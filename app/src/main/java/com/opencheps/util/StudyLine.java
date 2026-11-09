package com.opencheps.util;

import java.util.List;

/** One possible branch, with alternating sides; never several candidate arrows. */
public final class StudyLine {
    private StudyLine() {}
    public static String format(List<String> moves, Side first) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(6, moves.size()); i++) {
            String move = moves.get(i);
            if (move == null || !move.matches("[a-h][1-8][a-h][1-8][qrbn]?")) break;
            if (i > 0) text.append(i % 2 == 0 ? "\n" : "   ");
            Side side = i % 2 == 0 ? first : first == Side.WHITE ? Side.BLACK : Side.WHITE;
            text.append(side == Side.WHITE ? "W " : "B ")
                    .append(move, 0, 2).append('→').append(move, 2, 4);
            if (move.length() == 5) text.append('=').append(Character.toUpperCase(move.charAt(4)));
        }
        return text.toString();
    }
}
