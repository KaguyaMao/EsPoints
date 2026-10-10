package com.example.espoints.tactical;

import java.util.Arrays;

/** 指挥官、小队/火力组组长及合法载具席位可放置的战术标点类型。 */
public enum TacticalMarkerType {
    ENEMY_INFANTRY("敌方步兵", 0xFFE05252),
    ENEMY_TANK("敌方坦克", 0xFFE05252),
    ENEMY_IFV("敌方步战车", 0xFFE05252),
    ENEMY_LIGHT_VEHICLE("敌方轻型载具", 0xFFE05252),
    ENEMY_HELICOPTER("敌方运输直升机", 0xFFE05252),
    ATTACK_HERE("进攻该处", 0xFFFFB52E, true, true),
    DEFEND_HERE("防守该处", 0xFF4D9DFF, true, true),
    ARTILLERY_TARGET("155炮击点", 0xFFFF3B30, false, false),
    ENEMY_MACHINE_GUNNER("敌方机枪手", 0xFFE05252),
    ENEMY_LIGHT_AT("敌方轻反坦克", 0xFFE05252),
    ENEMY_HEAVY_AT("敌方重反坦克", 0xFFE05252),
    ENEMY_SNIPER("敌方狙击手", 0xFFE05252),
    ENEMY_APC("敌方装甲运输车", 0xFFE05252),
    ENEMY_TRACKED_IFV("敌方履带步战车", 0xFFE05252),
    ENEMY_TRACKED_APC("敌方履带运输车", 0xFFE05252),
    ENEMY_RECON("敌方装甲侦察车", 0xFFE05252),
    ENEMY_ARMED_JEEP("敌方武装吉普", 0xFFE05252),
    ENEMY_AT_JEEP("敌方反坦克吉普", 0xFFE05252),
    ENEMY_MOTORCYCLE("敌方摩托车", 0xFFE05252),
    ENEMY_BOAT("敌方快艇", 0xFFE05252),
    ENEMY_LOGISTICS("敌方后勤卡车", 0xFFE05252),
    ENEMY_TRANSPORT_TRUCK("敌方运输卡车", 0xFFE05252),
    ENEMY_ANTI_AIR("敌方防空载具", 0xFFE05252),
    ENEMY_ARTILLERY_VEHICLE("敌方火炮载具", 0xFFE05252),
    ENEMY_ATTACK_HELICOPTER("敌方武装直升机", 0xFFE05252),
    ENEMY_SCOUT_HELICOPTER("敌方侦察直升机", 0xFFE05252),
    ENEMY_UAV("敌方无人机", 0xFFE05252),
    ENEMY_JET("敌方固定翼", 0xFFE05252),
    ENEMY_FOB("敌方电台", 0xFFE05252),
    ENEMY_HAB("敌方兵站", 0xFFE05252),
    ENEMY_RALLY("敌方集结点", 0xFFE05252),
    ENEMY_REPAIR("敌方维修站", 0xFFE05252),
    ENEMY_MORTAR("敌方迫击炮", 0xFFE05252),
    ENEMY_HMG("敌方固定机枪", 0xFFE05252),
    ENEMY_AT_WEAPON("敌方反坦克阵地", 0xFFE05252),
    ENEMY_AA_WEAPON("敌方防空阵地", 0xFFE05252),
    ENEMY_MINE("敌方地雷", 0xFFE05252),
    ENEMY_IED("敌方 IED", 0xFFE05252),
    MOVE_HERE("移动到此", 0xFFFFB52E, true, true),
    OBSERVE_HERE("观察此处", 0xFFFFB52E, true, true),
    REQUEST_AMMO("请求弹药", 0xFFFFB52E, true, true),
    REQUEST_CONSTRUCTION("请求建材", 0xFFFFB52E, true, true),
    REQUEST_PICKUP("请求接送", 0xFFFFB52E, true, true);

    private final String displayName;
    private final int color;
    private final boolean selectableFromMenu;
    private final boolean persistentUntilRemoved;

    TacticalMarkerType(String displayName, int color) {
        this(displayName, color, true, false);
    }

    TacticalMarkerType(String displayName, int color, boolean selectableFromMenu) {
        this(displayName, color, selectableFromMenu, false);
    }

    TacticalMarkerType(String displayName, int color, boolean selectableFromMenu,
                       boolean persistentUntilRemoved) {
        this.displayName = displayName;
        this.color = color;
        this.selectableFromMenu = selectableFromMenu;
        this.persistentUntilRemoved = persistentUntilRemoved;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        return color;
    }

    public boolean isSelectableFromMenu() {
        return selectableFromMenu;
    }

    /** 不参与 lifetime 清理，仅放置者手动删除（或战场 reset）。 */
    public boolean isPersistentUntilRemoved() {
        return persistentUntilRemoved;
    }

    public static TacticalMarkerType[] selectableValues() {
        return Arrays.stream(values())
            .filter(TacticalMarkerType::isSelectableFromMenu)
            .toArray(TacticalMarkerType[]::new);
    }

    public static TacticalMarkerType fromNetworkId(int id) {
        TacticalMarkerType[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
