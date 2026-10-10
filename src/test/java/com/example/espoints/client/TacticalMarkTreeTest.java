package com.example.espoints.client;

import com.example.espoints.tactical.TacticalMarkerType;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class TacticalMarkTreeTest {
    @Test void everySelectableMarkHasExactlyOneLeafAndNoArtilleryEntry() {
        var leaves=TacticalMarkCatalog.directories().stream().flatMap(d->d.marks().stream()).toList();
        assertEquals(TacticalMarkerType.selectableValues().length,leaves.size());
        assertEquals(leaves.size(),new HashSet<>(leaves).size());
        for(var type:TacticalMarkerType.selectableValues()) assertTrue(leaves.contains(type));
        assertFalse(leaves.contains(TacticalMarkerType.ARTILLERY_TARGET));
    }
    @Test void allDirectoriesReachARootWithoutCyclesAndFitSixActions() {
        var all=TacticalMarkCatalog.directories();
        for(var directory:all) {
            assertTrue(directory.marks().size()+TacticalMarkCatalog.children(directory.id()).size()<=6);
            var visited=new HashSet<String>();var current=directory;
            while(!current.parent().isEmpty()) {
                assertTrue(visited.add(current.id()));String parent=current.parent();
                current=all.stream().filter(d->d.id().equals(parent)).findFirst().orElseThrow();
            }
        }
    }
    @Test void armourLightAndSupportAreSeparateVehicleSubdirectories() {
        assertEquals(3,TacticalMarkCatalog.children("vehicles").size());
        assertTrue(TacticalMarkCatalog.directories().stream().filter(d->d.id().equals("armour"))
            .findFirst().orElseThrow().marks().contains(TacticalMarkerType.ENEMY_TANK));
        var requests=TacticalMarkCatalog.directories().stream().filter(d->d.id().equals("requests"))
            .findFirst().orElseThrow().marks();
        assertEquals(java.util.List.of(TacticalMarkerType.REQUEST_PICKUP), requests);
        assertFalse(TacticalMarkerType.REQUEST_AMMO.isSelectableFromMenu());
        assertFalse(TacticalMarkerType.REQUEST_CONSTRUCTION.isSelectableFromMenu());
    }
    @Test void originalNetworkOrdinalsRemainStable() {
        assertEquals(0,TacticalMarkerType.ENEMY_INFANTRY.ordinal());assertEquals(5,TacticalMarkerType.ATTACK_HERE.ordinal());
        assertEquals(7,TacticalMarkerType.ARTILLERY_TARGET.ordinal());
        assertNull(TacticalMarkerType.fromNetworkId(-1));assertNull(TacticalMarkerType.fromNetworkId(1000));
    }
}
