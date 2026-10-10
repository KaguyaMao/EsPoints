package com.example.espoints.tactical;

import java.util.Optional;

/** Fixed world-space grid. Its origin is the configured map corner, never the viewport. */
public record TacticalMapGrid(double minX, double minZ, double maxX, double maxZ) {
    public static final double LARGE = 150;
    public static final double MEDIUM = 50;
    public static final double SMALL = 50.0 / 3;
    public TacticalMapGrid {
        if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX)
                || !Double.isFinite(maxZ) || maxX <= minX || maxZ <= minZ)
            throw new IllegalArgumentException("Invalid map bounds");
    }
    public record Address(long column, long row, int medium, int small) {
        public String label() { return "(" + columnLabel(column) + "," + (row + 1) + "," + medium + "," + small + ")"; }
        public int mediumColumn() { return (medium - 1) % 3; }
        public int mediumRow() { return (medium - 1) / 3; }
        public int smallColumn() { return (small - 1) % 3; }
        public int smallRow() { return (small - 1) / 3; }
    }
    public Optional<Address> address(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || x < minX || x >= maxX || z < minZ || z >= maxZ)
            return Optional.empty();
        double dx = x - minX, dz = z - minZ;
        long column = (long) Math.floor(dx / LARGE), row = (long) Math.floor(dz / LARGE);
        int mc = (int) ((long) Math.floor(dx / MEDIUM) % 3), mr = (int) ((long) Math.floor(dz / MEDIUM) % 3);
        int sc = (int) ((long) Math.floor(dx * 3 / MEDIUM) % 3), sr = (int) ((long) Math.floor(dz * 3 / MEDIUM) % 3);
        return Optional.of(new Address(column, row, mr * 3 + mc + 1, sr * 3 + sc + 1));
    }
    public static String columnLabel(long zeroBased) {
        if (zeroBased < 0 || zeroBased == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid column");
        StringBuilder label = new StringBuilder();
        for (long n = zeroBased + 1; n > 0; n = (n - 1) / 26) label.append((char) ('A' + (n - 1) % 26));
        return label.reverse().toString();
    }
    public double largeX(Address cell) { return minX + cell.column * LARGE; }
    public double largeZ(Address cell) { return minZ + cell.row * LARGE; }
    public double mediumX(Address cell) { return largeX(cell) + cell.mediumColumn() * MEDIUM; }
    public double mediumZ(Address cell) { return largeZ(cell) + cell.mediumRow() * MEDIUM; }
    public double smallX(Address cell) { return mediumX(cell) + cell.smallColumn() * SMALL; }
    public double smallZ(Address cell) { return mediumZ(cell) + cell.smallRow() * SMALL; }
}
