package com.example.espoints.hud;

import com.example.espoints.tactical.TacticalMapGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Projection changes line density, but never the 150/50/50:3 world-space cells. */
public final class TacticalMapGridRenderer {
    public record View(int left, int top, int width, int height, double minX, double minZ, double scaleX, double scaleZ) {
        public int right() { return left + width; }
        public int bottom() { return top + height; }
        public int x(double world) { return (int) Math.round(left + (world - minX) * scaleX); }
        public int y(double world) { return (int) Math.round(top + (world - minZ) * scaleZ); }
        public double worldX(double screen) { return minX + (screen - left) / scaleX; }
        public double worldZ(double screen) { return minZ + (screen - top) / scaleZ; }
    }
    private TacticalMapGridRenderer() { }
    public static void drawGrid(GuiGraphics g, TacticalMapGrid grid, View v, TacticalMapGrid.Address hover) {
        // Only visit visible indices. Very distant fine lines are hidden, not rescaled.
        lines(g, grid, v, TacticalMapGrid.SMALL, 10, 0x244E858C);
        lines(g, grid, v, TacticalMapGrid.MEDIUM, 10, 0x556A9DA4);
        lines(g, grid, v, TacticalMapGrid.LARGE, 4, 0xB39ECACD);
        if (hover != null) {
            box(g, grid, v, grid.largeX(hover), grid.largeZ(hover), 150, 0x557BC9C5, false);
            box(g, grid, v, grid.mediumX(hover), grid.mediumZ(hover), 50, 0x667BC9C5, false);
            box(g, grid, v, grid.smallX(hover), grid.smallZ(hover), TacticalMapGrid.SMALL, 0x387BC9C5, true);
            box(g, grid, v, grid.smallX(hover), grid.smallZ(hover), TacticalMapGrid.SMALL, 0xFFADF4E3, false);
        }
    }
    private static void lines(GuiGraphics g, TacticalMapGrid grid, View v, double step, int minPixels, int color) {
        double endX = Math.min(grid.maxX(), v.worldX(v.right())), endZ = Math.min(grid.maxZ(), v.worldZ(v.bottom()));
        double startX = Math.max(grid.minX(), v.minX), startZ = Math.max(grid.minZ(), v.minZ);
        if (endX <= startX || endZ <= startZ) return;
        int left = Math.max(v.left, v.x(grid.minX())), top = Math.max(v.top, v.y(grid.minZ()));
        int right = Math.min(v.right(), v.x(grid.maxX())), bottom = Math.min(v.bottom(), v.y(grid.maxZ()));
        if (step * v.scaleX >= minPixels) {
            long first = Math.max(0, (long) Math.ceil((startX - grid.minX()) / step));
            long last = (long) Math.floor((endX - grid.minX()) / step);
            for (long i = first; i <= last; i++) {
                int x = v.x(grid.minX() + i * step); g.fill(x, top, x + 1, bottom, color);
            }
        }
        if (step * v.scaleZ >= minPixels) {
            long first = Math.max(0, (long) Math.ceil((startZ - grid.minZ()) / step));
            long last = (long) Math.floor((endZ - grid.minZ()) / step);
            for (long i = first; i <= last; i++) {
                int y = v.y(grid.minZ() + i * step); g.fill(left, y, right, y + 1, color);
            }
        }
    }
    private static void box(GuiGraphics g, TacticalMapGrid grid, View v, double x, double z, double size, int color, boolean fill) {
        int l = v.x(x), t = v.y(z), r = v.x(Math.min(grid.maxX(), x + size)), b = v.y(Math.min(grid.maxZ(), z + size));
        if (r <= l || b <= t) return;
        if (fill) g.fill(l, t, r, b, color);
        else g.renderOutline(l, t, r - l, b - t, color);
    }
    public static void drawChrome(GuiGraphics g, TacticalMapGrid grid, View v, TacticalMapGrid.Address hover,
                                  double mouseX, double mouseY, String markerCoordinates, boolean interactive) {
        var font = Minecraft.getInstance().font;
        // Detail numbers are local to the hovered parent: no wall of tiny labels.
        if (hover != null) {
            boolean smallNumbers = TacticalMapGrid.SMALL * v.scaleX >= 25 && TacticalMapGrid.SMALL * v.scaleZ >= 20;
            numbers(g, grid, v, grid.largeX(hover), grid.largeZ(hover), 50, 0xFFB6D9DA, smallNumbers ? hover.medium() : -1);
            numbers(g, grid, v, grid.mediumX(hover), grid.mediumZ(hover), TacticalMapGrid.SMALL, 0xFFE4FFF8, -1);
        }
        final int ruler = 15;
        g.fill(v.left, v.top, v.right(), v.top + ruler, 0xE51C3038);
        g.fill(v.left, v.top, v.left + ruler, v.bottom(), 0xE51C3038);
        rulerLabels(g, grid, v, true, ruler);
        rulerLabels(g, grid, v, false, ruler);
        if (interactive && v.width >= 260) {
            String legend = "大格150 · 中格50 · 小格16⅔";
            int w = font.width(legend);
            g.fill(v.left + 18, v.bottom() - 13, v.left + 22 + w, v.bottom(), 0xC91C3038);
            g.drawString(font, legend, v.left + 20, v.bottom() - 11, 0xFFC8DDDF, false);
        }
        if (hover == null || !interactive || mouseX < v.left + ruler || mouseY < v.top + ruler) return;
        String label = hover.label();
        String detail = markerCoordinates == null ? "X: " + (long) Math.floor(v.worldX(mouseX)) + "  Z: " + (long) Math.floor(v.worldZ(mouseY)) : markerCoordinates;
        int width = Math.min(v.width, Math.max(font.width(label), font.width(detail)) + 10), height = 28;
        int x = (int) mouseX + 12, y = (int) mouseY + 12;
        if (x + width > v.right()) x = (int) mouseX - width - 10;
        if (y + height > v.bottom()) y = (int) mouseY - height - 10;
        x = Math.max(v.left, Math.min(v.right() - width, x)); y = Math.max(v.top, Math.min(v.bottom() - height, y));
        g.fill(x, y, x + width, y + height, 0xF01C3038);
        g.renderOutline(x, y, width, height, 0xFF8FC7C4);
        g.drawString(font, label, x + 5, y + 4, 0xFFE4FFF8, false);
        g.drawString(font, detail, x + 5, y + 16, 0xFFAFC3C7, false);
    }
    private static void numbers(GuiGraphics g, TacticalMapGrid grid, View v, double x, double z, double step, int color, int skip) {
        if (step * v.scaleX < 25 || step * v.scaleZ < 20) return;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            if (row * 3 + column + 1 == skip || x + (column + .5) * step >= grid.maxX() || z + (row + .5) * step >= grid.maxZ()) continue;
            String text = Integer.toString(row * 3 + column + 1);
            int sx = v.x(x + (column + .5) * step), sy = v.y(z + (row + .5) * step);
            g.drawString(Minecraft.getInstance().font, text, sx - 3, sy - 4, color, false);
        }
    }
    private static void rulerLabels(GuiGraphics g, TacticalMapGrid grid, View v, boolean horizontal, int ruler) {
        double origin = horizontal ? grid.minX() : grid.minZ(), max = horizontal ? grid.maxX() : grid.maxZ();
        double minView = horizontal ? v.minX : v.minZ, scale = horizontal ? v.scaleX : v.scaleZ;
        int start = horizontal ? v.left : v.top, end = horizontal ? v.right() : v.bottom();
        long first = Math.max(0, (long) Math.floor((minView - origin) / 150));
        long last = (long) Math.ceil((Math.min(max, minView + (end - start) / scale) - origin) / 150) - 1;
        long stride = Math.max(1, (long) Math.ceil(24 / (150 * scale)));
        for (long i = first; i <= last; i += stride) {
            double a = Math.max(origin + i * 150, minView), b = Math.min(Math.min(origin + (i + 1) * 150, max), minView + (end - start) / scale);
            if (b <= a) continue;
            int center = (int) Math.round(start + ((a + b) / 2 - minView) * scale);
            String label = horizontal ? TacticalMapGrid.columnLabel(i) : Long.toString(i + 1);
            var font = Minecraft.getInstance().font;
            if (horizontal) g.drawString(font, label, center - font.width(label) / 2, v.top + 3, 0xFFD8F5F1, false);
            else {
                // Rows with multiple digits remain readable in a narrow ruler.
                float textScale = Math.min(1, 12f / Math.max(1, font.width(label)));
                g.pose().pushPose();
                g.pose().translate(v.left + ruler / 2.0, center, 0); g.pose().scale(textScale, textScale, 1);
                g.drawString(font, label, -font.width(label) / 2, -4, 0xFFD8F5F1, false); g.pose().popPose();
            }
        }
    }
}
