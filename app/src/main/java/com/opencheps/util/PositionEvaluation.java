package com.opencheps.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A root-position score from the side-to-move perspective, independent of practice strength. */
public final class PositionEvaluation {
    public static final int MAX_LINE_PLIES = 128;
    public final int cp;
    public final boolean mate;
    public final List<String> moves;

    public PositionEvaluation(int cp, boolean mate, List<String> moves) {
        this.cp = cp;
        this.mate = mate;
        this.moves = Collections.unmodifiableList(new ArrayList<>(moves.subList(0,
                Math.min(MAX_LINE_PLIES, moves.size()))));
    }

    public String score(Side turn, Side selected) {
        long value = turn == selected ? cp : -(long) cp;
        if (mate) return (value < 0 ? "-M" : "M") + mateMoves();
        return String.format(Locale.ROOT, "%+.2f", value / 100.0);
    }
    public long mateMoves() { return Math.max(0, 100000L - Math.abs((long) cp)); }
    public boolean hasMateLine() { return mate && mateMoves() > 0 && !moves.isEmpty(); }
    public int matePlies() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, 2*mateMoves() - (cp > 0 ? 1 : 0)));
    }
    public boolean completeMateLine() { return mate && moves.size() >= matePlies(); }
    public String mateLine(Side first) {
        return hasMateLine() ? StudyLine.format(moves, first, Math.min(matePlies(), MAX_LINE_PLIES)) : "";
    }
}
