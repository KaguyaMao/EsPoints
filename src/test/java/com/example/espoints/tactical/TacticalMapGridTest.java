package com.example.espoints.tactical;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TacticalMapGridTest {
    private final TacticalMapGrid grid = new TacticalMapGrid(0, 0, 650, 430);
    @Test void approvedExamplesAndPartialEdgeCells() {
        double[][] inputs = {{0,0},{50,0},{0,50},{149,149},{150,0},{225,385},{649,429}};
        String[] expected = {"(A,1,1,1)","(A,1,1,2)","(A,1,1,4)","(A,1,5,5)","(A,1,2,2)","(A,2,3,7)","(C,2,4,2)"};
        for (int i = 0; i < inputs.length; i++) assertEquals(expected[i], grid.address(inputs[i][0], inputs[i][1]).orElseThrow().label());
    }
    @Test void originCanBeNegativeAndIsIndependentOfMapSize() {
        var shifted = new TacticalMapGrid(-512, -2000, 5000, 5000);
        for (int x = 0; x < 650; x += 7) for (int z = 0; z < 430; z += 11)
            assertEquals(grid.address(x,z), shifted.address(x - 512,z - 2000));
    }
    @Test void mediumAndSmallNumberingIsRowMajor() {
        for (int mr = 0; mr < 3; mr++) for (int mc = 0; mc < 3; mc++)
            for (int sr = 0; sr < 3; sr++) for (int sc = 0; sc < 3; sc++) {
                var a = grid.address(mc * TacticalMapGrid.MEDIUM + (sc + .5) * TacticalMapGrid.SMALL,
                    mr * TacticalMapGrid.MEDIUM + (sr + .5) * TacticalMapGrid.SMALL).orElseThrow();
                assertEquals(mr * 3 + mc + 1, a.medium()); assertEquals(sr * 3 + sc + 1, a.small());
            }
    }
    @Test void boundsAreHalfOpenAndRejectNonFiniteInput() {
        for (double[] p : new double[][] {{-1,0},{0,-1},{650,0},{0,430},{Double.NaN,0},{0,Double.POSITIVE_INFINITY}})
            assertTrue(grid.address(p[0],p[1]).isEmpty());
        assertTrue(grid.address(Math.nextDown(650d), Math.nextDown(430d)).isPresent());
        assertEquals("(A,1,2,2)", grid.address(150,0).orElseThrow().label());
        assertEquals("(A,1,1,2)", grid.address(50,0).orElseThrow().label());
        assertEquals("(A,1,1,2)", grid.address(TacticalMapGrid.SMALL,0).orElseThrow().label());
    }
    @Test void letterRolloverKeepsGlobalIndices() {
        assertEquals("A", TacticalMapGrid.columnLabel(0)); assertEquals("Z", TacticalMapGrid.columnLabel(25));
        assertEquals("AA", TacticalMapGrid.columnLabel(26)); assertEquals("AZ", TacticalMapGrid.columnLabel(51));
        assertEquals("BA", TacticalMapGrid.columnLabel(52)); assertEquals("ZZ", TacticalMapGrid.columnLabel(701));
        assertEquals("AAA", TacticalMapGrid.columnLabel(702));
        assertEquals("(N,1,1,1)", new TacticalMapGrid(0,0,8000,8000).address(3900,0).orElseThrow().label());
    }
    @Test void fractionalSmallCellsDoNotAccumulateRoundingDrift() {
        var wide = new TacticalMapGrid(-10000,-10000,100000,100000);
        for (int i = 0; i < 5000; i++) {
            double x = -10000 + (i + .5) * TacticalMapGrid.SMALL;
            var a = wide.address(x,-10000).orElseThrow();
            assertEquals(i / 9, a.column()); assertEquals((i / 3) % 3 + 1, a.medium());
            assertEquals(i % 3 + 1, a.small());
            assertTrue(x >= wide.smallX(a) && x < wide.smallX(a) + TacticalMapGrid.SMALL);
        }
    }
    @Test void invalidBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TacticalMapGrid(0,0,0,1));
        assertThrows(IllegalArgumentException.class, () -> new TacticalMapGrid(0,0,1,Double.NaN));
    }
    @Test void projectionPanAndZoomDoNotRenumberCells() {
        for (double scale : new double[] {.2, .8, 2, 4}) for (double start : new double[] {-10, 0, 150}) {
            var view = new com.example.espoints.hud.TacticalMapGridRenderer.View(20,40,500,300,start,200,scale,scale);
            double sx = view.left() + (225 - start) * scale, sy = view.top() + (385 - 200) * scale;
            assertEquals("(A,2,3,7)",grid.address(view.worldX(sx),view.worldZ(sy)).orElseThrow().label());
        }
    }
}
