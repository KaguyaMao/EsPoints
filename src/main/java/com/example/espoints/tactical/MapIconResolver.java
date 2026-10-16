package com.example.espoints.tactical;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 战术地图「文件名驱动图标」的公共校验层：编制 {@code map_icon}（载具）与工事
 * {@code map_icon}（固定武器）共用同一套白名单 / 存在性缓存 / 回退语义，避免复制两份。
 *
 * <p>只允许 {@code textures/gui/mark/friendly/} 下的纯文件名：
 * 白名单正则拦掉路径穿越与非法字符，存在性检查拦掉打错的文件名（否则会画出紫黑格）。</p>
 */
public final class MapIconResolver {
    /** 图标文件名白名单：纯小写文件名 + .png，禁止任何路径分隔符。 */
    public static final Pattern SAFE_ICON_NAME = Pattern.compile("[a-z0-9_]+\\.png");
    /** 贴图目录：只允许从这里挑图标（客户端只解析这一个目录）。 */
    public static final String ICON_DIR = "textures/gui/mark/friendly/";
    /** 配置过的自定义图标名缓存（避免逐帧检查资源存在性）。 */
    private static final Map<String, Boolean> CUSTOM_ICON_CACHE = new ConcurrentHashMap<>();
    /** 资源管理器不可用（启动早期/测试环境）时置位，之后直接放行，交给纹理管理器处理。 */
    private static volatile boolean resourceLookupUnavailable;

    private MapIconResolver() {
    }

    /** 该目录下的完整 ResourceLocation。 */
    public static ResourceLocation textureFor(String fileName) {
        return ResourceLocation.fromNamespaceAndPath("espoints", ICON_DIR + fileName);
    }

    /**
     * 数据驱动图标是否可用：名字必须过白名单，且资源包里真的存在这个 PNG。
     */
    public static boolean isUsableIcon(String fileName) {
        if (fileName == null) return false;
        String name = fileName.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || !SAFE_ICON_NAME.matcher(name).matches()) return false;
        return iconExists(name);
    }

    /**
     * 按优先级挑第一个可用图标：配置值 → 兜底值 → {@code ultimateFallback}。
     * 全部不可用时返回 null，由调用方决定画什么。
     */
    public static ResourceLocation resolve(String configured, String fallback,
                                           String ultimateFallback) {
        if (isUsableIcon(configured)) {
            return textureFor(configured.trim().toLowerCase(Locale.ROOT));
        }
        if (isUsableIcon(fallback)) {
            return textureFor(fallback.trim().toLowerCase(Locale.ROOT));
        }
        if (isUsableIcon(ultimateFallback)) {
            return textureFor(ultimateFallback.trim().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    /**
     * 存在性检查。第一次遇到某个名字时查一次资源包；不存在就永久回退，
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
}
