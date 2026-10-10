package com.example.espoints.hud;

import com.example.espoints.tactical.TacticalMapGrid;
import net.minecraft.client.gui.GuiGraphics;
import org.esradial.client.RadialUiText;

/** Projection changes line density, but never the 300/100/100:3 world-space cells. */
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
        // 很细很淡：小格几乎只有提示作用，大格才稍明显
        lines(g, grid, v, TacticalMapGrid.SMALL, 14, 0x144E858C);
        lines(g, grid, v, TacticalMapGrid.MEDIUM, 14, 0x2A6A9DA4);
        lines(g, grid, v, TacticalMapGrid.LARGE, 4, 0x669ECACD);
        if (hover != null) {
            box(g, grid, v, grid.largeX(hover), grid.largeZ(hover), TacticalMapGrid.LARGE, 0x447BC9C5, false);
            box(g, grid, v, grid.mediumX(hover), grid.mediumZ(hover), TacticalMapGrid.MEDIUM, 0x557BC9C5, false);
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
        RadialUiText.beginFrame();
        var tooltip=tooltip(v,hover,mouseX,mouseY,markerCoordinates,interactive);
        // Detail numbers are local to the hovered parent: no wall of tiny labels.
        if (hover != null) {
            boolean smallNumbers = TacticalMapGrid.SMALL * v.scaleX >= 25 && TacticalMapGrid.SMALL * v.scaleZ >= 20;
            // 中格数字必须按当前中格边长(100)排布，曾硬编码 50 导致位置挤在一起
            numbers(g, grid, v, grid.largeX(hover), grid.largeZ(hover), TacticalMapGrid.MEDIUM, 0xFFB6D9DA, smallNumbers ? hover.medium() : -1,tooltip);
            numbers(g, grid, v, grid.mediumX(hover), grid.mediumZ(hover), TacticalMapGrid.SMALL, 0xFFE4FFF8, -1,tooltip);
        }
        final int ruler = 13;
        // 需求：取消上方/左侧大格坐标的背景色（不再画刻度条底色）
        rulerLabels(g, grid, v, true, ruler);
        rulerLabels(g, grid, v, false, ruler);
        if (tooltip == null) return;
        int x=tooltip.x(), y=tooltip.y(), width=tooltip.width(), height=tooltip.height();
        String label=tooltip.label(), detail=tooltip.detail();
        g.fill(x, y, x + width, y + height, 0xF01C3038);
        g.renderOutline(x, y, width, height, 0xFF8FC7C4);
        RadialUiText.draw(g,RadialUiText.fit(label,width-10,9,600),x+5,y+3,0xFFE4FFF8,9,600);
        RadialUiText.draw(g,RadialUiText.fit(detail,width-10,9,500),x+5,y+15,0xFFAFC3C7,9,500);
    }
    private record Tooltip(int x,int y,int width,int height,String label,String detail) { }
    private static Tooltip tooltip(View v,TacticalMapGrid.Address hover,double mouseX,double mouseY,String markerCoordinates,boolean interactive) {
        if (hover == null || !interactive || mouseX < v.left + 13 || mouseY < v.top + 13) return null;
        String label = hover.label();
        String detail = markerCoordinates == null ? "X: " + (long) Math.floor(v.worldX(mouseX)) + "  Z: " + (long) Math.floor(v.worldZ(mouseY)) : markerCoordinates;
        int width = Math.min(v.width,(int)Math.ceil(Math.max(RadialUiText.width(label,9,600),RadialUiText.width(detail,9,500)))+10), height = 28;
        int x = (int) mouseX + 12, y = (int) mouseY + 12;
        if (x + width > v.right()) x = (int) mouseX - width - 10;
        if (y + height > v.bottom()) y = (int) mouseY - height - 10;
        x = Math.max(v.left, Math.min(v.right() - width, x)); y = Math.max(v.top, Math.min(v.bottom() - height, y));
        return new Tooltip(x,y,width,height,label,detail);
    }
    private static void numbers(GuiGraphics g, TacticalMapGrid grid, View v, double x, double z, double step, int color, int skip, Tooltip tooltip) {
        if (step * v.scaleX < 25 || step * v.scaleZ < 20) return;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            if (row * 3 + column + 1 == skip || x + (column + .5) * step >= grid.maxX() || z + (row + .5) * step >= grid.maxZ()) continue;
            String text = Integer.toString(row * 3 + column + 1);
            int sx = v.x(x + (column + .5) * step), sy = v.y(z + (row + .5) * step);
            double textWidth = RadialUiText.width(text,7,400);
            if (sx-textWidth/2 < v.left+13 || sx+textWidth/2 > v.right()-1
                || sy-5 < v.top+13 || sy+6 > v.bottom()-1) continue;
            if (tooltip != null && sx+textWidth/2 > tooltip.x() && sx-textWidth/2 < tooltip.x()+tooltip.width()
                && sy+6 > tooltip.y() && sy-5 < tooltip.y()+tooltip.height()) continue;
            RadialUiText.draw(g,text,sx-textWidth/2,sy-4,color,7,400);
        }
    }
    private static void rulerLabels(GuiGraphics g, TacticalMapGrid grid, View v, boolean horizontal, int ruler) {
        double origin = horizontal ? grid.minX() : grid.minZ(), max = horizontal ? grid.maxX() : grid.maxZ();
        double minView = horizontal ? v.minX : v.minZ, scale = horizontal ? v.scaleX : v.scaleZ;
        int start = horizontal ? v.left : v.top, end = horizontal ? v.right() : v.bottom();
        // 坐标系间隔必须跟随大格边长（曾硬编码 150，改 300 后刻度就错位了）
        final double step = com.example.espoints.tactical.TacticalMapGrid.LARGE;
        long first = Math.max(0, (long) Math.floor((minView - origin) / step));
        long last = (long) Math.ceil((Math.min(max, minView + (end - start) / scale) - origin) / step) - 1;
        long stride = Math.max(1, (long) Math.ceil(24 / (step * scale)));
        for (long i = first; i <= last; i += stride) {
            double a = Math.max(origin + i * step, minView), b = Math.min(Math.min(origin + (i + 1) * step, max), minView + (end - start) / scale);
            if (b <= a) continue;
            int center = (int) Math.round(start + ((a + b) / 2 - minView) * scale);
            String label = horizontal ? TacticalMapGrid.columnLabel(i) : Long.toString(i + 1);
            double textWidth=RadialUiText.width(label,6,400);
            if (horizontal) {
                String fitted=RadialUiText.fit(label,Math.max(0,(b-a)*scale-4),6,400);
                if (!fitted.isEmpty()) RadialUiText.draw(g,fitted,center-RadialUiText.width(fitted,6,400)/2,v.top+2,0xCCEAFBF7,6,400);
            }
            else {
                if (center-5 < v.top+ruler || center+6 > v.bottom()) continue;
                // Rows with multiple digits remain readable in a narrow ruler.
                float textScale = (float)Math.min(1,8/Math.max(1,textWidth));
                g.pose().pushPose();
                g.pose().translate(v.left + ruler / 2.0, center, 0); g.pose().scale(textScale, textScale, 1);
                RadialUiText.draw(g,label,-textWidth/2,-4,0xCCEAFBF7,6,400);g.pose().popPose();
            }
        }
    }
}
