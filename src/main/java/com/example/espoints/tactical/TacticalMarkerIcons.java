package com.example.espoints.tactical;

import com.example.espoints.ESPointsMod;
import net.minecraft.resources.ResourceLocation;

/** 战术标点 type → 贴图（地图 2D 与世界 3D 共用）。 */
public final class TacticalMarkerIcons {

    public static final ResourceLocation EN_SOLDIER =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_soldier.png");
    /** 敌方单位统一红色贴图（轮盘 / 地图 / 3D 共用）。 */
    public static final ResourceLocation EN_TANK =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_tank.png");
    public static final ResourceLocation EN_IFV =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_ifv.png");
    public static final ResourceLocation EN_TRUCK =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_truck.png");
    public static final ResourceLocation EN_HELI =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_heli.png");
    public static final ResourceLocation MARK_ATTACK =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/mark_attack.png");
    public static final ResourceLocation MARK_DEFEND =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/mark_defend.png");
    public static final ResourceLocation ACP =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/acp.png");

    /** 敌方单位标点统一红（ARGB）。 */
    public static final int ENEMY_RED = 0xFFE05252;

    private TacticalMarkerIcons() {
    }

    public static ResourceLocation textureFor(TacticalMarkerType type) {
        if (type == null) {
            return EN_SOLDIER;
        }
        return switch (type) {
            case ENEMY_INFANTRY -> icon("enemy/map_genericinfantry.png");
            case ENEMY_TANK -> icon("enemy/map_tank.png");
            case ENEMY_IFV -> icon("enemy/map_ifv.png");
            case ENEMY_LIGHT_VEHICLE -> icon("enemy/map_jeep.png");
            case ENEMY_HELICOPTER -> icon("enemy/map_transporthelo.png");
            case ATTACK_HERE -> icon("commands/attack.png");
            case DEFEND_HERE -> icon("commands/defend.png");
            case ARTILLERY_TARGET -> ACP;
            case ENEMY_MACHINE_GUNNER -> icon("enemy/map_infmg.png");
            case ENEMY_LIGHT_AT -> icon("enemy/map_lat.png");
            case ENEMY_HEAVY_AT -> icon("enemy/map_hat.png");
            case ENEMY_SNIPER -> icon("enemy/map_marksmansniper.png");
            case ENEMY_APC -> icon("enemy/map_apc.png");
            case ENEMY_TRACKED_IFV -> icon("enemy/map_trackedifv.png");
            case ENEMY_TRACKED_APC -> icon("enemy/map_trackedapc.png");
            case ENEMY_RECON -> icon("enemy/t_map_wheeledrecon.png");
            case ENEMY_ARMED_JEEP -> icon("enemy/map_jeep_turret.png");
            case ENEMY_AT_JEEP -> icon("enemy/map_jeep_antitank.png");
            case ENEMY_MOTORCYCLE -> icon("enemy/map_motorcycle.png");
            case ENEMY_BOAT -> icon("enemy/map_boat.png");
            case ENEMY_LOGISTICS -> icon("enemy/map_truck_logistics.png");
            case ENEMY_TRANSPORT_TRUCK -> icon("enemy/map_truck_transport.png");
            case ENEMY_ANTI_AIR -> icon("enemy/map_antiair.png");
            case ENEMY_ARTILLERY_VEHICLE -> icon("enemy/t_map_truck_artillery.png");
            case ENEMY_ATTACK_HELICOPTER -> icon("enemy/map_attackhelo.png");
            case ENEMY_SCOUT_HELICOPTER -> icon("enemy/t_map_helicopter_scout.png");
            case ENEMY_UAV -> icon("enemy/map_uav.png");
            case ENEMY_JET -> icon("enemy/map_jet_a10.png");
            case ENEMY_FOB -> icon("enemy/deployable_fob.png");
            case ENEMY_HAB -> icon("enemy/deployable_hab.png");
            case ENEMY_RALLY -> icon("enemy/rallypoint.png");
            case ENEMY_REPAIR -> icon("enemy/deployable_repairstation.png");
            case ENEMY_MORTAR -> icon("enemy/deployable_mortars.png");
            case ENEMY_HMG -> icon("enemy/deployable_hmg.png");
            case ENEMY_AT_WEAPON -> icon("enemy/deployable_anti_tank.png");
            case ENEMY_AA_WEAPON -> icon("enemy/deployable_antiairgun.png");
            case ENEMY_MINE -> icon("enemy/map_mine.png");
            case ENEMY_IED -> icon("enemy/map_ied.png");
            case MOVE_HERE -> icon("commands/move.png");
            case OBSERVE_HERE -> icon("commands/observe.png");
            case REQUEST_AMMO -> icon("commands/resupply_ammo.png");
            case REQUEST_CONSTRUCTION -> icon("commands/resupply_construction.png");
            case REQUEST_PICKUP -> icon("commands/pickup.png");
        };
    }

    private static ResourceLocation icon(String path) {
        return ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/mark/" + path);
    }

    /** 是否为敌方单位类标点（不含进攻/防守指令标）。 */
    public static boolean isEnemyUnit(TacticalMarkerType type) {
        if (type == null) {
            return false;
        }
        return type.name().startsWith("ENEMY_");
    }
}
