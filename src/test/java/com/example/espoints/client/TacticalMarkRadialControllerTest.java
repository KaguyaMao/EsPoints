package com.example.espoints.client;

import org.esradial.core.RadialSession;
import org.esradial.core.RadialLayout;
import com.example.espoints.tactical.TacticalMarkerIcons;
import com.example.espoints.tactical.TacticalMarkerType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TacticalMarkRadialControllerTest {

    @Test
    void menuListsEverySelectableTypeOnceAndSkipsArtillery() {
        List<String> ids = TacticalMarkRadialController.menuSlotIds();
        assertEquals(TacticalMarkerType.selectableValues().length, ids.size());
        assertTrue(ids.contains("espoints.mark.ENEMY_INFANTRY"));
        assertTrue(ids.contains("espoints.mark.ATTACK_HERE"));
        assertTrue(ids.contains("espoints.mark.DEFEND_HERE"));
        assertFalse(ids.contains("espoints.mark.ARTILLERY_TARGET"));
        assertEquals(ids.size(), ids.stream().distinct().count());
    }

    @Test
    void menuModelHasExactlyOneSlotPerSelectableTypeWithoutArtillery() {
        var entries = TacticalMarkRadialController.menuEntries();
        Map<String, TacticalMarkRadialController.MenuEntry> byName = entries.stream()
            .collect(Collectors.toMap(TacticalMarkRadialController.MenuEntry::id, slot -> slot));
        for (TacticalMarkerType type : TacticalMarkerType.selectableValues()) {
            assertTrue(byName.containsKey("espoints.mark." + type.name()),
                "缺失标点槽位: " + type);
        }
        assertEquals(TacticalMarkerType.selectableValues().length, byName.size());
        assertFalse(byName.containsKey("espoints.mark.ARTILLERY_TARGET"));
    }

    @Test
    void everyMarkSlotClosesAfterAction() {
        var entries = TacticalMarkRadialController.menuEntries();
        assertFalse(entries.isEmpty());
        for (var slot : entries) {
            assertTrue(slot.closeAfterAction(),
                "标点槽位必须选择后关闭: " + slot.id());
        }
    }

    @Test
    void squadLayoutHasUnequalActionsAndInertGaps() {
        var entries = TacticalMarkRadialController.menuEntries();
        var layout = TacticalMarkRadialController.menuLayout();
        List<RadialLayout.Sector> sectors = layout.sectors();
        assertTrue(sectors.stream().filter(s -> s.slotIndex() >= 0)
            .map(RadialLayout.Sector::sweepDegrees).distinct().count() > 1);
        assertEquals(2, sectors.stream().filter(s -> s.slotIndex() == -1).count());
        for (RadialLayout.Sector sector : sectors) {
            double angle = sector.centerRadians();
            assertEquals(sector.slotIndex(), layout.hitIndex(
                Math.sin(angle) * 70, -Math.cos(angle) * 70, entries.size()));
        }
    }

    @Test
    void eachSlotUsesTheExistingPackagedTacticalIcon() {
        var entries = TacticalMarkRadialController.menuEntries();
        for (TacticalMarkerType type : TacticalMarkerType.selectableValues()) {
            var slot = entries.stream().filter(s -> s.id().endsWith("." + type.name()))
                .findFirst().orElseThrow();
            var texture = TacticalMarkerIcons.textureFor(type);
            assertEquals(texture, slot.icon());
            assertNotNull(getClass().getResource("/assets/" + texture.getNamespace() + "/" + texture.getPath()));
        }
    }

    @Test
    void clickingThenReleasingConfirmsOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        var session = sessionWithCounter(calls);
        var layout = session.page().layout();
        session.hover(layout.slotX(0, session.page().slots().size()),
            layout.slotY(0, session.page().slots().size()));
        session.updatePrimary(true);
        assertFalse(session.confirmRelease());
        assertEquals(1, calls.get());
        assertTrue(session.isClosed());
    }

    @Test
    void releasingOverAnActionConfirmsOnce() {
        AtomicInteger calls = new AtomicInteger();
        var session = sessionWithCounter(calls);
        var layout = session.page().layout();
        session.hover(layout.slotX(0, session.page().slots().size()),
            layout.slotY(0, session.page().slots().size()));
        assertTrue(session.confirmRelease());
        assertFalse(session.confirmRelease());
        assertEquals(1, calls.get());
    }

    @Test
    void centerGapAndCancelNeverPlaceAMarker() {
        AtomicInteger calls = new AtomicInteger();
        var center = sessionWithCounter(calls);
        center.hover(0, 0);
        assertFalse(center.confirmRelease());
        var gap = sessionWithCounter(calls);
        var sector = gap.page().layout().sectors().stream()
            .filter(s -> s.slotIndex() == -1).findFirst().orElseThrow();
        gap.hover(Math.sin(sector.centerRadians()) * 70, -Math.cos(sector.centerRadians()) * 70);
        gap.updatePrimary(true);
        assertFalse(gap.confirmRelease());
        var canceled = sessionWithCounter(calls);
        var layout = canceled.page().layout();
        canceled.hover(layout.slotX(0, canceled.page().slots().size()),
            layout.slotY(0, canceled.page().slots().size()));
        canceled.close(RadialSession.CloseReason.CANCEL);
        assertFalse(canceled.confirmRelease());
        assertEquals(0, calls.get());
    }

    private static RadialSession<TacticalMarkRadialController.MenuEntry> sessionWithCounter(AtomicInteger calls) {
        var entries = TacticalMarkRadialController.menuEntries();
        var slots = entries.stream().map(s -> new RadialSession.Slot<>(s.id(), s,
            true, s.closeAfterAction(), 0, (Runnable) () -> calls.incrementAndGet())).toList();
        return new RadialSession<>(new RadialSession.Page<>("espoints:tactical_mark", TacticalMarkRadialController.menuLayout(), slots), reason -> { });
    }
}
