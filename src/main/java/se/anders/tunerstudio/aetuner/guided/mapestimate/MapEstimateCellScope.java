package se.anders.tunerstudio.aetuner.guided.mapestimate;

import java.util.BitSet;

/** Immutable table-cell mask. Whole-table and arbitrary selected-cell scopes share one representation. */
public final class MapEstimateCellScope {
    private final int rows;
    private final int cols;
    private final BitSet selected;

    private MapEstimateCellScope(int rows, int cols, BitSet selected) {
        if (rows <= 0 || cols <= 0) throw new IllegalArgumentException("positive table dimensions required");
        this.rows = rows;
        this.cols = cols;
        this.selected = (BitSet) selected.clone();
    }

    public static MapEstimateCellScope all(int rows, int cols) {
        BitSet bits = new BitSet(rows * cols);
        bits.set(0, rows * cols);
        return new MapEstimateCellScope(rows, cols, bits);
    }

    public static MapEstimateCellScope none(int rows, int cols) {
        return new MapEstimateCellScope(rows, cols, new BitSet(rows * cols));
    }

    public MapEstimateCellScope withCell(int row, int col, boolean value) {
        check(row, col);
        BitSet bits = (BitSet) selected.clone();
        bits.set(index(row, col), value);
        return new MapEstimateCellScope(rows, cols, bits);
    }

    public MapEstimateCellScope withRectangle(int rowA, int colA, int rowB, int colB, boolean value) {
        int r0 = Math.min(rowA, rowB), r1 = Math.max(rowA, rowB);
        int c0 = Math.min(colA, colB), c1 = Math.max(colA, colB);
        check(r0, c0); check(r1, c1);
        BitSet bits = (BitSet) selected.clone();
        for (int r = r0; r <= r1; r++) for (int c = c0; c <= c1; c++) bits.set(index(r,c), value);
        return new MapEstimateCellScope(rows, cols, bits);
    }

    public boolean contains(int row, int col) { check(row, col); return selected.get(index(row,col)); }
    public int size() { return selected.cardinality(); }
    public boolean isWholeTable() { return size() == rows * cols; }
    public int rows() { return rows; }
    public int cols() { return cols; }

    private int index(int row, int col) { return row * cols + col; }
    private void check(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) throw new IndexOutOfBoundsException(row + "," + col);
    }
}
