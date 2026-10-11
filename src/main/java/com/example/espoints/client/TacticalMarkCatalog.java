package com.example.espoints.client;

import com.example.espoints.tactical.TacticalMarkerType;
import java.util.List;

/** Menu tree descriptors independent of Minecraft registries, also used by map placement. */
public final class TacticalMarkCatalog {
    public record Directory(String id, String title, String icon, String parent, List<TacticalMarkerType> marks) { }
    private TacticalMarkCatalog() { }
    public static List<Directory> directories() { return List.of(
        new Directory("enemy", "敌军标记", "radialenemyicon", "", List.of()),
        new Directory("orders", "战术指令", "radialobservemarker", "", List.of()),
        new Directory("infantry", "步兵", "mark/enemy/map_genericinfantry.png", "enemy", List.of(TacticalMarkerType.ENEMY_INFANTRY, TacticalMarkerType.ENEMY_MACHINE_GUNNER, TacticalMarkerType.ENEMY_LIGHT_AT, TacticalMarkerType.ENEMY_HEAVY_AT, TacticalMarkerType.ENEMY_SNIPER)),
        new Directory("vehicles", "载具", "mark/enemy/map_truck_logistics.png", "enemy", List.of()),
        new Directory("air", "空中目标", "mark/enemy/map_transporthelo.png", "enemy", List.of(TacticalMarkerType.ENEMY_HELICOPTER, TacticalMarkerType.ENEMY_ATTACK_HELICOPTER, TacticalMarkerType.ENEMY_SCOUT_HELICOPTER, TacticalMarkerType.ENEMY_UAV, TacticalMarkerType.ENEMY_JET)),
        new Directory("bases", "敌方据点", "map/radio.png", "enemy", List.of(TacticalMarkerType.ENEMY_FOB, TacticalMarkerType.ENEMY_HAB, TacticalMarkerType.ENEMY_RALLY, TacticalMarkerType.ENEMY_REPAIR)),
        new Directory("weapons", "固定武器", "mark/enemy/deployable_hmg.png", "enemy", List.of(TacticalMarkerType.ENEMY_MORTAR, TacticalMarkerType.ENEMY_HMG, TacticalMarkerType.ENEMY_AT_WEAPON, TacticalMarkerType.ENEMY_AA_WEAPON)),
        new Directory("hazards", "危险物", "mark/enemy/map_ied.png", "enemy", List.of(TacticalMarkerType.ENEMY_MINE, TacticalMarkerType.ENEMY_IED)),
        new Directory("armour", "装甲载具", "radialenemyicon", "vehicles", List.of(TacticalMarkerType.ENEMY_TANK, TacticalMarkerType.ENEMY_IFV, TacticalMarkerType.ENEMY_APC, TacticalMarkerType.ENEMY_TRACKED_IFV, TacticalMarkerType.ENEMY_TRACKED_APC, TacticalMarkerType.ENEMY_RECON)),
        new Directory("light", "轻型载具", "radialenemyicon", "vehicles", List.of(TacticalMarkerType.ENEMY_LIGHT_VEHICLE, TacticalMarkerType.ENEMY_ARMED_JEEP, TacticalMarkerType.ENEMY_AT_JEEP, TacticalMarkerType.ENEMY_MOTORCYCLE, TacticalMarkerType.ENEMY_BOAT)),
        new Directory("support", "后勤与支援", "radialenemyicon", "vehicles", List.of(TacticalMarkerType.ENEMY_LOGISTICS, TacticalMarkerType.ENEMY_TRANSPORT_TRUCK, TacticalMarkerType.ENEMY_ANTI_AIR, TacticalMarkerType.ENEMY_ARTILLERY_VEHICLE)),
        new Directory("actions", "行动指令", "radialobservemarker", "orders", List.of(TacticalMarkerType.ATTACK_HERE, TacticalMarkerType.DEFEND_HERE, TacticalMarkerType.MOVE_HERE, TacticalMarkerType.OBSERVE_HERE)),
        new Directory("requests", "补给与接送", "resupply_icon", "orders", List.of(TacticalMarkerType.REQUEST_PICKUP))); }
    public static List<Directory> children(String parent) {
        return directories().stream().filter(d -> d.parent().equals(parent)).toList();
    }
}
