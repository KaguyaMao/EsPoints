package com.example.espoints.tactical;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * 载具地图图标：车型关键字表 + 编制 {@code map_icon} 覆盖。
 * 文件名白名单 / 存在性缓存 / 回退语义已抽到 {@link MapIconResolver}，与固定武器共用。
 */
public final class FriendlyVehicleIcons {
    private FriendlyVehicleIcons() { }

    /**
     * 编制 JSON {@code map_icon} 给的图标优先；缺省、非法或文件不存在时回退到车型关键字表。
     */
    public static ResourceLocation texture(String type, String mapIcon) {
        ResourceLocation resolved = MapIconResolver.resolve(mapIcon, fileFor(type), fileFor(type));
        return resolved == null ? textureFor(fileFor(type)) : resolved;
    }

    private static ResourceLocation textureFor(String fileName) {
        return MapIconResolver.textureFor(fileName);
    }

    public static String fileFor(String type) {
        String name = type.toLowerCase(Locale.ROOT);
        if (has(name, "logistic", "supply", "ural", "truck")) return "map_truck_logistics.png";
        if (has(name, "heli", "helicopter", "uh60", "mi8", "z19", "apache")) return "map_transporthelo.png";
        if (has(name, "uav", "drone")) return "map_uav.png";
        if (has(name, "boat", "ship")) return "map_boat.png";
        if (has(name, "antiair", "anti_air", "pantsir", "shilka")) return "map_antiair.png";
        if (has(name, "ifv", "bmp", "bradley", "lav", "zbd")) return "map_ifv.png";
        if (has(name, "apc", "btr", "m113", "stryker")) return "map_apc.png";
        if (has(name, "tank", "mbt", "abrams", "leopard", "t90", "t72", "t_90", "t_72", "ztz", "challenger")) return "map_tank.png";
        return "map_jeep.png";
    }
    /** 兼容旧调用点：只按车型关键字表选图标。 */
    public static ResourceLocation texture(String type) {
        return textureFor(fileFor(type));
    }
    private static boolean has(String name, String... fragments) {
        for (String fragment : fragments) if (name.contains(fragment)) return true;
        return false;
    }
}
