package com.example.espoints.tactical;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class FriendlyVehicleIcons {
    /** 编制 JSON 的 map_icon 只允许这种纯文件名，避免路径穿越与非法字符。 */
    private static final Pattern SAFE_ICON_NAME = Pattern.compile("[a-z0-9_]+\\.png");
    /** 贴图目录：只允许从这里挑图标。 */
    private static final String ICON_DIR = "textures/gui/mark/friendly/";
    /** 编制里配置过的自定义图标名缓存（避免逐帧检查资源存在性）。 */
    private static final Map<String, Boolean> CUSTOM_ICON_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /** 资源管理器不可用（启动早期/测试环境）时置位，之后直接放行，交给纹理管理器处理。 */
    private static volatile boolean resourceLookupUnavailable;
    private FriendlyVehicleIcons() { }

    /**
     * 编制 JSON {@code map_icon} 给的图标优先；缺省、非法或文件不存在时回退到车型关键字表。
     */
    public static ResourceLocation texture(String type, String mapIcon) {
        String fallback = fileFor(type);
        if (mapIcon == null) return textureFor(fallback);
        String name = mapIcon.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) return textureFor(fallback);
        if (!SAFE_ICON_NAME.matcher(name).matches()) return textureFor(fallback);
        return iconExists(name) ? textureFor(name) : textureFor(fallback);
    }

    private static ResourceLocation textureFor(String fileName) {
        return ResourceLocation.fromNamespaceAndPath("espoints", ICON_DIR + fileName);
    }

    /**
     * 数据驱动图标的存在性检查。第一次遇到某个名字时查一次资源包；不存在就永久回退，
     * 避免纹理管理器画出紫黑格。资源管理器不可用（启动早期/测试环境）时按可用处理。
     */
    private static boolean iconExists(String name) {
        Boolean cached = CUSTOM_ICON_CACHE.get(name);
        if (cached != null) return cached;
        if (resourceLookupUnavailable) return true;
        Boolean resolved = resolveIconPresence(name);
        if (resolved == null) {
            resourceLookupUnavailable = true;
            return true;
        }
        CUSTOM_ICON_CACHE.putIfAbsent(name, resolved);
        return resolved;
    }

    private static Boolean resolveIconPresence(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return null;
        try {
            return minecraft.getResourceManager().getResource(
                ResourceLocation.fromNamespaceAndPath("espoints", ICON_DIR + name)).isPresent();
        } catch (RuntimeException e) {
            return null;
        }
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
