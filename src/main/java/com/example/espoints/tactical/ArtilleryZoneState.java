package com.example.espoints.tactical;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端火炮覆盖区域缓存（本方可见）。
 *
 * <p>服务端在炮击确认后广播，地图（小地图 + J 菜单大地图）据此绘制覆盖区：
 * 定点炮击 = 圆（红边 + 红色半透明底 + 中心火炮图标）；
 * 徐进弹幕 = 长方形（同配色，宽度为火炮覆盖直径）。</p>
 */
public final class ArtilleryZoneState {

    /** 一块覆盖区。kind: "circle" / "line"。 */
    public record Zone(String kind, double x, double z, double endX, double endZ,
                       double radius, ResourceLocation icon, long endAtMs, String dimension) {
    }

    private static volatile List<Zone> zones = List.of();

    private ArtilleryZoneState() {
    }

    /** 服务端同步过来的完整列表（已过滤过期项）。 */
    public static void replace(List<Zone> incoming) {
        long now = System.currentTimeMillis();
        List<Zone> kept = new ArrayList<>();
        if (incoming != null) {
            for (Zone zone : incoming) {
                if (zone != null && zone.endAtMs() > now) {
                    kept.add(zone);
                }
            }
        }
        zones = List.copyOf(kept);
    }

    /** 当前应绘制的区域（按维度与过期时间过滤）。 */
    public static List<Zone> activeForCurrentDimension() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return List.of();
        }
        String dimension = mc.level.dimension().location().toString();
        long now = System.currentTimeMillis();
        List<Zone> result = new ArrayList<>();
        for (Zone zone : zones) {
            if (zone.endAtMs() <= now) {
                continue;
            }
            if (zone.dimension() != null && !zone.dimension().isBlank()
                && !zone.dimension().equals(dimension)) {
                continue;
            }
            result.add(zone);
        }
        return result;
    }

    /** 清空（换局/断线时用）。 */
    public static void clear() {
        zones = List.of();
    }

    /** 便捷：世界坐标是否落在某区域内（调试/扩展用）。 */
    public static boolean contains(double worldX, double worldZ) {
        for (Zone zone : activeForCurrentDimension()) {
            if ("circle".equals(zone.kind())) {
                double dx = worldX - zone.x();
                double dz = worldZ - zone.z();
                if (dx * dx + dz * dz <= zone.radius() * zone.radius()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 便于扩展：把 Vec3 转成世界坐标判定。 */
    public static boolean contains(Vec3 pos) {
        return pos != null && contains(pos.x, pos.z);
    }
}
